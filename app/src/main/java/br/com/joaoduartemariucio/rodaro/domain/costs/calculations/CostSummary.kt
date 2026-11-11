package br.com.joaoduartemariucio.rodaro.domain.costs.calculations

import br.com.joaoduartemariucio.rodaro.domain.common.calculations.sum
import br.com.joaoduartemariucio.rodaro.domain.common.calculations.toDecimal
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import java.math.BigDecimal
import java.math.MathContext

data class CostSummary(
    val fuelCost: BigDecimal,
    val serviceCost: BigDecimal,
    val distance: Double,
    val expensesInCost: BigDecimal = BigDecimal.ZERO,
    val expensesOutsideCost: BigDecimal = BigDecimal.ZERO,
    val fines: BigDecimal = BigDecimal.ZERO,
    val byCategory: Map<ExpenseCategory, BigDecimal> = emptyMap(),
) {
    val totalCost: BigDecimal get() = fuelCost + serviceCost + expensesInCost

    val expensesSpent: BigDecimal get() = byCategory.values.sum()

    val totalSpent: BigDecimal get() = fuelCost + serviceCost + expensesSpent

    val costPerKilometer: BigDecimal?
        get() = if (distance > 0) totalCost.divide(distance.toDecimal(), MathContext.DECIMAL128) else null
}
