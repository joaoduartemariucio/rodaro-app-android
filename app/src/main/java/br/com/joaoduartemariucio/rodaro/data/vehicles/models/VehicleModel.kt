package br.com.joaoduartemariucio.rodaro.data.vehicles.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.FuelCapability
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import java.time.Instant
import java.util.UUID

@Entity(tableName = "vehicles")
data class VehicleModel(
    @PrimaryKey val id: UUID,
    val name: String,
    val kind: VehicleKind,
    val plate: String?,
    val initialOdometer: Double,
    val tankCapacity: Double?,
    val createdAt: Instant,
    val isArchived: Boolean,
    @ColumnInfo(defaultValue = "flex") val fuelCapability: FuelCapability = FuelCapability.FLEX,
    val uf: String? = null,
    val modelYear: Int? = null,
)
