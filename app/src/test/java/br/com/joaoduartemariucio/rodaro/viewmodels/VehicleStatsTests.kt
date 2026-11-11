package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.calculations.assertDecimal
import br.com.joaoduartemariucio.rodaro.calculations.day
import br.com.joaoduartemariucio.rodaro.calculations.expense
import br.com.joaoduartemariucio.rodaro.calculations.full
import br.com.joaoduartemariucio.rodaro.calculations.partial
import br.com.joaoduartemariucio.rodaro.calculations.rule
import br.com.joaoduartemariucio.rodaro.calculations.sample
import br.com.joaoduartemariucio.rodaro.calculations.service
import br.com.joaoduartemariucio.rodaro.calculations.testVehicleId
import br.com.joaoduartemariucio.rodaro.calculations.utc
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDetails
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.MaintenanceForecast
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.MaintenanceStatus
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.MaintenanceStatus.Level
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.ServiceDue
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceRule
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.fakes.TestLocalizer
import br.com.joaoduartemariucio.rodaro.presentation.common.color
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedKilometersPerMonth
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedPerKilometer
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedPerLiter
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedRelativeDays
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedRemainingKilometers
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedShortMonth
import br.com.joaoduartemariucio.rodaro.presentation.common.icon
import br.com.joaoduartemariucio.rodaro.presentation.common.label
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.VehicleStats
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.VehicleTimeline
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VehicleStatsTests {
    private val vehicle = Vehicle(id = testVehicleId, name = "Gol", initialOdometer = 1_000.0, createdAt = day(0))
    private val localizer = TestLocalizer()

    private fun stats(
        fuel: List<FuelEntry> = emptyList(),
        services: List<ServiceEntry> = emptyList(),
        rules: List<ServiceRule> = emptyList(),
        now: Int = 45,
        expenses: List<Expense> = emptyList(),
    ) = VehicleStats(vehicle, fuel, services, rules, day(now), utc, expenses)

    @Test
    fun `km per liter is attached to the fill-up that closes each interval`() {
        val first = full(1, 1_000.0, 40.0)
        val middle = partial(10, 1_200.0, 10.0)
        val closing = full(20, 1_500.0, 30.0, price = BigDecimal(6))

        val result = stats(fuel = listOf(first, middle, closing))

        assertEquals(mapOf(closing.id to 500.0 / 40), result.consumptionByEntry)
        assertEquals(1, result.consumptionHistory.size)
        assertDecimal("6", result.lastPricePerLiter)
    }

    @Test
    fun `spending this month counts only records in the current calendar month`() {
        val result =
            stats(
                fuel = listOf(full(10, 1_100.0, 10.0), full(35, 1_400.0, 10.0)),
                services = listOf(service(40, 1_450.0, BigDecimal(120)), service(5, 1_050.0, BigDecimal(999))),
                now = 45,
            )

        assertDecimal("170", result.spentThisMonth)
    }

    @Test
    fun `spending this month includes paid expenses and leaves unpaid fines out`() {
        val result =
            stats(
                fuel = listOf(full(35, 1_400.0, 10.0)),
                expenses =
                    listOf(
                        expense(ExpenseCategory.LAVAGEM, "40", dayOffset = 40),
                        expense(ExpenseCategory.MULTA, "195.23", dayOffset = 41, fine = FineDetails(isPaid = false)),
                        expense(ExpenseCategory.PEDAGIO, "99", dayOffset = 5),
                    ),
                now = 45,
            )

        assertDecimal("90", result.spentThisMonth)
        assertDecimal("189", result.cost.totalSpent)
    }

    @Test
    fun `dues are sorted by urgency, the most urgent first`() {
        val relaxed = ServiceRule(vehicleId = testVehicleId, kind = ServiceKind.AIR_FILTER, kilometerInterval = 20_000.0)
        val urgent = ServiceRule(vehicleId = testVehicleId, kind = ServiceKind.CHAIN, kilometerInterval = 600.0)

        val result = stats(fuel = listOf(full(1, 1_000.0, 40.0), full(40, 1_500.0, 30.0)), rules = listOf(relaxed, urgent))

        assertEquals(listOf(ServiceKind.CHAIN, ServiceKind.AIR_FILTER), result.dues.map { it.due.rule.kind })
        assertEquals(
            ServiceKind.CHAIN,
            result.nextDue
                ?.due
                ?.rule
                ?.kind,
        )
        assertEquals(Level.DUE_SOON, result.attentionLevel)
        assertNull(stats().attentionLevel)
    }

    @Test
    fun `a passed pace estimate is not shown as days late`() {
        val due =
            ServiceDue(
                rule = rule(10_000.0, null),
                forecast = MaintenanceForecast(day(10), MaintenanceForecast.Trigger.DISTANCE, 11_000.0, null),
                since = sample(0, 1_000.0),
            )
        val passed = VehicleStats.ServiceDueItem(due, MaintenanceStatus(Level.DUE_SOON, 1.0, 500.0, -5))
        val overdue = VehicleStats.ServiceDueItem(due, MaintenanceStatus(Level.OVERDUE, 1.0, -5.0, -5))

        assertTrue(passed.status.isEstimatePassed)
        assertEquals("Estimate passed", passed.relativeDue(localizer))
        assertFalse(overdue.status.isEstimatePassed)
        assertEquals((-5).formattedRelativeDays(localizer), overdue.relativeDue(localizer))

        val dateless = VehicleStats.ServiceDueItem(due, MaintenanceStatus(Level.ON_TRACK, 0.5, 500.0, null))
        assertEquals(500.0.formattedRemainingKilometers(localizer), dateless.relativeDue(localizer))
        assertEquals("", VehicleStats.ServiceDueItem(due, MaintenanceStatus(Level.ON_TRACK, 0.5, null, null)).relativeDue(localizer))
    }

    @Test
    fun `a km-only rule without a pace still shows, after the dated ones`() {
        val chain = ServiceRule(vehicleId = testVehicleId, kind = ServiceKind.CHAIN, kilometerInterval = 1_000.0)
        val inspection = ServiceRule(vehicleId = testVehicleId, kind = ServiceKind.INSPECTION, monthInterval = 12)

        val result = stats(rules = listOf(chain, inspection))

        assertEquals(listOf(ServiceKind.INSPECTION, ServiceKind.CHAIN), result.dues.map { it.due.rule.kind })
        assertNull(
            result.dues
                .last()
                .due.forecast.dueDate,
        )
        assertEquals(
            1_000.0,
            result.dues
                .last()
                .status.remainingKilometers,
        )
    }

    @Test
    fun `sections can be filtered by record type and know their total`() {
        val sections = VehicleTimeline.sections(listOf(full(1, 1_000.0, 10.0)), listOf(service(2, 1_100.0, BigDecimal(80))), utc)

        assertEquals(1, sections.size)
        assertDecimal("130", sections[0].total)
        assertEquals(1, sections[0].filtered(VehicleTimeline.Filter.FUEL)?.items?.size)
        assertDecimal("80", sections[0].filtered(VehicleTimeline.Filter.SERVICE)?.total)
        assertEquals(2, sections[0].filtered(VehicleTimeline.Filter.ALL)?.items?.size)

        val fuelOnly = VehicleTimeline.sections(listOf(full(1, 1_000.0, 10.0)), emptyList(), utc)
        assertNull(fuelOnly[0].filtered(VehicleTimeline.Filter.SERVICE))
    }

    @Test
    fun `monthly spending fills months without records with zero`() {
        val sections = VehicleTimeline.sections(listOf(full(1, 1_000.0, 10.0)), listOf(service(70, 1_500.0, BigDecimal(200))), utc)

        val months = VehicleTimeline.spending(sections, 3, day(75), utc)

        assertEquals(3, months.size)
        assertEquals(listOf(50, 0, 0), months.map { it.fuel.toInt() })
        assertEquals(listOf(0, 0, 200), months.map { it.service.toInt() })
        assertDecimal("200", months.last().total)
        assertTrue(VehicleTimeline.spending(sections, 0, day(75), utc).isEmpty())
    }

    @Test
    fun `every filter and status level has its own label, color and icon`() {
        val levels = Level.entries
        assertEquals(3, levels.map { localizer.string(it.label) }.toSet().size)
        assertEquals(3, levels.map { it.icon }.toSet().size)
        assertEquals(3, levels.map { it.color }.toSet().size)
        assertEquals(
            4,
            VehicleTimeline.Filter.entries
                .map { localizer.string(it.label) }
                .toSet()
                .size,
        )
        assertTrue(Level.ON_TRACK < Level.OVERDUE)
    }

    @Test
    fun `display formats include their units`() {
        assertTrue(1_200.0.formattedKilometersPerMonth(localizer).contains("1"))
        assertNotEquals(500.0.formattedRemainingKilometers(localizer), (-500.0).formattedRemainingKilometers(localizer))
        assertTrue((-500.0).formattedRemainingKilometers(localizer).contains("500"))
        assertTrue(BigDecimal(1).formattedPerKilometer(localizer).endsWith("/km"))
        assertTrue(BigDecimal(6).formattedPerLiter(localizer).endsWith("/L"))
        assertNotEquals(3.formattedRelativeDays(localizer), (-3).formattedRelativeDays(localizer))
        assertEquals(
            listOf("today", "tomorrow", "yesterday", "in 1 day", "1 day ago"),
            listOf(0, 1, -1).map { it.formattedRelativeDays(localizer) } +
                listOf(localizer.plural(R.plurals.relative_in_days, 1, 1), localizer.plural(R.plurals.relative_days_ago, 1, 1)),
        )
        assertFalse(day(0).formattedShortMonth().isEmpty())
    }
}
