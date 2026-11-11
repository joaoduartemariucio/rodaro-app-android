package br.com.joaoduartemariucio.rodaro.domain.reminders.calculations

import br.com.joaoduartemariucio.rodaro.domain.common.calculations.atHour
import br.com.joaoduartemariucio.rodaro.domain.common.calculations.daysBetween
import br.com.joaoduartemariucio.rodaro.domain.common.calculations.plusDays
import br.com.joaoduartemariucio.rodaro.domain.common.calculations.startOfDay
import br.com.joaoduartemariucio.rodaro.domain.common.entities.sortedChronologically
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.dues.calculations.DueSchedule
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueItem
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.Maintenance
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.MaintenanceStatus
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.ServiceDue
import br.com.joaoduartemariucio.rodaro.domain.vehicles.calculations.Odometer
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.OdometerSample
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleRecords
import java.time.Instant
import java.time.ZoneId

object ReminderPlan {
    const val HOUR_OF_DAY = 9
    const val DAILY_OVERDUE_DAYS = 7
    const val OVERDUE_REPEAT_DAYS = 7
    val FINE_LEAD_DAYS = listOf(7, 0)

    fun reminders(
        records: List<VehicleRecords>,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<PlannedReminder> =
        records
            .filter { !it.vehicle.isArchived }
            .flatMap { reminders(it, now, zone) }
            .sortedWith(compareBy<PlannedReminder>({ it.fireDate }, { it.id.toString() }))

    private fun reminders(
        record: VehicleRecords,
        now: Instant,
        zone: ZoneId,
    ): List<PlannedReminder> {
        val history = Odometer.samples(record.vehicle, record.fuel, record.services)
        val current = Odometer.current(history) ?: record.vehicle.initialOdometer

        return record.rules.mapNotNull { rule ->
            val due = Maintenance.due(rule, record.vehicle, record.services, history, zone) ?: return@mapNotNull null
            val status = Maintenance.status(due, current, now, zone)
            val fireDate = fireDate(due, status, history, now, zone) ?: return@mapNotNull null

            PlannedReminder(
                vehicle = record.vehicle,
                due = due,
                status = status,
                currentOdometer = current,
                fireDate = fireDate,
            )
        }
    }

    private fun fireDate(
        due: ServiceDue,
        status: MaintenanceStatus,
        history: List<OdometerSample>,
        now: Instant,
        zone: ZoneId,
    ): Instant? {
        if (status.level == MaintenanceStatus.Level.OVERDUE) {
            val since = overdueSince(due, status, history, now, zone)
            return overdueFireDate(since, now, zone)
        }
        return listOfNotNull(due.forecast.dueDate, status.calendarDueDate)
            .map { it.atHour(HOUR_OF_DAY, zone) }
            .filter { it > now }
            .minOrNull()
    }

    fun dueReminders(
        items: List<DueItem>,
        vehicles: List<Vehicle>,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<PlannedDueReminder> {
        val active = vehicles.filter { !it.isArchived }.associateBy { it.id }
        return items
            .flatMap { item ->
                val vehicleId = item.vehicleId
                val vehicle = vehicleId?.let { active[it] ?: return@flatMap emptyList() }
                DueSchedule.stages(item, now, zone).map { (stage, fireDate) -> PlannedDueReminder(item, vehicle, stage, fireDate) }
            }.sortedWith(compareBy<PlannedDueReminder>({ it.fireDate }, { it.id.toString() }))
    }

    fun fineReminders(
        expenses: List<Expense>,
        vehicles: List<Vehicle>,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<PlannedFineReminder> {
        val active = vehicles.filter { !it.isArchived }.associateBy { it.id }
        return expenses
            .filter { it.category == ExpenseCategory.MULTA && !it.isPaid }
            .flatMap { expense ->
                val vehicle = active[expense.vehicleId] ?: return@flatMap emptyList()
                val dueDate = expense.fine?.dueDate ?: return@flatMap emptyList()
                DueSchedule
                    .stages(dueDate, FINE_LEAD_DAYS, 0, now, zone)
                    .map { (stage, fireDate) -> PlannedFineReminder(expense, vehicle, dueDate, stage, fireDate) }
            }.sortedWith(compareBy<PlannedFineReminder>({ it.fireDate }, { it.id.toString() }))
    }

    fun overdueSince(
        due: ServiceDue,
        status: MaintenanceStatus,
        history: List<OdometerSample>,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Instant {
        val sorted = history.sortedChronologically()
        val candidates = mutableListOf<Instant>()

        val calendarDueDate = status.calendarDueDate
        if (calendarDueDate != null && calendarDueDate < now.startOfDay(zone)) {
            candidates += calendarDueDate
        }
        val remaining = status.remainingKilometers
        val dueOdometer = due.forecast.dueOdometer
        if (remaining != null && remaining <= 0 && dueOdometer != null) {
            val reached = sorted.firstOrNull { it.date >= due.since.date && it.odometer >= dueOdometer }
            candidates += reached?.date ?: sorted.lastOrNull()?.date ?: now
        }
        return candidates.minOrNull() ?: now
    }

    fun overdueFireDate(
        since: Instant,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Instant {
        val next = fireDate(since, now, zone)
        val elapsed = daysBetween(since.startOfDay(zone), next.startOfDay(zone), zone)
        if (elapsed <= DAILY_OVERDUE_DAYS) return next

        val offset = (OVERDUE_REPEAT_DAYS - elapsed % OVERDUE_REPEAT_DAYS) % OVERDUE_REPEAT_DAYS
        return next.plusDays(offset.toLong(), zone)
    }

    fun fireDate(
        dueDate: Instant,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Instant {
        val onDueDay = dueDate.atHour(HOUR_OF_DAY, zone)
        if (onDueDay > now) return onDueDay

        val today = now.atHour(HOUR_OF_DAY, zone)
        return if (today > now) today else today.plusDays(1, zone)
    }
}
