package br.com.joaoduartemariucio.rodaro.domain.fueling.calculations

import br.com.joaoduartemariucio.rodaro.domain.common.calculations.toDecimal
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelGroup
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

data class FuelAdvice(
    val cheaper: FuelGroup?,
    val thresholdEthanolPrice: BigDecimal,
    val ethanolCostPerKilometer: BigDecimal?,
    val gasolineCostPerKilometer: BigDecimal?,
    val estimate: FuelRatioEstimate,
)

object FuelAdvisor {
    private const val THRESHOLD_DECIMALS = 3

    fun advise(
        ethanolPrice: BigDecimal,
        gasolinePrice: BigDecimal,
        estimate: FuelRatioEstimate,
    ): FuelAdvice? {
        if (ethanolPrice.signum() <= 0 || gasolinePrice.signum() <= 0) return null

        val threshold = gasolinePrice.multiply(estimate.ratio.toDecimal(), MathContext.DECIMAL128)
        val comparison = ethanolPrice.compareTo(threshold)
        val cheaper =
            when {
                comparison < 0 -> FuelGroup.ETANOL
                comparison > 0 -> FuelGroup.GASOLINA
                else -> null
            }
        val personal = estimate.basis as? FuelRatioEstimate.Basis.Personal

        return FuelAdvice(
            cheaper = cheaper,
            thresholdEthanolPrice = threshold.setScale(THRESHOLD_DECIMALS, RoundingMode.HALF_EVEN),
            ethanolCostPerKilometer = personal?.let { costPerKilometer(ethanolPrice, it.ethanolKilometersPerLiter) },
            gasolineCostPerKilometer = personal?.let { costPerKilometer(gasolinePrice, it.gasolineKilometersPerLiter) },
            estimate = estimate,
        )
    }

    private fun costPerKilometer(
        price: BigDecimal,
        kilometersPerLiter: Double,
    ): BigDecimal? = if (kilometersPerLiter > 0) price.divide(kilometersPerLiter.toDecimal(), MathContext.DECIMAL128) else null
}
