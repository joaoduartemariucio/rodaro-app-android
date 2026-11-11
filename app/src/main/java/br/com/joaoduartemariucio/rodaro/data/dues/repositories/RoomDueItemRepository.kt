package br.com.joaoduartemariucio.rodaro.data.dues.repositories

import br.com.joaoduartemariucio.rodaro.data.common.repositories.persisting
import br.com.joaoduartemariucio.rodaro.data.dues.mappers.DueItemMapper
import br.com.joaoduartemariucio.rodaro.data.dues.models.DueItemDao
import br.com.joaoduartemariucio.rodaro.data.vehicles.models.VehicleDao
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RepositoryError
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueItem
import br.com.joaoduartemariucio.rodaro.domain.dues.repositories.DueItemRepository
import java.util.UUID

class RoomDueItemRepository(
    private val dao: DueItemDao,
    private val vehicles: VehicleDao,
) : DueItemRepository {
    override suspend fun items(vehicleId: UUID): List<DueItem> = persisting { dao.byVehicle(vehicleId).map(DueItemMapper::entity) }

    override suspend fun driverItems(): List<DueItem> = persisting { dao.driverItems().map(DueItemMapper::entity) }

    override suspend fun all(): List<DueItem> = persisting { dao.all().map(DueItemMapper::entity) }

    override suspend fun save(item: DueItem) =
        persisting {
            val vehicleId = item.vehicleId
            if (vehicleId != null && vehicles.byId(vehicleId) == null) throw RepositoryError.VehicleNotFound(vehicleId)
            dao.upsert(DueItemMapper.model(item))
        }

    override suspend fun delete(id: UUID) =
        persisting {
            if (dao.delete(id) == 0) throw RepositoryError.EntryNotFound(id)
        }
}
