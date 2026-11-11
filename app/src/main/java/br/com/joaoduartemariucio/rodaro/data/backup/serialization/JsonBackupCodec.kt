package br.com.joaoduartemariucio.rodaro.data.backup.serialization

import br.com.joaoduartemariucio.rodaro.data.network.Polyline
import br.com.joaoduartemariucio.rodaro.domain.backup.entities.BackupSnapshot
import br.com.joaoduartemariucio.rodaro.domain.backup.entities.PortablePreferences
import br.com.joaoduartemariucio.rodaro.domain.backup.errors.BackupError
import br.com.joaoduartemariucio.rodaro.domain.backup.services.BackupCodec
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceRule
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.CurrencyOption
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.LanguageOption
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.UnitSystem
import br.com.joaoduartemariucio.rodaro.domain.trips.entities.SavedRoute
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class JsonBackupCodec(
    private val appVersion: String,
) : BackupCodec {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = true
        }

    override suspend fun encode(snapshot: BackupSnapshot): String =
        withContext(Dispatchers.Default) {
            json.encodeToString(BackupFileDto.serializer(), file(snapshot))
        }

    override suspend fun decode(text: String): BackupSnapshot =
        withContext(Dispatchers.Default) {
            if (text.length > MAX_CHARACTERS) throw BackupError.TooLarge
            try {
                val root = json.parseToJsonElement(text).jsonObject
                if (root["format"]?.jsonPrimitive?.contentOrNull != FORMAT) throw BackupError.Unreadable()
                val version = root["formatVersion"]?.jsonPrimitive?.intOrNull ?: throw BackupError.Unreadable()
                if (version > FORMAT_VERSION) throw BackupError.NewerFormat(version, FORMAT_VERSION)
                when (version) {
                    1 -> version1(root)
                    else -> throw BackupError.Unreadable()
                }
            } catch (error: IllegalArgumentException) {
                throw BackupError.Unreadable(error)
            } catch (error: DateTimeException) {
                throw BackupError.Unreadable(error)
            } catch (error: ArithmeticException) {
                throw BackupError.Unreadable(error)
            }
        }

    private fun version1(root: JsonObject): BackupSnapshot {
        val file = json.decodeFromJsonElement(BackupFileDto.serializer(), root)
        return BackupSnapshot(
            vehicles = file.vehicles.map(::vehicle),
            fuelEntries = file.fuelEntries.map(::fuelEntry),
            serviceEntries = file.serviceEntries.map(::serviceEntry),
            serviceRules = file.serviceRules.map(::serviceRule),
            savedRoutes = file.savedRoutes.map(::savedRoute),
            preferences = file.preferences?.let(::preferences),
            exportedAt = file.exportedAt?.let(Instant::parse),
        )
    }

    private fun file(snapshot: BackupSnapshot): BackupFileDto =
        BackupFileDto(
            format = FORMAT,
            formatVersion = FORMAT_VERSION,
            exportedAt = snapshot.exportedAt?.let(::instantText),
            app = AppInfoDto(platform = PLATFORM, version = appVersion),
            vehicles = snapshot.vehicles.map(::vehicleDto),
            fuelEntries = snapshot.fuelEntries.map(::fuelEntryDto),
            serviceEntries = snapshot.serviceEntries.map(::serviceEntryDto),
            serviceRules = snapshot.serviceRules.map(::serviceRuleDto),
            savedRoutes = snapshot.savedRoutes.map(::savedRouteDto),
            preferences = snapshot.preferences?.let(::preferencesDto),
        )

    private fun vehicleDto(vehicle: Vehicle) =
        VehicleDto(
            id = vehicle.id.toString(),
            name = vehicle.name,
            kind = vehicle.kind.name.lowercase(Locale.ROOT),
            plate = vehicle.plate,
            initialOdometerKm = vehicle.initialOdometer,
            tankCapacityLiters = vehicle.tankCapacity,
            createdAt = instantText(vehicle.createdAt),
            isArchived = vehicle.isArchived,
        )

    private fun vehicle(dto: VehicleDto) =
        Vehicle(
            id = UUID.fromString(dto.id),
            name = dto.name,
            kind = VehicleKind.entries.firstOrNull { it.name.lowercase(Locale.ROOT) == dto.kind } ?: VehicleKind.CAR,
            plate = dto.plate,
            initialOdometer = dto.initialOdometerKm,
            tankCapacity = dto.tankCapacityLiters,
            createdAt = Instant.parse(dto.createdAt),
            isArchived = dto.isArchived,
        )

    private fun fuelEntryDto(entry: FuelEntry) =
        FuelEntryDto(
            id = entry.id.toString(),
            vehicleId = entry.vehicleId.toString(),
            date = instantText(entry.date),
            odometerKm = entry.odometer,
            liters = entry.liters,
            pricePerLiter = entry.pricePerLiter.toPlainString(),
            isFullTank = entry.isFullTank,
        )

    private fun fuelEntry(dto: FuelEntryDto) =
        FuelEntry(
            id = UUID.fromString(dto.id),
            vehicleId = UUID.fromString(dto.vehicleId),
            date = Instant.parse(dto.date),
            odometer = dto.odometerKm,
            liters = dto.liters,
            pricePerLiter = BigDecimal(dto.pricePerLiter),
            isFullTank = dto.isFullTank,
        )

    private fun serviceEntryDto(entry: ServiceEntry) =
        ServiceEntryDto(
            id = entry.id.toString(),
            vehicleId = entry.vehicleId.toString(),
            kind = entry.kind.rawValue,
            date = instantText(entry.date),
            odometerKm = entry.odometer,
            cost = entry.cost.toPlainString(),
            notes = entry.notes,
        )

    private fun serviceEntry(dto: ServiceEntryDto) =
        ServiceEntry(
            id = UUID.fromString(dto.id),
            vehicleId = UUID.fromString(dto.vehicleId),
            kind = serviceKind(dto.kind),
            date = Instant.parse(dto.date),
            odometer = dto.odometerKm,
            cost = BigDecimal(dto.cost),
            notes = dto.notes,
        )

    private fun serviceRuleDto(rule: ServiceRule) =
        ServiceRuleDto(
            id = rule.id.toString(),
            vehicleId = rule.vehicleId.toString(),
            kind = rule.kind.rawValue,
            kilometerInterval = rule.kilometerInterval,
            monthInterval = rule.monthInterval,
        )

    private fun serviceRule(dto: ServiceRuleDto) =
        ServiceRule(
            id = UUID.fromString(dto.id),
            vehicleId = UUID.fromString(dto.vehicleId),
            kind = serviceKind(dto.kind),
            kilometerInterval = dto.kilometerInterval,
            monthInterval = dto.monthInterval,
        )

    private fun savedRouteDto(route: SavedRoute) =
        SavedRouteDto(
            id = route.id.toString(),
            vehicleId = route.vehicleId.toString(),
            origin = route.origin,
            destination = route.destination,
            distanceKm = route.distanceKm,
            isRoundTrip = route.isRoundTrip,
            hasTolls = route.hasTolls,
            tollCost = route.tollCost.toPlainString(),
            kilometersPerLiter = route.kilometersPerLiter,
            pricePerLiter = route.pricePerLiter.toPlainString(),
            liters = route.liters,
            fuelCost = route.fuelCost.toPlainString(),
            costPerKilometer = route.costPerKilometer.toPlainString(),
            createdAt = instantText(route.createdAt),
            pathPolyline6 = Polyline.encode(route.path, Polyline.PRECISION_6),
        )

    private fun savedRoute(dto: SavedRouteDto) =
        SavedRoute(
            id = UUID.fromString(dto.id),
            vehicleId = UUID.fromString(dto.vehicleId),
            origin = dto.origin,
            destination = dto.destination,
            distanceKm = dto.distanceKm,
            isRoundTrip = dto.isRoundTrip,
            hasTolls = dto.hasTolls,
            tollCost = BigDecimal(dto.tollCost),
            kilometersPerLiter = dto.kilometersPerLiter,
            pricePerLiter = BigDecimal(dto.pricePerLiter),
            liters = dto.liters,
            fuelCost = BigDecimal(dto.fuelCost),
            costPerKilometer = BigDecimal(dto.costPerKilometer),
            createdAt = Instant.parse(dto.createdAt),
            path = Polyline.decode(dto.pathPolyline6, Polyline.PRECISION_6),
        )

    private fun preferencesDto(preferences: PortablePreferences) =
        PreferencesDto(
            pushEnabled = preferences.pushEnabled,
            remindersEnabled = preferences.remindersEnabled,
            currency = preferences.currency?.code,
            unitSystem = preferences.unitSystem.name.lowercase(Locale.ROOT),
            language = if (preferences.language == LanguageOption.SYSTEM) SYSTEM_LANGUAGE else preferences.language.tag,
        )

    private fun preferences(dto: PreferencesDto) =
        PortablePreferences(
            pushEnabled = dto.pushEnabled,
            remindersEnabled = dto.remindersEnabled,
            currency = CurrencyOption.entries.firstOrNull { it.code == dto.currency },
            unitSystem = UnitSystem.entries.firstOrNull { it.name.lowercase(Locale.ROOT) == dto.unitSystem } ?: UnitSystem.METRIC,
            language = language(dto.language),
        )

    private fun language(tag: String): LanguageOption =
        if (tag == SYSTEM_LANGUAGE) {
            LanguageOption.SYSTEM
        } else {
            LanguageOption.entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: LanguageOption.SYSTEM
        }

    private fun serviceKind(raw: String): ServiceKind = ServiceKind.entries.firstOrNull { it.rawValue == raw } ?: ServiceKind.OTHER

    private fun instantText(instant: Instant): String = INSTANT_FORMAT.format(instant)

    companion object {
        const val FORMAT = "rodaro.backup"
        const val FORMAT_VERSION = 1
        const val MAX_CHARACTERS = 32 * 1024 * 1024
        private const val PLATFORM = "android"
        private const val SYSTEM_LANGUAGE = "system"
        private val INSTANT_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC)
    }
}
