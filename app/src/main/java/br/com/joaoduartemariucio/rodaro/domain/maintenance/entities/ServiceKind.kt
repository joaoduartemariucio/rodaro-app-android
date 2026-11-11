package br.com.joaoduartemariucio.rodaro.domain.maintenance.entities

enum class ServiceKind(
    val rawValue: String,
) {
    OIL_CHANGE("oilChange"),
    OIL_FILTER("oilFilter"),
    AIR_FILTER("airFilter"),
    FUEL_FILTER("fuelFilter"),
    SPARK_PLUGS("sparkPlugs"),
    BRAKES("brakes"),
    TIRES("tires"),
    CHAIN("chain"),
    COOLANT("coolant"),
    INSPECTION("inspection"),
    TIMING_BELT("timingBelt"),
    BRAKE_FLUID("brakeFluid"),
    BATTERY("battery"),
    ALIGNMENT("alignment"),
    CABIN_FILTER("cabinFilter"),
    TRANSMISSION_OIL("transmissionOil"),
    CHAIN_KIT("chainKit"),
    CVT_BELT("cvtBelt"),
    VALVE_CLEARANCE("valveClearance"),
    OTHER("other"),
}
