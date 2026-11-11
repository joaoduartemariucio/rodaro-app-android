package br.com.joaoduartemariucio.rodaro.data.dues.models

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import java.util.UUID

@Dao
interface DueItemDao {
    @Query("SELECT * FROM due_items WHERE vehicleId = :vehicleId ORDER BY dueDate, id")
    suspend fun byVehicle(vehicleId: UUID): List<DueItemModel>

    @Query("SELECT * FROM due_items WHERE vehicleId IS NULL ORDER BY dueDate, id")
    suspend fun driverItems(): List<DueItemModel>

    @Query("SELECT * FROM due_items ORDER BY dueDate, id")
    suspend fun all(): List<DueItemModel>

    @Upsert
    suspend fun upsert(model: DueItemModel)

    @Query("DELETE FROM due_items WHERE id = :id")
    suspend fun delete(id: UUID): Int
}
