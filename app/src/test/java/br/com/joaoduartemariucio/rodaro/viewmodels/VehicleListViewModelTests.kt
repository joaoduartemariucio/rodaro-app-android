package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.calculations.assertDecimal
import br.com.joaoduartemariucio.rodaro.calculations.day
import br.com.joaoduartemariucio.rodaro.calculations.expense
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RepositoryError
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDetails
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceRule
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.fakes.FixedDateProvider
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryExpenseRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryFuelEntryRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryServiceRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryVehicleRepository
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.list.VehicleListViewModel
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.list.VehicleListViewModel.Scope
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.list.VehicleListViewModel.State
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VehicleListViewModelTests {
    private fun vehicle(
        name: String,
        archived: Boolean = false,
    ) = Vehicle(name = name, createdAt = day(0), isArchived = archived)

    private fun makeViewModel(
        scope: Scope = Scope.ACTIVE,
        vehicles: List<Vehicle> = emptyList(),
        fuel: List<FuelEntry> = emptyList(),
        services: List<ServiceEntry> = emptyList(),
        rules: List<ServiceRule> = emptyList(),
        now: Instant = day(60),
        failure: Exception? = null,
        expenses: List<Expense> = emptyList(),
    ) = VehicleListViewModel(
        scope = scope,
        vehicleRepository = InMemoryVehicleRepository(vehicles, failure),
        fuelEntryRepository = InMemoryFuelEntryRepository(fuel),
        serviceRepository = InMemoryServiceRepository(services, rules),
        expenseRepository = InMemoryExpenseRepository(expenses),
        dateProvider = FixedDateProvider(now),
    )

    private fun fuel(
        vehicle: Vehicle,
        dayOffset: Int,
        odometer: Double,
        liters: Double = 40.0,
    ) = FuelEntry(
        vehicleId = vehicle.id,
        date = day(dayOffset),
        odometer = odometer,
        liters = liters,
        pricePerLiter = BigDecimal(5),
        isFullTank = true,
    )

    private val VehicleListViewModel.rows get() = assertIs<State.Loaded>(state).rows

    @Test
    fun `loads vehicles with their stats`() =
        runTest {
            val gol = vehicle("Gol")
            val viewModel = makeViewModel(vehicles = listOf(gol), fuel = listOf(fuel(gol, 0, 1_000.0), fuel(gol, 10, 1_400.0)))

            viewModel.load()

            val rows = viewModel.rows
            assertEquals(1, rows.size)
            assertEquals(gol.id, rows[0].vehicle.id)
            assertEquals(1_400.0, rows[0].stats.currentOdometer)
            assertEquals(10.0, rows[0].stats.latestConsumption?.kilometersPerLiter)
        }

    @Test
    fun `an empty repository loads an empty list, not an error`() =
        runTest {
            val viewModel = makeViewModel()

            viewModel.load()

            assertTrue(viewModel.rows.isEmpty())
        }

    @Test
    fun `a repository failure surfaces as failed instead of being swallowed`() =
        runTest {
            val viewModel = makeViewModel(failure = RepositoryError.VehicleNotFound(UUID.randomUUID()))

            viewModel.load()

            assertIs<State.Failed>(viewModel.state)
        }

    @Test
    fun `the active scope hides archived vehicles`() =
        runTest {
            val viewModel = makeViewModel(vehicles = listOf(vehicle("Gol"), vehicle("Uno", archived = true)))

            viewModel.load()

            assertEquals(listOf("Gol"), viewModel.rows.map { it.vehicle.name })
        }

    @Test
    fun `the archived scope shows only archived vehicles`() =
        runTest {
            val viewModel = makeViewModel(Scope.ARCHIVED, listOf(vehicle("Gol"), vehicle("Uno", archived = true)))

            viewModel.load()

            assertEquals(listOf("Uno"), viewModel.rows.map { it.vehicle.name })
        }

    @Test
    fun `archiving reloads and drops the vehicle from the active list`() =
        runTest {
            val gol = vehicle("Gol")
            val viewModel = makeViewModel(vehicles = listOf(gol))
            viewModel.load()

            viewModel.setArchived(true, gol)

            assertTrue(viewModel.rows.isEmpty())
        }

    @Test
    fun `archiving a vehicle that is gone surfaces the failure`() =
        runTest {
            val viewModel = makeViewModel()

            viewModel.setArchived(true, vehicle("Ghost"))

            assertIs<State.Failed>(viewModel.state)
        }

    @Test
    fun `the overview adds up this month's spending and the reminders that need attention`() =
        runTest {
            val gol = vehicle("Gol")
            val cb = vehicle("CB")
            val viewModel =
                makeViewModel(
                    vehicles = listOf(gol, cb),
                    fuel = listOf(fuel(gol, 40, 1_000.0, liters = 10.0)),
                    services =
                        listOf(
                            ServiceEntry(vehicleId = cb.id, kind = ServiceKind.CHAIN, date = day(65), odometer = 500.0, cost = BigDecimal(30)),
                        ),
                    rules =
                        listOf(
                            ServiceRule(vehicleId = cb.id, kind = ServiceKind.CHAIN, kilometerInterval = 300.0),
                            ServiceRule(vehicleId = gol.id, kind = ServiceKind.OIL_CHANGE, monthInterval = 1),
                        ),
                    now = day(70),
                )

            assertNull(viewModel.overview)
            viewModel.load()

            val overview = viewModel.overview
            assertEquals(2, overview?.vehicleCount)
            assertDecimal("30", overview?.spentThisMonth)
            assertEquals(1, overview?.overdueCount)
            assertEquals(1, overview?.dueSoonCount)
        }

    @Test
    fun `this month's spending includes paid expenses but not unpaid fines`() =
        runTest {
            val gol = vehicle("Gol")
            val viewModel =
                makeViewModel(
                    vehicles = listOf(gol),
                    fuel = listOf(fuel(gol, 62, 1_000.0, liters = 10.0)),
                    expenses =
                        listOf(
                            expense(ExpenseCategory.MULTA, "100", dayOffset = 63, fine = FineDetails(), vehicleId = gol.id),
                            expense(ExpenseCategory.MULTA, "300", dayOffset = 63, fine = FineDetails(isPaid = false), vehicleId = gol.id),
                            expense(ExpenseCategory.FINANCIAMENTO, "500", dayOffset = 64, vehicleId = gol.id),
                            expense(ExpenseCategory.ACESSORIOS, "80", dayOffset = 65, vehicleId = gol.id),
                        ),
                    now = day(70),
                )

            viewModel.load()

            assertDecimal("730", viewModel.overview?.spentThisMonth)
            val stats = assertIs<VehicleListViewModel.State.Loaded>(viewModel.state).rows.single().stats
            assertDecimal("730", stats.cost.totalSpent)
        }
}
