package br.com.joaoduartemariucio.rodaro.domain.reminders.calculations

import br.com.joaoduartemariucio.rodaro.domain.dues.calculations.DueSchedule
import br.com.joaoduartemariucio.rodaro.domain.dues.calculations.DueStage
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueItem
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import java.time.Instant
import java.util.UUID

data class PlannedDueReminder(
    val item: DueItem,
    val vehicle: Vehicle?,
    val stage: DueStage,
    val fireDate: Instant,
) {
    val id: UUID get() = DueSchedule.reminderId(item.id, stage)
}
