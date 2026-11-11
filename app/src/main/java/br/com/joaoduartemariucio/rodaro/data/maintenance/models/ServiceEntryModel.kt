package br.com.joaoduartemariucio.rodaro.data.maintenance.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import br.com.joaoduartemariucio.rodaro.data.vehicles.models.VehicleModel
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "service_entries",
    foreignKeys = [
        ForeignKey(VehicleModel::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("vehicleId")],
)
data class ServiceEntryModel(
    @PrimaryKey val id: UUID,
    val vehicleId: UUID,
    val kind: ServiceKind,
    val date: Instant,
    val odometer: Double,
    val cost: BigDecimal,
    val notes: String,
    val workshop: String? = null,
    @ColumnInfo(defaultValue = "0") val createdAt: Instant = date,
)
