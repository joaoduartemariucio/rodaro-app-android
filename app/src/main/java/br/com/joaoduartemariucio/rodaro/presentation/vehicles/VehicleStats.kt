package br.com.joaoduartemariucio.rodaro.presentation.vehicles

import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.domain.common.calculations.monthStart
import br.com.joaoduartemariucio.rodaro.domain.common.calculations.plusMonths
import br.com.joaoduartemariucio.rodaro.domain.common.calculations.sum
import br.com.joaoduartemariucio.rodaro.domain.common.entities.sortedChronologically
import br.com.joaoduartemariucio.rodaro.domain.costs.calculations.Cost
import br.com.joaoduartemariucio.rodaro.domain.costs.calculations.CostSummary
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.Consumption
import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.ConsumptionInterval
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.Maintenance
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.MaintenanceStatus
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.ServiceDue
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceRule
import br.com.joaoduartemariucio.rodaro.domain.valuation.calculations.DepreciationSummary
import br.com.joaoduartemariucio.rodaro.domain.vehicles.calculations.Odometer
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.presentation.common.Localizer
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedRelativeDays
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedRemainingKilometers
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class VehicleStats(
    vehicle: Vehicle,
    fuel: List<FuelEntry>,
    services: List<ServiceEntry>,
    rules: List<ServiceRule> = emptyList(),
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
    expenses: List<Expense> = emptyList(),
    val depreciation: DepreciationSummary? = null,
) {
    data class ServiceDueItem(
        val due: ServiceDue,
        val status: MaintenanceStatus,
    ) {
        val id: UUID get() = due.rule.id

        fun relativeDue(localizer: Localizer): String {
            if (status.isEstimatePassed) return localizer.string(R.string.estimate_passed)
            status.remainingDays?.let { return it.formattedRelativeDays(localizer) }
            return status.remainingKilometers?.formattedRemainingKilometers(localizer) ?: ""
        }
    }

    val currentOdometer: Double
    val latestConsumption: ConsumptionInterval?
    val averageConsumption: Double?
    val consumptionHistory: List<ConsumptionInterval>
    val consumptionByEntry: Map<UUID, Double>
    val kilometersPerMonth: Double?
    val cost: CostSummary
    val spentThisMonth: BigDecimal
    val lastPricePerLiter: BigDecimal?
    val dues: List<ServiceDueItem>

    val nextDue: ServiceDueItem? get() = dues.firstOrNull()

    val attentionLevel: MaintenanceStatus.Level? get() = dues.maxOfOrNull { it.status.level }

    init {
        val samples = Odometer.samples(vehicle, fuel, services)
        val intervals = Consumption.intervals(fuel)
        val current = Odometer.current(samples) ?: vehicle.initialOdometer

        currentOdometer = current
        latestConsumption = intervals.lastOrNull()
        averageConsumption = Consumption.average(fuel)
        consumptionHistory = intervals
        consumptionByEntry = consumptionByEntry(fuel, intervals)
        kilometersPerMonth = Maintenance.averageKilometersPerMonth(samples)
        cost = Cost.summary(fuel, services, expenses, samples, null, zone).copy(depreciation = depreciation)
        lastPricePerLiter = fuel.sortedChronologically().lastOrNull()?.pricePerLiter

        val monthStart = now.monthStart(zone)
        val nextMonth = monthStart.plusMonths(1, zone)
        val inThisMonth = { date: Instant -> date >= monthStart && date < nextMonth }
        spentThisMonth =
            fuel.filter { inThisMonth(it.date) }.map { it.totalCost }.sum() +
            services.filter { inThisMonth(it.date) }.map { it.cost }.sum() +
            expenses.filter { it.isPaid && inThisMonth(it.date) }.map { it.amount }.sum()

        dues =
            rules
                .mapNotNull { Maintenance.due(it, vehicle, services, samples, zone) }
                .map { ServiceDueItem(it, Maintenance.status(it, current, now, zone)) }
                .sortedWith(
                    compareByDescending<ServiceDueItem> { it.status.level }
                        .then(Maintenance.byDueDate { it.due.forecast.dueDate })
                        .thenBy { it.due.rule.kind.rawValue },
                )
    }

    private companion object {
        fun consumptionByEntry(
            fuel: List<FuelEntry>,
            intervals: List<ConsumptionInterval>,
        ): Map<UUID, Double> =
            intervals
                .mapNotNull { interval ->
                    fuel
                        .firstOrNull { it.isFullTank && it.date == interval.endDate && it.odometer == interval.endOdometer }
                        ?.let { it.id to interval.kilometersPerLiter }
                }.toMap()
    }
}
