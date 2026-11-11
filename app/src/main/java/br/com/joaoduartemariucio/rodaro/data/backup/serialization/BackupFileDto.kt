package br.com.joaoduartemariucio.rodaro.data.backup.serialization

import kotlinx.serialization.Serializable

@Serializable
data class BackupFileDto(
    val format: String,
    val formatVersion: Int,
    val exportedAt: String? = null,
    val app: AppInfoDto? = null,
    val vehicles: List<VehicleDto> = emptyList(),
    val fuelEntries: List<FuelEntryDto> = emptyList(),
    val serviceEntries: List<ServiceEntryDto> = emptyList(),
    val serviceRules: List<ServiceRuleDto> = emptyList(),
    val savedRoutes: List<SavedRouteDto> = emptyList(),
    val expenses: List<ExpenseDto> = emptyList(),
    val dueItems: List<DueItemDto> = emptyList(),
    val fipeQuotes: List<FipeQuoteDto> = emptyList(),
    val preferences: PreferencesDto? = null,
)

@Serializable
data class AppInfoDto(
    val platform: String,
    val version: String,
)

@Serializable
data class VehicleDto(
    val id: String,
    val name: String,
    val kind: String,
    val plate: String? = null,
    val initialOdometerKm: Double = 0.0,
    val tankCapacityLiters: Double? = null,
    val createdAt: String,
    val isArchived: Boolean = false,
    val fuelCapability: String = "flex",
    val uf: String? = null,
    val modelYear: Int? = null,
    val fipe: FipeSelectionDto? = null,
    val purchaseDate: String? = null,
    val purchasePrice: String? = null,
)

@Serializable
data class FipeSelectionDto(
    val code: String,
    val yearCode: String,
    val brand: String,
    val model: String,
    val year: String,
)

@Serializable
data class FipeQuoteDto(
    val id: String,
    val vehicleId: String,
    val fipeCode: String,
    val yearCode: String,
    val month: String,
    val price: String,
    val fetchedAt: String,
)

@Serializable
data class FuelEntryDto(
    val id: String,
    val vehicleId: String,
    val date: String,
    val odometerKm: Double,
    val liters: Double,
    val pricePerLiter: String,
    val isFullTank: Boolean,
    val fuelType: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class ServiceEntryDto(
    val id: String,
    val vehicleId: String,
    val kind: String,
    val date: String,
    val odometerKm: Double,
    val cost: String,
    val notes: String = "",
    val workshop: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class ServiceRuleDto(
    val id: String,
    val vehicleId: String,
    val kind: String,
    val kilometerInterval: Double? = null,
    val monthInterval: Int? = null,
)

@Serializable
data class SavedRouteDto(
    val id: String,
    val vehicleId: String,
    val origin: String,
    val destination: String,
    val distanceKm: Double,
    val isRoundTrip: Boolean,
    val hasTolls: Boolean,
    val tollCost: String,
    val kilometersPerLiter: Double,
    val pricePerLiter: String,
    val liters: Double,
    val fuelCost: String,
    val costPerKilometer: String,
    val createdAt: String,
    val pathPolyline6: String = "",
)

@Serializable
data class ExpenseDto(
    val id: String,
    val vehicleId: String,
    val category: String,
    val date: String,
    val amount: String,
    val odometerKm: Double? = null,
    val note: String = "",
    val coverageStart: String? = null,
    val coverageEnd: String? = null,
    val fine: FineDto? = null,
    val createdAt: String? = null,
)

@Serializable
data class FineDto(
    val infractionDate: String? = null,
    val severity: String? = null,
    val points: Int? = null,
    val dueDate: String? = null,
    val discount: String = "none",
    val isPaid: Boolean = true,
)

@Serializable
data class DueItemDto(
    val id: String,
    val vehicleId: String? = null,
    val kind: String,
    val dueDate: String,
    val amount: String? = null,
    val paidAt: String? = null,
    val installmentNumber: Int? = null,
    val installmentCount: Int? = null,
    val note: String = "",
    val createdAt: String,
)

@Serializable
data class PreferencesDto(
    val pushEnabled: Boolean = true,
    val remindersEnabled: Boolean = true,
    val currency: String? = null,
    val unitSystem: String = "metric",
    val language: String = "system",
)
