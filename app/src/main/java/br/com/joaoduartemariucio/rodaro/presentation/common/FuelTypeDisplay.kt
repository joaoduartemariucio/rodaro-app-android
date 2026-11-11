package br.com.joaoduartemariucio.rodaro.presentation.common

import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelGroup
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.FuelCapability

val FuelType.label: Int
    get() =
        when (this) {
            FuelType.GASOLINA_COMUM -> R.string.fuel_gasolina_comum
            FuelType.GASOLINA_ADITIVADA -> R.string.fuel_gasolina_aditivada
            FuelType.GASOLINA_PREMIUM -> R.string.fuel_gasolina_premium
            FuelType.ETANOL -> R.string.fuel_etanol
            FuelType.DIESEL_S10 -> R.string.fuel_diesel_s10
            FuelType.DIESEL_S500 -> R.string.fuel_diesel_s500
        }

val FuelType.shortLabel: Int
    get() =
        when (this) {
            FuelType.GASOLINA_COMUM -> R.string.fuel_short_gasolina_comum
            FuelType.GASOLINA_ADITIVADA -> R.string.fuel_short_gasolina_aditivada
            FuelType.GASOLINA_PREMIUM -> R.string.fuel_short_gasolina_premium
            FuelType.ETANOL -> R.string.fuel_short_etanol
            FuelType.DIESEL_S10 -> R.string.fuel_short_diesel_s10
            FuelType.DIESEL_S500 -> R.string.fuel_short_diesel_s500
        }

val FuelCapability.label: Int
    get() =
        when (this) {
            FuelCapability.FLEX -> R.string.capability_flex
            FuelCapability.GASOLINA -> R.string.capability_gasolina
            FuelCapability.ETANOL -> R.string.capability_etanol
            FuelCapability.DIESEL -> R.string.capability_diesel
        }

val FuelGroup.label: Int
    get() =
        when (this) {
            FuelGroup.GASOLINA -> R.string.fuel_group_gasolina
            FuelGroup.ETANOL -> R.string.fuel_group_etanol
            FuelGroup.DIESEL -> R.string.fuel_group_diesel
        }
