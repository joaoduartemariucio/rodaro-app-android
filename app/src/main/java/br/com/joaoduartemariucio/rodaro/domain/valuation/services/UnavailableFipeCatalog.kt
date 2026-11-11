package br.com.joaoduartemariucio.rodaro.domain.valuation.services

import br.com.joaoduartemariucio.rodaro.domain.common.errors.RemoteDataError
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeDetail
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeOption
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipePrice
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind

object UnavailableFipeCatalog : FipeCatalog {
    override suspend fun brands(kind: VehicleKind): List<FipeOption> = throw RemoteDataError.NetworkUnavailable

    override suspend fun models(
        kind: VehicleKind,
        brandCode: String,
    ): List<FipeOption> = throw RemoteDataError.NetworkUnavailable

    override suspend fun years(
        kind: VehicleKind,
        brandCode: String,
        modelCode: String,
    ): List<FipeOption> = throw RemoteDataError.NetworkUnavailable

    override suspend fun detail(
        kind: VehicleKind,
        brandCode: String,
        modelCode: String,
        year: FipeOption,
    ): FipeDetail = throw RemoteDataError.NetworkUnavailable

    override suspend fun latestPrice(
        kind: VehicleKind,
        fipeCode: String,
        yearCode: String,
    ): FipePrice = throw RemoteDataError.NetworkUnavailable

    override suspend fun recentPrices(
        kind: VehicleKind,
        fipeCode: String,
        yearCode: String,
    ): List<FipePrice> = throw RemoteDataError.NetworkUnavailable
}
