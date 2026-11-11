package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.calculations.assertDecimal
import br.com.joaoduartemariucio.rodaro.calculations.day
import br.com.joaoduartemariucio.rodaro.calculations.decimal
import br.com.joaoduartemariucio.rodaro.calculations.dueItem
import br.com.joaoduartemariucio.rodaro.calculations.testVehicleId
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RepositoryError
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueItem
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueKind
import br.com.joaoduartemariucio.rodaro.domain.dues.repositories.DueItemRepository
import br.com.joaoduartemariucio.rodaro.fakes.FakeAnalytics
import br.com.joaoduartemariucio.rodaro.fakes.FixedDateProvider
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryDueItemRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryExpenseRepository
import br.com.joaoduartemariucio.rodaro.presentation.common.FormState
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.common.ValidationIssue
import br.com.joaoduartemariucio.rodaro.presentation.dues.payment.DuePaymentViewModel
import kotlinx.coroutines.test.runTest
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

class DuePaymentViewModelTests {
    private val ipva = dueItem(DueKind.IPVA, LocalDate.parse("2001-03-01"), amount = "812.45")
    private val license = dueItem(DueKind.CNH, LocalDate.parse("2001-06-01"), vehicleId = null)

    private fun makeViewModel(
        item: DueItem = ipva,
        dues: DueItemRepository = InMemoryDueItemRepository(listOf(item)),
        expenses: InMemoryExpenseRepository = InMemoryExpenseRepository(),
        analytics: FakeAnalytics = FakeAnalytics(),
    ) = DuePaymentViewModel(item, dues, expenses, FixedDateProvider(day(50)), analytics)

    @Test
    fun `paying creates the expense with the item id and marks the item paid`() =
        runTest {
            val dues = InMemoryDueItemRepository(listOf(ipva))
            val expenses = InMemoryExpenseRepository()
            val analytics = FakeAnalytics()
            val viewModel = makeViewModel(dues = dues, expenses = expenses, analytics = analytics)

            assertTrue(viewModel.offersExpense)
            assertTrue(viewModel.createsExpense)
            assertDecimal("812.45", viewModel.parsedAmount)
            viewModel.paidOn = day(48)
            viewModel.confirm()

            assertEquals(FormState.Saved, viewModel.state)
            val expense = expenses.expenses(testVehicleId).single()
            assertEquals(ipva.id, expense.id)
            assertEquals(ExpenseCategory.IPVA, expense.category)
            assertEquals(day(48), expense.date)
            assertEquals(day(48), dues.items(testVehicleId).first { it.id == ipva.id }.paidAt)
            assertEquals("true", analytics.parameters("due_item_paid")?.get("expense"))
            assertEquals(LocalDate.parse("2002-03-01"), viewModel.suggestion?.dueDate)
        }

    @Test
    fun `declining the expense creates none and keeps the typed amount on the item`() =
        runTest {
            val dues = InMemoryDueItemRepository(listOf(ipva))
            val expenses = InMemoryExpenseRepository()
            val viewModel = makeViewModel(dues = dues, expenses = expenses)
            viewModel.createsExpense = false
            viewModel.amount = NumberInput.text(decimal("800"), NumberInput.Style.MONEY)

            viewModel.confirm()

            assertTrue(expenses.expenses(testVehicleId).isEmpty())
            assertDecimal("800", dues.items(testVehicleId).single().amount)
        }

    @Test
    fun `creating an expense needs an amount`() {
        val viewModel = makeViewModel(item = ipva.copy(amount = null))

        assertEquals(listOf(ValidationIssue.AmountRequired), viewModel.issues)
        assertFalse(viewModel.canConfirm)

        viewModel.createsExpense = false
        assertTrue(viewModel.canConfirm)
    }

    @Test
    fun `renewing the driver license creates no expense and suggests nothing`() =
        runTest {
            val dues = InMemoryDueItemRepository(listOf(license))
            val expenses = InMemoryExpenseRepository()
            val viewModel = makeViewModel(item = license, dues = dues, expenses = expenses)

            assertFalse(viewModel.offersExpense)
            viewModel.confirm()

            assertEquals(day(50), dues.driverItems().single().paidAt)
            assertNull(viewModel.suggestion)
            assertTrue(viewModel.issues.isEmpty())
        }

    @Test
    fun `accepting the suggestion saves next year's item and declining drops it`() =
        runTest {
            val dues = InMemoryDueItemRepository(listOf(ipva))
            val analytics = FakeAnalytics()
            val viewModel = makeViewModel(dues = dues, analytics = analytics)
            viewModel.confirm()
            val suggestion = assertNotNull(viewModel.suggestion)

            viewModel.acceptSuggestion()

            assertNull(viewModel.suggestion)
            assertEquals(listOf(ipva.id, suggestion.id), dues.items(testVehicleId).map { it.id })
            assertNull(dues.items(testVehicleId).last().paidAt)
            assertTrue("due_item_next_year_created" in analytics.names())

            viewModel.acceptSuggestion()
            assertEquals(2, dues.items(testVehicleId).size)

            val other = makeViewModel()
            other.confirm()
            other.declineSuggestion()
            assertNull(other.suggestion)
        }

    @Test
    fun `a failure after the expense was saved can be retried without duplicating it`() =
        runTest {
            val dues = InMemoryDueItemRepository(listOf(ipva), failure = RepositoryError.PersistenceFailed(IllegalStateException("disk")))
            val expenses = InMemoryExpenseRepository()
            val viewModel = makeViewModel(dues = dues, expenses = expenses)

            viewModel.confirm()
            assertIs<FormState.Failed>(viewModel.state)
            assertEquals(1, expenses.expenses(testVehicleId).size)

            dues.failure = null
            viewModel.confirm()

            assertEquals(FormState.Saved, viewModel.state)
            assertEquals(1, expenses.expenses(testVehicleId).size)
            assertTrue(dues.items(testVehicleId).single().isPaid)
        }

    @Test
    fun `saving the suggestion can fail`() =
        runTest {
            val dues = InMemoryDueItemRepository(listOf(ipva))
            val viewModel = makeViewModel(dues = dues)
            viewModel.confirm()
            dues.failure = RepositoryError.PersistenceFailed(IllegalStateException("disk"))

            viewModel.acceptSuggestion()

            assertIs<FormState.Failed>(viewModel.state)
        }

    @Test
    fun `cancellation is rethrown`() =
        runTest {
            val cancelling =
                object : DueItemRepository {
                    override suspend fun items(vehicleId: UUID): List<DueItem> = emptyList()

                    override suspend fun driverItems(): List<DueItem> = emptyList()

                    override suspend fun all(): List<DueItem> = emptyList()

                    override suspend fun save(item: DueItem): Unit = throw CancellationException("gone")

                    override suspend fun delete(id: UUID) = Unit
                }

            assertFailsWith<CancellationException> { makeViewModel(dues = cancelling).confirm() }
        }
}
