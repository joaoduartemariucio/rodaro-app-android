package br.com.joaoduartemariucio.rodaro.domain.valuation.services

import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeDetail
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeOption
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipePrice
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind

interface FipeCatalog {
    suspend fun brands(kind: VehicleKind): List<FipeOption>

    suspend fun models(
        kind: VehicleKind,
        brandCode: String,
    ): List<FipeOption>

    suspend fun years(
        kind: VehicleKind,
        brandCode: String,
        modelCode: String,
    ): List<FipeOption>

    suspend fun detail(
        kind: VehicleKind,
        brandCode: String,
        modelCode: String,
        year: FipeOption,
    ): FipeDetail

    suspend fun latestPrice(
        kind: VehicleKind,
        fipeCode: String,
        yearCode: String,
    ): FipePrice

    suspend fun recentPrices(
        kind: VehicleKind,
        fipeCode: String,
        yearCode: String,
    ): List<FipePrice>
}
