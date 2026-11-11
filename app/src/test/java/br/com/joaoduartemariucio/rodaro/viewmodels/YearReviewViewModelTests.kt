package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.calculations.testVehicleId
import br.com.joaoduartemariucio.rodaro.calculations.utc
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RepositoryError
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.fueling.repositories.FuelEntryRepository
import br.com.joaoduartemariucio.rodaro.domain.reports.calculations.YearReview
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.fakes.FakeAnalytics
import br.com.joaoduartemariucio.rodaro.fakes.FixedDateProvider
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryExpenseRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryFuelEntryRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryServiceRepository
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.review.YearReviewViewModel
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.review.YearReviewViewModel.State
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YearReviewViewModelTests {
    private val vehicle = Vehicle(id = testVehicleId, name = "Gol", initialOdometer = 10_000.0, createdAt = at("2023-12-15"))
    private val analytics = FakeAnalytics()

    private fun at(date: String): Instant = LocalDate.parse(date).atStartOfDay(utc).toInstant()

    private val fills: List<FuelEntry> =
        (0..26).map { index ->
            val month = YearMonth.of(2023, 12).plusMonths(index.toLong())
            FuelEntry(
                vehicleId = testVehicleId,
                date = month.atDay(15).atStartOfDay(utc).toInstant(),
                odometer = 10_000.0 + 1_000.0 * index,
                liters = 40.0,
                pricePerLiter = BigDecimal(5),
                isFullTank = true,
                fuelType = FuelType.GASOLINA_COMUM,
            )
        }

    private fun makeViewModel(
        fuel: FuelEntryRepository = InMemoryFuelEntryRepository(fills),
        now: String = "2026-03-01",
    ) = YearReviewViewModel(vehicle, fuel, InMemoryServiceRepository(), InMemoryExpenseRepository(), FixedDateProvider(at(now)), analytics)

    @Test
    fun `the newest year is selected by default and reported without values`() =
        runTest {
            val viewModel = makeViewModel()

            viewModel.load()

            assertEquals(State.Loaded, viewModel.state)
            assertEquals(listOf(2026, 2025, 2024, 2023).filter { it < 2026 }, viewModel.availableYears)
            assertEquals(2025, viewModel.selectedYear)
            assertEquals(2025, viewModel.review?.year)
            assertEquals(mapOf("year_offset" to "1"), analytics.parameters("year_review_viewed"))
        }

    @Test
    fun `selecting a year recomputes the review and keeps it on reload`() =
        runTest {
            val viewModel = makeViewModel()
            viewModel.load()

            viewModel.select(2024)
            viewModel.load()

            assertEquals(2024, viewModel.selectedYear)
            assertEquals(2024, viewModel.review?.year)
            assertEquals(mapOf("year_offset" to "2"), analytics.parameters("year_review_viewed"))
        }

    @Test
    fun `a year without enough data says what is missing`() =
        runTest {
            val viewModel = makeViewModel()
            viewModel.load()

            viewModel.select(2023)

            assertNull(viewModel.review)
            assertTrue(YearReview.Shortfall.FUEL_UPS in viewModel.shortfalls)
        }

    @Test
    fun `no records means no years`() =
        runTest {
            val viewModel = makeViewModel(fuel = InMemoryFuelEntryRepository())

            viewModel.load()

            assertTrue(viewModel.availableYears.isEmpty())
            assertNull(viewModel.selectedYear)
            assertNull(viewModel.review)
        }

    @Test
    fun `a storage failure is shown and cancellation is rethrown`() =
        runTest {
            val failing = makeViewModel(fuel = InMemoryFuelEntryRepository(failure = RepositoryError.PersistenceFailed(IllegalStateException())))
            failing.load()
            assertIs<State.Failed>(failing.state)

            val cancelling =
                object : FuelEntryRepository {
                    override suspend fun entries(vehicleId: UUID): List<FuelEntry> = throw CancellationException("gone")

                    override suspend fun save(entry: FuelEntry) = Unit

                    override suspend fun delete(id: UUID) = Unit
                }
            assertFailsWith<CancellationException> { makeViewModel(fuel = cancelling).load() }
            assertNotNull(makeViewModel().vehicle)
        }
}
