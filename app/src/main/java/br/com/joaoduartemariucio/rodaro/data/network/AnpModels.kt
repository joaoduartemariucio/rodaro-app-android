package br.com.joaoduartemariucio.rodaro.data.network

import kotlinx.serialization.Serializable

@Serializable
data class AnpPricesDto(
    val format: String,
    val formatVersion: Int,
    val updatedAt: String,
    val weeks: List<AnpWeekDto> = emptyList(),
)

@Serializable
data class AnpWeekDto(
    val start: String,
    val end: String,
    val states: Map<String, Map<String, AnpPriceDto>> = emptyMap(),
)

@Serializable
data class AnpPriceDto(
    val mean: String,
    val sd: String? = null,
    val stations: Int? = null,
)
