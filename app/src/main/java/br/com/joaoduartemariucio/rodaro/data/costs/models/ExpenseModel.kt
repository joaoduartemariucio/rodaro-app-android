package br.com.joaoduartemariucio.rodaro.data.costs.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import br.com.joaoduartemariucio.rodaro.data.vehicles.models.VehicleModel
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDiscount
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineSeverity
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(VehicleModel::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("vehicleId")],
)
data class ExpenseModel(
    @PrimaryKey val id: UUID,
    val vehicleId: UUID,
    val category: ExpenseCategory,
    val date: Instant,
    val amount: BigDecimal,
    val odometer: Double?,
    val note: String,
    val coverageStart: LocalDate?,
    val coverageEnd: LocalDate?,
    val fineInfractionDate: LocalDate?,
    val fineSeverity: FineSeverity?,
    val finePoints: Int?,
    val fineDueDate: LocalDate?,
    val fineDiscount: FineDiscount?,
    val fineIsPaid: Boolean?,
    val createdAt: Instant,
)
