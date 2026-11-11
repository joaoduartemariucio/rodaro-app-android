package br.com.joaoduartemariucio.rodaro.data

import br.com.joaoduartemariucio.rodaro.calculations.assertDecimal
import br.com.joaoduartemariucio.rodaro.data.valuation.services.FipeText
import org.junit.Test
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FipeTextTests {
    @Test
    fun `prices are read from the Brazilian currency text`() {
        assertDecimal("85386.00", FipeText.price("R$ 85.386,00"))
        assertDecimal("1234567.89", FipeText.price("R$ 1.234.567,89"))
        assertDecimal("950", FipeText.price("R$ 950"))
        assertDecimal("12.5", FipeText.price("12,5"))
        assertNull(FipeText.price("R$ —"))
        assertNull(FipeText.price(""))
        assertNull(FipeText.price("1,2,3"))
    }

    @Test
    fun `every month name is read in both formats, with or without accents and case`() {
        val names =
            listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")
        names.forEachIndexed { index, name ->
            assertEquals(YearMonth.of(2026, index + 1), FipeText.month("$name de 2026"))
            assertEquals(YearMonth.of(2025, index + 1), FipeText.month("$name/2025"))
            assertEquals(YearMonth.of(2025, index + 1), FipeText.month(" ${name.uppercase()} DE 2025 "))
        }
        assertEquals(YearMonth.of(2026, 3), FipeText.month("marco/2026"))
    }

    @Test
    fun `anything else is not a month`() {
        assertNull(FipeText.month("smarch de 2026"))
        assertNull(FipeText.month("novembro 2026"))
        assertNull(FipeText.month("11/2026"))
        assertNull(FipeText.month(""))
    }
}
