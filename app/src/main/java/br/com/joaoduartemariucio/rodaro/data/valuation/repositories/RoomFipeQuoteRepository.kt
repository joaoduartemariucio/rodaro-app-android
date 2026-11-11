package br.com.joaoduartemariucio.rodaro.data.valuation.repositories

import br.com.joaoduartemariucio.rodaro.data.common.repositories.persisting
import br.com.joaoduartemariucio.rodaro.data.valuation.mappers.FipeQuoteMapper
import br.com.joaoduartemariucio.rodaro.data.valuation.models.FipeQuoteDao
import br.com.joaoduartemariucio.rodaro.data.vehicles.models.VehicleDao
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RepositoryError
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeQuote
import br.com.joaoduartemariucio.rodaro.domain.valuation.repositories.FipeQuoteRepository
import java.util.UUID

class RoomFipeQuoteRepository(
    private val dao: FipeQuoteDao,
    private val vehicles: VehicleDao,
) : FipeQuoteRepository {
    override suspend fun quotes(vehicleId: UUID): List<FipeQuote> = persisting { dao.byVehicle(vehicleId).map(FipeQuoteMapper::entity) }

    override suspend fun save(quotes: List<FipeQuote>) =
        persisting {
            quotes.map { it.vehicleId }.toSet().forEach { id ->
                if (vehicles.byId(id) == null) throw RepositoryError.VehicleNotFound(id)
            }
            dao.upsert(quotes.map(FipeQuoteMapper::model))
        }
}
