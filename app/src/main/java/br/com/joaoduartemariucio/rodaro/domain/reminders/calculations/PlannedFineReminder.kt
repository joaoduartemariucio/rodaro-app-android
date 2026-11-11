package br.com.joaoduartemariucio.rodaro.domain.reminders.calculations

import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import br.com.joaoduartemariucio.rodaro.domain.dues.calculations.DueSchedule
import br.com.joaoduartemariucio.rodaro.domain.dues.calculations.DueStage
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PlannedFineReminder(
    val expense: Expense,
    val vehicle: Vehicle,
    val dueDate: LocalDate,
    val stage: DueStage,
    val fireDate: Instant,
) {
    val id: UUID get() = DueSchedule.reminderId(expense.id, stage)
}
