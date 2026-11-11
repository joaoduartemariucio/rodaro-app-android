package br.com.joaoduartemariucio.rodaro.data.network

import kotlinx.serialization.Serializable

@Serializable
data class FipeOptionDto(
    val code: String,
    val name: String,
)

@Serializable
data class FipeDetailDto(
    val price: String? = null,
    val brand: String,
    val model: String,
    val modelYear: Int? = null,
    val fuel: String? = null,
    val codeFipe: String,
    val referenceMonth: String? = null,
    val priceHistory: List<FipeHistoryDto> = emptyList(),
)

@Serializable
data class FipeHistoryDto(
    val price: String,
    val month: String,
    val reference: String? = null,
)
