package br.com.joaoduartemariucio.rodaro.data.costs.models

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import java.util.UUID

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE vehicleId = :vehicleId ORDER BY date, id")
    suspend fun expenses(vehicleId: UUID): List<ExpenseModel>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun byId(id: UUID): ExpenseModel?

    @Upsert
    suspend fun upsert(model: ExpenseModel)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: UUID): Int
}
