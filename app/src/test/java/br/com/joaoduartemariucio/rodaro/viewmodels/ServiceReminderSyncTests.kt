package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.calculations.day
import br.com.joaoduartemariucio.rodaro.calculations.dueItem
import br.com.joaoduartemariucio.rodaro.calculations.expense
import br.com.joaoduartemariucio.rodaro.calculations.sample
import br.com.joaoduartemariucio.rodaro.calculations.testVehicleId
import br.com.joaoduartemariucio.rodaro.calculations.utc
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RepositoryError
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDetails
import br.com.joaoduartemariucio.rodaro.domain.dues.calculations.DueSchedule
import br.com.joaoduartemariucio.rodaro.domain.dues.calculations.DueStage
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueKind
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.MaintenanceForecast
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.MaintenanceForecast.Trigger
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.MaintenanceStatus
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.ServiceDue
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceRule
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.Preferences
import br.com.joaoduartemariucio.rodaro.domain.reminders.calculations.PlannedDueReminder
import br.com.joaoduartemariucio.rodaro.domain.reminders.calculations.PlannedFineReminder
import br.com.joaoduartemariucio.rodaro.domain.reminders.calculations.PlannedReminder
import br.com.joaoduartemariucio.rodaro.domain.reminders.services.Reminder
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.fakes.FakeReminderScheduler
import br.com.joaoduartemariucio.rodaro.fakes.FixedDateProvider
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryDueItemRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryExpenseRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryFuelEntryRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryPreferencesRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryServiceRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryVehicleRepository
import br.com.joaoduartemariucio.rodaro.fakes.TestLocalizer
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedFullDate
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedKilometers
import br.com.joaoduartemariucio.rodaro.presentation.reminders.ServiceReminderSync
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.VehicleDetailViewModel
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.list.VehicleListViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceReminderSyncTests {
    private val vehicle = Vehicle(id = testVehicleId, name = "Gol", initialOdometer = 10_000.0, createdAt = day(0))
    private val oilRule = ServiceRule(vehicleId = testVehicleId, kind = ServiceKind.OIL_CHANGE, monthInterval = 6)

    private fun makeSync(
        vehicles: List<Vehicle> = listOf(vehicle),
        rules: List<ServiceRule> = listOf(oilRule),
        scheduler: FakeReminderScheduler = FakeReminderScheduler(),
        vehicleFailure: Exception? = null,
        preferences: InMemoryPreferencesRepository = InMemoryPreferencesRepository(),
        dues: InMemoryDueItemRepository = InMemoryDueItemRepository(),
        expenses: InMemoryExpenseRepository = InMemoryExpenseRepository(),
        now: Instant = day(1),
    ) = ServiceReminderSync(
        vehicleRepository = InMemoryVehicleRepository(vehicles, vehicleFailure),
        fuelEntryRepository = InMemoryFuelEntryRepository(),
        serviceRepository = InMemoryServiceRepository(rules = rules),
        dueItemRepository = dues,
        expenseRepository = expenses,
        scheduler = scheduler,
        dateProvider = FixedDateProvider(now),
        localizer = TestLocalizer(),
        preferences = preferences,
    )

    private fun stale(id: UUID = UUID.randomUUID()) = Reminder(id, "old", "old", day(2))

    @Test
    fun `cancels everything and stops when reminders are disabled`() =
        runTest {
            val scheduler = FakeReminderScheduler(pending = listOf(stale()))
            val preferences = InMemoryPreferencesRepository(stored = Preferences(remindersEnabled = false))

            makeSync(scheduler = scheduler, preferences = preferences).sync()

            assertTrue(scheduler.scheduled.isEmpty())
            assertEquals(0, scheduler.authorizationRequests)
        }

    @Test
    fun `schedules one reminder per rule with a known due date`() =
        runTest {
            val scheduler = FakeReminderScheduler()

            makeSync(scheduler = scheduler).sync()

            assertEquals(setOf(oilRule.id), scheduler.scheduled.keys)
            assertEquals("Gol: Oil change", scheduler.scheduled[oilRule.id]?.title)
            assertFalse(scheduler.scheduled[oilRule.id]?.body.isNullOrEmpty())
            assertEquals(1, scheduler.authorizationRequests)
        }

    @Test
    fun `cancels reminders whose rule no longer applies`() =
        runTest {
            val leftover = stale()
            val scheduler = FakeReminderScheduler(listOf(leftover))

            makeSync(scheduler = scheduler).sync()

            assertFalse(leftover.id in scheduler.scheduled)
            assertTrue(oilRule.id in scheduler.scheduled)
        }

    @Test
    fun `without anything to schedule, it cancels and never asks for permission`() =
        runTest {
            val scheduler = FakeReminderScheduler(listOf(stale()))

            makeSync(rules = emptyList(), scheduler = scheduler).sync()

            assertTrue(scheduler.scheduled.isEmpty())
            assertEquals(0, scheduler.authorizationRequests)
        }

    @Test
    fun `when notifications are denied, nothing is scheduled`() =
        runTest {
            val scheduler = FakeReminderScheduler(isAuthorized = false)

            makeSync(scheduler = scheduler).sync()

            assertTrue(scheduler.scheduled.isEmpty())
        }

    @Test
    fun `a repository failure leaves pending reminders untouched`() =
        runTest {
            val pending = stale(oilRule.id)
            val scheduler = FakeReminderScheduler(listOf(pending))

            makeSync(scheduler = scheduler, vehicleFailure = RepositoryError.VehicleNotFound(testVehicleId)).sync()

            assertEquals(mapOf(pending.id to pending), scheduler.scheduled.toMap())
        }

    private fun planned(
        dueDate: Instant?,
        trigger: Trigger,
        dueOdometer: Double?,
        overdueBy: Trigger? = null,
    ) = PlannedReminder(
        vehicle = vehicle,
        due = ServiceDue(oilRule, MaintenanceForecast(dueDate, trigger, dueOdometer, null), sample(0, 10_000.0)),
        status =
            MaintenanceStatus(
                level = if (overdueBy == null) MaintenanceStatus.Level.ON_TRACK else MaintenanceStatus.Level.OVERDUE,
                progress = 0.5,
                remainingKilometers = null,
                remainingDays = null,
                overdueBy = overdueBy,
                calendarDueDate = day(8),
            ),
        currentOdometer = 21_234.0,
        fireDate = day(10),
    )

    @Test
    fun `the body says whether the service is overdue by km or date, or due by distance or date`() {
        val sync = makeSync()
        val byDate = sync.reminder(planned(day(10), Trigger.CALENDAR, null))
        val byDistance = sync.reminder(planned(day(10), Trigger.DISTANCE, 20_000.0))
        val overdueByDate = sync.reminder(planned(day(10), Trigger.CALENDAR, null, Trigger.CALENDAR))
        val overdueByDistance = sync.reminder(planned(null, Trigger.DISTANCE, 20_000.0, Trigger.DISTANCE))
        val dateless = sync.reminder(planned(null, Trigger.DISTANCE, 20_000.0))

        assertEquals(oilRule.id, byDate.id)
        assertEquals(day(10), byDate.fireDate)
        assertTrue(byDate.title.contains(vehicle.name))
        assertEquals(5, listOf(byDate, byDistance, overdueByDate, overdueByDistance, dateless).map { it.body }.toSet().size)
        assertTrue(byDistance.body.contains(20_000.0.formattedKilometers()))
        assertTrue(overdueByDistance.body.contains(21_234.0.formattedKilometers()))
        assertTrue(overdueByDate.body.contains(day(8).formattedFullDate(FixedDateProvider(day(1)).zone)))
    }

    @Test
    fun `loading the list and the detail syncs reminders`() =
        runTest {
            val scheduler = FakeReminderScheduler()
            val sync = makeSync(scheduler = scheduler)
            val list =
                VehicleListViewModel(
                    scope = VehicleListViewModel.Scope.ACTIVE,
                    vehicleRepository = InMemoryVehicleRepository(listOf(vehicle)),
                    fuelEntryRepository = InMemoryFuelEntryRepository(),
                    serviceRepository = InMemoryServiceRepository(),
                    expenseRepository = InMemoryExpenseRepository(),
                    dateProvider = FixedDateProvider(day(30)),
                    reminders = sync,
                )

            list.load()
            assertEquals(1, scheduler.scheduled.size)

            scheduler.cancel(oilRule.id)
            val detail =
                VehicleDetailViewModel(
                    vehicle = vehicle,
                    vehicleRepository = InMemoryVehicleRepository(listOf(vehicle)),
                    fuelEntryRepository = InMemoryFuelEntryRepository(),
                    serviceRepository = InMemoryServiceRepository(),
                    expenseRepository = InMemoryExpenseRepository(),
                    dueItemRepository = InMemoryDueItemRepository(),
                    dateProvider = FixedDateProvider(day(1)),
                    reminders = sync,
                )

            detail.load()
            assertEquals(1, scheduler.scheduled.size)
        }

    private val ipva = dueItem(DueKind.IPVA, LocalDate.parse("2001-03-01")).copy(installmentNumber = 1, installmentCount = 3)
    private val license = dueItem(DueKind.CNH, LocalDate.parse("2001-06-01"), vehicleId = null)
    private val fine = expense(ExpenseCategory.MULTA, "195.23", fine = FineDetails(dueDate = LocalDate.parse("2001-02-01"), isPaid = false))

    @Test
    fun `service, due date and fine reminders are scheduled together and none cancels the others`() =
        runTest {
            val scheduler = FakeReminderScheduler()
            val sync =
                makeSync(
                    scheduler = scheduler,
                    dues = InMemoryDueItemRepository(listOf(ipva, license)),
                    expenses = InMemoryExpenseRepository(listOf(fine)),
                )

            sync.sync()
            sync.sync()

            val ids = scheduler.scheduled.keys
            assertTrue(oilRule.id in ids)
            val ipvaStages =
                listOf(DueStage.DaysBefore(15), DueStage.DaysBefore(3), DueStage.DueToday) + listOf(7, 14, 21, 28).map { DueStage.Overdue(it) }
            assertTrue(ipvaStages.all { DueSchedule.reminderId(ipva.id, it) in ids })
            assertTrue(DueSchedule.reminderId(license.id, DueStage.DaysBefore(60)) in ids)
            assertTrue(DueSchedule.reminderId(fine.id, DueStage.DaysBefore(7)) in ids)
            assertTrue(DueSchedule.reminderId(fine.id, DueStage.DueToday) in ids)
            assertEquals(1 + 7 + 6 + 2, ids.size)
        }

    @Test
    fun `paying an item cancels its reminders at the next sync and past stages are cancelled`() =
        runTest {
            val pastStage = DueSchedule.reminderId(ipva.id, DueStage.DaysBefore(15))
            val scheduler = FakeReminderScheduler()
            val dues = InMemoryDueItemRepository(listOf(ipva))
            makeSync(scheduler = scheduler, dues = dues, rules = emptyList()).sync()
            assertTrue(pastStage in scheduler.scheduled.keys)

            makeSync(scheduler = scheduler, dues = dues, rules = emptyList(), now = Instant.parse("2001-02-20T00:00:00Z")).sync()
            assertFalse(pastStage in scheduler.scheduled.keys)
            assertEquals(6, scheduler.scheduled.size)

            dues.save(ipva.copy(paidAt = day(40)))
            makeSync(scheduler = scheduler, dues = dues, rules = emptyList(), now = Instant.parse("2001-02-20T00:00:00Z")).sync()
            assertTrue(scheduler.scheduled.isEmpty())
        }

    @Test
    fun `disabled reminders cancel due date and fine reminders too`() =
        runTest {
            val scheduler = FakeReminderScheduler()
            val dues = InMemoryDueItemRepository(listOf(ipva))
            makeSync(scheduler = scheduler, dues = dues).sync()
            assertTrue(scheduler.scheduled.isNotEmpty())

            makeSync(scheduler = scheduler, dues = dues, preferences = InMemoryPreferencesRepository(Preferences(remindersEnabled = false))).sync()

            assertTrue(scheduler.scheduled.isEmpty())
        }

    @Test
    fun `due date and fine reminders read naturally`() =
        runTest {
            val sync = makeSync()
            val zone = utc
            val dueText =
                ipva.dueDate
                    .atStartOfDay(zone)
                    .toInstant()
                    .formattedFullDate(zone)

            val before = sync.reminder(PlannedDueReminder(ipva, vehicle, DueStage.DaysBefore(15), day(44)))
            assertEquals("Gol: IPVA (vehicle tax) 1/3", before.title)
            assertEquals("Due in 15 days ($dueText).", before.body)
            assertEquals(DueSchedule.reminderId(ipva.id, DueStage.DaysBefore(15)), before.id)

            assertEquals("Due today.", sync.reminder(PlannedDueReminder(ipva, vehicle, DueStage.DueToday, day(59))).body)
            assertEquals("Was due on $dueText.", sync.reminder(PlannedDueReminder(ipva, vehicle, DueStage.Overdue(7), day(66))).body)

            val cnh = sync.reminder(PlannedDueReminder(license, null, DueStage.DaysBefore(30), day(120)))
            assertEquals("Driver's license (CNH)", cnh.title)
            assertTrue(cnh.body.endsWith("Good drivers may get an automatic renewal; check with the Detran."))

            val fineDue = LocalDate.parse("2001-02-01")
            val fineText = fineDue.atStartOfDay(zone).toInstant().formattedFullDate(zone)
            val early = sync.reminder(PlannedFineReminder(fine, vehicle, fineDue, DueStage.DaysBefore(7), day(24)))
            assertEquals("Gol: Traffic fine", early.title)
            assertEquals("Fine due on $fineText.", early.body)
            assertEquals("The fine is due today.", sync.reminder(PlannedFineReminder(fine, vehicle, fineDue, DueStage.DueToday, day(31))).body)
        }
}
