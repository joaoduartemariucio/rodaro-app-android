package br.com.joaoduartemariucio.rodaro.calculations

import br.com.joaoduartemariucio.rodaro.domain.common.calculations.atHour
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDetails
import br.com.joaoduartemariucio.rodaro.domain.dues.calculations.DueSchedule
import br.com.joaoduartemariucio.rodaro.domain.dues.calculations.DueStage
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueKind
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.MaintenanceForecast
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.MaintenanceForecast.Trigger
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.MaintenanceStatus
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.ServiceDue
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceRule
import br.com.joaoduartemariucio.rodaro.domain.reminders.calculations.ReminderPlan
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleRecords
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReminderPlanTests {
    private val vehicle = Vehicle(id = testVehicleId, name = "Gol", initialOdometer = 10_000.0, createdAt = day(0))

    private fun records(
        vehicle: Vehicle = this.vehicle,
        fuel: List<FuelEntry> = emptyList(),
        services: List<ServiceEntry> = emptyList(),
        rules: List<ServiceRule>,
    ) = VehicleRecords(vehicle, fuel, services, rules)

    private fun rule(
        kind: ServiceKind,
        kilometers: Double? = null,
        months: Int? = null,
    ) = ServiceRule(vehicleId = testVehicleId, kind = kind, kilometerInterval = kilometers, monthInterval = months)

    @Test
    fun `fires at 9 on the due day when it is still ahead`() {
        assertEquals(hour(9, 10), ReminderPlan.fireDate(day(10), hour(15, 2), utc))
    }

    @Test
    fun `an overdue reminder fires at the next 9`() {
        assertEquals(hour(9, 5), ReminderPlan.fireDate(day(1), hour(7, 5), utc))
        assertEquals(hour(9, 6), ReminderPlan.fireDate(day(1), hour(10, 5), utc))
    }

    @Test
    fun `a calendar rule is due months after the last service of its kind`() {
        val rule = rule(ServiceKind.OIL_CHANGE, months = 6)

        val planned = ReminderPlan.reminders(listOf(records(rules = listOf(rule))), day(1), utc)

        val reminder = planned.first()
        assertEquals(1, planned.size)
        assertEquals(rule.id, reminder.id)
        assertEquals(Trigger.CALENDAR, reminder.due.forecast.trigger)
        assertEquals(assertNotNull(reminder.due.forecast.dueDate).atHour(9, utc), reminder.fireDate)
        assertFalse(reminder.isOverdue)
    }

    @Test
    fun `a reminder past its due date is flagged as overdue`() {
        val planned = ReminderPlan.reminders(listOf(records(rules = listOf(rule(ServiceKind.INSPECTION, months = 1)))), day(90), utc)

        assertTrue(planned.first().isOverdue)
    }

    @Test
    fun `an overdue reminder repeats daily for a week, then weekly`() {
        assertEquals(hour(9, 4), ReminderPlan.overdueFireDate(day(0), hour(10, 3), utc))
        assertEquals(hour(9, 7), ReminderPlan.overdueFireDate(day(0), hour(10, 6), utc))
        assertEquals(hour(9, 14), ReminderPlan.overdueFireDate(day(0), hour(10, 7), utc))
        assertEquals(hour(9, 14), ReminderPlan.overdueFireDate(day(0), hour(8, 14), utc))
        assertEquals(hour(9, 21), ReminderPlan.overdueFireDate(day(0), hour(10, 14), utc))
    }

    @Test
    fun `a passed pace estimate is not repeated until the service is really overdue`() {
        val fuel = listOf(full(10, 10_500.0, 30.0), full(20, 11_000.0, 30.0))

        assertTrue(
            ReminderPlan.reminders(listOf(records(fuel = fuel, rules = listOf(rule(ServiceKind.OIL_CHANGE, 5_000.0)))), day(200), utc).isEmpty(),
        )
    }

    @Test
    fun `a km-only rule without a pace is notified once the odometer reaches it`() {
        val vehicle = Vehicle(id = testVehicleId, name = "CB", initialOdometer = 10_000.0, createdAt = day(5))

        val planned =
            ReminderPlan.reminders(
                listOf(records(vehicle = vehicle, fuel = listOf(full(5, 11_200.0, 10.0)), rules = listOf(rule(ServiceKind.CHAIN, 1_000.0)))),
                hour(10, 5),
                utc,
            )

        val reminder = planned.first()
        assertTrue(reminder.isOverdue)
        assertEquals(Trigger.DISTANCE, reminder.status.overdueBy)
        assertEquals(11_200.0, reminder.currentOdometer)
        assertEquals(hour(9, 6), reminder.fireDate)
    }

    @Test
    fun `overdue by km counts from the first reading at or past the due km`() {
        val fuel = listOf(full(10, 10_600.0, 10.0), full(20, 11_100.0, 10.0), full(30, 11_500.0, 10.0))

        val reminder =
            ReminderPlan.reminders(listOf(records(fuel = fuel, rules = listOf(rule(ServiceKind.CHAIN, 1_000.0)))), hour(10, 40), utc).first()

        assertEquals(
            day(20),
            ReminderPlan.overdueSince(
                reminder.due,
                reminder.status,
                listOf(sample(0, 10_000.0), sample(10, 10_600.0), sample(20, 11_100.0), sample(30, 11_500.0)),
                day(40),
                utc,
            ),
        )
        assertEquals(hour(9, 41), reminder.fireDate)
    }

    @Test
    fun `a passed pace estimate still reminds on the calendar due day`() {
        val fuel = listOf(full(10, 10_500.0, 30.0), full(20, 11_000.0, 30.0))

        val reminder =
            ReminderPlan.reminders(listOf(records(fuel = fuel, rules = listOf(rule(ServiceKind.OIL_CHANGE, 5_000.0, 12)))), day(200), utc).first()

        assertFalse(reminder.isOverdue)
        assertEquals(hour(9, 365), reminder.fireDate)
    }

    @Test
    fun `overdue both ways counts from the earlier date`() {
        val fuel = listOf(full(10, 11_200.0, 10.0), full(50, 11_400.0, 10.0))

        val reminder =
            ReminderPlan.reminders(listOf(records(fuel = fuel, rules = listOf(rule(ServiceKind.OIL_CHANGE, 1_000.0, 1)))), hour(10, 60), utc).first()

        assertEquals(Trigger.CALENDAR, reminder.status.overdueBy)
        assertEquals(
            day(10),
            ReminderPlan.overdueSince(
                reminder.due,
                reminder.status,
                listOf(sample(0, 10_000.0), sample(10, 11_200.0), sample(50, 11_400.0)),
                hour(10, 60),
                utc,
            ),
        )
    }

    @Test
    fun `without a reading past the due km, overdue counts from the latest reading`() {
        val due =
            ServiceDue(
                rule = rule(ServiceKind.CHAIN, 1_000.0),
                forecast = MaintenanceForecast(null, Trigger.DISTANCE, 11_000.0, null),
                since = sample(30, 10_000.0),
            )
        val status = MaintenanceStatus(MaintenanceStatus.Level.OVERDUE, 1.0, -200.0, null, Trigger.DISTANCE)

        assertEquals(day(20), ReminderPlan.overdueSince(due, status, listOf(sample(0, 10_000.0), sample(20, 11_200.0)), day(40), utc))
        assertEquals(day(40), ReminderPlan.overdueSince(due, status, emptyList(), day(40), utc))
    }

    @Test
    fun `a distance rule without a km pace yet has nothing to schedule`() {
        assertTrue(ReminderPlan.reminders(listOf(records(rules = listOf(rule(ServiceKind.TIRES, 20_000.0)))), day(1), utc).isEmpty())
    }

    @Test
    fun `archived vehicles get no reminders`() {
        val archived = vehicle.copy(isArchived = true)

        assertTrue(
            ReminderPlan.reminders(listOf(records(vehicle = archived, rules = listOf(rule(ServiceKind.OIL_CHANGE, months = 6)))), day(1), utc).isEmpty(),
        )
    }

    @Test
    fun `reminders come out ordered by fire date`() {
        val later = rule(ServiceKind.OIL_CHANGE, months = 12)
        val sooner = rule(ServiceKind.INSPECTION, months = 3)

        val planned = ReminderPlan.reminders(listOf(records(rules = listOf(later, sooner))), day(1), utc)

        assertEquals(listOf(sooner.id, later.id), planned.map { it.id })
    }

    @Test
    fun `due reminders cover every future stage, include the driver license and skip archived or unknown vehicles`() {
        val archived = Vehicle(name = "Old", createdAt = day(0), isArchived = true)
        val ipva = dueItem(DueKind.IPVA, LocalDate.parse("2001-03-01"))
        val license = dueItem(DueKind.CNH, LocalDate.parse("2001-06-01"), vehicleId = null)
        val items =
            listOf(
                ipva,
                license,
                dueItem(DueKind.SEGURO, LocalDate.parse("2001-03-01"), vehicleId = archived.id),
                dueItem(DueKind.SEGURO, LocalDate.parse("2001-03-01"), vehicleId = UUID.randomUUID()),
                dueItem(DueKind.LICENCIAMENTO, LocalDate.parse("2001-03-01"), paidAt = day(1)),
            )

        val planned = ReminderPlan.dueReminders(items, listOf(vehicle, archived), day(1), utc)

        assertEquals(setOf(ipva.id, license.id), planned.map { it.item.id }.toSet())
        assertEquals(7, planned.count { it.item.id == ipva.id })
        assertEquals(6, planned.count { it.item.id == license.id })
        assertEquals(vehicle, planned.first { it.item.id == ipva.id }.vehicle)
        assertEquals(null, planned.first { it.item.id == license.id }.vehicle)
        assertEquals(planned.sortedBy { it.fireDate }, planned)
        assertEquals(DueSchedule.reminderId(ipva.id, DueStage.DaysBefore(15)), planned.first().id)
        assertEquals(planned.size, planned.map { it.id }.toSet().size)
    }

    @Test
    fun `fine reminders fire 7 days before and on the due day while unpaid`() {
        val dueDate = LocalDate.parse("2001-02-01")
        val unpaid = expense(ExpenseCategory.MULTA, "195.23", fine = FineDetails(dueDate = dueDate, isPaid = false))
        val paid = expense(ExpenseCategory.MULTA, "195.23", fine = FineDetails(dueDate = dueDate))
        val undated = expense(ExpenseCategory.MULTA, "195.23", fine = FineDetails(isPaid = false))
        val archived = Vehicle(name = "Old", createdAt = day(0), isArchived = true)
        val elsewhere = unpaid.copy(id = UUID.randomUUID(), vehicleId = archived.id)

        val planned = ReminderPlan.fineReminders(listOf(unpaid, paid, undated, elsewhere), listOf(vehicle, archived), day(1), utc)

        assertEquals(listOf(DueStage.DaysBefore(7), DueStage.DueToday), planned.map { it.stage })
        assertEquals(listOf(Instant.parse("2001-01-25T09:00:00Z"), Instant.parse("2001-02-01T09:00:00Z")), planned.map { it.fireDate })
        assertTrue(planned.all { it.expense.id == unpaid.id && it.dueDate == dueDate })

        val later = ReminderPlan.fineReminders(listOf(unpaid), listOf(vehicle), Instant.parse("2001-01-26T00:00:00Z"), utc)
        assertEquals(listOf(DueStage.DueToday), later.map { it.stage })
        assertFalse(ReminderPlan.fineReminders(listOf(unpaid), listOf(vehicle), Instant.parse("2001-02-02T00:00:00Z"), utc).isNotEmpty())
    }
}
