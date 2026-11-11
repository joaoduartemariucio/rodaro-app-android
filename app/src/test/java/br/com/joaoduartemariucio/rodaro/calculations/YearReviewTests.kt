package br.com.joaoduartemariucio.rodaro.calculations

import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDetails
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelGroup
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.reports.calculations.YearReview.Shortfall
import br.com.joaoduartemariucio.rodaro.domain.reports.calculations.YearReviews
import br.com.joaoduartemariucio.rodaro.domain.vehicles.calculations.Odometer
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YearReviewTests {
    private fun at(date: String): Instant = LocalDate.parse(date).atStartOfDay(utc).toInstant()

    private fun vehicle(
        created: String = "2024-12-15",
        odometer: Double = 10_000.0,
        kind: VehicleKind = VehicleKind.CAR,
    ) = Vehicle(id = testVehicleId, name = "Gol", kind = kind, initialOdometer = odometer, createdAt = at(created))

    private fun fill(
        date: String,
        odometer: Double,
        liters: Double = 40.0,
        price: String = "5",
        type: FuelType? = FuelType.GASOLINA_COMUM,
        isFull: Boolean = true,
    ) = FuelEntry(
        vehicleId = testVehicleId,
        date = at(date),
        odometer = odometer,
        liters = liters,
        pricePerLiter = BigDecimal(price),
        isFullTank = isFull,
        fuelType = type,
    )

    private fun service(
        date: String,
        odometer: Double,
        cost: String = "100",
    ) = ServiceEntry(vehicleId = testVehicleId, date = at(date), odometer = odometer, cost = BigDecimal(cost))

    private fun paid(
        category: ExpenseCategory,
        date: String,
        amount: String,
        fine: FineDetails? = null,
    ) = Expense(vehicleId = testVehicleId, category = category, date = at(date), amount = BigDecimal(amount), fine = fine)

    private val monthly: List<FuelEntry> =
        (0..13).map { index ->
            val month = YearMonth.of(2024, 12).plusMonths(index.toLong())
            fill(month.atDay(15).toString(), 10_000.0 + 1_000.0 * index)
        }

    private val now = at("2026-03-01")

    @Test
    fun `distance is interpolated at January 1 and the next January 1`() {
        val distance = assertNotNull(YearReviews.distance(samples(vehicle(), monthly), 2025, utc))

        val start = 10_000.0 + 1_000.0 * 17 / 31
        val end = 22_000.0 + 1_000.0 * 17 / 31
        assertEquals(end - start, distance.kilometers, 0.001)
        assertFalse(distance.fromIsReading)
        assertFalse(distance.untilIsReading)
        assertEquals(365, distance.coveredDays)
        assertEquals(12_000.0 / 365 * 30.4375, assertNotNull(distance.perMonth), 0.001)
    }

    @Test
    fun `without readings around the year the first and last readings inside it are used`() {
        val fills = listOf(fill("2025-03-10", 10_500.0), fill("2025-06-10", 12_000.0), fill("2025-10-20", 14_500.0))

        val distance = assertNotNull(YearReviews.distance(samples(vehicle("2025-03-10", 10_500.0), fills), 2025, utc))

        assertTrue(distance.fromIsReading)
        assertTrue(distance.untilIsReading)
        assertEquals(at("2025-03-10"), distance.from)
        assertEquals(at("2025-10-20"), distance.until)
        assertEquals(4_000.0, distance.kilometers)
        assertNull(YearReviews.distance(samples(vehicle("2025-03-10", 10_500.0), fills), 2023, utc))
    }

    @Test
    fun `a reading that goes backwards is ignored`() {
        val samples = samples(vehicle(), monthly, listOf(service("2025-06-01", 500.0)))

        val distance = assertNotNull(YearReviews.distance(samples, 2025, utc))

        assertEquals(12_000.0, distance.kilometers, 0.001)
    }

    @Test
    fun `each minimum fails on its own`() {
        val shortSpan = listOf(fill("2025-03-01", 10_000.0), fill("2025-04-01", 10_500.0), fill("2025-05-01", 11_000.0), fill("2025-05-29", 11_500.0))
        assertEquals(setOf(Shortfall.DAYS), YearReviews.shortfalls(vehicle("2025-03-01"), shortSpan, emptyList(), 2025, utc))

        val threeFills = listOf(fill("2025-03-01", 10_000.0), fill("2025-06-01", 11_500.0), fill("2025-09-01", 13_000.0))
        assertEquals(setOf(Shortfall.FUEL_UPS), YearReviews.shortfalls(vehicle("2025-03-01"), threeFills, emptyList(), 2025, utc))

        val closeReadings =
            listOf(
                fill("2025-06-01", 11_000.0),
                fill("2025-06-10", 11_300.0),
                fill("2025-06-20", 11_600.0),
                fill("2025-06-30", 11_900.0),
                fill("2026-02-01", 15_000.0),
            )
        assertEquals(setOf(Shortfall.READINGS), YearReviews.shortfalls(vehicle("2024-11-01"), closeReadings, emptyList(), 2025, utc))
        assertNull(YearReviews.review(vehicle("2024-11-01"), closeReadings, emptyList(), emptyList(), 2025, now, utc))

        assertEquals(
            setOf(Shortfall.DAYS, Shortfall.FUEL_UPS, Shortfall.READINGS),
            YearReviews.shortfalls(vehicle("2025-03-01"), emptyList(), emptyList(), 2024, utc),
        )
    }

    @Test
    fun `the current year opens on December 15`() {
        val fills = listOf(fill("2025-03-01", 10_000.0), fill("2026-02-01", 12_000.0))

        assertEquals(listOf(2025), YearReviews.availableYears(fills, emptyList(), emptyList(), at("2026-12-14"), utc))
        assertEquals(listOf(2026, 2025), YearReviews.availableYears(fills, emptyList(), emptyList(), at("2026-12-15"), utc))
    }

    @Test
    fun `a vehicle with only 2023 data offers 2023`() {
        val fills = listOf(fill("2023-03-01", 10_000.0))
        val expenses = listOf(paid(ExpenseCategory.LAVAGEM, "2023-05-01", "30"))

        assertEquals(listOf(2023), YearReviews.availableYears(fills, emptyList(), expenses, now, utc))
        assertEquals(emptyList(), YearReviews.availableYears(emptyList(), emptyList(), emptyList(), now, utc))
    }

    @Test
    fun `cash spending leaves unpaid fines out and lists paid fines apart`() {
        val expenses =
            listOf(
                paid(ExpenseCategory.PEDAGIO, "2025-03-20", "50"),
                paid(ExpenseCategory.MULTA, "2025-04-02", "195.23", FineDetails(isPaid = true)),
                paid(ExpenseCategory.MULTA, "2025-04-03", "293.47", FineDetails(isPaid = false)),
                paid(ExpenseCategory.PEDAGIO, "2024-12-20", "999"),
            )
        val review =
            assertNotNull(YearReviews.review(vehicle(), monthly, listOf(service("2025-08-01", 17_500.0, "300")), expenses, 2025, now, utc))

        val spending = assertNotNull(review.spending)
        assertDecimal("2400", spending.fuel)
        assertDecimal("300", spending.services)
        assertDecimal("195.23", spending.fines)
        assertDecimal("50", spending.byCategory[ExpenseCategory.PEDAGIO])
        assertDecimal("2945.23", spending.total)
        assertEquals(listOf(service("2025-08-01", 17_500.0, "300").date), review.services.map { it.date })
        assertFalse(review.isPartial)
    }

    @Test
    fun `the most expensive month is the one with the most spent`() {
        val review =
            assertNotNull(
                YearReviews.review(vehicle(), monthly, listOf(service("2025-08-03", 17_600.0, "300")), emptyList(), 2025, now, utc),
            )

        assertEquals(YearMonth.of(2025, 8), review.spending?.mostExpensiveMonth)
        assertDecimal("500", review.spending?.mostExpensiveAmount)
    }

    @Test
    fun `average price is weighted by liters and the minimum is per fuel type`() {
        val fills =
            monthly.mapIndexed { index, entry ->
                when (index) {
                    3 -> entry.copy(liters = 10.0, pricePerLiter = BigDecimal("6.00"))
                    4 -> entry.copy(liters = 30.0, pricePerLiter = BigDecimal("4.00"))
                    5 -> entry.copy(fuelType = FuelType.ETANOL, pricePerLiter = BigDecimal("3.90"))
                    6 -> entry.copy(fuelType = null)
                    else -> entry
                }
            }

        val fuel = assertNotNull(assertNotNull(YearReviews.review(vehicle(), fills, emptyList(), emptyList(), 2025, now, utc)).fuel)

        assertEquals(12, fuel.fuelUps)
        assertDecimal("3.90", fuel.minimumPrice[FuelType.ETANOL])
        val gasolineLiters = 8 * 40.0 + 10.0 + 30.0
        val gasolineCost = BigDecimal(8 * 40 * 5 + 60 + 120)
        assertDecimal(gasolineCost.divide(BigDecimal(gasolineLiters.toString()), MathContext.DECIMAL128).toPlainString(), fuel.averagePrice[FuelType.GASOLINA_COMUM])
        assertDecimal("4.00", fuel.minimumPrice[FuelType.GASOLINA_COMUM])
        assertEquals(40.0, fuel.litersByGroup[null])
        assertEquals(40.0, fuel.litersByGroup[FuelGroup.ETANOL])
    }

    @Test
    fun `consumption is distance over liters of intervals ending in the year`() {
        val review = assertNotNull(YearReviews.review(vehicle(), monthly, emptyList(), emptyList(), 2025, now, utc))

        val gasoline = assertNotNull(review.consumption[FuelGroup.GASOLINA])
        assertEquals(1_000.0 / 40.0, gasoline.kilometersPerLiter, 0.0001)
        assertEquals(11, gasoline.intervals)
    }

    @Test
    fun `best and worst tanks skip short intervals and need two of them`() {
        val fills =
            listOf(
                fill("2024-12-15", 10_000.0),
                fill("2025-02-01", 10_500.0, liters = 40.0),
                fill("2025-03-01", 10_590.0, liters = 2.0),
                fill("2025-04-01", 11_190.0, liters = 40.0),
                fill("2025-05-01", 11_690.0, liters = 50.0),
                fill("2026-01-15", 12_500.0, liters = 50.0),
            )

        val car = assertNotNull(YearReviews.review(vehicle(), fills, emptyList(), emptyList(), 2025, now, utc)).consumption.getValue(FuelGroup.GASOLINA)
        assertEquals(15.0, car.best?.kilometersPerLiter)
        assertEquals(10.0, car.worst?.kilometersPerLiter)

        val moto =
            assertNotNull(YearReviews.review(vehicle(kind = VehicleKind.MOTORCYCLE), fills, emptyList(), emptyList(), 2025, now, utc))
                .consumption
                .getValue(FuelGroup.GASOLINA)
        assertEquals(45.0, moto.best?.kilometersPerLiter)

        val few = listOf(fill("2024-12-15", 10_000.0), fill("2025-02-01", 10_090.0, liters = 9.0), fill("2025-03-01", 10_590.0), fill("2025-04-01", 10_600.0), fill("2025-05-01", 10_610.0), fill("2026-01-15", 10_620.0))
        val single = assertNotNull(YearReviews.review(vehicle(), few, emptyList(), emptyList(), 2025, now, utc)).consumption.getValue(FuelGroup.GASOLINA)
        assertNull(single.best)
        assertNull(single.worst)
        assertEquals(100.0, YearReviews.minimumIntervalKilometers(VehicleKind.CAR))
        assertEquals(50.0, YearReviews.minimumIntervalKilometers(VehicleKind.MOTORCYCLE))
    }

    @Test
    fun `cost per km accrues only the tracked part of a full-year IPVA`() {
        val fills =
            (0..6).map { index ->
                val month = YearMonth.of(2025, 7).plusMonths(index.toLong())
                fill(month.atDay(1).toString(), 10_000.0 + 1_000.0 * index)
            }
        val ipva = paid(ExpenseCategory.IPVA, "2025-01-20", "1200")

        val review = assertNotNull(YearReviews.review(vehicle("2025-07-01"), fills, emptyList(), listOf(ipva), 2025, now, utc))

        assertTrue(review.distance.fromIsReading)
        assertEquals(6_000.0, review.distance.kilometers)
        val accrued = BigDecimal(1200).multiply(BigDecimal(184)).divide(BigDecimal(365), MathContext.DECIMAL128)
        val expected = BigDecimal(1200).add(accrued).divide(BigDecimal(6000), MathContext.DECIMAL128)
        val actual = assertNotNull(review.costPerKilometer)
        assertTrue(expected.subtract(actual).abs() < BigDecimal("0.000000001"), "expected $expected, got $actual")
    }

    @Test
    fun `the current year is partial`() {
        val review = assertNotNull(YearReviews.review(vehicle(), monthly, emptyList(), emptyList(), 2025, at("2025-12-20"), utc))

        assertTrue(review.isPartial)
    }

    private fun samples(
        vehicle: Vehicle,
        fuel: List<FuelEntry>,
        services: List<ServiceEntry> = emptyList(),
    ) = Odometer.samples(vehicle, fuel, services)
}
