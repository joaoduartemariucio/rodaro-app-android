package br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail

import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.domain.common.calculations.monthStart
import br.com.joaoduartemariucio.rodaro.domain.common.calculations.plusMonths
import br.com.joaoduartemariucio.rodaro.domain.common.calculations.sum
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense as ExpenseRecord

object VehicleTimeline {
    sealed interface Item {
        val id: UUID
        val key: String
        val date: Instant
        val odometer: Double?
        val cost: BigDecimal

        data class Fuel(
            val entry: FuelEntry,
        ) : Item {
            override val id get() = entry.id
            override val key get() = "fuel-${entry.id}"
            override val date get() = entry.date
            override val odometer: Double get() = entry.odometer
            override val cost: BigDecimal get() = entry.totalCost
        }

        data class Service(
            val entry: ServiceEntry,
        ) : Item {
            override val id get() = entry.id
            override val key get() = "service-${entry.id}"
            override val date get() = entry.date
            override val odometer: Double get() = entry.odometer
            override val cost: BigDecimal get() = entry.cost
        }

        data class Expense(
            val expense: ExpenseRecord,
        ) : Item {
            override val id get() = expense.id
            override val key get() = "expense-${expense.id}"
            override val date get() = expense.date
            override val odometer get() = expense.odometer
            override val cost: BigDecimal get() = if (expense.isPaid) expense.amount else BigDecimal.ZERO
        }

        fun matches(filter: Filter): Boolean =
            when (filter) {
                Filter.ALL -> true
                Filter.FUEL -> this is Fuel
                Filter.SERVICE -> this is Service
                Filter.EXPENSES -> this is Expense
            }
    }

    enum class Filter(
        val label: Int,
    ) {
        ALL(R.string.filter_all),
        FUEL(R.string.fuel),
        SERVICE(R.string.services),
        EXPENSES(R.string.filter_expenses),
    }

    data class MonthSection(
        val month: Instant,
        val items: List<Item>,
    ) {
        val total: BigDecimal get() = items.map { it.cost }.sum()

        fun filtered(filter: Filter): MonthSection? {
            val kept = items.filter { it.matches(filter) }
            return if (kept.isEmpty()) null else MonthSection(month, kept)
        }
    }

    data class MonthSpending(
        val month: Instant,
        val fuel: BigDecimal,
        val service: BigDecimal,
        val expenses: BigDecimal = BigDecimal.ZERO,
    ) {
        val total: BigDecimal get() = fuel + service + expenses
    }

    fun sections(
        fuel: List<FuelEntry>,
        services: List<ServiceEntry>,
        zone: ZoneId,
        expenses: List<ExpenseRecord> = emptyList(),
    ): List<MonthSection> =
        (fuel.map(Item::Fuel) + services.map(Item::Service) + expenses.map(Item::Expense))
            .groupBy { it.date.monthStart(zone) }
            .map { (month, items) ->
                MonthSection(
                    month,
                    items.sortedWith(compareByDescending<Item> { it.date }.thenBy(nullsLast(reverseOrder())) { it.odometer }),
                )
            }.sortedByDescending { it.month }

    fun spending(
        sections: List<MonthSection>,
        months: Int,
        endingAt: Instant,
        zone: ZoneId,
    ): List<MonthSpending> {
        val byMonth = sections.associate { it.month to it.items }
        val current = endingAt.monthStart(zone)

        return (0 until maxOf(0, months)).reversed().map { offset ->
            val month = current.plusMonths(-offset.toLong(), zone)
            val items = byMonth[month].orEmpty()
            MonthSpending(
                month = month,
                fuel = items.filter { it.matches(Filter.FUEL) }.map { it.cost }.sum(),
                service = items.filter { it.matches(Filter.SERVICE) }.map { it.cost }.sum(),
                expenses = items.filter { it.matches(Filter.EXPENSES) }.map { it.cost }.sum(),
            )
        }
    }
}
