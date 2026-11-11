package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.calculations.quote
import br.com.joaoduartemariucio.rodaro.calculations.selection
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RemoteDataError
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.CurrencyOption
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.Preferences
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipePrice
import br.com.joaoduartemariucio.rodaro.domain.valuation.services.FipeCatalog
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import br.com.joaoduartemariucio.rodaro.fakes.FakeFipeCatalog
import br.com.joaoduartemariucio.rodaro.fakes.FixedDateProvider
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryDueItemRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryExpenseRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryFipeQuoteRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryFuelEntryRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryServiceRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryVehicleRepository
import br.com.joaoduartemariucio.rodaro.presentation.common.DisplayPreferences
import br.com.joaoduartemariucio.rodaro.presentation.common.UnitConversion
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedShortMonthAndYear
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.RefreshBackoff
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.VehicleDetailViewModel
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.VehicleDetailViewModel.State
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.VehicleDetailViewModel.ValuationStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VehicleValuationTests {
    private val now = Instant.parse("2026-12-10T12:00:00Z")
    private val vehicle =
        Vehicle(
            name = "Amarok",
            kind = VehicleKind.CAR,
            initialOdometer = 10_000.0,
            createdAt = Instant.parse("2026-05-01T00:00:00Z"),
            fipe = selection(),
        )
    private val fake = FakeFipeCatalog()

    @Before
    fun setUp() {
        DisplayPreferences.update(Preferences(currency = CurrencyOption.BRL))
    }

    @After
    fun tearDown() {
        DisplayPreferences.update(Preferences())
    }

    private fun stored(
        month: Int,
        price: String,
        fetchedAt: Instant = Instant.parse("2026-%02d-05T00:00:00Z".format(month)),
    ) = quote(YearMonth.of(2026, month), price, fetchedAt = fetchedAt, vehicleId = vehicle.id)

    private fun makeViewModel(
        quotes: InMemoryFipeQuoteRepository = InMemoryFipeQuoteRepository(),
        catalog: FipeCatalog = fake.catalog,
        vehicles: InMemoryVehicleRepository = InMemoryVehicleRepository(listOf(vehicle)),
        backoff: RefreshBackoff = RefreshBackoff(),
        at: Instant = now,
    ) = VehicleDetailViewModel(
        vehicle = vehicle,
        vehicleRepository = vehicles,
        fuelEntryRepository =
            InMemoryFuelEntryRepository(
                listOf(
                    FuelEntry(vehicleId = vehicle.id, date = Instant.parse("2026-11-20T00:00:00Z"), odometer = 14_000.0, liters = 50.0, pricePerLiter = BigDecimal(6), isFullTank = true),
                ),
            ),
        serviceRepository = InMemoryServiceRepository(),
        expenseRepository = InMemoryExpenseRepository(),
        dueItemRepository = InMemoryDueItemRepository(),
        dateProvider = FixedDateProvider(at),
        fipeQuoteRepository = quotes,
        fipeCatalog = catalog,
        backoff = backoff,
    )

    private val VehicleDetailViewModel.stats get() = assertIs<State.Loaded>(state).content.stats

    @Test
    fun `loading reads stored quotes and never touches the network`() =
        runTest {
            val viewModel = makeViewModel(InMemoryFipeQuoteRepository(listOf(stored(6, "90000"), stored(11, "86000"))))

            viewModel.load()

            assertEquals(0, fake.totalCalls)
            val depreciation = assertNotNull(viewModel.stats.depreciation)
            assertEquals(0, BigDecimal("4000").compareTo(depreciation.amount))
            assertEquals(YearMonth.of(2026, 11), viewModel.latestQuote?.month)
            assertTrue(viewModel.showsValuation)
        }

    @Test
    fun `the first refresh stores the free history and updates the stats without reloading`() =
        runTest {
            val quotes = InMemoryFipeQuoteRepository()
            val viewModel = makeViewModel(quotes)
            viewModel.load()

            viewModel.refreshValuation()

            assertEquals(1, fake.calls["recent"])
            assertNull(fake.calls["latest"])
            assertEquals(listOf(9, 10, 11), quotes.all.map { it.month.monthValue })
            assertTrue(quotes.all.all { it.fetchedAt == now && it.vehicleId == vehicle.id })
            assertEquals(
                YearMonth.of(2026, 11),
                viewModel.stats.depreciation
                    ?.latest
                    ?.month,
            )
            assertEquals(ValuationStatus.Idle, viewModel.valuationStatus)
        }

    @Test
    fun `an empty or missing history falls back to the latest price`() =
        runTest {
            fake.recent = emptyList()
            val quotes = InMemoryFipeQuoteRepository()
            makeViewModel(quotes).apply { load() }.refreshValuation()
            assertEquals(listOf(YearMonth.of(2026, 11)), quotes.all.map { it.month })

            val missingHistory =
                object : FipeCatalog by fake.catalog {
                    override suspend fun recentPrices(
                        kind: VehicleKind,
                        fipeCode: String,
                        yearCode: String,
                    ): List<FipePrice> = throw RemoteDataError.NotFound
                }
            val other = InMemoryFipeQuoteRepository()
            makeViewModel(other, catalog = missingHistory).apply { load() }.refreshValuation()
            assertEquals(1, other.all.size)
        }

    @Test
    fun `a new month asks for the latest price once and the same month does nothing`() =
        runTest {
            val quotes = InMemoryFipeQuoteRepository(listOf(stored(10, "86000")))
            val viewModel = makeViewModel(quotes)
            viewModel.load()

            viewModel.refreshValuation()
            viewModel.refreshValuation()

            assertEquals(1, fake.calls["latest"])
            assertEquals(listOf(10, 11), quotes.all.map { it.month.monthValue })
        }

    @Test
    fun `a failure keeps the stored value, waits an hour and can be retried`() =
        runTest {
            val quotes = InMemoryFipeQuoteRepository(listOf(stored(10, "86000")))
            val backoff = RefreshBackoff()
            val viewModel = makeViewModel(quotes, backoff = backoff)
            viewModel.load()
            fake.failure = RemoteDataError.NetworkUnavailable

            viewModel.refreshValuation()

            assertEquals(ValuationStatus.Failed(RemoteDataError.NetworkUnavailable), viewModel.valuationStatus)
            assertEquals(YearMonth.of(2026, 10), viewModel.latestQuote?.month)
            assertNotNull(viewModel.stats.depreciation)

            fake.failure = null
            makeViewModel(quotes, backoff = backoff).apply { load() }.refreshValuation()
            assertEquals(1, fake.calls["latest"])
            makeViewModel(quotes, backoff = backoff, at = now.plus(Duration.ofMinutes(61))).apply { load() }.refreshValuation()
            assertEquals(2, fake.calls["latest"])
        }

    @Test
    fun `retry ignores the wait`() =
        runTest {
            val viewModel = makeViewModel()
            viewModel.load()
            fake.failure = RemoteDataError.QuotaExceeded
            viewModel.refreshValuation()
            fake.failure = null

            viewModel.retryValuation()

            assertEquals(ValuationStatus.Idle, viewModel.valuationStatus)
            assertEquals(2, fake.calls["recent"])
        }

    @Test
    fun `nothing is asked without a FIPE choice, outside reais or without storage`() =
        runTest {
            val plain = vehicle.copy(fipe = null)
            makeViewModel(vehicles = InMemoryVehicleRepository(listOf(plain))).apply { load() }.refreshValuation()

            DisplayPreferences.update(Preferences(currency = CurrencyOption.USD))
            val dollars = makeViewModel()
            dollars.load()
            dollars.refreshValuation()
            assertFalse(dollars.showsValuation)

            DisplayPreferences.update(Preferences(currency = CurrencyOption.BRL))
            val withoutStorage =
                VehicleDetailViewModel(
                    vehicle = vehicle,
                    vehicleRepository = InMemoryVehicleRepository(listOf(vehicle)),
                    fuelEntryRepository = InMemoryFuelEntryRepository(),
                    serviceRepository = InMemoryServiceRepository(),
                    expenseRepository = InMemoryExpenseRepository(),
                    dueItemRepository = InMemoryDueItemRepository(),
                    dateProvider = FixedDateProvider(now),
                    fipeCatalog = fake.catalog,
                )
            withoutStorage.load()
            withoutStorage.refreshValuation()

            assertEquals(0, fake.totalCalls)
            assertNull(withoutStorage.stats.depreciation)
        }

    @Test
    fun `editing the vehicle to a new FIPE choice refreshes its value`() =
        runTest {
            val vehicles = InMemoryVehicleRepository(listOf(vehicle.copy(fipe = null)))
            val quotes = InMemoryFipeQuoteRepository()
            val viewModel = makeViewModel(quotes, vehicles = vehicles)
            viewModel.load()
            viewModel.reloadAfterEdit()
            assertEquals(0, fake.totalCalls)

            vehicles.save(vehicle)
            viewModel.reloadAfterEdit()

            assertEquals(1, fake.calls["recent"])
            assertEquals(3, quotes.all.size)
        }

    @Test
    fun `cancellation is rethrown and the status goes back to idle`() =
        runTest {
            val cancelling =
                object : FipeCatalog by fake.catalog {
                    override suspend fun recentPrices(
                        kind: VehicleKind,
                        fipeCode: String,
                        yearCode: String,
                    ): List<FipePrice> = throw CancellationException("gone")
                }
            val viewModel = makeViewModel(catalog = cancelling)
            viewModel.load()

            assertFailsWith<CancellationException> { viewModel.refreshValuation() }
            assertEquals(ValuationStatus.Idle, viewModel.valuationStatus)
        }

    @Test
    fun `a failed save is a soft failure`() =
        runTest {
            val quotes = InMemoryFipeQuoteRepository()
            val viewModel = makeViewModel(quotes)
            viewModel.load()
            quotes.failure = IllegalStateException("disk")

            viewModel.refreshValuation()

            assertIs<ValuationStatus.Failed>(viewModel.valuationStatus)
            assertIs<State.Loaded>(viewModel.state)
        }

    @Test
    fun `the backoff opens again after its window`() {
        val backoff = RefreshBackoff(Duration.ofHours(1))
        val key = UUID.randomUUID()

        assertTrue(backoff.allows(key, now))
        backoff.failed(key, now)
        assertFalse(backoff.allows(key, now.plusSeconds(3_599)))
        assertTrue(backoff.allows(key, now.plusSeconds(3_600)))
        backoff.clear(key)
        assertTrue(backoff.allows(key, now))
    }

    @Test
    fun `FIPE values show only in reais`() {
        assertTrue(UnitConversion.usesBrazilianReal)
        DisplayPreferences.update(Preferences(currency = CurrencyOption.EUR))
        assertFalse(UnitConversion.usesBrazilianReal)
        DisplayPreferences.update(Preferences(currency = null))
        assertEquals(
            java.util.Currency
                .getInstance(java.util.Locale.getDefault())
                .currencyCode == "BRL",
            UnitConversion.usesBrazilianReal,
        )
    }

    @Test
    fun `a reference month reads as month and year`() {
        val text = YearMonth.of(2026, 11).formattedShortMonthAndYear(ZoneOffset.UTC)

        assertTrue(text.contains("2026"), text)
    }
}
