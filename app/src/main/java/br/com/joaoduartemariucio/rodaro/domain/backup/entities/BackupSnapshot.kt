package br.com.joaoduartemariucio.rodaro.domain.backup.entities

import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceRule
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.CurrencyOption
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.LanguageOption
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.Preferences
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.UnitSystem
import br.com.joaoduartemariucio.rodaro.domain.trips.entities.SavedRoute
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import java.time.Instant

data class BackupSnapshot(
    val vehicles: List<Vehicle> = emptyList(),
    val fuelEntries: List<FuelEntry> = emptyList(),
    val serviceEntries: List<ServiceEntry> = emptyList(),
    val serviceRules: List<ServiceRule> = emptyList(),
    val savedRoutes: List<SavedRoute> = emptyList(),
    val preferences: PortablePreferences? = null,
    val exportedAt: Instant? = null,
) {
    val contents: BackupContents
        get() =
            BackupContents(
                vehicles = vehicles.size,
                fuelEntries = fuelEntries.size,
                serviceEntries = serviceEntries.size,
                serviceRules = serviceRules.size,
                savedRoutes = savedRoutes.size,
            )
}

data class BackupContents(
    val vehicles: Int,
    val fuelEntries: Int,
    val serviceEntries: Int,
    val serviceRules: Int,
    val savedRoutes: Int,
)

data class PortablePreferences(
    val pushEnabled: Boolean = true,
    val remindersEnabled: Boolean = true,
    val currency: CurrencyOption? = null,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val language: LanguageOption = LanguageOption.SYSTEM,
) {
    fun applied(to: Preferences): Preferences =
        to.copy(
            pushEnabled = pushEnabled,
            remindersEnabled = remindersEnabled,
            currency = currency,
            unitSystem = unitSystem,
            language = language,
        )

    companion object {
        fun from(preferences: Preferences): PortablePreferences =
            PortablePreferences(
                pushEnabled = preferences.pushEnabled,
                remindersEnabled = preferences.remindersEnabled,
                currency = preferences.currency,
                unitSystem = preferences.unitSystem,
                language = preferences.language,
            )
    }
}
