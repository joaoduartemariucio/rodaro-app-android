package br.com.joaoduartemariucio.rodaro.data.valuation.services

import br.com.joaoduartemariucio.rodaro.data.network.FipeApi
import br.com.joaoduartemariucio.rodaro.data.network.FipeDetailDto
import br.com.joaoduartemariucio.rodaro.data.network.FipeOptionDto
import br.com.joaoduartemariucio.rodaro.data.network.remote
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RemoteDataError
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeDetail
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeOption
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipePrice
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeSelection
import br.com.joaoduartemariucio.rodaro.domain.valuation.services.FipeCatalog
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FipeHttpCatalog(
    private val api: FipeApi,
) : FipeCatalog {
    override suspend fun brands(kind: VehicleKind): List<FipeOption> = call { api.brands(type(kind)).map(::option) }

    override suspend fun models(
        kind: VehicleKind,
        brandCode: String,
    ): List<FipeOption> = call { api.models(type(kind), brandCode).map(::option) }

    override suspend fun years(
        kind: VehicleKind,
        brandCode: String,
        modelCode: String,
    ): List<FipeOption> = call { api.years(type(kind), brandCode, modelCode).map(::option) }

    override suspend fun detail(
        kind: VehicleKind,
        brandCode: String,
        modelCode: String,
        year: FipeOption,
    ): FipeDetail =
        call {
            val dto = api.detail(type(kind), brandCode, modelCode, year.code)
            FipeDetail(FipeSelection(dto.codeFipe, year.code, dto.brand, dto.model, year.name), price(dto))
        }

    override suspend fun latestPrice(
        kind: VehicleKind,
        fipeCode: String,
        yearCode: String,
    ): FipePrice = call { price(api.price(type(kind), fipeCode, yearCode)) }

    override suspend fun recentPrices(
        kind: VehicleKind,
        fipeCode: String,
        yearCode: String,
    ): List<FipePrice> =
        call {
            api
                .history(type(kind), fipeCode, yearCode)
                .priceHistory
                .mapNotNull { item ->
                    val month = FipeText.month(item.month) ?: return@mapNotNull null
                    val price = FipeText.price(item.price) ?: return@mapNotNull null
                    FipePrice(month, price)
                }.sortedBy { it.month }
        }

    private suspend fun <T> call(block: suspend () -> T): T = withContext(Dispatchers.IO) { remote(block) }

    private fun option(dto: FipeOptionDto) = FipeOption(dto.code, dto.name)

    private fun price(dto: FipeDetailDto): FipePrice {
        val price = dto.price?.let(FipeText::price) ?: throw RemoteDataError.Unreadable()
        val month = dto.referenceMonth?.let(FipeText::month) ?: throw RemoteDataError.Unreadable()
        return FipePrice(month, price)
    }

    private fun type(kind: VehicleKind): String =
        when (kind) {
            VehicleKind.CAR -> "cars"
            VehicleKind.MOTORCYCLE -> "motorcycles"
        }
}
