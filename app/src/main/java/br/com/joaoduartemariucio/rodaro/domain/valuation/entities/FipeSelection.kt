package br.com.joaoduartemariucio.rodaro.domain.valuation.entities

import java.math.BigDecimal
import java.time.YearMonth

data class FipeSelection(
    val fipeCode: String,
    val yearCode: String,
    val brandName: String,
    val modelName: String,
    val yearName: String,
) {
    val modelYear: Int?
        get() =
            yearCode
                .substringBefore('-')
                .toIntOrNull()
                ?.takeIf { it in MIN_YEAR..MAX_YEAR }

    private companion object {
        const val MIN_YEAR = 1900
        const val MAX_YEAR = 2999
    }
}

data class FipeOption(
    val code: String,
    val name: String,
)

data class FipePrice(
    val month: YearMonth,
    val price: BigDecimal,
)

data class FipeDetail(
    val selection: FipeSelection,
    val price: FipePrice,
)
