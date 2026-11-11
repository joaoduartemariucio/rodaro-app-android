package br.com.joaoduartemariucio.rodaro.calculations

import br.com.joaoduartemariucio.rodaro.domain.common.errors.RemoteDataError
import br.com.joaoduartemariucio.rodaro.domain.valuation.calculations.Depreciation
import br.com.joaoduartemariucio.rodaro.domain.valuation.calculations.FipeRefresh
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeOption
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeQuote
import br.com.joaoduartemariucio.rodaro.domain.valuation.services.UnavailableFipeCatalog
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.OdometerSample
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DepreciationTests {
    private val vehicle =
        Vehicle(
            id = testVehicleId,
            name = "Amarok",
            createdAt = at(2026, 5, 1),
            fipe = selection(),
        )

    private fun at(
        year: Int,
        month: Int,
        day: Int,
    ): Instant = LocalDate.of(year, month, day).atStartOfDay(utc).toInstant()

    private fun reading(
        year: Int,
        month: Int,
        day: Int,
        odometer: Double,
    ) = OdometerSample(at(year, month, day), odometer)

    private val samples =
        listOf(
            reading(2026, 5, 1, 10_000.0),
            reading(2026, 7, 1, 11_000.0),
            reading(2026, 9, 1, 12_000.0),
            reading(2026, 11, 1, 13_000.0),
        )

    private val quotes =
        listOf(
            quote(YearMonth.of(2026, 8), "90000"),
            quote(YearMonth.of(2026, 9), "89000"),
            quote(YearMonth.of(2026, 11), "87000"),
        )

    @Test
    fun `the start is the purchase month when its quote exists`() {
        val bought = vehicle.copy(purchaseDate = LocalDate.of(2026, 9, 20), purchasePrice = BigDecimal("95000"))

        val summary = assertNotNull(Depreciation.summary(bought, quotes, samples, utc))

        assertEquals(YearMonth.of(2026, 9), summary.start.month)
        assertTrue(summary.startsAtPurchase)
        assertDecimal("2000", summary.amount)
        assertEquals(2, summary.months)
    }

    @Test
    fun `a purchase older than the stored quotes starts at the earliest one and says so`() {
        val bought = vehicle.copy(purchaseDate = LocalDate.of(2025, 1, 10))

        val summary = assertNotNull(Depreciation.summary(bought, quotes, samples, utc))

        assertEquals(YearMonth.of(2026, 8), summary.start.month)
        assertFalse(summary.startsAtPurchase)
        assertDecimal("3000", summary.amount)
    }

    @Test
    fun `the start quote is the earliest on or after the start month, else the latest`() {
        assertEquals(YearMonth.of(2026, 11), Depreciation.startQuote(quotes, YearMonth.of(2026, 10))?.month)
        assertEquals(YearMonth.of(2026, 11), Depreciation.startQuote(quotes, YearMonth.of(2027, 2))?.month)
        assertEquals(YearMonth.of(2026, 8), Depreciation.startQuote(quotes, null)?.month)
        assertNull(Depreciation.startQuote(emptyList(), YearMonth.of(2026, 1)))
    }

    @Test
    fun `without a purchase date the first reading month is the start`() {
        val samples = listOf(reading(2026, 9, 12, 12_000.0), reading(2026, 11, 20, 13_500.0))

        val summary = assertNotNull(Depreciation.summary(vehicle, quotes, samples, utc))

        assertEquals(YearMonth.of(2026, 9), summary.start.month)
        assertFalse(summary.startsAtPurchase)
    }

    @Test
    fun `quotes for another FIPE code or year are ignored`() {
        val other = selection(fipeCode = "001234-5")
        val mixed = quotes + quote(YearMonth.of(2026, 12), "10", selection = other) + quote(YearMonth.of(2026, 12), "20", selection = selection(yearCode = "2015-3"))

        val summary = assertNotNull(Depreciation.summary(vehicle, mixed, samples, utc))

        assertEquals(YearMonth.of(2026, 11), summary.latest.month)
        assertEquals(3, Depreciation.relevant(mixed, selection()).size)
    }

    @Test
    fun `a value that went up is kept negative, never clamped`() {
        val rising = listOf(quote(YearMonth.of(2026, 5), "80000"), quote(YearMonth.of(2026, 9), "83000"))

        val summary = assertNotNull(Depreciation.summary(vehicle, rising, samples, utc))

        assertDecimal("-3000", summary.amount)
        assertTrue(summary.isAppreciation)
        assertDecimal("-750", summary.perMonth)
        assertDecimal("-1.5", summary.perKilometer)
    }

    @Test
    fun `per month and per kilometer need three months and a thousand kilometers`() {
        val twoMonths = listOf(quote(YearMonth.of(2026, 5), "90000"), quote(YearMonth.of(2026, 7), "88000"))
        val shortSummary = assertNotNull(Depreciation.summary(vehicle, twoMonths, samples, utc))
        assertNull(shortSummary.perMonth)
        assertNull(shortSummary.perKilometer)

        val slowSamples = listOf(reading(2026, 5, 1, 10_000.0), reading(2026, 11, 1, 10_999.0))
        val threeMonths = listOf(quote(YearMonth.of(2026, 5), "90000"), quote(YearMonth.of(2026, 8), "87000"))
        val slow = assertNotNull(Depreciation.summary(vehicle, threeMonths, slowSamples, utc))
        assertEquals(3, slow.months)
        assertNull(slow.perMonth)

        val exactSamples = listOf(reading(2026, 5, 1, 10_000.0), reading(2026, 8, 1, 11_000.0))
        val exact = assertNotNull(Depreciation.summary(vehicle, threeMonths, exactSamples, utc))
        assertEquals(1_000.0, exact.kilometers)
        assertDecimal("1000", exact.perMonth)
        assertDecimal("3", exact.perKilometer)
    }

    @Test
    fun `kilometers are interpolated at both month starts`() {
        val window = listOf(quote(YearMonth.of(2026, 6), "90000"), quote(YearMonth.of(2026, 10), "86000"))

        val summary = assertNotNull(Depreciation.summary(vehicle.copy(purchaseDate = LocalDate.of(2026, 6, 3)), window, samples, utc))

        val june = 10_000.0 + 1_000.0 * 31 / 61
        val october = 12_000.0 + 1_000.0 * 30 / 61
        assertEquals(october - june, assertNotNull(summary.kilometers), 0.001)
    }

    @Test
    fun `a window past the last reading falls back to the last reading inside it`() {
        val samples = listOf(reading(2026, 5, 10, 10_000.0), reading(2026, 9, 10, 11_500.0))
        val window = listOf(quote(YearMonth.of(2026, 5), "90000"), quote(YearMonth.of(2026, 11), "86000"))

        val summary = assertNotNull(Depreciation.summary(vehicle, window, samples, utc))

        assertEquals(1_500.0, summary.kilometers)
    }

    @Test
    fun `no readings in the window leave kilometers unknown`() {
        val window = listOf(quote(YearMonth.of(2026, 5), "90000"), quote(YearMonth.of(2026, 11), "86000"))

        val summary = assertNotNull(Depreciation.summary(vehicle, window, emptyList(), utc))

        assertNull(summary.kilometers)
        assertNull(summary.perKilometer)
    }

    @Test
    fun `purchase price is compared with the latest FIPE value`() {
        val bought = vehicle.copy(purchaseDate = LocalDate.of(2026, 8, 1), purchasePrice = BigDecimal("92500.50"))

        val summary = assertNotNull(Depreciation.summary(bought, quotes, samples, utc))

        assertDecimal("92500.50", summary.purchasePrice)
        assertDecimal("5500.50", summary.purchaseVsLatest)
    }

    @Test
    fun `a single quote is a zero summary and nothing without a selection or quotes`() {
        val single = assertNotNull(Depreciation.summary(vehicle, quotes.take(1), samples, utc))
        assertDecimal("0", single.amount)
        assertEquals(0, single.months)

        assertNull(Depreciation.summary(vehicle.copy(fipe = null), quotes, samples, utc))
        assertNull(Depreciation.summary(vehicle, emptyList(), samples, utc))
    }

    @Test
    fun `refresh plan asks for history first, then the latest price once a month`() {
        val now = at(2026, 12, 10)

        assertEquals(FipeRefresh.NONE, Depreciation.refreshPlan(vehicle.copy(fipe = null), quotes, now, utc))
        assertEquals(FipeRefresh.HISTORY, Depreciation.refreshPlan(vehicle, emptyList(), now, utc))
        assertEquals(FipeRefresh.HISTORY, Depreciation.refreshPlan(vehicle, listOf(quote(YearMonth.of(2026, 1), "1", selection = selection("x"))), now, utc))
        assertEquals(FipeRefresh.LATEST, Depreciation.refreshPlan(vehicle, quotes, now, utc))

        val fetchedThisMonth = quotes + quote(YearMonth.of(2026, 11), "87000", fetchedAt = at(2026, 12, 2))
        assertEquals(FipeRefresh.NONE, Depreciation.refreshPlan(vehicle, fetchedThisMonth, now, utc))

        val current = quotes + quote(YearMonth.of(2026, 12), "86000", fetchedAt = at(2026, 11, 30))
        assertEquals(FipeRefresh.NONE, Depreciation.refreshPlan(vehicle, current, now, utc))
    }

    @Test
    fun `the month boundary follows the local zone`() {
        val saoPaulo = ZoneId.of("America/Sao_Paulo")
        val fetched = listOf(quote(YearMonth.of(2026, 10), "87000", fetchedAt = Instant.parse("2026-11-01T02:00:00Z")))

        assertEquals(FipeRefresh.LATEST, Depreciation.refreshPlan(vehicle, fetched, Instant.parse("2026-11-15T12:00:00Z"), saoPaulo))
        assertEquals(FipeRefresh.NONE, Depreciation.refreshPlan(vehicle, fetched, Instant.parse("2026-11-01T02:30:00Z"), saoPaulo))
        assertEquals(FipeRefresh.NONE, Depreciation.refreshPlan(vehicle, fetched, Instant.parse("2026-11-01T02:30:00Z"), utc))
    }

    @Test
    fun `quote ids are stable per vehicle, code, year and month`() {
        val first = FipeQuote.id(testVehicleId, "005340-6", "2014-3", YearMonth.of(2026, 11))

        assertEquals(first, FipeQuote.id(testVehicleId, "005340-6", "2014-3", YearMonth.of(2026, 11)))
        assertFalse(first == FipeQuote.id(testVehicleId, "005340-6", "2014-3", YearMonth.of(2026, 10)))
    }

    @Test
    fun `model year comes from the year code, zero km has none`() {
        assertEquals(2014, selection(yearCode = "2014-3").modelYear)
        assertNull(selection(yearCode = "32000-1").modelYear)
        assertNull(selection(yearCode = "abc").modelYear)
    }

    @Test
    fun `the unavailable catalog always reports no network`() =
        runTest {
            val year = FipeOption("2014-3", "2014 Diesel")
            assertFailsWith<RemoteDataError.NetworkUnavailable> { UnavailableFipeCatalog.brands(VehicleKind.CAR) }
            assertFailsWith<RemoteDataError.NetworkUnavailable> { UnavailableFipeCatalog.models(VehicleKind.CAR, "1") }
            assertFailsWith<RemoteDataError.NetworkUnavailable> { UnavailableFipeCatalog.years(VehicleKind.CAR, "1", "2") }
            assertFailsWith<RemoteDataError.NetworkUnavailable> { UnavailableFipeCatalog.detail(VehicleKind.CAR, "1", "2", year) }
            assertFailsWith<RemoteDataError.NetworkUnavailable> { UnavailableFipeCatalog.latestPrice(VehicleKind.CAR, "1", "2") }
            assertFailsWith<RemoteDataError.NetworkUnavailable> { UnavailableFipeCatalog.recentPrices(VehicleKind.CAR, "1", "2") }
        }
}
