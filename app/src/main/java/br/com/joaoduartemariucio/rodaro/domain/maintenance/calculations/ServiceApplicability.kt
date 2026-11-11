package br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations

import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind

object ServiceApplicability {
    private val car =
        listOf(
            ServiceKind.OIL_CHANGE,
            ServiceKind.OIL_FILTER,
            ServiceKind.AIR_FILTER,
            ServiceKind.FUEL_FILTER,
            ServiceKind.SPARK_PLUGS,
            ServiceKind.BRAKES,
            ServiceKind.TIRES,
            ServiceKind.COOLANT,
            ServiceKind.INSPECTION,
            ServiceKind.TIMING_BELT,
            ServiceKind.BRAKE_FLUID,
            ServiceKind.BATTERY,
            ServiceKind.ALIGNMENT,
            ServiceKind.CABIN_FILTER,
            ServiceKind.TRANSMISSION_OIL,
            ServiceKind.OTHER,
        )

    private val motorcycle =
        listOf(
            ServiceKind.OIL_CHANGE,
            ServiceKind.OIL_FILTER,
            ServiceKind.AIR_FILTER,
            ServiceKind.FUEL_FILTER,
            ServiceKind.SPARK_PLUGS,
            ServiceKind.BRAKES,
            ServiceKind.TIRES,
            ServiceKind.CHAIN,
            ServiceKind.CHAIN_KIT,
            ServiceKind.COOLANT,
            ServiceKind.INSPECTION,
            ServiceKind.BRAKE_FLUID,
            ServiceKind.BATTERY,
            ServiceKind.CVT_BELT,
            ServiceKind.VALVE_CLEARANCE,
            ServiceKind.OTHER,
        )

    fun kinds(vehicleKind: VehicleKind): List<ServiceKind> =
        when (vehicleKind) {
            VehicleKind.CAR -> car
            VehicleKind.MOTORCYCLE -> motorcycle
        }

    fun isApplicable(
        kind: ServiceKind,
        vehicleKind: VehicleKind,
    ): Boolean = kind in kinds(vehicleKind)
}
