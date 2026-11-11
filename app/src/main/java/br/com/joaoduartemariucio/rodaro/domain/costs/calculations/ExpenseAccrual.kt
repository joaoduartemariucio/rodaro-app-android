package br.com.joaoduartemariucio.rodaro.domain.costs.calculations

import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object ExpenseAccrual {
    private const val DEFAULT_COVERAGE_MONTHS = 12L

    fun coverage(
        expense: Expense,
        zone: ZoneId,
    ): ClosedRange<LocalDate>? {
        if (!expense.category.isFixed) return null
        val start = expense.coverageStart
        val end = expense.coverageEnd
        if (start != null && end != null && !end.isBefore(start)) return start..end

        val anchor = start ?: expense.date.atZone(zone).toLocalDate()
        return if (expense.category == ExpenseCategory.IPVA) {
            LocalDate.of(anchor.year, 1, 1)..LocalDate.of(anchor.year, 12, 31)
        } else {
            anchor..anchor.plusMonths(DEFAULT_COVERAGE_MONTHS).minusDays(1)
        }
    }

    fun accrued(
        expense: Expense,
        start: LocalDate,
        endExclusive: LocalDate,
        zone: ZoneId,
    ): BigDecimal {
        if (!expense.isPaid || !endExclusive.isAfter(start)) return BigDecimal.ZERO

        val coverage = coverage(expense, zone)
        if (coverage == null) {
            val day = expense.date.atZone(zone).toLocalDate()
            return if (!day.isBefore(start) && day.isBefore(endExclusive)) expense.amount else BigDecimal.ZERO
        }

        val coverageEnd = coverage.endInclusive.plusDays(1)
        val overlapStart = maxOf(coverage.start, start)
        val overlapEnd = minOf(coverageEnd, endExclusive)
        val overlap = ChronoUnit.DAYS.between(overlapStart, overlapEnd)
        if (overlap <= 0) return BigDecimal.ZERO

        val coverageDays = ChronoUnit.DAYS.between(coverage.start, coverageEnd)
        return expense.amount
            .multiply(BigDecimal.valueOf(overlap))
            .divide(BigDecimal.valueOf(coverageDays), MathContext.DECIMAL128)
    }
}
