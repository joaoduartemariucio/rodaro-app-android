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
data class PreferencesDto(
    val pushEnabled: Boolean = true,
    val remindersEnabled: Boolean = true,
    val currency: String? = null,
    val unitSystem: String = "metric",
    val language: String = "system",
)
