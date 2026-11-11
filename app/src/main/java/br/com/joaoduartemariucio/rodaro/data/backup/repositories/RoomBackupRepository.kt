package br.com.joaoduartemariucio.rodaro.data.backup.repositories

import br.com.joaoduartemariucio.rodaro.data.backup.models.BackupDao
import br.com.joaoduartemariucio.rodaro.data.backup.models.BackupTables
import br.com.joaoduartemariucio.rodaro.data.common.repositories.persisting
import br.com.joaoduartemariucio.rodaro.data.fueling.mappers.FuelEntryMapper
import br.com.joaoduartemariucio.rodaro.data.maintenance.mappers.ServiceMapper
import br.com.joaoduartemariucio.rodaro.data.trips.mappers.SavedRouteMapper
import br.com.joaoduartemariucio.rodaro.data.vehicles.mappers.VehicleMapper
import br.com.joaoduartemariucio.rodaro.domain.backup.entities.BackupSnapshot
import br.com.joaoduartemariucio.rodaro.domain.backup.repositories.BackupRepository

class RoomBackupRepository(
    private val dao: BackupDao,
) : BackupRepository {
    override suspend fun snapshot(): BackupSnapshot =
        persisting {
            val tables = dao.tables()
            BackupSnapshot(
                vehicles = tables.vehicles.map(VehicleMapper::entity),
                fuelEntries = tables.fuelEntries.map(FuelEntryMapper::entity),
                serviceEntries = tables.serviceEntries.map(ServiceMapper::entity),
                serviceRules = tables.serviceRules.map(ServiceMapper::entity),
                savedRoutes = tables.tripRoutes.map(SavedRouteMapper::entity),
            )
        }

    override suspend fun replaceAll(snapshot: BackupSnapshot) =
        persisting {
            dao.replaceAll(
                BackupTables(
                    vehicles = snapshot.vehicles.map(VehicleMapper::model),
                    fuelEntries = snapshot.fuelEntries.map(FuelEntryMapper::model),
                    serviceEntries = snapshot.serviceEntries.map(ServiceMapper::model),
                    serviceRules = snapshot.serviceRules.map(ServiceMapper::model),
                    tripRoutes = snapshot.savedRoutes.map(SavedRouteMapper::model),
                ),
            )
        }
}
