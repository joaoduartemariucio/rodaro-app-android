package br.com.joaoduartemariucio.rodaro.domain.fueling.calculations

import br.com.joaoduartemariucio.rodaro.domain.common.calculations.plusMonths
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelGroup
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import java.time.Instant
import java.time.ZoneId

data class FuelRatioEstimate(
    val ratio: Double,
    val basis: Basis,
) {
    sealed interface Basis {
        data class Personal(
            val ethanolKilometersPerLiter: Double,
            val gasolineKilometersPerLiter: Double,
            val ethanolIntervals: Int,
            val gasolineIntervals: Int,
        ) : Basis

        data class GeneralRule(
            val reason: Reason,
        ) : Basis
    }

    enum class Reason {
        NOT_ENOUGH_DATA,
        ALWAYS_ALTERNATING,
        INCONSISTENT,
    }

    val isPersonal: Boolean get() = basis is Basis.Personal
}

object FuelRatio {
    const val GENERAL_RULE = 0.70
    val SANITY_BAND = 0.60..0.85
    const val MIN_INTERVALS = 3
    const val MIN_SWITCHES_FOR_ALTERNATING = 2
    const val WINDOW_MONTHS = 12L

    fun minimumKilometers(kind: VehicleKind): Double =
        when (kind) {
            VehicleKind.CAR -> 600.0
            VehicleKind.MOTORCYCLE -> 300.0
        }

    fun estimate(
        records: List<FuelEntry>,
        kind: VehicleKind,
        now: Instant,
        zone: ZoneId,
    ): FuelRatioEstimate {
        val since = now.plusMonths(-WINDOW_MONTHS, zone)
        val window = Consumption.intervals(records).filter { it.endDate >= since && it.endDate <= now }
        val ethanol = window.filter { it.fuelGroup == FuelGroup.ETANOL }
        val gasoline = window.filter { it.fuelGroup == FuelGroup.GASOLINA }

        if (!isEnough(ethanol, kind) || !isEnough(gasoline, kind)) {
            val reason =
                if (window.count { it.isAfterFuelSwitch } >= MIN_SWITCHES_FOR_ALTERNATING) {
                    FuelRatioEstimate.Reason.ALWAYS_ALTERNATING
                } else {
                    FuelRatioEstimate.Reason.NOT_ENOUGH_DATA
                }
            return general(reason)
        }

        val ethanolKilometersPerLiter = Consumption.averageOf(ethanol) ?: return general(FuelRatioEstimate.Reason.NOT_ENOUGH_DATA)
        val gasolineKilometersPerLiter = Consumption.averageOf(gasoline) ?: return general(FuelRatioEstimate.Reason.NOT_ENOUGH_DATA)
        val ratio = ethanolKilometersPerLiter / gasolineKilometersPerLiter
        if (ratio !in SANITY_BAND) return general(FuelRatioEstimate.Reason.INCONSISTENT)

        return FuelRatioEstimate(
            ratio = ratio,
            basis =
                FuelRatioEstimate.Basis.Personal(
                    ethanolKilometersPerLiter = ethanolKilometersPerLiter,
                    gasolineKilometersPerLiter = gasolineKilometersPerLiter,
                    ethanolIntervals = ethanol.size,
                    gasolineIntervals = gasoline.size,
                ),
        )
    }

    private fun isEnough(
        intervals: List<ConsumptionInterval>,
        kind: VehicleKind,
    ): Boolean = intervals.size >= MIN_INTERVALS && intervals.sumOf { it.distance } >= minimumKilometers(kind)

    private fun general(reason: FuelRatioEstimate.Reason) = FuelRatioEstimate(GENERAL_RULE, FuelRatioEstimate.Basis.GeneralRule(reason))
}
