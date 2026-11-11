package br.com.joaoduartemariucio.rodaro.data.database

import androidx.room.TypeConverter
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDiscount
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineSeverity
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueKind
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.FuelCapability
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class Converters {
    @TypeConverter
    fun uuid(value: String): UUID = UUID.fromString(value)

    @TypeConverter
    fun uuidText(value: UUID): String = value.toString()

    @TypeConverter
    fun instant(value: Long): Instant = Instant.ofEpochMilli(value)

    @TypeConverter
    fun instantMillis(value: Instant): Long = value.toEpochMilli()

    @TypeConverter
    fun decimal(value: String): BigDecimal = BigDecimal(value)

    @TypeConverter
    fun decimalText(value: BigDecimal): String = value.toPlainString()

    @TypeConverter
    fun serviceKind(value: String): ServiceKind = ServiceKind.entries.firstOrNull { it.rawValue == value } ?: ServiceKind.OTHER

    @TypeConverter
    fun serviceKindText(value: ServiceKind): String = value.rawValue

    @TypeConverter
    fun vehicleKind(value: String): VehicleKind = VehicleKind.entries.firstOrNull { it.name.lowercase() == value } ?: VehicleKind.CAR

    @TypeConverter
    fun vehicleKindText(value: VehicleKind): String = value.name.lowercase()

    @TypeConverter
    fun localDate(value: String): LocalDate = LocalDate.parse(value)

    @TypeConverter
    fun localDateText(value: LocalDate): String = value.toString()

    @TypeConverter
    fun fuelType(value: String?): FuelType? = FuelType.of(value)

    @TypeConverter
    fun fuelTypeText(value: FuelType?): String? = value?.rawValue

    @TypeConverter
    fun fuelCapability(value: String): FuelCapability = FuelCapability.of(value)

    @TypeConverter
    fun fuelCapabilityText(value: FuelCapability): String = value.rawValue

    @TypeConverter
    fun expenseCategory(value: String): ExpenseCategory = ExpenseCategory.of(value)

    @TypeConverter
    fun expenseCategoryText(value: ExpenseCategory): String = value.rawValue

    @TypeConverter
    fun fineSeverity(value: String?): FineSeverity? = FineSeverity.of(value)

    @TypeConverter
    fun fineSeverityText(value: FineSeverity?): String? = value?.rawValue

    @TypeConverter
    fun fineDiscount(value: String): FineDiscount = FineDiscount.of(value)

    @TypeConverter
    fun fineDiscountText(value: FineDiscount): String = value.rawValue

    @TypeConverter
    fun dueKind(value: String): DueKind = DueKind.entries.first { it.rawValue == value }

    @TypeConverter
    fun dueKindText(value: DueKind): String = value.rawValue

    @TypeConverter
    fun yearMonth(value: String): YearMonth = YearMonth.parse(value)

    @TypeConverter
    fun yearMonthText(value: YearMonth): String = value.toString()
}
