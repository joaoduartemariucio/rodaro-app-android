package br.com.joaoduartemariucio.rodaro.domain.fueling.entities

enum class FuelGroup {
    GASOLINA,
    ETANOL,
    DIESEL,
}

enum class FuelType(
    val rawValue: String,
    val group: FuelGroup,
) {
    GASOLINA_COMUM("gasolinaComum", FuelGroup.GASOLINA),
    GASOLINA_ADITIVADA("gasolinaAditivada", FuelGroup.GASOLINA),
    GASOLINA_PREMIUM("gasolinaPremium", FuelGroup.GASOLINA),
    ETANOL("etanol", FuelGroup.ETANOL),
    DIESEL_S10("dieselS10", FuelGroup.DIESEL),
    DIESEL_S500("dieselS500", FuelGroup.DIESEL),
    ;

    companion object {
        fun of(raw: String?): FuelType? = entries.firstOrNull { it.rawValue == raw }
    }
}
