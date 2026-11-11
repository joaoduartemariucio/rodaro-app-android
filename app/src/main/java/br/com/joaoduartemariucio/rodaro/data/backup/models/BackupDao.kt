package br.com.joaoduartemariucio.rodaro.data.backup.models

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import br.com.joaoduartemariucio.rodaro.data.fueling.models.FuelEntryModel
import br.com.joaoduartemariucio.rodaro.data.maintenance.models.ServiceEntryModel
import br.com.joaoduartemariucio.rodaro.data.maintenance.models.ServiceRuleModel
import br.com.joaoduartemariucio.rodaro.data.trips.models.TripRouteModel
import br.com.joaoduartemariucio.rodaro.data.vehicles.models.VehicleModel

data class BackupTables(
    val vehicles: List<VehicleModel>,
    val fuelEntries: List<FuelEntryModel>,
    val serviceEntries: List<ServiceEntryModel>,
    val serviceRules: List<ServiceRuleModel>,
    val tripRoutes: List<TripRouteModel>,
)

@Dao
interface BackupDao {
    @Query("SELECT * FROM vehicles ORDER BY createdAt, id")
    suspend fun vehicles(): List<VehicleModel>

    @Query("SELECT * FROM fuel_entries ORDER BY vehicleId, date, odometer")
    suspend fun fuelEntries(): List<FuelEntryModel>

    @Query("SELECT * FROM service_entries ORDER BY vehicleId, date, odometer")
    suspend fun serviceEntries(): List<ServiceEntryModel>

    @Query("SELECT * FROM service_rules ORDER BY vehicleId, id")
    suspend fun serviceRules(): List<ServiceRuleModel>

    @Query("SELECT * FROM trip_routes ORDER BY createdAt")
    suspend fun tripRoutes(): List<TripRouteModel>

    @Transaction
    suspend fun tables(): BackupTables =
        BackupTables(
            vehicles = vehicles(),
            fuelEntries = fuelEntries(),
            serviceEntries = serviceEntries(),
            serviceRules = serviceRules(),
            tripRoutes = tripRoutes(),
        )

    @Query("DELETE FROM fuel_entries")
    suspend fun deleteFuelEntries()

    @Query("DELETE FROM service_entries")
    suspend fun deleteServiceEntries()

    @Query("DELETE FROM service_rules")
    suspend fun deleteServiceRules()

    @Query("DELETE FROM trip_routes")
    suspend fun deleteTripRoutes()

    @Query("DELETE FROM vehicles")
    suspend fun deleteVehicles()

    @Insert
    suspend fun insertVehicles(models: List<VehicleModel>)

    @Insert
    suspend fun insertFuelEntries(models: List<FuelEntryModel>)

    @Insert
    suspend fun insertServiceEntries(models: List<ServiceEntryModel>)

    @Insert
    suspend fun insertServiceRules(models: List<ServiceRuleModel>)

    @Insert
    suspend fun insertTripRoutes(models: List<TripRouteModel>)

    @Transaction
    suspend fun replaceAll(tables: BackupTables) {
        deleteFuelEntries()
        deleteServiceEntries()
        deleteServiceRules()
        deleteTripRoutes()
        deleteVehicles()
        insertVehicles(tables.vehicles)
        insertFuelEntries(tables.fuelEntries)
        insertServiceEntries(tables.serviceEntries)
        insertServiceRules(tables.serviceRules)
        insertTripRoutes(tables.tripRoutes)
    }
}
