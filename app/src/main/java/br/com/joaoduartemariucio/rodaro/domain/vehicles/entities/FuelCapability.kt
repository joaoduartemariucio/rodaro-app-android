package br.com.joaoduartemariucio.rodaro.domain.vehicles.entities

import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType

enum class FuelCapability(
    val rawValue: String,
) {
    FLEX("flex"),
    GASOLINA("gasolina"),
    ETANOL("etanol"),
    DIESEL("diesel"),
    ;

    val fuelTypes: List<FuelType>
        get() =
            when (this) {
                FLEX -> listOf(FuelType.GASOLINA_COMUM, FuelType.GASOLINA_ADITIVADA, FuelType.GASOLINA_PREMIUM, FuelType.ETANOL)
                GASOLINA -> listOf(FuelType.GASOLINA_COMUM, FuelType.GASOLINA_ADITIVADA, FuelType.GASOLINA_PREMIUM)
                ETANOL -> listOf(FuelType.ETANOL)
                DIESEL -> listOf(FuelType.DIESEL_S10, FuelType.DIESEL_S500)
            }

    companion object {
        fun available(kind: VehicleKind): List<FuelCapability> =
            when (kind) {
                VehicleKind.CAR -> entries
                VehicleKind.MOTORCYCLE -> entries.filter { it != DIESEL }
            }

        fun of(raw: String?): FuelCapability = entries.firstOrNull { it.rawValue == raw } ?: FLEX
    }
}
