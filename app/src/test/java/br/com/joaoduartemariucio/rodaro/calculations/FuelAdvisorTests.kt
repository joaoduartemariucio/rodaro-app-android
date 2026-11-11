package br.com.joaoduartemariucio.rodaro.calculations

import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.FuelAdvisor
import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.FuelRatioEstimate
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelGroup
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FuelAdvisorTests {
    private val general = FuelRatioEstimate(0.70, FuelRatioEstimate.Basis.GeneralRule(FuelRatioEstimate.Reason.NOT_ENOUGH_DATA))
    private val personal = FuelRatioEstimate(10.0 / 14.0, FuelRatioEstimate.Basis.Personal(10.0, 14.0, 3, 4))

    @Test
    fun `ethanol below the threshold is the cheaper fuel`() {
        val advice = assertNotNull(FuelAdvisor.advise(decimal("3.99"), decimal("5.99"), general))

        assertEquals(FuelGroup.ETANOL, advice.cheaper)
        assertDecimal("4.193", advice.thresholdEthanolPrice)
        assertNull(advice.ethanolCostPerKilometer)
        assertNull(advice.gasolineCostPerKilometer)
    }

    @Test
    fun `ethanol above the threshold makes gasoline cheaper`() {
        assertEquals(FuelGroup.GASOLINA, FuelAdvisor.advise(decimal("4.50"), decimal("5.99"), general)?.cheaper)
    }

    @Test
    fun `a price exactly at the threshold is a tie`() {
        val advice = assertNotNull(FuelAdvisor.advise(decimal("4.20"), decimal("6.00"), general))

        assertNull(advice.cheaper)
        assertDecimal("4.200", advice.thresholdEthanolPrice)
    }

    @Test
    fun `the threshold is rounded to three decimals only for display`() {
        val advice = assertNotNull(FuelAdvisor.advise(decimal("4.0594"), decimal("5.799"), general))

        assertDecimal("4.059", advice.thresholdEthanolPrice)
        assertEquals(FuelGroup.GASOLINA, advice.cheaper)
    }

    @Test
    fun `cost per kilometer is shown only with the personal ratio`() {
        val advice = assertNotNull(FuelAdvisor.advise(decimal("3.99"), decimal("5.99"), personal))

        assertDecimal("0.399", advice.ethanolCostPerKilometer)
        assertEquals(5.99 / 14.0, assertNotNull(advice.gasolineCostPerKilometer).toDouble(), 0.000_001)
        assertEquals(FuelGroup.ETANOL, advice.cheaper)
        assertEquals(personal, advice.estimate)
    }

    @Test
    fun `non-positive prices give no advice`() {
        assertNull(FuelAdvisor.advise(BigDecimal.ZERO, decimal("5.99"), general))
        assertNull(FuelAdvisor.advise(decimal("3.99"), decimal("-1"), general))
    }
}
