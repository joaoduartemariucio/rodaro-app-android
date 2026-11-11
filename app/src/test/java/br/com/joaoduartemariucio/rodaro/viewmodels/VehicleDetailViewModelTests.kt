package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.calculations.assertDecimal
import br.com.joaoduartemariucio.rodaro.calculations.day
import br.com.joaoduartemariucio.rodaro.calculations.dueItem
import br.com.joaoduartemariucio.rodaro.calculations.expense
import br.com.joaoduartemariucio.rodaro.calculations.utc
import br.com.joaoduartemariucio.rodaro.domain.common.calculations.sum
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RepositoryError
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDetails
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueKind
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.MaintenanceStatus
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceRule
import br.com.joaoduartemariucio.rodaro.domain.vehicles.calculations.Odometer
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.FuelCapability
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.fakes.FakeAnalytics
import br.com.joaoduartemariucio.rodaro.fakes.FixedDateProvider
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryDueItemRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryExpenseRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryFuelEntryRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryServiceRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryVehicleRepository
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.VehicleDetailViewModel
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.VehicleDetailViewModel.State
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.VehicleTimeline
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VehicleDetailViewModelTests {
    private val vehicle = Vehicle(name = "Gol", initialOdometer = 1_000.0, createdAt = day(0))

    private fun fuel(
        dayOffset: Int,
        odometer: Double,
    ) = FuelEntry(
        vehicleId = vehicle.id,
        date = day(dayOffset),
        odometer = odometer,
        liters = 40.0,
        pricePerLiter = BigDecimal(5),
        isFullTank = true,
    )

    private fun makeViewModel(
        fuel: List<FuelEntry> = emptyList(),
        services: List<ServiceEntry> = emptyList(),
        rules: List<ServiceRule> = emptyList(),
        fuelFailure: Exception? = null,
        vehicles: InMemoryVehicleRepository = InMemoryVehicleRepository(listOf(vehicle)),
        expenses: InMemoryExpenseRepository = InMemoryExpenseRepository(),
        dues: InMemoryDueItemRepository = InMemoryDueItemRepository(),
    ) = VehicleDetailViewModel(
        vehicle = vehicle,
        vehicleRepository = vehicles,
        fuelEntryRepository = InMemoryFuelEntryRepository(fuel, fuelFailure),
        serviceRepository = InMemoryServiceRepository(services, rules),
        expenseRepository = expenses,
        dueItemRepository = dues,
        dateProvider = FixedDateProvider(day(120)),
    )

    private val VehicleDetailViewModel.content get() = assertIs<State.Loaded>(state).content

    @Test
    fun `loads stats, timeline and rules together`() =
        runTest {
            val viewModel =
                makeViewModel(
                    fuel = listOf(fuel(1, 1_000.0), fuel(40, 1_400.0)),
                    services =
                        listOf(
                            ServiceEntry(vehicleId = vehicle.id, kind = ServiceKind.OIL_CHANGE, date = day(20), odometer = 1_200.0, cost = BigDecimal(300)),
                        ),
                    rules = listOf(ServiceRule(vehicleId = vehicle.id, kind = ServiceKind.OIL_CHANGE, kilometerInterval = 10_000.0, monthInterval = 12)),
                )

            viewModel.load()

            val content = viewModel.content
            assertEquals(1_400.0, content.stats.currentOdometer)
            assertDecimal("700", content.stats.cost.totalCost)
            assertEquals(1, content.rules.size)
            assertNotNull(content.stats.nextDue)
            assertEquals(3, content.sections.flatMap { it.items }.size)
        }

    @Test
    fun `a vehicle without history loads with empty sections, not an error`() =
        runTest {
            val viewModel = makeViewModel()

            viewModel.load()

            assertTrue(viewModel.content.sections.isEmpty())
            assertEquals(1_000.0, viewModel.content.stats.currentOdometer)
            assertNull(viewModel.content.stats.nextDue)
        }

    @Test
    fun `a repository failure surfaces as failed instead of being swallowed`() =
        runTest {
            val viewModel = makeViewModel(fuelFailure = RepositoryError.EntryNotFound(UUID.randomUUID()))

            viewModel.load()

            assertIs<State.Failed>(viewModel.state)
        }

    @Test
    fun `history is grouped by month, most recent first`() =
        runTest {
            val viewModel = makeViewModel(fuel = listOf(fuel(1, 1_000.0), fuel(40, 1_400.0)))

            viewModel.load()

            assertEquals(2, viewModel.content.sections.size)
            assertTrue(viewModel.content.sections[0].month > viewModel.content.sections[1].month)
        }

    @Test
    fun `rules are exposed once loaded and empty before`() =
        runTest {
            val rule = ServiceRule(vehicleId = vehicle.id, kind = ServiceKind.OIL_CHANGE, kilometerInterval = 10_000.0)
            val viewModel = makeViewModel(rules = listOf(rule))
            assertTrue(viewModel.rules.isEmpty())

            viewModel.load()

            assertEquals(listOf(rule), viewModel.rules)
        }

    @Test
    fun `timeline items expose the odometer of fuel and service entries`() {
        val fuelItem = VehicleTimeline.Item.Fuel(fuel(1, 1_000.0))
        val serviceItem = VehicleTimeline.Item.Service(ServiceEntry(vehicleId = vehicle.id, date = day(2), odometer = 1_100.0, cost = BigDecimal(50)))

        assertEquals(1_000.0, fuelItem.odometer)
        assertEquals(1_100.0, serviceItem.odometer)
    }

    @Test
    fun `odometer readings excluding an entry ignore that entry`() =
        runTest {
            val latest = fuel(40, 1_400.0)
            val viewModel = makeViewModel(fuel = listOf(fuel(1, 1_000.0), latest))
            viewModel.load()

            assertEquals(1_400.0, Odometer.current(viewModel.odometerReadings()))
            assertEquals(1_000.0, Odometer.current(viewModel.odometerReadings(VehicleTimeline.Item.Fuel(latest))))
        }

    @Test
    fun `deleting an entry reloads without it`() =
        runTest {
            val doomed = fuel(40, 1_400.0)
            val viewModel = makeViewModel(fuel = listOf(fuel(1, 1_000.0), doomed))
            viewModel.load()

            viewModel.delete(VehicleTimeline.Item.Fuel(doomed))

            assertEquals(
                1,
                viewModel.content.sections
                    .flatMap { it.items }
                    .size,
            )
            assertEquals(1_000.0, viewModel.content.stats.currentOdometer)
        }

    @Test
    fun `deleting a service entry that is gone surfaces the failure`() =
        runTest {
            val viewModel = makeViewModel()
            viewModel.load()

            viewModel.delete(VehicleTimeline.Item.Service(ServiceEntry(vehicleId = vehicle.id, date = day(2), odometer = 1_100.0, cost = BigDecimal(50))))

            assertIs<State.Failed>(viewModel.state)
        }

    @Test
    fun `the history filter hides the other record type and drops empty months`() =
        runTest {
            val viewModel =
                makeViewModel(
                    fuel = listOf(fuel(1, 1_000.0), fuel(40, 1_400.0)),
                    services =
                        listOf(
                            ServiceEntry(vehicleId = vehicle.id, kind = ServiceKind.OIL_CHANGE, date = day(2), odometer = 1_050.0, cost = BigDecimal(300)),
                        ),
                )
            assertTrue(viewModel.visibleSections.isEmpty())
            assertFalse(viewModel.hasHistory)
            assertNull(viewModel.lastPricePerLiter)

            viewModel.load()

            assertTrue(viewModel.hasHistory)
            assertDecimal("5", viewModel.lastPricePerLiter)
            assertEquals(3, viewModel.visibleSections.flatMap { it.items }.size)

            viewModel.filter = VehicleTimeline.Filter.SERVICE
            assertEquals(1, viewModel.visibleSections.size)
            assertEquals(1, viewModel.visibleSections.flatMap { it.items }.size)

            viewModel.filter = VehicleTimeline.Filter.FUEL
            assertEquals(2, viewModel.visibleSections.flatMap { it.items }.size)
        }

    @Test
    fun `spending covers the last six months`() =
        runTest {
            val viewModel = makeViewModel(fuel = listOf(fuel(100, 1_400.0)))

            viewModel.load()

            assertEquals(VehicleDetailViewModel.SPENDING_MONTHS, viewModel.content.spending.size)
            assertDecimal(
                "200",
                viewModel.content.spending
                    .map { it.total }
                    .sum(),
            )
        }

    @Test
    fun `loading picks up a vehicle edited since the screen opened, or keeps the current one if it is gone`() =
        runTest {
            val vehicles = InMemoryVehicleRepository(listOf(vehicle))
            val viewModel = makeViewModel(vehicles = vehicles)
            vehicles.save(vehicle.copy(name = "Gol G5", fuelCapability = FuelCapability.DIESEL))

            viewModel.load()
            assertEquals("Gol G5", viewModel.vehicle.name)
            assertEquals(FuelCapability.DIESEL, viewModel.vehicle.fuelCapability)

            val missing = makeViewModel(vehicles = InMemoryVehicleRepository())
            missing.load()
            assertEquals(vehicle, missing.vehicle)
        }

    @Test
    fun `the next fuel-up suggests the last typed fuel and the latest price of each type`() =
        runTest {
            val viewModel =
                makeViewModel(
                    fuel =
                        listOf(
                            fuel(1, 1_000.0).copy(fuelType = FuelType.GASOLINA_COMUM, pricePerLiter = BigDecimal("5.89")),
                            fuel(10, 1_300.0).copy(fuelType = FuelType.ETANOL, pricePerLiter = BigDecimal("3.99")),
                            fuel(20, 1_600.0).copy(fuelType = FuelType.GASOLINA_COMUM, pricePerLiter = BigDecimal("6.09")),
                            fuel(30, 1_900.0),
                        ),
                )

            viewModel.load()

            assertEquals(FuelType.GASOLINA_COMUM, viewModel.suggestedFuelType)
            assertEquals(mapOf(FuelType.GASOLINA_COMUM to BigDecimal("6.09"), FuelType.ETANOL to BigDecimal("3.99")), viewModel.suggestedPrices)
        }

    @Test
    fun `expenses join the timeline, its filter, the spending and can be deleted`() =
        runTest {
            val wash = expense(ExpenseCategory.LAVAGEM, "60", dayOffset = 100, vehicleId = vehicle.id)
            val unpaid = expense(ExpenseCategory.MULTA, "195.23", dayOffset = 101, fine = FineDetails(isPaid = false), vehicleId = vehicle.id)
            val expenses = InMemoryExpenseRepository(listOf(wash, unpaid))
            val analytics = FakeAnalytics()
            val viewModel =
                VehicleDetailViewModel(
                    vehicle = vehicle,
                    vehicleRepository = InMemoryVehicleRepository(listOf(vehicle)),
                    fuelEntryRepository = InMemoryFuelEntryRepository(listOf(fuel(99, 1_100.0))),
                    serviceRepository = InMemoryServiceRepository(),
                    expenseRepository = expenses,
                    dueItemRepository = InMemoryDueItemRepository(),
                    dateProvider = FixedDateProvider(day(120)),
                    analytics = analytics,
                )

            viewModel.load()

            assertTrue(viewModel.hasExpenses)
            assertEquals(
                3,
                viewModel.content.sections
                    .flatMap { it.items }
                    .size,
            )
            assertEquals(
                listOf("expense-${unpaid.id}", "expense-${wash.id}", "fuel-"),
                viewModel.content.sections
                    .single()
                    .items
                    .map { it.key.take(if (it is VehicleTimeline.Item.Fuel) 5 else 100) },
            )
            assertDecimal(
                "260",
                viewModel.content.sections
                    .single()
                    .total,
            )
            val april =
                viewModel.content.spending
                    .dropLast(1)
                    .last()
            assertDecimal("60", april.expenses)
            assertDecimal("260", april.total)

            viewModel.filter = VehicleTimeline.Filter.EXPENSES
            assertEquals(listOf(unpaid.id, wash.id), viewModel.visibleSections.flatMap { it.items }.map { it.id })

            viewModel.delete(VehicleTimeline.Item.Expense(wash))
            assertEquals(listOf(unpaid), expenses.expenses(vehicle.id))
            assertEquals("expense", analytics.parameters("timeline_entry_deleted")?.get("kind"))
        }

    @Test
    fun `timeline items without an odometer sort after the others on the same date`() {
        val toll = expense(ExpenseCategory.PEDAGIO, "10", dayOffset = 5, vehicleId = vehicle.id)
        val fill = fuel(5, 1_100.0)

        val items = VehicleTimeline.sections(listOf(fill), emptyList(), utc, listOf(toll)).single().items

        assertEquals(listOf(fill.id, toll.id), items.map { it.id })
        assertNull(VehicleTimeline.Item.Expense(toll).odometer)
    }

    @Test
    fun `due items load by urgency, paid ones apart, and can be deleted`() =
        runTest {
            val later = dueItem(DueKind.SEGURO, LocalDate.parse("2001-12-01"), vehicleId = vehicle.id)
            val overdue = dueItem(DueKind.IPVA, LocalDate.parse("2001-04-01"), vehicleId = vehicle.id)
            val soon = dueItem(DueKind.LICENCIAMENTO, LocalDate.parse("2001-05-20"), vehicleId = vehicle.id)
            val paid = dueItem(DueKind.IPVA, LocalDate.parse("2001-02-01"), vehicleId = vehicle.id, paidAt = day(30))
            val dues = InMemoryDueItemRepository(listOf(later, overdue, soon, paid))
            val viewModel = makeViewModel(dues = dues)

            viewModel.load()

            assertEquals(listOf(overdue.id, soon.id, later.id), viewModel.content.dueItems.map { it.item.id })
            assertEquals(
                listOf(MaintenanceStatus.Level.OVERDUE, MaintenanceStatus.Level.DUE_SOON, MaintenanceStatus.Level.ON_TRACK),
                viewModel.content.dueItems.map { it.level },
            )
            assertEquals(listOf(paid.id), viewModel.content.paidDueItems.map { it.item.id })

            viewModel.deleteDueItem(later)
            assertEquals(listOf(overdue.id, soon.id), viewModel.content.dueItems.map { it.item.id })

            viewModel.deleteDueItem(later)
            assertIs<State.Failed>(viewModel.state)
        }
}
