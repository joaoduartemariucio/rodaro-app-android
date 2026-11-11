package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.calculations.assertDecimal
import br.com.joaoduartemariucio.rodaro.calculations.day
import br.com.joaoduartemariucio.rodaro.calculations.decimal
import br.com.joaoduartemariucio.rodaro.calculations.dueItem
import br.com.joaoduartemariucio.rodaro.calculations.testVehicleId
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RepositoryError
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueKind
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.fakes.FakeAnalytics
import br.com.joaoduartemariucio.rodaro.fakes.FixedDateProvider
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryDueItemRepository
import br.com.joaoduartemariucio.rodaro.fakes.TestLocalizer
import br.com.joaoduartemariucio.rodaro.presentation.common.FormState
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.common.ValidationIssue
import br.com.joaoduartemariucio.rodaro.presentation.dues.form.DueItemFormViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DueItemFormViewModelTests {
    private val vehicle = Vehicle(id = testVehicleId, name = "Gol", plate = "ABC1D23", uf = "SP", createdAt = day(0))
    private val portuguese = TestLocalizer("values-pt-rBR")

    private fun makeViewModel(
        mode: DueItemFormViewModel.Mode = DueItemFormViewModel.Mode.Create(vehicle),
        repository: InMemoryDueItemRepository = InMemoryDueItemRepository(),
        analytics: FakeAnalytics = FakeAnalytics(),
    ) = DueItemFormViewModel(mode, repository, FixedDateProvider(day(30)), analytics)

    @Test
    fun `a vehicle offers IPVA, licensing and insurance while the driver only has the license`() {
        val forVehicle = makeViewModel()
        val forDriver = makeViewModel(DueItemFormViewModel.Mode.Create(vehicle = null))

        assertEquals(listOf(DueKind.IPVA, DueKind.LICENCIAMENTO, DueKind.SEGURO), forVehicle.availableKinds)
        assertEquals(DueKind.IPVA, forVehicle.kind)
        assertEquals(LocalDate.parse("2001-01-31"), forVehicle.dueDate)
        assertEquals(listOf(DueKind.CNH), forDriver.availableKinds)
        assertEquals(DueKind.CNH, forDriver.kind)
        assertTrue(forDriver.isDriverItem)
        assertEquals(DueKind.SEGURO, makeViewModel(DueItemFormViewModel.Mode.Create(vehicle, DueKind.SEGURO)).kind)
        assertEquals(DueKind.IPVA, makeViewModel(DueItemFormViewModel.Mode.Create(vehicle, DueKind.CNH)).kind)
    }

    @Test
    fun `installments need both numbers, the number not above the total`() {
        val viewModel = makeViewModel()
        assertTrue(viewModel.issues.isEmpty())

        viewModel.installmentNumber = "2"
        assertEquals(listOf(ValidationIssue.InstallmentInvalid), viewModel.issues)
        assertFalse(viewModel.canSave)

        viewModel.installmentCount = "1"
        assertEquals(listOf(ValidationIssue.InstallmentInvalid), viewModel.issues)

        viewModel.installmentCount = "3x"
        assertEquals("3", viewModel.installmentCount)
        assertTrue(viewModel.issues.isEmpty())

        viewModel.installmentNumber = "0"
        assertEquals(listOf(ValidationIssue.InstallmentInvalid), viewModel.issues)
    }

    @Test
    fun `the hint points to the state calendar with the plate final digit`() {
        assertEquals('3', makeViewModel().plateFinalDigit)
        assertEquals("SP", makeViewModel().uf)
        assertEquals("Final da placa 3: consulte o calendário da Sefaz/Detran (SP) para a data exata.", makeViewModel().calendarHint(portuguese))

        val noPlate = makeViewModel(DueItemFormViewModel.Mode.Create(vehicle.copy(plate = null, uf = null)))
        assertNull(noPlate.plateFinalDigit)
        assertEquals("Consulte o calendário da Sefaz/Detran (do seu estado) para a data exata.", noPlate.calendarHint(portuguese))

        assertTrue(makeViewModel(DueItemFormViewModel.Mode.Create(null)).calendarHint(portuguese).contains("renovação automática"))
    }

    @Test
    fun `saving creates the item with now as createdAt and reports it`() =
        runTest {
            val repository = InMemoryDueItemRepository()
            val analytics = FakeAnalytics()
            val viewModel = makeViewModel(repository = repository, analytics = analytics)
            viewModel.kind = DueKind.LICENCIAMENTO
            viewModel.dueDate = LocalDate.parse("2001-09-30")
            viewModel.amount = NumberInput.text(decimal("160.22"), NumberInput.Style.MONEY)
            viewModel.installmentNumber = "1"
            viewModel.installmentCount = "1"
            viewModel.note = "  CRLV  "

            viewModel.save()

            val saved = repository.items(testVehicleId).single()
            assertEquals(FormState.Saved, viewModel.state)
            assertEquals(DueKind.LICENCIAMENTO, saved.kind)
            assertEquals(LocalDate.parse("2001-09-30"), saved.dueDate)
            assertDecimal("160.22", saved.amount)
            assertEquals(1, saved.installmentCount)
            assertEquals("CRLV", saved.note)
            assertEquals(day(30), saved.createdAt)
            assertEquals("licensing", analytics.parameters("due_item_saved")?.get("kind"))
        }

    @Test
    fun `a driver license has no vehicle and editing keeps the identity`() =
        runTest {
            val repository = InMemoryDueItemRepository()
            val created = makeViewModel(DueItemFormViewModel.Mode.Create(null), repository)
            created.save()
            val license = repository.driverItems().single()
            assertNull(license.vehicleId)
            assertNull(license.amount)

            val edit = makeViewModel(DueItemFormViewModel.Mode.Edit(license, null), repository)
            edit.dueDate = LocalDate.parse("2006-01-01")
            edit.save()

            assertEquals(listOf(license.copy(dueDate = LocalDate.parse("2006-01-01"))), repository.driverItems())
        }

    @Test
    fun `editing prefills the fields and a failure is shown`() =
        runTest {
            val item = dueItem(DueKind.SEGURO, LocalDate.parse("2001-05-01"), amount = "2450.37").copy(installmentNumber = 2, installmentCount = 4, note = "Porto")
            val viewModel = makeViewModel(DueItemFormViewModel.Mode.Edit(item, vehicle), InMemoryDueItemRepository(failure = RepositoryError.EntryNotFound(item.id)))

            assertEquals(DueKind.SEGURO, viewModel.kind)
            assertDecimal("2450.37", viewModel.parsedAmount)
            assertEquals("2", viewModel.installmentNumber)
            assertEquals("Porto", viewModel.note)

            viewModel.save()
            assertIs<FormState.Failed>(viewModel.state)

            viewModel.installmentNumber = "5"
            val before = viewModel.state
            viewModel.save()
            assertEquals(before, viewModel.state)
        }
}
