package br.com.joaoduartemariucio.rodaro.calculations

import br.com.joaoduartemariucio.rodaro.domain.reports.calculations.PlateMask
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlateMaskTests {
    @Test
    fun `Mercosul and old plates keep the first five characters`() {
        assertEquals("ABC1D**", PlateMask.mask("ABC1D23"))
        assertEquals("ABC12**", PlateMask.mask("ABC1234"))
    }

    @Test
    fun `hyphens, spaces and lower case are normalized`() {
        assertEquals("ABC12**", PlateMask.mask("abc-1234"))
        assertEquals("ABC1D**", PlateMask.mask(" abc 1d23 "))
        assertEquals("ABC1234", PlateMask.normalized("abc-1234"))
    }

    @Test
    fun `short, blank or missing plates still hide their end`() {
        assertEquals("A**", PlateMask.mask("ab1"))
        assertEquals("**", PlateMask.mask("a1"))
        assertNull(PlateMask.mask("  - "))
        assertNull(PlateMask.mask(null))
    }
}
