package br.com.joaoduartemariucio.rodaro.domain.fueling.calculations

import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelGroup
import java.time.Instant

data class ConsumptionInterval(
    val startDate: Instant,
    val endDate: Instant,
    val startOdometer: Double,
    val endOdometer: Double,
    val liters: Double,
    val fuelGroup: FuelGroup? = null,
    val isAfterFuelSwitch: Boolean = false,
) {
    val distance: Double get() = endOdometer - startOdometer

    val kilometersPerLiter: Double get() = distance / liters
}
