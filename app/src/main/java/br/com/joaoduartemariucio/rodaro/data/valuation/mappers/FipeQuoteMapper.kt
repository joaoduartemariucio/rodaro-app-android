package br.com.joaoduartemariucio.rodaro.data.valuation.mappers

import br.com.joaoduartemariucio.rodaro.data.valuation.models.FipeQuoteModel
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeQuote

object FipeQuoteMapper {
    fun entity(model: FipeQuoteModel): FipeQuote =
        FipeQuote(
            id = model.id,
            vehicleId = model.vehicleId,
            fipeCode = model.fipeCode,
            yearCode = model.yearCode,
            month = model.referenceMonth,
            price = model.price,
            fetchedAt = model.fetchedAt,
        )

    fun model(entity: FipeQuote): FipeQuoteModel =
        FipeQuoteModel(
            id = entity.id,
            vehicleId = entity.vehicleId,
            fipeCode = entity.fipeCode,
            yearCode = entity.yearCode,
            referenceMonth = entity.month,
            price = entity.price,
            fetchedAt = entity.fetchedAt,
        )
}
