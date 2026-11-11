package br.com.joaoduartemariucio.rodaro.domain.fueling.entities

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class RegionalPrice(
    val mean: BigDecimal,
    val standardDeviation: BigDecimal? = null,
    val stations: Int? = null,
)

data class RegionalWeek(
    val start: LocalDate,
    val end: LocalDate,
    val prices: Map<String, Map<FuelType, RegionalPrice>>,
)

data class RegionalPriceTable(
    val updatedAt: Instant,
    val weeks: List<RegionalWeek>,
)
