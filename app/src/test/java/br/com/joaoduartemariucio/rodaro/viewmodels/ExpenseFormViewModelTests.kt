package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.calculations.assertDecimal
import br.com.joaoduartemariucio.rodaro.calculations.day
import br.com.joaoduartemariucio.rodaro.calculations.decimal
import br.com.joaoduartemariucio.rodaro.calculations.expense
import br.com.joaoduartemariucio.rodaro.calculations.testVehicleId
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RepositoryError
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDetails
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDiscount
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineSeverity
import br.com.joaoduartemariucio.rodaro.domain.costs.repositories.ExpenseRepository
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.Preferences
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.UnitSystem
import br.com.joaoduartemariucio.rodaro.fakes.FakeAnalytics
import br.com.joaoduartemariucio.rodaro.fakes.FixedDateProvider
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryExpenseRepository
import br.com.joaoduartemariucio.rodaro.fakes.TestLocalizer
import br.com.joaoduartemariucio.rodaro.presentation.common.DisplayPreferences
import br.com.joaoduartemariucio.rodaro.presentation.common.FormState
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.common.ValidationIssue
import br.com.joaoduartemariucio.rodaro.presentation.costs.form.ExpenseFormViewModel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExpenseFormViewModelTests {
    @After
    fun tearDown() {
        DisplayPreferences.update(Preferences())
    }

    private fun makeViewModel(
        mode: ExpenseFormViewModel.Mode = ExpenseFormViewModel.Mode.Create(testVehicleId),
        repository: ExpenseRepository = InMemoryExpenseRepository(),
        analytics: FakeAnalytics = FakeAnalytics(),
    ) = ExpenseFormViewModel(mode, repository, FixedDateProvider(day(30)), analytics)

    private fun money(value: String) = NumberInput.text(decimal(value), NumberInput.Style.MONEY)

    @Test
    fun `a new expense needs an amount`() {
        val viewModel = makeViewModel()

        assertEquals(listOf(ValidationIssue.AmountRequired), viewModel.issues)
        assertFalse(viewModel.canSave)

        viewModel.amount = money("0")
        assertEquals(listOf(ValidationIssue.AmountRequired), viewModel.issues)

        viewModel.amount = money("12.40")
        assertTrue(viewModel.issues.isEmpty())
    }

    @Test
    fun `saving creates the expense with now as createdAt and reports it`() =
        runTest {
            val repository = InMemoryExpenseRepository()
            val analytics = FakeAnalytics()
            val viewModel = makeViewModel(repository = repository, analytics = analytics)
            viewModel.category = ExpenseCategory.ESTACIONAMENTO
            viewModel.amount = money("25")
            viewModel.date = day(10)
            viewModel.odometer = NumberInput.text(1_234.0, NumberInput.Style.KILOMETERS)
            viewModel.note = "  Shopping  "

            viewModel.save()

            val saved = repository.expenses(testVehicleId).single()
            assertEquals(FormState.Saved, viewModel.state)
            assertEquals(ExpenseCategory.ESTACIONAMENTO, saved.category)
            assertDecimal("25", saved.amount)
            assertEquals(1_234.0, saved.odometer)
            assertEquals("Shopping", saved.note)
            assertEquals(day(10), saved.date)
            assertEquals(day(30), saved.createdAt)
            assertNull(saved.fine)
            assertEquals("parking", analytics.parameters("expense_saved")?.get("category"))
            assertEquals("create", analytics.parameters("expense_saved")?.get("mode"))
        }

    @Test
    fun `a refund is saved as a negative amount and read back as a toggle`() =
        runTest {
            val repository = InMemoryExpenseRepository()
            val viewModel = makeViewModel(repository = repository)
            viewModel.amount = money("35.10")
            viewModel.isRefund = true

            viewModel.save()

            val saved = repository.expenses(testVehicleId).single()
            assertDecimal("-35.10", saved.amount)

            val edit = makeViewModel(ExpenseFormViewModel.Mode.Edit(saved), repository)
            assertTrue(edit.isRefund)
            assertEquals(money("35.10"), edit.amount)
            assertDecimal("-35.10", edit.parsedAmount)
        }

    @Test
    fun `editing keeps the identity, createdAt and the edited fields`() =
        runTest {
            val existing = expense(ExpenseCategory.LAVAGEM, "60", dayOffset = 5).copy(createdAt = day(6), note = "Lavagem")
            val repository = InMemoryExpenseRepository(listOf(existing))
            val analytics = FakeAnalytics()
            val viewModel = makeViewModel(ExpenseFormViewModel.Mode.Edit(existing), repository, analytics)

            assertEquals(ExpenseCategory.LAVAGEM, viewModel.category)
            assertEquals("Lavagem", viewModel.note)
            viewModel.amount = money("70")
            viewModel.save()

            val saved = repository.expenses(testVehicleId).single()
            assertEquals(existing.id, saved.id)
            assertEquals(day(6), saved.createdAt)
            assertDecimal("70", saved.amount)
            assertEquals("edit", analytics.parameters("expense_saved")?.get("mode"))
        }

    @Test
    fun `coverage is only for fixed categories and must not end before it starts`() =
        runTest {
            val repository = InMemoryExpenseRepository()
            val viewModel = makeViewModel(repository = repository)
            viewModel.amount = money("1200")
            viewModel.category = ExpenseCategory.SEGURO
            viewModel.coverageStart = LocalDate.parse("2001-03-01")
            viewModel.coverageEnd = LocalDate.parse("2001-02-01")

            assertTrue(viewModel.isFixed)
            assertEquals(listOf(ValidationIssue.CoverageEndBeforeStart), viewModel.issues)

            viewModel.coverageEnd = LocalDate.parse("2002-02-28")
            viewModel.save()
            assertEquals(LocalDate.parse("2001-03-01"), repository.expenses(testVehicleId).single().coverageStart)

            val toll = makeViewModel(repository = repository)
            toll.amount = money("10")
            toll.coverageStart = LocalDate.parse("2001-03-01")
            toll.coverageEnd = LocalDate.parse("2001-02-01")
            assertTrue(toll.issues.isEmpty())
            toll.save()
            assertNull(repository.expenses(testVehicleId).first { it.category == ExpenseCategory.PEDAGIO }.coverageStart)
        }

    @Test
    fun `fixed categories show the default coverage as a hint`() {
        val viewModel = makeViewModel()
        viewModel.date = day(73)

        assertNull(viewModel.defaultCoverage)
        assertNull(viewModel.coverageHint(TestLocalizer()))

        viewModel.category = ExpenseCategory.IPVA
        assertEquals(LocalDate.parse("2001-01-01")..LocalDate.parse("2001-12-31"), viewModel.defaultCoverage)

        viewModel.category = ExpenseCategory.LICENCIAMENTO
        assertEquals(LocalDate.parse("2001-03-15")..LocalDate.parse("2002-03-14"), viewModel.defaultCoverage)
        assertTrue(assertNotNull(viewModel.coverageHint(TestLocalizer())).startsWith("If left empty, it covers"))
    }

    @Test
    fun `choosing a severity fills the amount and points, the discount adjusts them, typed values are kept`() {
        val viewModel = makeViewModel()
        viewModel.category = ExpenseCategory.MULTA

        viewModel.severity = FineSeverity.GRAVE
        assertDecimal("195.23", viewModel.parsedAmount)
        assertEquals(5, viewModel.parsedPoints)

        viewModel.discount = FineDiscount.TWENTY
        assertDecimal("156.18", viewModel.parsedAmount)
        assertFalse(viewModel.showsDiscountCaveat)

        viewModel.discount = FineDiscount.FORTY
        assertDecimal("117.14", viewModel.parsedAmount)
        assertTrue(viewModel.showsDiscountCaveat)

        viewModel.amount = money("150")
        viewModel.points = "7a"
        viewModel.severity = FineSeverity.GRAVISSIMA
        assertDecimal("150", viewModel.parsedAmount)
        assertEquals("7", viewModel.points)

        viewModel.severity = null
        assertNull(viewModel.severity)
    }

    @Test
    fun `fine details are saved only for fines`() =
        runTest {
            val repository = InMemoryExpenseRepository()
            val viewModel = makeViewModel(repository = repository)
            viewModel.category = ExpenseCategory.MULTA
            viewModel.severity = FineSeverity.LEVE
            viewModel.infractionDate = LocalDate.parse("2001-01-10")
            viewModel.dueDate = LocalDate.parse("2001-02-20")
            viewModel.isPaid = false

            viewModel.save()

            val fine = assertNotNull(repository.expenses(testVehicleId).single().fine)
            assertEquals(FineDetails(LocalDate.parse("2001-01-10"), FineSeverity.LEVE, 3, LocalDate.parse("2001-02-20"), FineDiscount.NONE, false), fine)

            val edit = makeViewModel(ExpenseFormViewModel.Mode.Edit(repository.expenses(testVehicleId).single()), repository)
            assertEquals(FineSeverity.LEVE, edit.severity)
            assertFalse(edit.isPaid)
            edit.category = ExpenseCategory.OUTROS
            edit.save()
            assertNull(repository.expenses(testVehicleId).single().fine)
        }

    @Test
    fun `the odometer is optional and typed in miles under the imperial system`() =
        runTest {
            DisplayPreferences.update(Preferences(unitSystem = UnitSystem.IMPERIAL))
            val repository = InMemoryExpenseRepository()
            val viewModel = makeViewModel(repository = repository)
            viewModel.amount = money("10")
            viewModel.odometer = NumberInput.text(100.0, NumberInput.Style.KILOMETERS)

            viewModel.save()

            assertEquals(160.9344, assertNotNull(repository.expenses(testVehicleId).single().odometer), 0.001)
            assertNull(makeViewModel().parsedOdometer)
        }

    @Test
    fun `a blocking issue prevents saving and a failure is shown`() =
        runTest {
            val repository = InMemoryExpenseRepository()
            val blocked = makeViewModel(repository = repository)
            blocked.save()
            assertEquals(FormState.Editing, blocked.state)
            assertTrue(repository.expenses(testVehicleId).isEmpty())

            val failing = makeViewModel(repository = InMemoryExpenseRepository(failure = RepositoryError.VehicleNotFound(testVehicleId)))
            failing.amount = money("10")
            failing.save()
            assertIs<FormState.Failed>(failing.state)
        }

    @Test
    fun `cancellation is rethrown`() =
        runTest {
            val cancelling =
                object : ExpenseRepository {
                    override suspend fun expenses(vehicleId: UUID): List<Expense> = emptyList()

                    override suspend fun save(expense: Expense): Unit = throw CancellationException("gone")

                    override suspend fun delete(id: UUID) = Unit
                }
            val viewModel = makeViewModel(repository = cancelling)
            viewModel.amount = money("10")

            assertFailsWith<CancellationException> { viewModel.save() }
        }
}
