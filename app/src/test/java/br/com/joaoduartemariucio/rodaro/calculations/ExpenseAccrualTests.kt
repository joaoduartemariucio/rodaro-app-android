package br.com.joaoduartemariucio.rodaro.calculations

import br.com.joaoduartemariucio.rodaro.domain.costs.calculations.ExpenseAccrual
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDetails
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExpenseAccrualTests {
    private fun date(text: String): LocalDate = LocalDate.parse(text)

    @Test
    fun `IPVA covers the calendar year of its date, leap years included`() {
        val ipva = expense(ExpenseCategory.IPVA, "366", dayOffset = 40)
        val leap = ipva.copy(date = Instant.parse("2004-06-01T12:00:00Z"))

        assertEquals(date("2001-01-01")..date("2001-12-31"), ExpenseAccrual.coverage(ipva, utc))
        assertEquals(date("2004-01-01")..date("2004-12-31"), ExpenseAccrual.coverage(leap, utc))
        assertDecimal("31", ExpenseAccrual.accrued(leap, date("2004-01-01"), date("2004-02-01"), utc))
    }

    @Test
    fun `licensing and insurance cover twelve months from their date`() {
        val licensing = expense(ExpenseCategory.LICENCIAMENTO, "100", dayOffset = 73)
        val insurance = expense(ExpenseCategory.SEGURO, "100", dayOffset = 0)

        assertEquals(date("2001-03-15")..date("2002-03-14"), ExpenseAccrual.coverage(licensing, utc))
        assertEquals(date("2001-01-01")..date("2001-12-31"), ExpenseAccrual.coverage(insurance, utc))
    }

    @Test
    fun `an explicit coverage wins, and a start alone anchors the default length`() {
        val policy = expense(ExpenseCategory.SEGURO, "100", coverageStart = date("2001-02-01"), coverageEnd = date("2001-07-31"))
        val startOnly = expense(ExpenseCategory.SEGURO, "100", coverageStart = date("2001-02-01"))
        val reversed = expense(ExpenseCategory.SEGURO, "100", coverageStart = date("2001-05-01"), coverageEnd = date("2001-02-01"))

        assertEquals(date("2001-02-01")..date("2001-07-31"), ExpenseAccrual.coverage(policy, utc))
        assertEquals(date("2001-02-01")..date("2002-01-31"), ExpenseAccrual.coverage(startOnly, utc))
        assertEquals(date("2001-05-01")..date("2002-04-30"), ExpenseAccrual.coverage(reversed, utc))
    }

    @Test
    fun `a fixed expense accrues by the days of the window it covers`() {
        val insurance = expense(ExpenseCategory.SEGURO, "365")

        assertDecimal("181", ExpenseAccrual.accrued(insurance, date("2001-01-01"), date("2001-07-01"), utc))
        assertDecimal("365", ExpenseAccrual.accrued(insurance, date("2000-06-01"), date("2003-01-01"), utc))
        assertDecimal("0", ExpenseAccrual.accrued(insurance, date("2002-01-01"), date("2002-07-01"), utc))
        assertDecimal("0", ExpenseAccrual.accrued(insurance, date("2001-07-01"), date("2001-07-01"), utc))
    }

    @Test
    fun `a variable expense counts in full only inside the window`() {
        val toll = expense(ExpenseCategory.PEDAGIO, "12.40", dayOffset = 10)

        assertNull(ExpenseAccrual.coverage(toll, utc))
        assertDecimal("12.40", ExpenseAccrual.accrued(toll, date("2001-01-01"), date("2001-02-01"), utc))
        assertDecimal("0", ExpenseAccrual.accrued(toll, date("2001-01-12"), date("2001-02-01"), utc))
        assertDecimal("0", ExpenseAccrual.accrued(toll, date("2000-12-01"), date("2001-01-11"), utc))
    }

    @Test
    fun `an unpaid fine accrues nothing`() {
        val fine = expense(ExpenseCategory.MULTA, "195.23", fine = FineDetails(isPaid = false))

        assertDecimal("0", ExpenseAccrual.accrued(fine, date("2000-01-01"), date("2002-01-01"), utc))
        assertDecimal("195.23", ExpenseAccrual.accrued(fine.copy(fine = FineDetails()), date("2000-01-01"), date("2002-01-01"), utc))
    }

    @Test
    fun `a refund accrues as a negative amount`() {
        assertDecimal("-50", ExpenseAccrual.accrued(expense(ExpenseCategory.OUTROS, "-50"), date("2001-01-01"), date("2001-02-01"), utc))
        assertDecimal("-181", ExpenseAccrual.accrued(expense(ExpenseCategory.SEGURO, "-365"), date("2001-01-01"), date("2001-07-01"), utc))
    }
}
