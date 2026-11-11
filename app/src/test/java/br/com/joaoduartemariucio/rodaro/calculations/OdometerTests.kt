package br.com.joaoduartemariucio.rodaro.calculations

import br.com.joaoduartemariucio.rodaro.domain.vehicles.calculations.Odometer
import br.com.joaoduartemariucio.rodaro.domain.vehicles.calculations.OdometerNeighbors
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OdometerTests {
    @Test
    fun `current per scenario`() {
        val scenarios =
            listOf(
                Triple("empty history", emptyList(), null),
                Triple("a single sample", listOf(sample(0, 10_000.0)), 10_000.0),
                Triple("the most recent reading wins", listOf(sample(0, 10_000.0), sample(30, 12_000.0), sample(10, 11_000.0)), 12_000.0),
                Triple(
                    "a backwards odometer returns the most recent, not the highest",
                    listOf(sample(0, 10_000.0), sample(10, 15_000.0), sample(20, 12_000.0)),
                    12_000.0,
                ),
                Triple(
                    "a date tie resolves by the higher odometer",
                    listOf(sample(0, 10_000.0), sample(10, 11_000.0), sample(10, 11_500.0)),
                    11_500.0,
                ),
            )
        for ((name, samples, expected) in scenarios) {
            assertEquals(expected, Odometer.current(samples), name)
        }
    }

    @Test
    fun `neighbors brackets a date between the readings around it`() {
        val samples = listOf(sample(0, 10_000.0), sample(10, 11_000.0), sample(20, 12_000.0))

        assertEquals(OdometerNeighbors(11_000.0, 12_000.0), Odometer.neighbors(day(15), samples))
    }

    @Test
    fun `neighbors has no next reading after the most recent one`() {
        assertEquals(OdometerNeighbors(11_000.0, null), Odometer.neighbors(day(30), listOf(sample(0, 10_000.0), sample(10, 11_000.0))))
    }

    @Test
    fun `neighbors has no previous reading before the first one`() {
        assertEquals(OdometerNeighbors(null, 10_000.0), Odometer.neighbors(day(-5), listOf(sample(0, 10_000.0))))
    }

    @Test
    fun `samples includes the vehicle registration`() {
        val vehicle = Vehicle(id = testVehicleId, name = "Gol", initialOdometer = 50_000.0, createdAt = day(0))

        val samples = Odometer.samples(vehicle, listOf(full(10, 51_000.0, 40.0)), listOf(service(20, 52_000.0, BigDecimal(300))))

        assertEquals(3, samples.size)
        assertEquals(52_000.0, Odometer.current(samples))
        assertEquals(50_000.0, Odometer.current(Odometer.samples(vehicle, emptyList(), emptyList())))
    }

    @Test
    fun `interpolation is linear between readings and exact at a reading`() {
        val samples = listOf(sample(10, 2_000.0), sample(0, 1_000.0))

        assertEquals(1_500.0, Odometer.interpolated(day(5), samples))
        assertEquals(1_250.0, Odometer.interpolated(hour(12, 2), samples))
        assertEquals(2_000.0, Odometer.interpolated(day(10), samples))
        assertEquals(1_000.0, Odometer.interpolated(day(0), samples))
    }

    @Test
    fun `interpolation outside the readings is unknown`() {
        val samples = listOf(sample(0, 1_000.0), sample(10, 2_000.0))

        assertNull(Odometer.interpolated(day(-1), samples))
        assertNull(Odometer.interpolated(day(11), samples))
        assertNull(Odometer.interpolated(day(0), emptyList()))
    }

    @Test
    fun `readings that go backwards are skipped when interpolating`() {
        val samples = listOf(sample(0, 1_000.0), sample(5, 800.0), sample(10, 2_000.0))

        assertEquals(1_500.0, Odometer.interpolated(day(5), samples))
    }
}
