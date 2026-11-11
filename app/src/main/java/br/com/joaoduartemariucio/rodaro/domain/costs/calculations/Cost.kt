package br.com.joaoduartemariucio.rodaro.domain.costs.calculations

import br.com.joaoduartemariucio.rodaro.domain.common.calculations.sum
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.vehicles.calculations.Odometer
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.OdometerSample
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CostPeriod(
    val start: Instant,
    val end: Instant,
)

object Cost {
    fun summary(
        fuel: List<FuelEntry>,
        services: List<ServiceEntry>,
        expenses: List<Expense> = emptyList(),
        samples: List<OdometerSample> = emptyList(),
        period: CostPeriod? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): CostSummary {
        val inPeriod = { date: Instant -> period == null || (date >= period.start && date < period.end) }
        val periodFuel = fuel.filter { inPeriod(it.date) }
        val periodServices = services.filter { inPeriod(it.date) }
        val paid = expenses.filter { it.isPaid }
        val cash = paid.filter { inPeriod(it.date) }

        val distance: Double
        val window: Pair<LocalDate, LocalDate>?
        if (period == null) {
            val odometers = fuel.map { it.odometer } + services.map { it.odometer }
            val lowest = odometers.minOrNull()
            val highest = odometers.maxOrNull()
            distance = if (lowest != null && highest != null) maxOf(0.0, highest - lowest) else 0.0
            val dates = fuel.map { it.date } + services.map { it.date }
            val first = dates.minOrNull()
            val last = dates.maxOrNull()
            window =
                if (first != null && last != null) {
                    first.atZone(zone).toLocalDate() to last.atZone(zone).toLocalDate().plusDays(1)
                } else {
                    null
                }
        } else {
            val start = Odometer.interpolated(period.start, samples)
            val end = Odometer.interpolated(period.end, samples)
            distance = if (start != null && end != null) maxOf(0.0, end - start) else 0.0
            window = period.start.atZone(zone).toLocalDate() to period.end.atZone(zone).toLocalDate()
        }

        val inCost =
            paid
                .filter { it.category.countsInCostPerKm }
                .map { expense ->
                    when {
                        expense.category.isFixed && window != null -> ExpenseAccrual.accrued(expense, window.first, window.second, zone)
                        expense.category.isFixed -> BigDecimal.ZERO
                        inPeriod(expense.date) -> expense.amount
                        else -> BigDecimal.ZERO
                    }
                }.sum()

        return CostSummary(
            fuelCost = periodFuel.map { it.totalCost }.sum(),
            serviceCost = periodServices.map { it.cost }.sum(),
            distance = distance,
            expensesInCost = inCost,
            expensesOutsideCost =
                cash
                    .filter { it.category == ExpenseCategory.FINANCIAMENTO || it.category == ExpenseCategory.ACESSORIOS }
                    .map { it.amount }
                    .sum(),
            fines = cash.filter { it.category == ExpenseCategory.MULTA }.map { it.amount }.sum(),
            byCategory = cash.groupBy { it.category }.mapValues { (_, items) -> items.map { it.amount }.sum() },
        )
    }
}
