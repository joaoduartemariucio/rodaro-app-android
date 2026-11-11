package br.com.joaoduartemariucio.rodaro.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.joaoduartemariucio.rodaro.calculations.assertDecimal
import br.com.joaoduartemariucio.rodaro.calculations.day
import br.com.joaoduartemariucio.rodaro.data.common.repositories.persisting
import br.com.joaoduartemariucio.rodaro.data.costs.repositories.RoomExpenseRepository
import br.com.joaoduartemariucio.rodaro.data.database.RodaroDatabase
import br.com.joaoduartemariucio.rodaro.data.dues.repositories.RoomDueItemRepository
import br.com.joaoduartemariucio.rodaro.data.fueling.repositories.RoomFuelEntryRepository
import br.com.joaoduartemariucio.rodaro.data.maintenance.repositories.RoomServiceRepository
import br.com.joaoduartemariucio.rodaro.data.trips.repositories.RoomRouteRepository
import br.com.joaoduartemariucio.rodaro.data.vehicles.repositories.RoomVehicleRepository
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RepositoryError
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
import br.com.joaoduartemariucio.rodaro.domain.trips.entities.RoutePoint
import br.com.joaoduartemariucio.rodaro.domain.trips.entities.SavedRoute
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.FuelCapability
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RoomRepositoriesTests {
    private val database = RodaroDatabase.create(ApplicationProvider.getApplicationContext<Context>(), inMemory = true)
    private val vehicles = RoomVehicleRepository(database.vehicles())
    private val fuel = RoomFuelEntryRepository(database.fuelEntries(), database.vehicles())
    private val services = RoomServiceRepository(database.services(), database.vehicles())
    private val routes = RoomRouteRepository(database.tripRoutes())
    private val expenses = RoomExpenseRepository(database.expenses(), database.vehicles())
    private val dues = RoomDueItemRepository(database.dueItems(), database.vehicles())

    private val gol =
        Vehicle(name = "Gol", kind = VehicleKind.CAR, plate = "ABC1D23", initialOdometer = 1_000.0, tankCapacity = 55.0, createdAt = day(0))

    @After
    fun close() = database.close()

    private fun fuelEntry(
        dayOffset: Int,
        odometer: Double,
        vehicleId: UUID = gol.id,
    ) = FuelEntry(
        vehicleId = vehicleId,
        date = day(dayOffset),
        odometer = odometer,
        liters = 40.5,
        pricePerLiter = BigDecimal("5.899"),
        isFullTank = true,
    )

    @Test
    fun `vehicles round-trip, sort newest first and hide archived ones`() =
        runTest {
            val cb = Vehicle(name = "CB 500", kind = VehicleKind.MOTORCYCLE, createdAt = day(10))
            vehicles.save(gol)
            vehicles.save(cb)

            assertEquals(listOf(cb, gol), vehicles.all(includingArchived = false))

            vehicles.setArchived(true, gol.id)
            assertEquals(listOf(cb), vehicles.all(includingArchived = false))
            assertEquals(listOf(cb.id, gol.id), vehicles.all(includingArchived = true).map { it.id })
            assertTrue(vehicles.vehicle(gol.id)?.isArchived == true)
            assertNull(vehicles.vehicle(UUID.randomUUID()))
        }

    @Test
    fun `saving an existing vehicle keeps its creation date`() =
        runTest {
            vehicles.save(gol)

            vehicles.save(gol.copy(name = "Gol G5", createdAt = day(99)))

            assertEquals(gol.copy(name = "Gol G5"), vehicles.vehicle(gol.id))
        }

    @Test
    fun `archiving a missing vehicle fails`() =
        runTest {
            assertIs<RepositoryError.VehicleNotFound>(assertFailsWith<RepositoryError> { vehicles.setArchived(true, UUID.randomUUID()) })
        }

    @Test
    fun `fuel entries round-trip in chronological order and keep exact prices`() =
        runTest {
            vehicles.save(gol)
            val later = fuelEntry(20, 1_800.0)
            val earlier = fuelEntry(10, 1_400.0)
            fuel.save(later)
            fuel.save(earlier)

            val saved = fuel.entries(gol.id)
            assertEquals(listOf(earlier.id, later.id), saved.map { it.id })
            assertDecimal("5.899", saved.first().pricePerLiter)
            assertEquals(40.5, saved.first().liters)

            fuel.save(earlier.copy(liters = 30.0))
            assertEquals(30.0, fuel.entries(gol.id).first().liters)

            fuel.delete(earlier.id)
            assertEquals(listOf(later.id), fuel.entries(gol.id).map { it.id })
        }

    @Test
    fun `a fuel entry needs an existing vehicle and deleting a missing one fails`() =
        runTest {
            assertIs<RepositoryError.VehicleNotFound>(assertFailsWith<RepositoryError> { fuel.save(fuelEntry(1, 1_000.0, UUID.randomUUID())) })
            assertIs<RepositoryError.EntryNotFound>(assertFailsWith<RepositoryError> { fuel.delete(UUID.randomUUID()) })
        }

    @Test
    fun `service entries round-trip and can be deleted`() =
        runTest {
            vehicles.save(gol)
            val oil =
                ServiceEntry(vehicleId = gol.id, kind = ServiceKind.OIL_CHANGE, date = day(5), odometer = 1_200.0, cost = BigDecimal("320.00"), notes = "5W30")
            services.save(oil)

            assertEquals(listOf(oil), services.entries(gol.id).map { it.copy(cost = it.cost.setScale(2)) })

            services.deleteEntry(oil.id)
            assertTrue(services.entries(gol.id).isEmpty())
            assertIs<RepositoryError.EntryNotFound>(assertFailsWith<RepositoryError> { services.deleteEntry(oil.id) })
            assertIs<RepositoryError.VehicleNotFound>(
                assertFailsWith<RepositoryError> { services.save(oil.copy(id = UUID.randomUUID(), vehicleId = UUID.randomUUID())) },
            )
        }

    @Test
    fun `saving rules replaces the vehicle's set`() =
        runTest {
            vehicles.save(gol)
            val oil = ServiceRule(vehicleId = gol.id, kind = ServiceKind.OIL_CHANGE, kilometerInterval = 10_000.0, monthInterval = 12)
            val tires = ServiceRule(vehicleId = gol.id, kind = ServiceKind.TIRES, kilometerInterval = 40_000.0)
            services.save(listOf(oil, tires), gol.id)

            assertEquals(setOf(oil, tires), services.rules(gol.id).toSet())

            val chain = ServiceRule(vehicleId = gol.id, kind = ServiceKind.CHAIN, kilometerInterval = 1_000.0)
            services.save(listOf(oil.copy(monthInterval = 6), chain), gol.id)
            assertEquals(setOf(oil.copy(monthInterval = 6), chain), services.rules(gol.id).toSet())

            services.save(emptyList(), gol.id)
            assertTrue(services.rules(gol.id).isEmpty())

            services.save(listOf(oil), gol.id)
            services.deleteRule(oil.id)
            assertTrue(services.rules(gol.id).isEmpty())
            assertIs<RepositoryError.EntryNotFound>(assertFailsWith<RepositoryError> { services.deleteRule(oil.id) })
            assertIs<RepositoryError.VehicleNotFound>(assertFailsWith<RepositoryError> { services.save(listOf(oil), UUID.randomUUID()) })
        }

    @Test
    fun `a storage failure is wrapped as a persistence error and domain errors pass through`() =
        runTest {
            val failure = assertFailsWith<RepositoryError> { persisting { throw IllegalStateException("disk full") } }
            assertIs<RepositoryError.PersistenceFailed>(failure)
            assertIs<IllegalStateException>(failure.cause)

            val missing = RepositoryError.EntryNotFound(UUID.randomUUID())
            assertEquals(missing, assertFailsWith<RepositoryError> { persisting { throw missing } })
        }

    @Test
    fun `saved routes round-trip newest first, filter by vehicle and can be deleted`() =
        runTest {
            val earlier = savedRoute("São Paulo", "Rio de Janeiro", day(1))
            val later = savedRoute("Curitiba", "Paranaguá", day(5))
            routes.save(earlier)
            routes.save(later)

            assertEquals(listOf(later, earlier), routes.routes())
            assertEquals(listOf(later), routes.routes(vehicleId = later.vehicleId).filter { it.id == later.id })
            assertEquals(emptyList(), routes.routes(vehicleId = UUID.randomUUID()))
            assertDecimal("0.5", routes.routes().last().costPerKilometer)
            assertDecimal("12", routes.routes().last().tollCost)
            assertEquals(10.0, routes.routes().last().kilometersPerLiter, 0.001)
            assertDecimal("5", routes.routes().last().pricePerLiter)

            val path = listOf(RoutePoint(-23.55, -46.63), RoutePoint(-22.9, -43.17))
            routes.save(later.copy(path = path))
            assertEquals(path, routes.routes().first().path)
            assertEquals(emptyList(), routes.routes().last().path)

            routes.save(earlier.copy(hasTolls = false))
            assertEquals(false, routes.routes().last().hasTolls)

            routes.delete(earlier.id)
            assertEquals(listOf(later.id), routes.routes().map { it.id })
        }

    @Test
    fun `the new vehicle, fuel and service fields round-trip`() =
        runTest {
            val diesel = gol.copy(fuelCapability = FuelCapability.DIESEL, uf = "SP", modelYear = 2019)
            vehicles.save(diesel)
            val typed = fuelEntry(3, 1_200.0).copy(fuelType = FuelType.DIESEL_S10, createdAt = day(9))
            fuel.save(typed)
            val oil =
                ServiceEntry(
                    vehicleId = gol.id,
                    kind = ServiceKind.TIMING_BELT,
                    date = day(5),
                    odometer = 1_300.0,
                    cost = BigDecimal("900"),
                    workshop = "Oficina do Zé",
                    createdAt = day(40),
                )
            services.save(oil)

            assertEquals(diesel, vehicles.vehicle(gol.id))
            assertEquals(FuelType.DIESEL_S10, fuel.entries(gol.id).single().fuelType)
            assertEquals(day(9), fuel.entries(gol.id).single().createdAt)
            assertEquals(oil, services.entries(gol.id).single())
        }

    @Test
    fun `expenses round-trip with exact and negative amounts, fine details and coverage dates`() =
        runTest {
            vehicles.save(gol)
            val insurance =
                Expense(
                    vehicleId = gol.id,
                    category = ExpenseCategory.SEGURO,
                    date = day(3),
                    amount = BigDecimal("2450.37"),
                    odometer = 1_100.0,
                    note = "Apólice",
                    coverageStart = LocalDate.of(2001, 1, 4),
                    coverageEnd = LocalDate.of(2002, 1, 3),
                )
            val refund = Expense(vehicleId = gol.id, category = ExpenseCategory.OUTROS, date = day(1), amount = BigDecimal("-35.10"))
            val fine =
                Expense(
                    vehicleId = gol.id,
                    category = ExpenseCategory.MULTA,
                    date = day(8),
                    amount = BigDecimal("156.18"),
                    fine =
                        FineDetails(
                            infractionDate = LocalDate.of(2000, 12, 20),
                            severity = FineSeverity.GRAVE,
                            points = 5,
                            dueDate = LocalDate.of(2001, 2, 1),
                            discount = FineDiscount.TWENTY,
                            isPaid = false,
                        ),
                )
            val tollWithStrayFine = Expense(vehicleId = gol.id, category = ExpenseCategory.PEDAGIO, date = day(9), amount = BigDecimal("12.40"), fine = FineDetails())
            expenses.save(insurance)
            expenses.save(refund)
            expenses.save(fine)
            expenses.save(tollWithStrayFine)

            val saved = expenses.expenses(gol.id)
            assertEquals(listOf(refund, insurance, fine, tollWithStrayFine.copy(fine = null)), saved)
            assertDecimal("-35.10", saved.first().amount)

            expenses.save(refund.copy(vehicleId = UUID.randomUUID(), note = "Estorno"))
            assertEquals(gol.id, expenses.expenses(gol.id).first().vehicleId)

            expenses.delete(refund.id)
            assertEquals(3, expenses.expenses(gol.id).size)
        }

    @Test
    fun `an expense needs an existing vehicle and deleting a missing one fails`() =
        runTest {
            val orphan = Expense(vehicleId = UUID.randomUUID(), category = ExpenseCategory.LAVAGEM, date = day(1), amount = BigDecimal.TEN)

            assertIs<RepositoryError.VehicleNotFound>(assertFailsWith<RepositoryError> { expenses.save(orphan) })
            assertIs<RepositoryError.EntryNotFound>(assertFailsWith<RepositoryError> { expenses.delete(UUID.randomUUID()) })
        }

    @Test
    fun `due items round-trip, driver items have no vehicle and deleting a missing one fails`() =
        runTest {
            vehicles.save(gol)
            val ipva =
                DueItem(
                    vehicleId = gol.id,
                    kind = DueKind.IPVA,
                    dueDate = LocalDate.of(2001, 3, 10),
                    amount = BigDecimal("812.45"),
                    installmentNumber = 1,
                    installmentCount = 3,
                    note = "Cota 1",
                    createdAt = day(0),
                )
            val license = DueItem(vehicleId = null, kind = DueKind.CNH, dueDate = LocalDate.of(2003, 7, 1), paidAt = day(4), createdAt = day(1))
            dues.save(license)
            dues.save(ipva)

            assertEquals(listOf(ipva), dues.items(gol.id))
            assertEquals(listOf(license), dues.driverItems())
            assertEquals(listOf(ipva, license), dues.all())

            dues.delete(ipva.id)
            assertEquals(listOf(license), dues.all())
            assertIs<RepositoryError.EntryNotFound>(assertFailsWith<RepositoryError> { dues.delete(ipva.id) })
            assertIs<RepositoryError.VehicleNotFound>(assertFailsWith<RepositoryError> { dues.save(ipva.copy(vehicleId = UUID.randomUUID())) })
        }

    @Test
    fun `deleting a vehicle removes its expenses and due items but keeps the driver license`() =
        runTest {
            vehicles.save(gol)
            expenses.save(Expense(vehicleId = gol.id, category = ExpenseCategory.LAVAGEM, date = day(1), amount = BigDecimal.TEN))
            dues.save(DueItem(vehicleId = gol.id, kind = DueKind.SEGURO, dueDate = LocalDate.of(2001, 5, 1), createdAt = day(0)))
            val license = DueItem(vehicleId = null, kind = DueKind.CNH, dueDate = LocalDate.of(2003, 7, 1), createdAt = day(1))
            dues.save(license)

            database.openHelper.writableDatabase.execSQL("DELETE FROM vehicles WHERE id = '${gol.id}'")

            assertTrue(expenses.expenses(gol.id).isEmpty())
            assertTrue(dues.items(gol.id).isEmpty())
            assertEquals(listOf(license), dues.all())
        }

    private fun savedRoute(
        origin: String,
        destination: String,
        createdAt: java.time.Instant,
    ) = SavedRoute(
        id = UUID.randomUUID(),
        vehicleId = gol.id,
        origin = origin,
        destination = destination,
        distanceKm = 100.0,
        isRoundTrip = true,
        hasTolls = true,
        tollCost = BigDecimal("12"),
        kilometersPerLiter = 10.0,
        pricePerLiter = BigDecimal("5"),
        liters = 20.0,
        fuelCost = BigDecimal("100"),
        costPerKilometer = BigDecimal("0.5"),
        createdAt = createdAt,
    )
}
