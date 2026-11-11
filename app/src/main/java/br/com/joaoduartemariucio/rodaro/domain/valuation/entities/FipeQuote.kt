package br.com.joaoduartemariucio.rodaro.domain.valuation.entities

import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

data class FipeQuote(
    val id: UUID,
    val vehicleId: UUID,
    val fipeCode: String,
    val yearCode: String,
    val month: YearMonth,
    val price: BigDecimal,
    val fetchedAt: Instant,
) {
    companion object {
        fun id(
            vehicleId: UUID,
            fipeCode: String,
            yearCode: String,
            month: YearMonth,
        ): UUID = UUID.nameUUIDFromBytes("$vehicleId:$fipeCode:$yearCode:$month".toByteArray())

        fun of(
            vehicleId: UUID,
            selection: FipeSelection,
            price: FipePrice,
            fetchedAt: Instant,
        ): FipeQuote =
            FipeQuote(
                id = id(vehicleId, selection.fipeCode, selection.yearCode, price.month),
                vehicleId = vehicleId,
                fipeCode = selection.fipeCode,
                yearCode = selection.yearCode,
                month = price.month,
                price = price.price,
                fetchedAt = fetchedAt,
            )
    }
}
