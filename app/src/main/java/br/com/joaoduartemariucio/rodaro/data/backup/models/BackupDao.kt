package br.com.joaoduartemariucio.rodaro.data.backup.models

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import br.com.joaoduartemariucio.rodaro.data.costs.models.ExpenseModel
import br.com.joaoduartemariucio.rodaro.data.dues.models.DueItemModel
import br.com.joaoduartemariucio.rodaro.data.fueling.models.FuelEntryModel
import br.com.joaoduartemariucio.rodaro.data.maintenance.models.ServiceEntryModel
import br.com.joaoduartemariucio.rodaro.data.maintenance.models.ServiceRuleModel
import br.com.joaoduartemariucio.rodaro.data.trips.models.TripRouteModel
import br.com.joaoduartemariucio.rodaro.data.valuation.models.FipeQuoteModel
import br.com.joaoduartemariucio.rodaro.data.vehicles.models.VehicleModel

data class BackupTables(
    val vehicles: List<VehicleModel>,
    val fuelEntries: List<FuelEntryModel>,
    val serviceEntries: List<ServiceEntryModel>,
    val serviceRules: List<ServiceRuleModel>,
    val tripRoutes: List<TripRouteModel>,
    val expenses: List<ExpenseModel> = emptyList(),
    val dueItems: List<DueItemModel> = emptyList(),
    val fipeQuotes: List<FipeQuoteModel> = emptyList(),
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

    @Query("SELECT * FROM expenses ORDER BY vehicleId, date, id")
    suspend fun expenses(): List<ExpenseModel>

    @Query("SELECT * FROM due_items ORDER BY dueDate, id")
    suspend fun dueItems(): List<DueItemModel>

    @Query("SELECT * FROM fipe_quotes ORDER BY vehicleId, referenceMonth, id")
    suspend fun fipeQuotes(): List<FipeQuoteModel>

    @Transaction
    suspend fun tables(): BackupTables =
        BackupTables(
            vehicles = vehicles(),
            fuelEntries = fuelEntries(),
            serviceEntries = serviceEntries(),
            serviceRules = serviceRules(),
            tripRoutes = tripRoutes(),
            expenses = expenses(),
            dueItems = dueItems(),
            fipeQuotes = fipeQuotes(),
        )

    @Query("DELETE FROM fuel_entries")
    suspend fun deleteFuelEntries()

    @Query("DELETE FROM service_entries")
    suspend fun deleteServiceEntries()

    @Query("DELETE FROM service_rules")
    suspend fun deleteServiceRules()

    @Query("DELETE FROM trip_routes")
    suspend fun deleteTripRoutes()

    @Query("DELETE FROM expenses")
    suspend fun deleteExpenses()

    @Query("DELETE FROM due_items")
    suspend fun deleteDueItems()

    @Query("DELETE FROM fipe_quotes")
    suspend fun deleteFipeQuotes()

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

    @Insert
    suspend fun insertExpenses(models: List<ExpenseModel>)

    @Insert
    suspend fun insertDueItems(models: List<DueItemModel>)

    @Insert
    suspend fun insertFipeQuotes(models: List<FipeQuoteModel>)

    @Transaction
    suspend fun replaceAll(tables: BackupTables) {
        deleteFuelEntries()
        deleteServiceEntries()
        deleteServiceRules()
        deleteTripRoutes()
        deleteDueItems()
        deleteExpenses()
        deleteFipeQuotes()
        deleteVehicles()
        insertVehicles(tables.vehicles)
        insertFuelEntries(tables.fuelEntries)
        insertServiceEntries(tables.serviceEntries)
        insertServiceRules(tables.serviceRules)
        insertTripRoutes(tables.tripRoutes)
        insertExpenses(tables.expenses)
        insertDueItems(tables.dueItems)
        insertFipeQuotes(tables.fipeQuotes)
    }
}
