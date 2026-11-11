package br.com.joaoduartemariucio.rodaro.data.valuation.models

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import java.util.UUID

@Dao
interface FipeQuoteDao {
    @Query("SELECT * FROM fipe_quotes WHERE vehicleId = :vehicleId ORDER BY referenceMonth, id")
    suspend fun byVehicle(vehicleId: UUID): List<FipeQuoteModel>

    @Upsert
    suspend fun upsert(models: List<FipeQuoteModel>)
}
