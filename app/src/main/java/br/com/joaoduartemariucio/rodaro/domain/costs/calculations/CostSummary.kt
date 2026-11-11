package br.com.joaoduartemariucio.rodaro.domain.costs.calculations

import br.com.joaoduartemariucio.rodaro.domain.common.calculations.sum
import br.com.joaoduartemariucio.rodaro.domain.common.calculations.toDecimal
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.valuation.calculations.DepreciationSummary
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
    val depreciation: DepreciationSummary? = null,
) {
    val totalCost: BigDecimal get() = fuelCost + serviceCost + expensesInCost

    val expensesSpent: BigDecimal get() = byCategory.values.sum()

    val totalSpent: BigDecimal get() = fuelCost + serviceCost + expensesSpent

    val costPerKilometer: BigDecimal?
        get() = if (distance > 0) totalCost.divide(distance.toDecimal(), MathContext.DECIMAL128) else null

    val ownershipCost: BigDecimal get() = totalCost + (depreciation?.amount ?: BigDecimal.ZERO)

    val ownershipCostPerKilometer: BigDecimal?
        get() {
            val running = costPerKilometer ?: return null
            val depreciationPerKilometer = depreciation?.perKilometer ?: return null
            return running + depreciationPerKilometer
        }
}
