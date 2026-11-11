package br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations

import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind

data class ServiceInterval(
    val kilometers: Double?,
    val months: Int?,
)

object ServiceDefaults {
    private val car =
        mapOf(
            ServiceKind.OIL_CHANGE to ServiceInterval(10_000.0, 12),
            ServiceKind.OIL_FILTER to ServiceInterval(10_000.0, 12),
            ServiceKind.AIR_FILTER to ServiceInterval(10_000.0, 12),
            ServiceKind.FUEL_FILTER to ServiceInterval(10_000.0, 12),
            ServiceKind.SPARK_PLUGS to ServiceInterval(30_000.0, 36),
            ServiceKind.BRAKES to ServiceInterval(10_000.0, 12),
            ServiceKind.TIRES to ServiceInterval(10_000.0, 12),
            ServiceKind.COOLANT to ServiceInterval(40_000.0, 24),
            ServiceKind.INSPECTION to ServiceInterval(10_000.0, 12),
            ServiceKind.TIMING_BELT to ServiceInterval(50_000.0, 48),
            ServiceKind.BRAKE_FLUID to ServiceInterval(null, 24),
            ServiceKind.BATTERY to ServiceInterval(null, 36),
            ServiceKind.ALIGNMENT to ServiceInterval(10_000.0, 12),
            ServiceKind.CABIN_FILTER to ServiceInterval(10_000.0, 12),
            ServiceKind.TRANSMISSION_OIL to ServiceInterval(60_000.0, 60),
        )

    private val motorcycle =
        mapOf(
            ServiceKind.OIL_CHANGE to ServiceInterval(3_000.0, 6),
            ServiceKind.OIL_FILTER to ServiceInterval(6_000.0, 12),
            ServiceKind.AIR_FILTER to ServiceInterval(10_000.0, 12),
            ServiceKind.FUEL_FILTER to ServiceInterval(10_000.0, 24),
            ServiceKind.SPARK_PLUGS to ServiceInterval(10_000.0, 24),
            ServiceKind.BRAKES to ServiceInterval(5_000.0, 6),
            ServiceKind.TIRES to ServiceInterval(5_000.0, 6),
            ServiceKind.CHAIN to ServiceInterval(1_000.0, 1),
            ServiceKind.CHAIN_KIT to ServiceInterval(20_000.0, null),
            ServiceKind.COOLANT to ServiceInterval(null, 24),
            ServiceKind.INSPECTION to ServiceInterval(6_000.0, 6),
            ServiceKind.BRAKE_FLUID to ServiceInterval(null, 24),
            ServiceKind.BATTERY to ServiceInterval(null, 24),
            ServiceKind.CVT_BELT to ServiceInterval(20_000.0, 24),
            ServiceKind.VALVE_CLEARANCE to ServiceInterval(12_000.0, null),
        )

    fun interval(
        kind: ServiceKind,
        vehicleKind: VehicleKind,
    ): ServiceInterval? =
        when (vehicleKind) {
            VehicleKind.CAR -> car[kind]
            VehicleKind.MOTORCYCLE -> motorcycle[kind]
        }

    fun needsStrongManualHint(
        kind: ServiceKind,
        vehicleKind: VehicleKind,
    ): Boolean = vehicleKind == VehicleKind.MOTORCYCLE || kind == ServiceKind.TIMING_BELT || kind == ServiceKind.TRANSMISSION_OIL
}
