package br.com.joaoduartemariucio.rodaro.fakes

import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeDetail
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeOption
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipePrice
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeSelection
import br.com.joaoduartemariucio.rodaro.domain.valuation.services.FipeCatalog
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import java.math.BigDecimal
import java.time.YearMonth

class FakeFipeCatalog(
    var brands: List<FipeOption> = listOf(FipeOption("59", "VW - VolksWagen"), FipeOption("21", "Fiat")),
    var models: List<FipeOption> = listOf(FipeOption("5585", "Amarok CD 2.0 16V TDI 4x4 Diesel"), FipeOption("1", "Gol 1.0")),
    var years: List<FipeOption> = listOf(FipeOption("2014-3", "2014 Diesel"), FipeOption("2013-3", "2013 Diesel")),
    var latest: FipePrice = FipePrice(YearMonth.of(2026, 11), BigDecimal("85386.00")),
    var recent: List<FipePrice> =
        listOf(
            FipePrice(YearMonth.of(2026, 9), BigDecimal("87000.00")),
            FipePrice(YearMonth.of(2026, 10), BigDecimal("86000.00")),
            FipePrice(YearMonth.of(2026, 11), BigDecimal("85386.00")),
        ),
    var failure: Exception? = null,
) {
    val calls = mutableMapOf<String, Int>()
    val kinds = mutableListOf<VehicleKind>()

    val totalCalls: Int get() = calls.values.sum()

    private fun call(
        name: String,
        kind: VehicleKind,
    ) {
        calls[name] = (calls[name] ?: 0) + 1
        kinds += kind
        failure?.let { throw it }
    }

    val catalog: FipeCatalog =
        object : FipeCatalog {
            override suspend fun brands(kind: VehicleKind): List<FipeOption> {
                call("brands", kind)
                return brands
            }

            override suspend fun models(
                kind: VehicleKind,
                brandCode: String,
            ): List<FipeOption> {
                call("models", kind)
                return models
            }

            override suspend fun years(
                kind: VehicleKind,
                brandCode: String,
                modelCode: String,
            ): List<FipeOption> {
                call("years", kind)
                return years
            }

            override suspend fun detail(
                kind: VehicleKind,
                brandCode: String,
                modelCode: String,
                year: FipeOption,
            ): FipeDetail {
                call("detail", kind)
                return FipeDetail(
                    FipeSelection("005340-6", year.code, "VW - VolksWagen", "Amarok CD 2.0 16V TDI 4x4 Diesel", year.name),
                    latest,
                )
            }

            override suspend fun latestPrice(
                kind: VehicleKind,
                fipeCode: String,
                yearCode: String,
            ): FipePrice {
                call("latest", kind)
                return latest
            }

            override suspend fun recentPrices(
                kind: VehicleKind,
                fipeCode: String,
                yearCode: String,
            ): List<FipePrice> {
                call("recent", kind)
                return recent
            }
        }
}
