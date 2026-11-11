package br.com.joaoduartemariucio.rodaro.calculations

import br.com.joaoduartemariucio.rodaro.domain.costs.calculations.Cost
import br.com.joaoduartemariucio.rodaro.domain.costs.calculations.CostPeriod
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDetails
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CostTests {
    @Test
    fun `no records - everything zeroed and no cost per kilometer`() {
        val summary = Cost.summary(emptyList(), emptyList())

        assertDecimal("0", summary.totalCost)
        assertEquals(0.0, summary.distance)
        assertNull(summary.costPerKilometer)
    }

    @Test
    fun `a single record has a known spend but no cost per kilometer`() {
        val summary = Cost.summary(listOf(full(0, 1_000.0, 40.0, price = BigDecimal(5))), emptyList())

        assertDecimal("200", summary.fuelCost)
        assertEquals(0.0, summary.distance)
        assertNull(summary.costPerKilometer)
    }

    @Test
    fun `records all at the same odometer define no distance`() {
        val summary = Cost.summary(listOf(full(0, 1_000.0, 40.0), full(10, 1_000.0, 40.0)), emptyList())

        assertEquals(0.0, summary.distance)
        assertNull(summary.costPerKilometer)
    }

    @Test
    fun `fuel and services add up in the cost per kilometer`() {
        val summary =
            Cost.summary(
                fuel = listOf(full(0, 1_000.0, 40.0, price = BigDecimal(5)), full(10, 1_400.0, 40.0, price = decimal("5.50"))),
                services = listOf(service(5, 1_200.0, BigDecimal(100))),
            )

        assertDecimal("420", summary.fuelCost)
        assertDecimal("100", summary.serviceCost)
        assertDecimal("520", summary.totalCost)
        assertEquals(400.0, summary.distance)
        assertDecimal("1.30", summary.costPerKilometer)
    }

    @Test
    fun `a service can be the far end of the odometer span`() {
        val summary =
            Cost.summary(
                fuel = listOf(full(0, 1_000.0, 40.0), full(10, 1_400.0, 40.0)),
                services = listOf(service(20, 1_600.0, BigDecimal(200))),
            )

        assertEquals(600.0, summary.distance)
    }

    @Test
    fun `summing cents does not accumulate floating-point error`() {
        val dime = decimal("0.10")
        val fuel = (0 until 10).map { full(it, 1_000.0 + it * 100, 1.0, price = dime) }

        val summary = Cost.summary(fuel, emptyList())
        val viaDouble = (0 until 10).fold(0.0) { total, _ -> total + 0.1 }

        assertDecimal("1", summary.fuelCost)
        assertNotEquals(1.0, viaDouble)
    }

    @Test
    fun `the period is whatever the lists carry - filtering by date changes the result`() {
        val everything = listOf(full(0, 1_000.0, 40.0), full(10, 1_400.0, 40.0), full(20, 1_800.0, 40.0))
        val recent = everything.filter { it.date >= day(10) }

        assertEquals(800.0, Cost.summary(everything, emptyList()).distance)
        assertEquals(400.0, Cost.summary(recent, emptyList()).distance)
    }

    private val fuel = listOf(full(0, 1_000.0, 40.0), full(9, 1_400.0, 40.0))

    @Test
    fun `fines, financing and accessories stay out of cost per km but count as spent`() {
        val expenses =
            listOf(
                expense(ExpenseCategory.MULTA, "100", dayOffset = 2, fine = FineDetails()),
                expense(ExpenseCategory.MULTA, "300", dayOffset = 3, fine = FineDetails(isPaid = false)),
                expense(ExpenseCategory.FINANCIAMENTO, "500", dayOffset = 4),
                expense(ExpenseCategory.ACESSORIOS, "80", dayOffset = 5),
                expense(ExpenseCategory.PEDAGIO, "20", dayOffset = 6),
            )

        val summary = Cost.summary(fuel, emptyList(), expenses, zone = utc)

        assertDecimal("20", summary.expensesInCost)
        assertDecimal("420", summary.totalCost)
        assertDecimal("1.05", summary.costPerKilometer)
        assertDecimal("100", summary.fines)
        assertDecimal("580", summary.expensesOutsideCost)
        assertDecimal("700", summary.expensesSpent)
        assertDecimal("1100", summary.totalSpent)
        assertEquals(setOf(ExpenseCategory.MULTA, ExpenseCategory.FINANCIAMENTO, ExpenseCategory.ACESSORIOS, ExpenseCategory.PEDAGIO), summary.byCategory.keys)
        assertDecimal("100", summary.byCategory[ExpenseCategory.MULTA])
    }

    @Test
    fun `all time accrues fixed expenses over the span of the records`() {
        val insurance = expense(ExpenseCategory.SEGURO, "365")

        val summary = Cost.summary(fuel, emptyList(), listOf(insurance), zone = utc)

        assertDecimal("10", summary.expensesInCost)
        assertDecimal("365", summary.byCategory[ExpenseCategory.SEGURO])
        assertDecimal("765", summary.totalSpent)
        assertDecimal("0", Cost.summary(emptyList(), emptyList(), listOf(insurance), zone = utc).expensesInCost)
    }

    @Test
    fun `a period filters records, accrues fixed expenses and interpolates the odometer at its ends`() {
        val records = listOf(full(0, 1_000.0, 40.0), full(10, 2_000.0, 40.0), full(20, 3_000.0, 40.0))
        val expenses =
            listOf(
                expense(ExpenseCategory.SEGURO, "365"),
                expense(ExpenseCategory.PEDAGIO, "30", dayOffset = 12),
                expense(ExpenseCategory.PEDAGIO, "99", dayOffset = 16),
            )
        val samples = listOf(sample(0, 1_000.0), sample(20, 3_000.0))

        val summary = Cost.summary(records, emptyList(), expenses, samples, CostPeriod(day(5), day(15)), utc)

        assertEquals(1_000.0, summary.distance)
        assertDecimal("200", summary.fuelCost)
        assertDecimal("40", summary.expensesInCost)
        assertDecimal("0.24", summary.costPerKilometer)
        assertEquals(mapOf(ExpenseCategory.PEDAGIO to BigDecimal("30")), summary.byCategory)
        assertEquals(0.0, Cost.summary(records, emptyList(), period = CostPeriod(day(30), day(40)), zone = utc).distance)
    }
}
