package br.com.joaoduartemariucio.rodaro.data.valuation.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import br.com.joaoduartemariucio.rodaro.data.vehicles.models.VehicleModel
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

@Entity(
    tableName = "fipe_quotes",
    foreignKeys = [
        ForeignKey(VehicleModel::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("vehicleId")],
)
data class FipeQuoteModel(
    @PrimaryKey val id: UUID,
    val vehicleId: UUID,
    val fipeCode: String,
    val yearCode: String,
    val referenceMonth: YearMonth,
    val price: BigDecimal,
    val fetchedAt: Instant,
)
