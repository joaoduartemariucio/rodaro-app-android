package br.com.joaoduartemariucio.rodaro.calculations

import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.dues.calculations.DueSchedule
import br.com.joaoduartemariucio.rodaro.domain.dues.calculations.DueStage
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueKind
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.MaintenanceStatus
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DueScheduleTests {
    private val due = LocalDate.parse("2001-03-01")

    private fun at(text: String): Instant = Instant.parse(text)

    @Test
    fun `each kind has its own lead times`() {
        assertEquals(listOf(15, 3, 0), DueSchedule.leadDays(DueKind.IPVA))
        assertEquals(listOf(30, 7, 0), DueSchedule.leadDays(DueKind.LICENCIAMENTO))
        assertEquals(listOf(30, 7), DueSchedule.leadDays(DueKind.SEGURO))
        assertEquals(listOf(60, 30), DueSchedule.leadDays(DueKind.CNH))
    }

    @Test
    fun `every stage fires at 9 - leads, the day itself and four weekly overdue reminders`() {
        val stages = DueSchedule.stages(dueItem(DueKind.IPVA, due), day(0), utc)

        assertEquals(
            listOf(
                DueStage.DaysBefore(15) to at("2001-02-14T09:00:00Z"),
                DueStage.DaysBefore(3) to at("2001-02-26T09:00:00Z"),
                DueStage.DueToday to at("2001-03-01T09:00:00Z"),
                DueStage.Overdue(7) to at("2001-03-08T09:00:00Z"),
                DueStage.Overdue(14) to at("2001-03-15T09:00:00Z"),
                DueStage.Overdue(21) to at("2001-03-22T09:00:00Z"),
                DueStage.Overdue(28) to at("2001-03-29T09:00:00Z"),
            ),
            stages,
        )
    }

    @Test
    fun `past stages are skipped and the next fire date is the first one ahead`() {
        val item = dueItem(DueKind.IPVA, due)

        assertEquals(at("2001-02-26T09:00:00Z"), DueSchedule.nextFireDate(item, at("2001-02-20T10:00:00Z"), utc))
        assertEquals(at("2001-03-01T09:00:00Z"), DueSchedule.nextFireDate(item, at("2001-02-26T09:00:00Z"), utc))
        assertNull(DueSchedule.nextFireDate(item, at("2001-03-29T09:00:00Z"), utc))
    }

    @Test
    fun `insurance has no reminder on the day itself`() {
        val stages = DueSchedule.stages(dueItem(DueKind.SEGURO, due), day(0), utc).map { it.first }

        assertTrue(DueStage.DueToday !in stages)
        assertEquals(DueStage.DaysBefore(30), stages.first())
    }

    @Test
    fun `a paid item has no reminders and no level`() {
        val paid = dueItem(DueKind.IPVA, due, paidAt = day(20))

        assertTrue(DueSchedule.stages(paid, day(0), utc).isEmpty())
        assertNull(DueSchedule.nextFireDate(paid, day(0), utc))
        assertNull(DueSchedule.level(paid, LocalDate.parse("2001-04-01")))
    }

    @Test
    fun `the level follows the first lead time and the due date`() {
        val item = dueItem(DueKind.IPVA, due)

        assertEquals(MaintenanceStatus.Level.ON_TRACK, DueSchedule.level(item, LocalDate.parse("2001-02-13")))
        assertEquals(MaintenanceStatus.Level.DUE_SOON, DueSchedule.level(item, LocalDate.parse("2001-02-14")))
        assertEquals(MaintenanceStatus.Level.DUE_SOON, DueSchedule.level(item, due))
        assertEquals(MaintenanceStatus.Level.OVERDUE, DueSchedule.level(item, LocalDate.parse("2001-03-02")))
    }

    @Test
    fun `next year keeps the installment, clears amount and payment and handles February 29`() {
        val leap = dueItem(DueKind.IPVA, LocalDate.parse("2004-02-29"), amount = "812.45", paidAt = day(10)).copy(installmentNumber = 1, installmentCount = 3)

        val next = assertNotNull(DueSchedule.nextYear(leap, day(40)))

        assertEquals(LocalDate.parse("2005-02-28"), next.dueDate)
        assertNull(next.amount)
        assertNull(next.paidAt)
        assertEquals(1, next.installmentNumber)
        assertEquals(3, next.installmentCount)
        assertEquals(day(40), next.createdAt)
        assertNotEquals(leap.id, next.id)
        assertEquals(LocalDate.parse("2002-03-01"), DueSchedule.nextYear(dueItem(DueKind.LICENCIAMENTO, due), day(0))?.dueDate)
        assertEquals(LocalDate.parse("2002-03-01"), DueSchedule.nextYear(dueItem(DueKind.SEGURO, due), day(0))?.dueDate)
        assertNull(DueSchedule.nextYear(dueItem(DueKind.CNH, due, vehicleId = null), day(0)))
    }

    @Test
    fun `paying becomes an expense with the same id, and IPVA covers the calendar year of its due date`() {
        val ipva = dueItem(DueKind.IPVA, LocalDate.parse("2002-01-20"))

        val expense = assertNotNull(DueSchedule.expense(ipva, day(15), BigDecimal("812.45"), utc))

        assertEquals(ipva.id, expense.id)
        assertEquals(ExpenseCategory.IPVA, expense.category)
        assertEquals(LocalDate.parse("2002-01-01"), expense.coverageStart)
        assertEquals(LocalDate.parse("2002-12-31"), expense.coverageEnd)
        assertEquals(day(15), expense.date)
        assertDecimal("812.45", expense.amount)

        val licensing = assertNotNull(DueSchedule.expense(dueItem(DueKind.LICENCIAMENTO, due), day(15), BigDecimal.TEN, utc))
        assertEquals(ExpenseCategory.LICENCIAMENTO, licensing.category)
        assertNull(licensing.coverageStart)
        assertEquals(ExpenseCategory.SEGURO, DueSchedule.expense(dueItem(DueKind.SEGURO, due), day(15), BigDecimal.TEN, utc)?.category)
        assertNull(DueSchedule.expense(dueItem(DueKind.CNH, due, vehicleId = null), day(15), BigDecimal.TEN, utc))
        assertNull(DueSchedule.expense(dueItem(DueKind.CNH, due), day(15), BigDecimal.TEN, utc))
    }

    @Test
    fun `reminder ids are stable per item and stage`() {
        val item = dueItem(DueKind.IPVA, due)

        assertEquals(DueSchedule.reminderId(item.id, DueStage.DueToday), DueSchedule.reminderId(item.id, DueStage.DueToday))
        assertNotEquals(DueSchedule.reminderId(item.id, DueStage.DueToday), DueSchedule.reminderId(item.id, DueStage.Overdue(7)))
        assertEquals("before-3", DueStage.DaysBefore(3).key)
    }
}
