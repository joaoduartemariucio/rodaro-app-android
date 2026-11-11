package br.com.joaoduartemariucio.rodaro.data.dues.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import br.com.joaoduartemariucio.rodaro.data.vehicles.models.VehicleModel
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueKind
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity(
    tableName = "due_items",
    foreignKeys = [
        ForeignKey(VehicleModel::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("vehicleId")],
)
data class DueItemModel(
    @PrimaryKey val id: UUID,
    val vehicleId: UUID?,
    val kind: DueKind,
    val dueDate: LocalDate,
    val amount: BigDecimal?,
    val paidAt: Instant?,
    val installmentNumber: Int?,
    val installmentCount: Int?,
    val note: String,
    val createdAt: Instant,
)
