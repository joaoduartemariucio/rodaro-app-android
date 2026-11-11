package br.com.joaoduartemariucio.rodaro.calculations

import br.com.joaoduartemariucio.rodaro.domain.backup.entities.BackupSnapshot
import br.com.joaoduartemariucio.rodaro.domain.backup.entities.PortablePreferences
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceRule
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.CurrencyOption
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.LanguageOption
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.UnitSystem
import br.com.joaoduartemariucio.rodaro.domain.trips.entities.RoutePoint
import br.com.joaoduartemariucio.rodaro.domain.trips.entities.SavedRoute
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

object BackupFixtures {
    val golId: UUID = UUID.fromString("11111111-1111-4111-8111-111111111111")
    val cbId: UUID = UUID.fromString("22222222-2222-4222-8222-222222222222")

    val gol =
        Vehicle(
            id = golId,
            name = "Gol",
            kind = VehicleKind.CAR,
            plate = "ABC1D23",
            initialOdometer = 1_000.0,
            tankCapacity = 55.0,
            createdAt = Instant.parse("2025-01-10T12:30:00.123Z"),
        )

    val cb =
        Vehicle(
            id = cbId,
            name = "CB 500",
            kind = VehicleKind.MOTORCYCLE,
            createdAt = Instant.parse("2025-02-01T08:00:00Z"),
            isArchived = true,
        )

    val fuel =
        FuelEntry(
            id = UUID.fromString("33333333-3333-4333-8333-333333333333"),
            vehicleId = golId,
            date = Instant.parse("2025-03-01T10:00:00Z"),
            odometer = 1_500.0,
            liters = 40.5,
            pricePerLiter = BigDecimal("5.899"),
            isFullTank = true,
        )

    val service =
        ServiceEntry(
            id = UUID.fromString("44444444-4444-4444-8444-444444444444"),
            vehicleId = golId,
            kind = ServiceKind.OIL_CHANGE,
            date = Instant.parse("2025-03-05T10:00:00Z"),
            odometer = 1_600.0,
            cost = BigDecimal("120.00"),
            notes = "Synthetic 5W30",
        )

    val rule =
        ServiceRule(
            id = UUID.fromString("55555555-5555-4555-8555-555555555555"),
            vehicleId = golId,
            kind = ServiceKind.OIL_CHANGE,
            kilometerInterval = 10_000.0,
            monthInterval = 12,
        )

    val route =
        SavedRoute(
            id = UUID.fromString("66666666-6666-4666-8666-666666666666"),
            vehicleId = cbId,
            origin = "São Paulo",
            destination = "Campinas",
            distanceKm = 95.5,
            isRoundTrip = true,
            hasTolls = true,
            tollCost = BigDecimal("18.40"),
            kilometersPerLiter = 25.0,
            pricePerLiter = BigDecimal("5.899"),
            liters = 7.64,
            fuelCost = BigDecimal("45.07"),
            costPerKilometer = BigDecimal("0.33"),
            createdAt = Instant.parse("2025-04-01T09:15:00Z"),
            path = listOf(RoutePoint(-23.55052, -46.633308), RoutePoint(-22.905643, -47.06094)),
        )

    val preferences =
        PortablePreferences(
            pushEnabled = false,
            remindersEnabled = true,
            currency = CurrencyOption.BRL,
            unitSystem = UnitSystem.METRIC,
            language = LanguageOption.PORTUGUESE,
        )

    fun snapshot(
        preferences: PortablePreferences? = this.preferences,
        exportedAt: Instant? = Instant.parse("2025-05-01T18:00:00Z"),
    ): BackupSnapshot =
        BackupSnapshot(
            vehicles = listOf(gol, cb),
            fuelEntries = listOf(fuel),
            serviceEntries = listOf(service),
            serviceRules = listOf(rule),
            savedRoutes = listOf(route),
            preferences = preferences,
            exportedAt = exportedAt,
        )
}
