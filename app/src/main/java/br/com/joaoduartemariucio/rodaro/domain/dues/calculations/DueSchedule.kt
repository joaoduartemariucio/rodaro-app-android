package br.com.joaoduartemariucio.rodaro.domain.dues.calculations

import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueItem
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueKind
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.MaintenanceStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

object DueSchedule {
    const val HOUR_OF_DAY = 9
    const val OVERDUE_WEEKS = 4
    private const val DAYS_PER_WEEK = 7

    fun leadDays(kind: DueKind): List<Int> =
        when (kind) {
            DueKind.IPVA -> listOf(15, 3, 0)
            DueKind.LICENCIAMENTO -> listOf(30, 7, 0)
            DueKind.SEGURO -> listOf(30, 7)
            DueKind.CNH -> listOf(60, 30)
        }

    fun level(
        item: DueItem,
        today: LocalDate,
    ): MaintenanceStatus.Level? {
        if (item.isPaid) return null
        val firstLead = leadDays(item.kind).maxOrNull() ?: 0
        return when {
            today.isAfter(item.dueDate) -> MaintenanceStatus.Level.OVERDUE
            !today.isBefore(item.dueDate.minusDays(firstLead.toLong())) -> MaintenanceStatus.Level.DUE_SOON
            else -> MaintenanceStatus.Level.ON_TRACK
        }
    }

    fun stages(
        item: DueItem,
        now: Instant,
        zone: ZoneId,
    ): List<Pair<DueStage, Instant>> {
        if (item.isPaid) return emptyList()
        return stages(item.dueDate, leadDays(item.kind), OVERDUE_WEEKS, now, zone)
    }

    fun stages(
        dueDate: LocalDate,
        leadDays: List<Int>,
        overdueWeeks: Int,
        now: Instant,
        zone: ZoneId,
    ): List<Pair<DueStage, Instant>> {
        val before =
            leadDays.map { days ->
                (if (days == 0) DueStage.DueToday else DueStage.DaysBefore(days)) to
                    dueDate.minusDays(days.toLong())
            }
        val overdue =
            (1..overdueWeeks).map { week ->
                val days = week * DAYS_PER_WEEK
                DueStage.Overdue(days) to dueDate.plusDays(days.toLong())
            }
        return (before + overdue)
            .map { (stage, day) -> stage to day.atTime(HOUR_OF_DAY, 0).atZone(zone).toInstant() }
            .filter { (_, fireDate) -> fireDate > now }
            .sortedBy { (_, fireDate) -> fireDate }
    }

    fun nextFireDate(
        item: DueItem,
        now: Instant,
        zone: ZoneId,
    ): Instant? = stages(item, now, zone).firstOrNull()?.second

    fun reminderId(
        recordId: UUID,
        stage: DueStage,
    ): UUID = UUID.nameUUIDFromBytes("$recordId:${stage.key}".toByteArray())

    fun nextYear(
        item: DueItem,
        createdAt: Instant,
    ): DueItem? {
        if (item.kind == DueKind.CNH) return null
        return item.copy(
            id = UUID.randomUUID(),
            dueDate = item.dueDate.plusYears(1),
            amount = null,
            paidAt = null,
            createdAt = createdAt,
        )
    }

    fun expense(
        item: DueItem,
        paidAt: Instant,
        amount: BigDecimal,
        zone: ZoneId,
    ): Expense? {
        val vehicleId = item.vehicleId ?: return null
        val category =
            when (item.kind) {
                DueKind.IPVA -> ExpenseCategory.IPVA
                DueKind.LICENCIAMENTO -> ExpenseCategory.LICENCIAMENTO
                DueKind.SEGURO -> ExpenseCategory.SEGURO
                DueKind.CNH -> return null
            }
        val year = item.dueDate.year
        return Expense(
            id = item.id,
            vehicleId = vehicleId,
            category = category,
            date = paidAt,
            amount = amount,
            note = item.note,
            coverageStart = if (category == ExpenseCategory.IPVA) LocalDate.of(year, 1, 1) else null,
            coverageEnd = if (category == ExpenseCategory.IPVA) LocalDate.of(year, 12, 31) else null,
            createdAt = paidAt,
        )
    }
}
