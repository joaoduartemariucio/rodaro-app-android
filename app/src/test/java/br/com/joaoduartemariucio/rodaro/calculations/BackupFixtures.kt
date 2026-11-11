package br.com.joaoduartemariucio.rodaro.calculations

import br.com.joaoduartemariucio.rodaro.domain.backup.entities.BackupSnapshot
import br.com.joaoduartemariucio.rodaro.domain.backup.entities.PortablePreferences
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDetails
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDiscount
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineSeverity
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueItem
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueKind
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceRule
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.CurrencyOption
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.LanguageOption
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.UnitSystem
import br.com.joaoduartemariucio.rodaro.domain.trips.entities.RoutePoint
import br.com.joaoduartemariucio.rodaro.domain.trips.entities.SavedRoute
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeQuote
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeSelection
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.FuelCapability
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
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

    val insurance =
        Expense(
            id = UUID.fromString("77777777-7777-4777-8777-777777777777"),
            vehicleId = golId,
            category = ExpenseCategory.SEGURO,
            date = Instant.parse("2025-03-10T12:00:00Z"),
            amount = BigDecimal("2450.37"),
            odometer = 1_650.0,
            note = "Apólice anual",
            coverageStart = LocalDate.parse("2025-03-10"),
            coverageEnd = LocalDate.parse("2026-03-09"),
            createdAt = Instant.parse("2025-03-11T08:00:00Z"),
        )

    val fine =
        Expense(
            id = UUID.fromString("88888888-8888-4888-8888-888888888888"),
            vehicleId = golId,
            category = ExpenseCategory.MULTA,
            date = Instant.parse("2025-03-20T12:00:00Z"),
            amount = BigDecimal("156.18"),
            fine =
                FineDetails(
                    infractionDate = LocalDate.parse("2025-03-01"),
                    severity = FineSeverity.GRAVE,
                    points = 5,
                    dueDate = LocalDate.parse("2025-04-15"),
                    discount = FineDiscount.TWENTY,
                    isPaid = false,
                ),
        )

    val ipva =
        DueItem(
            id = UUID.fromString("99999999-9999-4999-8999-999999999999"),
            vehicleId = golId,
            kind = DueKind.IPVA,
            dueDate = LocalDate.parse("2025-03-15"),
            amount = BigDecimal("812.45"),
            paidAt = Instant.parse("2025-03-14T15:00:00Z"),
            installmentNumber = 1,
            installmentCount = 3,
            note = "Cota 1",
            createdAt = Instant.parse("2025-01-20T10:00:00Z"),
        )

    val license =
        DueItem(
            id = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
            vehicleId = null,
            kind = DueKind.CNH,
            dueDate = LocalDate.parse("2027-08-01"),
            createdAt = Instant.parse("2025-01-21T10:00:00Z"),
        )

    fun fullSnapshot(): BackupSnapshot =
        snapshot().copy(
            vehicles = listOf(gol.copy(fuelCapability = FuelCapability.FLEX, uf = "SP", modelYear = 2019), cb.copy(fuelCapability = FuelCapability.GASOLINA)),
            fuelEntries = listOf(fuel.copy(fuelType = FuelType.ETANOL, createdAt = Instant.parse("2025-03-02T09:00:00Z"))),
            serviceEntries = listOf(service.copy(workshop = "Oficina do Zé", createdAt = Instant.parse("2025-03-06T09:00:00Z"))),
            expenses = listOf(insurance, fine),
            dueItems = listOf(ipva, license),
        )

    val fipeSelection = FipeSelection("005340-6", "2014-3", "VW - VolksWagen", "Amarok CD 2.0 16V TDI 4x4 Diesel", "2014 Diesel")

    val fipeQuote =
        FipeQuote(
            id = FipeQuote.id(golId, "005340-6", "2014-3", YearMonth.of(2026, 11)),
            vehicleId = golId,
            fipeCode = "005340-6",
            yearCode = "2014-3",
            month = YearMonth.of(2026, 11),
            price = BigDecimal("85386.00"),
            fetchedAt = Instant.parse("2026-11-11T10:00:00Z"),
        )

    fun fipeSnapshot(): BackupSnapshot =
        fullSnapshot().let { snapshot ->
            snapshot.copy(
                vehicles =
                    snapshot.vehicles.map {
                        if (it.id == golId) {
                            it.copy(fipe = fipeSelection, purchaseDate = LocalDate.parse("2026-03-14"), purchasePrice = BigDecimal("92500.50"))
                        } else {
                            it
                        }
                    },
                fipeQuotes = listOf(fipeQuote),
            )
        }
}
