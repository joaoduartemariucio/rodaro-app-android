package br.com.joaoduartemariucio.rodaro.domain.valuation.repositories

import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeQuote
import java.util.UUID

interface FipeQuoteRepository {
    suspend fun quotes(vehicleId: UUID): List<FipeQuote>

    suspend fun save(quotes: List<FipeQuote>)
}
