package br.com.joaoduartemariucio.rodaro.data

import br.com.joaoduartemariucio.rodaro.data.costs.mappers.ExpenseMapper
import br.com.joaoduartemariucio.rodaro.data.database.RodaroDatabase
import br.com.joaoduartemariucio.rodaro.data.dues.mappers.DueItemMapper
import br.com.joaoduartemariucio.rodaro.data.fueling.mappers.FuelEntryMapper
import br.com.joaoduartemariucio.rodaro.data.maintenance.mappers.ServiceMapper
import br.com.joaoduartemariucio.rodaro.data.vehicles.mappers.VehicleMapper
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueItem
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueKind
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceRule
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

object PreviewData {
    private val referenceNow: Instant = Instant.now()

    val car =
        Vehicle(
            id = UUID.fromString("00000000-0000-0000-0000-0000000000C1"),
            name = "Gol 1.6",
            kind = VehicleKind.CAR,
            plate = "ABC1D23",
            initialOdometer = 38_000.0,
            tankCapacity = 55.0,
            createdAt = date(monthsAgo = 5),
            uf = "SP",
            modelYear = 2019,
        )

    val motorcycle =
        Vehicle(
            id = UUID.fromString("00000000-0000-0000-0000-0000000000B1"),
            name = "CB 500",
            kind = VehicleKind.MOTORCYCLE,
            plate = "XYZ9K87",
            initialOdometer = 12_000.0,
            tankCapacity = 17.0,
            createdAt = date(monthsAgo = 4),
        )

    suspend fun seed(database: RodaroDatabase) {
        if (database.vehicles().byId(car.id) != null) return
        seedCar(database)
        seedMotorcycle(database)
    }

    private fun date(
        monthsAgo: Long,
        plusDays: Long = 0,
    ): Instant =
        referenceNow
            .atZone(ZoneId.systemDefault())
            .minusMonths(monthsAgo)
            .plusDays(plusDays)
            .toInstant()

    private suspend fun fill(
        database: RodaroDatabase,
        vehicle: Vehicle,
        monthsAgo: Long,
        days: Long,
        odometer: Double,
        liters: Double,
        price: String,
        full: Boolean,
        fuelType: FuelType = FuelType.GASOLINA_COMUM,
    ) = database.fuelEntries().upsert(
        FuelEntryMapper.model(
            FuelEntry(
                vehicleId = vehicle.id,
                date = date(monthsAgo, days),
                odometer = odometer,
                liters = liters,
                pricePerLiter = BigDecimal(price),
                isFullTank = full,
                fuelType = fuelType,
            ),
        ),
    )

    private suspend fun seedCar(database: RodaroDatabase) {
        database.vehicles().upsert(VehicleMapper.model(car))

        var odometer = car.initialOdometer
        for (month in 4L downTo 0L) {
            odometer += 620
            val type = if (month >= 2) FuelType.GASOLINA_COMUM else FuelType.ETANOL
            val price = if (type == FuelType.ETANOL) "3.99" else "5.89"
            fill(database, car, month, -27, odometer, 42.0, price, full = true, fuelType = type)
            odometer += 180
            fill(database, car, month, -13, odometer, 15.0, price, full = false, fuelType = type)
        }

        database.services().upsert(
            ServiceMapper.model(
                ServiceEntry(
                    vehicleId = car.id,
                    kind = ServiceKind.OIL_CHANGE,
                    date = date(monthsAgo = 3),
                    odometer = car.initialOdometer + 1_500,
                    cost = BigDecimal("320.00"),
                    notes = "Óleo sintético 5W30",
                    workshop = "Auto Center",
                ),
            ),
        )
        database.expenses().upsert(
            ExpenseMapper.model(
                Expense(vehicleId = car.id, category = ExpenseCategory.LAVAGEM, date = date(monthsAgo = 1), amount = BigDecimal("60.00")),
            ),
        )
        database.dueItems().upsert(
            DueItemMapper.model(
                DueItem(
                    vehicleId = car.id,
                    kind = DueKind.IPVA,
                    dueDate = date(monthsAgo = -1).atZone(ZoneId.systemDefault()).toLocalDate(),
                    amount = BigDecimal("1250.00"),
                    createdAt = referenceNow,
                ),
            ),
        )
        database.services().upsertRules(
            listOf(
                ServiceMapper.model(
                    ServiceRule(vehicleId = car.id, kind = ServiceKind.OIL_CHANGE, kilometerInterval = 10_000.0, monthInterval = 12),
                ),
            ),
        )
    }

    private suspend fun seedMotorcycle(database: RodaroDatabase) {
        database.vehicles().upsert(VehicleMapper.model(motorcycle))

        var odometer = motorcycle.initialOdometer
        for (month in 3L downTo 0L) {
            odometer += 340
            fill(database, motorcycle, month, -22, odometer, 13.0, "5.79", full = true)
        }

        database.services().upsertRules(
            listOf(ServiceMapper.model(ServiceRule(vehicleId = motorcycle.id, kind = ServiceKind.CHAIN, kilometerInterval = 1_000.0))),
        )
    }
}
