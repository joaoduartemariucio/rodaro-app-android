package br.com.joaoduartemariucio.rodaro.calculations

import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.Consumption
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelGroup
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class ConsumptionTests {
    private data class Scenario(
        val name: String,
        val records: List<FuelEntry>,
        val intervals: List<Pair<Double, Double>>,
        val average: Double?,
    )

    private val scenarios =
        listOf(
            Scenario("empty history", emptyList(), emptyList(), null),
            Scenario("a single fill-up does not open a leg", listOf(full(0, 1_000.0, 40.0)), emptyList(), null),
            Scenario(
                "a sequence of partial fills only",
                listOf(partial(0, 1_000.0, 10.0), partial(5, 1_200.0, 12.0), partial(10, 1_400.0, 11.0)),
                emptyList(),
                null,
            ),
            Scenario("full tank to full tank", listOf(full(0, 1_000.0, 40.0), full(10, 1_400.0, 40.0)), listOf(400.0 to 40.0), 10.0),
            Scenario(
                "a partial fill before the first full tank is ignored",
                listOf(partial(0, 900.0, 10.0), full(5, 1_000.0, 40.0), full(15, 1_400.0, 40.0)),
                listOf(400.0 to 40.0),
                10.0,
            ),
            Scenario(
                "a partial fill in between adds liters to the leg",
                listOf(full(0, 1_000.0, 40.0), partial(5, 1_200.0, 20.0), full(10, 1_400.0, 20.0)),
                listOf(400.0 to 40.0),
                10.0,
            ),
            Scenario(
                "multiple partial fills within the same leg",
                listOf(
                    full(0, 1_000.0, 50.0),
                    partial(2, 1_100.0, 10.0),
                    partial(4, 1_200.0, 10.0),
                    partial(6, 1_300.0, 10.0),
                    full(8, 1_500.0, 20.0),
                ),
                listOf(500.0 to 50.0),
                10.0,
            ),
            Scenario(
                "two consecutive legs, weighted average",
                listOf(full(0, 1_000.0, 40.0), full(10, 1_400.0, 40.0), full(20, 1_800.0, 50.0)),
                listOf(400.0 to 40.0, 400.0 to 50.0),
                800.0 / 90.0,
            ),
            Scenario(
                "a backwards odometer discards only the affected leg",
                listOf(full(0, 1_000.0, 40.0), full(10, 900.0, 40.0), full(20, 1_400.0, 50.0)),
                listOf(500.0 to 50.0),
                10.0,
            ),
            Scenario(
                "an unchanged odometer between two full tanks is not a leg",
                listOf(full(0, 1_000.0, 40.0), full(10, 1_000.0, 30.0)),
                emptyList(),
                null,
            ),
            Scenario(
                "a record with non-positive liters is ignored",
                listOf(full(0, 1_000.0, 40.0), full(10, 1_400.0, 0.0)),
                emptyList(),
                null,
            ),
            Scenario(
                "an out-of-order array is sorted before calculating",
                listOf(full(10, 1_400.0, 40.0), full(0, 1_000.0, 40.0)),
                listOf(400.0 to 40.0),
                10.0,
            ),
        )

    @Test
    fun `intervals and average per scenario`() {
        for (scenario in scenarios) {
            val intervals = Consumption.intervals(scenario.records)

            assertEquals(scenario.intervals.size, intervals.size, scenario.name)
            intervals.zip(scenario.intervals).forEach { (actual, expected) ->
                assertEquals(expected.first, actual.distance, scenario.name)
                assertEquals(expected.second, actual.liters, scenario.name)
                assertEquals(expected.first / expected.second, actual.kilometersPerLiter, scenario.name)
            }

            val average = Consumption.average(scenario.records)
            when {
                average != null && scenario.average != null -> assertTrue(abs(average - scenario.average) < 1e-9, scenario.name)
                average == null && scenario.average == null -> Unit
                else -> fail("${scenario.name}: average $average != ${scenario.average}")
            }
        }
    }

    @Test
    fun `latest returns the most recent leg`() {
        val latest = Consumption.latest(listOf(full(0, 1_000.0, 40.0), full(10, 1_400.0, 40.0), full(20, 1_800.0, 50.0)))

        assertEquals(1_400.0, latest?.startOdometer)
        assertEquals(1_800.0, latest?.endOdometer)
        assertEquals(50.0, latest?.liters)
    }

    @Test
    fun `latest is null without a closed leg`() {
        assertNull(Consumption.latest(listOf(full(0, 1_000.0, 40.0))))
    }

    @Test
    fun `the leg carries the dates of the full tanks bounding it`() {
        val interval = Consumption.latest(listOf(full(3, 1_000.0, 40.0), full(13, 1_400.0, 40.0)))

        assertEquals(day(3), interval?.startDate)
        assertEquals(day(13), interval?.endDate)
    }

    private val gasolina = FuelType.GASOLINA_COMUM
    private val etanol = FuelType.ETANOL

    private fun groups(records: List<FuelEntry>) = Consumption.intervals(records).map { it.fuelGroup }

    @Test
    fun `every gasoline grade counts as gasoline and the first interval never qualifies`() {
        val records =
            listOf(
                full(0, 1_000.0, 40.0, fuelType = FuelType.GASOLINA_COMUM),
                full(10, 1_400.0, 40.0, fuelType = FuelType.GASOLINA_ADITIVADA),
                full(20, 1_800.0, 40.0, fuelType = FuelType.GASOLINA_PREMIUM),
            )

        assertEquals(listOf(null, FuelGroup.GASOLINA), groups(records))
    }

    @Test
    fun `the first interval after a switch is discarded and the next one counts`() {
        val records =
            listOf(
                full(0, 1_000.0, 40.0, fuelType = etanol),
                full(10, 1_400.0, 40.0, fuelType = etanol),
                full(20, 1_800.0, 40.0, fuelType = gasolina),
                full(30, 2_200.0, 40.0, fuelType = gasolina),
                full(40, 2_600.0, 40.0, fuelType = etanol),
            )

        val intervals = Consumption.intervals(records)

        assertEquals(listOf(null, FuelGroup.ETANOL, null, FuelGroup.GASOLINA), intervals.map { it.fuelGroup })
        assertEquals(listOf(false, false, true, false), intervals.map { it.isAfterFuelSwitch })
    }

    @Test
    fun `a partial fill of another fuel discards its interval and the next one`() {
        val records =
            listOf(
                full(0, 1_000.0, 40.0, fuelType = gasolina),
                full(10, 1_400.0, 40.0, fuelType = gasolina),
                partial(15, 1_600.0, 10.0, fuelType = etanol),
                full(20, 1_800.0, 30.0, fuelType = gasolina),
                full(30, 2_200.0, 40.0, fuelType = gasolina),
                full(40, 2_600.0, 40.0, fuelType = gasolina),
            )

        assertEquals(listOf(null, null, null, FuelGroup.GASOLINA), groups(records))
    }

    @Test
    fun `an unknown fuel type is never guessed`() {
        val records =
            listOf(
                full(0, 1_000.0, 40.0, fuelType = gasolina),
                full(10, 1_400.0, 40.0),
                full(20, 1_800.0, 40.0, fuelType = gasolina),
                full(30, 2_200.0, 40.0, fuelType = gasolina),
                full(40, 2_600.0, 40.0, fuelType = gasolina),
            )

        val intervals = Consumption.intervals(records)

        assertEquals(listOf(null, null, null, FuelGroup.GASOLINA), intervals.map { it.fuelGroup })
        assertTrue(intervals.none { it.isAfterFuelSwitch })
    }

    @Test
    fun `an interval with an anomalous record is discarded`() {
        val records =
            listOf(
                full(0, 1_000.0, 40.0, fuelType = gasolina),
                full(10, 1_400.0, 40.0, fuelType = gasolina),
                partial(15, 1_400.0, 5.0, fuelType = gasolina),
                full(20, 1_800.0, 35.0, fuelType = gasolina),
            )

        assertEquals(listOf(null, null), groups(records))
    }

    @Test
    fun `the average per fuel sums kilometers and liters instead of averaging ratios`() {
        val records =
            listOf(
                full(0, 1_000.0, 40.0, fuelType = etanol),
                full(10, 1_400.0, 40.0, fuelType = etanol),
                full(20, 1_800.0, 40.0, fuelType = etanol),
                full(30, 2_100.0, 20.0, fuelType = etanol),
            )

        val byGroup = Consumption.averageByGroup(records)

        assertEquals(setOf(FuelGroup.ETANOL), byGroup.keys)
        assertEquals(700.0 / 60.0, byGroup.getValue(FuelGroup.ETANOL), 0.000_001)
        assertTrue(Consumption.averageByGroup(listOf(full(0, 1_000.0, 40.0), full(10, 1_400.0, 40.0))).isEmpty())
    }

    @Test
    fun `the latest group comes from the most recent typed fill-up`() {
        val records =
            listOf(
                full(0, 1_000.0, 40.0, fuelType = gasolina),
                full(10, 1_400.0, 40.0, fuelType = etanol),
                full(20, 1_800.0, 40.0),
            )

        assertEquals(FuelGroup.ETANOL, Consumption.latestGroup(records))
        assertNull(Consumption.latestGroup(listOf(full(0, 1_000.0, 40.0))))
        assertNull(Consumption.averageOf(emptyList()))
    }
}
