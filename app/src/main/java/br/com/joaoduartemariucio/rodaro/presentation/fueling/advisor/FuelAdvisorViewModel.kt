package br.com.joaoduartemariucio.rodaro.presentation.fueling.advisor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.app.NoAnalytics
import br.com.joaoduartemariucio.rodaro.domain.common.entities.sortedChronologically
import br.com.joaoduartemariucio.rodaro.domain.common.services.Analytics
import br.com.joaoduartemariucio.rodaro.domain.common.services.DateProvider
import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.FuelAdvice
import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.FuelAdvisor
import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.FuelRatio
import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.FuelRatioEstimate
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelGroup
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.presentation.common.Localizer
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.common.UnitConversion
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedPerKilometer
import java.math.BigDecimal
import kotlin.math.roundToInt

class FuelAdvisorViewModel(
    vehicle: Vehicle,
    fuel: List<FuelEntry>,
    dateProvider: DateProvider,
    analytics: Analytics = NoAnalytics,
) : ViewModel() {
    val estimate: FuelRatioEstimate = FuelRatio.estimate(fuel, vehicle.kind, dateProvider.now, dateProvider.zone)

    var ethanolPrice by mutableStateOf(lastPrice(fuel, FuelGroup.ETANOL))
    var gasolinePrice by mutableStateOf(lastPrice(fuel, FuelGroup.GASOLINA))

    init {
        analytics.event("fuel_advisor_used", mapOf("basis" to basisName))
    }

    val parsedEthanolPrice: BigDecimal?
        get() = NumberInput.decimal(ethanolPrice, NumberInput.Style.FUEL_PRICE)?.let(UnitConversion::perVolumeToLiters)

    val parsedGasolinePrice: BigDecimal?
        get() = NumberInput.decimal(gasolinePrice, NumberInput.Style.FUEL_PRICE)?.let(UnitConversion::perVolumeToLiters)

    val advice: FuelAdvice?
        get() {
            val ethanol = parsedEthanolPrice ?: return null
            val gasoline = parsedGasolinePrice ?: return null
            return FuelAdvisor.advise(ethanol, gasoline, estimate)
        }

    val showsAlternatingHint: Boolean
        get() = (estimate.basis as? FuelRatioEstimate.Basis.GeneralRule)?.reason == FuelRatioEstimate.Reason.ALWAYS_ALTERNATING

    val thresholdText: String?
        get() = advice?.thresholdEthanolPrice?.let { NumberInput.text(UnitConversion.perVolume(it), NumberInput.Style.FUEL_PRICE) }

    fun verdict(localizer: Localizer): String? {
        val advice = advice ?: return null
        return localizer.string(
            when (advice.cheaper) {
                FuelGroup.ETANOL -> R.string.advisor_choose_ethanol
                FuelGroup.GASOLINA -> R.string.advisor_choose_gasoline
                else -> R.string.advisor_tie
            },
        )
    }

    fun costPerKilometer(localizer: Localizer): String? {
        val advice = advice ?: return null
        val ethanol = advice.ethanolCostPerKilometer ?: return null
        val gasoline = advice.gasolineCostPerKilometer ?: return null
        return localizer.string(
            R.string.advisor_cost_per_km,
            ethanol.formattedPerKilometer(localizer),
            gasoline.formattedPerKilometer(localizer),
        )
    }

    fun basis(localizer: Localizer): String =
        when (val basis = estimate.basis) {
            is FuelRatioEstimate.Basis.Personal -> {
                val intervals = basis.ethanolIntervals + basis.gasolineIntervals
                localizer.plural(R.plurals.advisor_basis_personal, intervals, percent(estimate.ratio), intervals)
            }

            is FuelRatioEstimate.Basis.GeneralRule -> {
                localizer.string(R.string.advisor_basis_general, percent(estimate.ratio)) + " " +
                    localizer.string(
                        when (basis.reason) {
                            FuelRatioEstimate.Reason.NOT_ENOUGH_DATA -> R.string.advisor_reason_not_enough
                            FuelRatioEstimate.Reason.ALWAYS_ALTERNATING -> R.string.advisor_reason_alternating
                            FuelRatioEstimate.Reason.INCONSISTENT -> R.string.advisor_reason_inconsistent
                        },
                    )
            }
        }

    private val basisName: String
        get() =
            when (val basis = estimate.basis) {
                is FuelRatioEstimate.Basis.Personal -> "personal"
                is FuelRatioEstimate.Basis.GeneralRule -> "general_${basis.reason.name.lowercase()}"
            }

    private companion object {
        fun lastPrice(
            fuel: List<FuelEntry>,
            group: FuelGroup,
        ): String =
            fuel
                .sortedChronologically()
                .lastOrNull { it.fuelType?.group == group }
                ?.let { NumberInput.text(UnitConversion.perVolume(it.pricePerLiter), NumberInput.Style.FUEL_PRICE) }
                .orEmpty()

        fun percent(ratio: Double): Int = (ratio * 100).roundToInt()
    }
}
