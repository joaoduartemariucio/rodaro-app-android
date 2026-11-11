package br.com.joaoduartemariucio.rodaro.presentation.reminders

import android.util.Log
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.domain.common.services.DateProvider
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.costs.repositories.ExpenseRepository
import br.com.joaoduartemariucio.rodaro.domain.dues.calculations.DueStage
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueKind
import br.com.joaoduartemariucio.rodaro.domain.dues.repositories.DueItemRepository
import br.com.joaoduartemariucio.rodaro.domain.fueling.repositories.FuelEntryRepository
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.MaintenanceForecast
import br.com.joaoduartemariucio.rodaro.domain.maintenance.repositories.ServiceRepository
import br.com.joaoduartemariucio.rodaro.domain.preferences.repositories.PreferencesRepository
import br.com.joaoduartemariucio.rodaro.domain.reminders.calculations.PlannedDueReminder
import br.com.joaoduartemariucio.rodaro.domain.reminders.calculations.PlannedFineReminder
import br.com.joaoduartemariucio.rodaro.domain.reminders.calculations.PlannedReminder
import br.com.joaoduartemariucio.rodaro.domain.reminders.calculations.ReminderPlan
import br.com.joaoduartemariucio.rodaro.domain.reminders.services.Reminder
import br.com.joaoduartemariucio.rodaro.domain.reminders.services.ReminderScheduler
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleRecords
import br.com.joaoduartemariucio.rodaro.domain.vehicles.repositories.VehicleRepository
import br.com.joaoduartemariucio.rodaro.presentation.common.Localizer
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedFullDate
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedKilometers
import br.com.joaoduartemariucio.rodaro.presentation.common.label
import kotlin.coroutines.cancellation.CancellationException

class ServiceReminderSync(
    private val vehicleRepository: VehicleRepository,
    private val fuelEntryRepository: FuelEntryRepository,
    private val serviceRepository: ServiceRepository,
    private val dueItemRepository: DueItemRepository,
    private val expenseRepository: ExpenseRepository,
    private val scheduler: ReminderScheduler,
    private val dateProvider: DateProvider,
    private val localizer: Localizer,
    private val preferences: PreferencesRepository,
) {
    suspend fun sync() {
        try {
            if (!preferences.get().remindersEnabled) {
                for (id in scheduler.pendingIdentifiers()) scheduler.cancel(id)
                return
            }

            val now = dateProvider.now
            val zone = dateProvider.zone
            val vehicles = vehicleRepository.all(includingArchived = false)
            val planned = ReminderPlan.reminders(records(vehicles), now, zone)
            val dues = ReminderPlan.dueReminders(dueItemRepository.all(), vehicles, now, zone)
            val fines = ReminderPlan.fineReminders(vehicles.flatMap { expenseRepository.expenses(it.id) }, vehicles, now, zone)
            val reminders = planned.map(::reminder) + dues.map(::reminder) + fines.map(::reminder)

            val wanted = reminders.map { it.id }.toSet()
            for (id in scheduler.pendingIdentifiers()) {
                if (id !in wanted) scheduler.cancel(id)
            }

            if (reminders.isEmpty() || !scheduler.requestAuthorization()) return
            for (reminder in reminders) {
                scheduler.schedule(reminder)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Could not sync reminders", error)
        }
    }

    private suspend fun records(vehicles: List<Vehicle>): List<VehicleRecords> =
        vehicles.map { vehicle ->
            VehicleRecords(
                vehicle = vehicle,
                fuel = fuelEntryRepository.entries(vehicle.id),
                services = serviceRepository.entries(vehicle.id),
                rules = serviceRepository.rules(vehicle.id),
            )
        }

    fun reminder(planned: PlannedReminder): Reminder {
        val forecast = planned.due.forecast
        val status = planned.status
        val zone = dateProvider.zone

        val lastReading = planned.currentOdometer.formattedKilometers()
        val dueOdometer = forecast.dueOdometer
        val calendarDueDate = status.calendarDueDate
        val dueDate = forecast.dueDate

        val body =
            when {
                status.overdueBy == MaintenanceForecast.Trigger.DISTANCE && dueOdometer != null -> {
                    localizer.string(R.string.reminder_was_due_at, dueOdometer.formattedKilometers(), lastReading)
                }

                status.overdueBy == MaintenanceForecast.Trigger.CALENDAR && calendarDueDate != null -> {
                    localizer.string(R.string.reminder_overdue_since, calendarDueDate.formattedFullDate(zone))
                }

                dueDate != null && forecast.trigger == MaintenanceForecast.Trigger.DISTANCE && dueOdometer != null -> {
                    localizer.string(
                        R.string.reminder_due_at_around,
                        dueOdometer.formattedKilometers(),
                        dueDate.formattedFullDate(zone),
                    )
                }

                dueDate != null -> {
                    localizer.string(R.string.reminder_due_on, dueDate.formattedFullDate(zone))
                }

                else -> {
                    localizer.string(R.string.reminder_due_at, (dueOdometer ?: 0.0).formattedKilometers())
                }
            }

        return Reminder(
            id = planned.id,
            title = "${planned.vehicle.name}: ${localizer.string(planned.due.rule.kind.label)}",
            body = body,
            fireDate = planned.fireDate,
        )
    }

    fun reminder(planned: PlannedDueReminder): Reminder {
        val item = planned.item
        val zone = dateProvider.zone
        val dueDate =
            item.dueDate
                .atStartOfDay(zone)
                .toInstant()
                .formattedFullDate(zone)
        val installment =
            item.installmentNumber?.let { number -> item.installmentCount?.let { count -> " $number/$count" } }.orEmpty()
        val kind = localizer.string(item.kind.label) + installment
        val body =
            when (val stage = planned.stage) {
                is DueStage.DaysBefore -> localizer.plural(R.plurals.reminder_due_in_days, stage.days, stage.days, dueDate)
                DueStage.DueToday -> localizer.string(R.string.reminder_due_today)
                is DueStage.Overdue -> localizer.string(R.string.reminder_due_overdue_since, dueDate)
            }
        val vehicle = planned.vehicle
        return Reminder(
            id = planned.id,
            title = if (vehicle == null) kind else "${vehicle.name}: $kind",
            body = if (item.kind == DueKind.CNH) "$body ${localizer.string(R.string.reminder_cnh_note)}" else body,
            fireDate = planned.fireDate,
        )
    }

    fun reminder(planned: PlannedFineReminder): Reminder {
        val zone = dateProvider.zone
        val dueDate =
            planned.dueDate
                .atStartOfDay(zone)
                .toInstant()
                .formattedFullDate(zone)
        return Reminder(
            id = planned.id,
            title = "${planned.vehicle.name}: ${localizer.string(ExpenseCategory.MULTA.label)}",
            body =
                if (planned.stage == DueStage.DueToday) {
                    localizer.string(R.string.reminder_fine_due_today)
                } else {
                    localizer.string(R.string.reminder_fine_due, dueDate)
                },
            fireDate = planned.fireDate,
        )
    }

    private companion object {
        const val TAG = "Rodaro.Reminders"
    }
}
