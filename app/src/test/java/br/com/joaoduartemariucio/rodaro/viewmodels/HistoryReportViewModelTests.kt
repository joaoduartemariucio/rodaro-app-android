package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.calculations.testVehicleId
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RepositoryError
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.fakes.FakeAnalytics
import br.com.joaoduartemariucio.rodaro.fakes.FakeReportRenderer
import br.com.joaoduartemariucio.rodaro.fakes.FixedDateProvider
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryFuelEntryRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryReportFileStore
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryServiceRepository
import br.com.joaoduartemariucio.rodaro.fakes.TestLocalizer
import br.com.joaoduartemariucio.rodaro.presentation.common.FormState
import br.com.joaoduartemariucio.rodaro.presentation.reports.HistoryReportViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryReportViewModelTests {
    private val vehicle = Vehicle(id = testVehicleId, name = "Fusca Azul 1.6 ção", plate = "ABC1D23", createdAt = Instant.parse("2025-01-01T00:00:00Z"))
    private val files = InMemoryReportFileStore()
    private val renderer = FakeReportRenderer()
    private val analytics = FakeAnalytics()
    private val localizer = TestLocalizer()

    private fun makeViewModel(vehicle: Vehicle = this.vehicle) =
        HistoryReportViewModel(
            vehicle = vehicle,
            fuelEntryRepository =
                InMemoryFuelEntryRepository(
                    listOf(FuelEntry(vehicleId = testVehicleId, date = Instant.parse("2025-02-01T00:00:00Z"), odometer = 1_000.0, liters = 30.0, pricePerLiter = BigDecimal(5), isFullTank = true)),
                ),
            serviceRepository = InMemoryServiceRepository(),
            files = files,
            renderer = renderer,
            dateProvider = FixedDateProvider(Instant.parse("2026-11-11T12:00:00Z")),
            analytics = analytics,
        )

    @Test
    fun `sharing renders the history and returns the shareable uri`() =
        runTest {
            val viewModel = makeViewModel()

            val uri = viewModel.share(localizer)

            assertEquals("rodaro-historico-fusca-azul-1-6-cao-2026-11-11.pdf", viewModel.fileName)
            assertEquals("content://test/${viewModel.fileName}", uri)
            assertTrue(files.shared.getValue(viewModel.fileName).isNotEmpty())
            assertEquals(FormState.Saved, viewModel.state)
            assertEquals(listOf("Page 1 of 2"), renderer.labels)
            assertTrue(
                renderer.documents
                    .single()
                    .text
                    .contains("ABC1D**"),
            )
            assertEquals(
                mapOf("include_costs" to "false", "include_fuel_log" to "false", "full_plate" to "false"),
                analytics.parameters("history_pdf_shared"),
            )
        }

    @Test
    fun `saving writes the bytes where the user picked, with the chosen options`() =
        runTest {
            val viewModel = makeViewModel()
            viewModel.includeCosts = true
            viewModel.includeFuelLog = true
            viewModel.showFullPlate = true

            assertTrue(viewModel.save("content://docs/report.pdf", localizer))

            assertTrue(files.written.getValue("content://docs/report.pdf").isNotEmpty())
            assertTrue(
                renderer.documents
                    .single()
                    .text
                    .contains("ABC1D23"),
            )
            assertEquals(
                mapOf("include_costs" to "true", "include_fuel_log" to "true", "full_plate" to "true"),
                analytics.parameters("history_pdf_saved"),
            )
            assertEquals("rodaro-historico-veiculo-2026-11-11.pdf", makeViewModel(vehicle.copy(name = "!!!")).fileName)
        }

    @Test
    fun `a storage or rendering failure is shown and nothing is reported`() =
        runTest {
            files.failure = RepositoryError.PersistenceFailed(IllegalStateException("disk"))
            val viewModel = makeViewModel()

            assertNull(viewModel.share(localizer))
            assertIs<FormState.Failed>(viewModel.state)
            assertFalse(viewModel.save("content://x", localizer))
            assertTrue(analytics.events.isEmpty())

            files.failure = null
            renderer.failure = IllegalStateException("pdf")
            assertNull(makeViewModel().share(localizer))
        }

    @Test
    fun `cancellation is rethrown and the form can be used again`() =
        runTest {
            renderer.failure = CancellationException("gone")
            val viewModel = makeViewModel()

            assertFailsWith<CancellationException> { viewModel.share(localizer) }
            assertEquals(FormState.Editing, viewModel.state)
        }
}
