package br.com.joaoduartemariucio.rodaro.calculations

import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelGroup
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.domain.reports.calculations.HistoryOptions
import br.com.joaoduartemariucio.rodaro.domain.reports.calculations.VehicleHistories
import br.com.joaoduartemariucio.rodaro.domain.reports.calculations.VehicleHistory
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import org.junit.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VehicleHistoryTests {
    private fun at(date: String): Instant = LocalDate.parse(date).atStartOfDay(utc).toInstant()

    private val vehicle =
        Vehicle(id = testVehicleId, name = "Gol", plate = "ABC1D23", initialOdometer = 10_000.0, createdAt = at("2024-12-15"), modelYear = 2019)

    private fun fill(
        date: String,
        odometer: Double,
        createdAt: Instant = at(date),
    ) = FuelEntry(
        vehicleId = testVehicleId,
        date = at(date),
        odometer = odometer,
        liters = 40.0,
        pricePerLiter = BigDecimal(5),
        isFullTank = true,
        fuelType = FuelType.GASOLINA_COMUM,
        createdAt = createdAt,
    )

    private fun service(
        date: String,
        odometer: Double,
        createdAt: Instant = at(date),
    ) = ServiceEntry(vehicleId = testVehicleId, kind = ServiceKind.OIL_CHANGE, date = at(date), odometer = odometer, cost = BigDecimal("250.00"), createdAt = createdAt)

    private val fills =
        (0..13).map {
            fill(
                YearMonth
                    .of(2024, 12)
                    .plusMonths(it.toLong())
                    .atDay(15)
                    .toString(),
                10_000.0 + 1_000.0 * it,
            )
        }

    private fun build(
        fuel: List<FuelEntry> = fills,
        services: List<ServiceEntry> = emptyList(),
        options: HistoryOptions = HistoryOptions(),
        vehicle: Vehicle = this.vehicle,
    ) = VehicleHistories.build(vehicle, fuel, services, options, at("2026-03-01"), utc)

    @Test
    fun `exactly thirty days late is not retroactive, a second more is`() {
        assertFalse(VehicleHistories.isRetroactive(at("2025-01-01"), at("2025-01-31")))
        assertTrue(VehicleHistories.isRetroactive(at("2025-01-01"), at("2025-01-31").plus(Duration.ofSeconds(1))))

        val history =
            build(
                services = listOf(service("2025-02-01", 12_200.0, at("2025-04-01")), service("2025-06-01", 16_200.0)),
                options = HistoryOptions(includeFuelLog = true),
                fuel = fills + fill("2026-01-20", 23_500.0, at("2026-03-01")),
            )
        assertEquals(listOf(true, false), history.services.map { it.isRetroactive })
        assertTrue(assertNotNull(history.fuelLog).last().isRetroactive)
    }

    @Test
    fun `costs and the fuel log are left out and the plate is masked by default`() {
        val history = build()

        assertNull(history.totals)
        assertNull(history.fuelLog)
        assertEquals("ABC1D**", history.plate)
        assertEquals(HistoryOptions(), history.options)
    }

    @Test
    fun `opting in adds costs, the fuel log and the full plate`() {
        val history = build(services = listOf(service("2025-02-01", 12_200.0)), options = HistoryOptions(true, true, true))

        assertEquals("ABC1D23", history.plate)
        assertDecimal("2800", history.totals?.fuel)
        assertDecimal("250.00", history.totals?.services)
        assertEquals(14, history.fuelLog?.size)
        assertNull(build(vehicle = vehicle.copy(plate = null)).plate)
    }

    @Test
    fun `the odometer timeline keeps the last reading of each month`() {
        val history = build(fuel = listOf(fill("2025-01-05", 10_200.0), fill("2025-01-25", 10_800.0), fill("2025-02-10", 11_300.0)))

        assertEquals(
            listOf(
                VehicleHistory.OdometerLine(YearMonth.of(2024, 12), 10_000.0, at("2024-12-15")),
                VehicleHistory.OdometerLine(YearMonth.of(2025, 1), 10_800.0, at("2025-01-25")),
                VehicleHistory.OdometerLine(YearMonth.of(2025, 2), 11_300.0, at("2025-02-10")),
            ),
            history.odometerTimeline,
        )
    }

    @Test
    fun `odometer readings that go backwards are listed with their source and both values`() {
        val history = build(services = listOf(service("2025-03-01", 9_500.0)), fuel = fills + fill("2025-08-01", 12_000.0))

        assertEquals(
            listOf(
                VehicleHistory.OdometerRegression(at("2025-03-01"), VehicleHistory.Source.SERVICE, 12_000.0, 9_500.0),
                VehicleHistory.OdometerRegression(at("2025-08-01"), VehicleHistory.Source.FUEL, 17_000.0, 12_000.0),
            ),
            history.regressions,
        )
        assertTrue(build().regressions.isEmpty())
    }

    @Test
    fun `kilometers per year, consumption, counts and tracking dates`() {
        val history = build(services = listOf(service("2025-02-01", 12_200.0, at("2024-11-01"))))

        assertEquals(12_000.0, assertNotNull(history.kilometersByYear[2025]), 0.001)
        assertEquals(25.0, history.consumptionByGroup[FuelGroup.GASOLINA])
        assertEquals(25.0, history.overallConsumption)
        assertEquals(15, history.recordCount)
        assertEquals(14, history.fuelUps)
        assertEquals(at("2024-11-01"), history.trackedSince)
        assertEquals(at("2024-12-15"), history.firstRecord)
        assertEquals(at("2026-03-01"), history.generatedAt)
    }

    @Test
    fun `an empty vehicle still builds a history`() {
        val history = build(fuel = emptyList())

        assertEquals(0, history.recordCount)
        assertNull(history.firstRecord)
        assertEquals(vehicle.createdAt, history.trackedSince)
        assertTrue(history.kilometersByYear.isEmpty())
    }
}
