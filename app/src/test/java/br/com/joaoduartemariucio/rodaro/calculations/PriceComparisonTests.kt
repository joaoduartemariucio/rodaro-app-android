package br.com.joaoduartemariucio.rodaro.calculations

import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.PriceComparison
import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.PriceComparison.Level
import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.PriceComparison.Reason
import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.PriceComparisons
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.RegionalPrice
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.RegionalPriceTable
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.RegionalWeek
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PriceComparisonTests {
    private val saoPaulo = ZoneId.of("America/Sao_Paulo")

    private fun week(
        start: String,
        mean: String = "6.000",
        sd: String? = "0.300",
        uf: String = "SP",
        type: FuelType = FuelType.GASOLINA_COMUM,
    ): RegionalWeek {
        val day = LocalDate.parse(start)
        return RegionalWeek(day, day.plusDays(6), mapOf(uf to mapOf(type to RegionalPrice(BigDecimal(mean), sd?.let(::BigDecimal), 100))))
    }

    private val table =
        RegionalPriceTable(
            Instant.parse("2026-11-10T13:00:00Z"),
            listOf(week("2026-11-01", mean = "6.000"), week("2026-10-25", mean = "5.000")),
        )

    private fun compare(
        price: String,
        date: Instant = Instant.parse("2026-11-03T15:00:00Z"),
        type: FuelType? = FuelType.GASOLINA_COMUM,
        uf: String? = "SP",
        table: RegionalPriceTable = this.table,
    ) = PriceComparisons.compare(BigDecimal(price), type, date, uf, table, saoPaulo)

    @Test
    fun `weeks run Sunday to Saturday in local time`() {
        val saturdayNight = assertIs<PriceComparison.Compared>(compare("5.0", Instant.parse("2026-11-01T02:30:00Z")))
        assertEquals(LocalDate.parse("2026-10-25"), saturdayNight.week.start)
        assertFalse(saturdayNight.isPreviousWeek)

        val sunday = assertIs<PriceComparison.Compared>(compare("6.0", Instant.parse("2026-11-01T03:30:00Z")))
        assertEquals(LocalDate.parse("2026-11-01"), sunday.week.start)
    }

    @Test
    fun `a missing week or product falls back to the previous week and says so`() {
        val missingSp = table.copy(weeks = listOf(week("2026-11-01", uf = "RJ"), week("2026-10-25", mean = "5.000")))
        val fallback = assertIs<PriceComparison.Compared>(compare("5.0", table = missingSp))
        assertTrue(fallback.isPreviousWeek)
        assertEquals(LocalDate.parse("2026-10-25"), fallback.week.start)

        val newer = assertIs<PriceComparison.Compared>(compare("6.0", Instant.parse("2026-11-10T15:00:00Z")))
        assertTrue(newer.isPreviousWeek)
        assertEquals(LocalDate.parse("2026-11-01"), newer.week.start)

        assertEquals(PriceComparison.Unavailable(Reason.NO_DATA), compare("6.0", Instant.parse("2026-11-17T15:00:00Z")))
    }

    @Test
    fun `within one standard deviation is average, outside is below or above`() {
        assertEquals(Level.AVERAGE, assertIs<PriceComparison.Compared>(compare("6.300")).level)
        assertEquals(Level.AVERAGE, assertIs<PriceComparison.Compared>(compare("5.700")).level)
        assertEquals(Level.ABOVE, assertIs<PriceComparison.Compared>(compare("6.301")).level)
        val below = assertIs<PriceComparison.Compared>(compare("5.699"))
        assertEquals(Level.BELOW, below.level)
        assertDecimal("-0.301", below.difference)
        assertDecimal("-5.016666666666666666666666666666667", below.percent)
    }

    @Test
    fun `without a standard deviation the band is two percent`() {
        val noSd = table.copy(weeks = listOf(week("2026-11-01", sd = null)))
        assertEquals(Level.AVERAGE, assertIs<PriceComparison.Compared>(compare("6.120", table = noSd)).level)
        assertEquals(Level.ABOVE, assertIs<PriceComparison.Compared>(compare("6.121", table = noSd)).level)
        val zeroSd = table.copy(weeks = listOf(week("2026-11-01", sd = "0")))
        assertDecimal("0.12", assertIs<PriceComparison.Compared>(compare("6.0", table = zeroSd)).tolerance)
    }

    @Test
    fun `the price is rounded to three decimals before comparing`() {
        val compared = assertIs<PriceComparison.Compared>(compare("6.3004"))
        assertDecimal("6.300", compared.price)
        assertEquals(Level.AVERAGE, compared.level)
    }

    @Test
    fun `premium, unknown type, missing state and very old fills are not compared`() {
        assertEquals(PriceComparison.Unavailable(Reason.NOT_SURVEYED), compare("6", type = FuelType.GASOLINA_PREMIUM))
        assertEquals(PriceComparison.Unavailable(Reason.UNKNOWN_FUEL_TYPE), compare("6", type = null))
        assertEquals(PriceComparison.Unavailable(Reason.NO_UF), compare("6", uf = null))
        assertEquals(PriceComparison.Unavailable(Reason.TOO_OLD), compare("6", Instant.parse("2025-10-20T12:00:00Z")))
        assertEquals(PriceComparison.Unavailable(Reason.NO_DATA), compare("6", Instant.parse("2025-11-03T12:00:00Z")))
        assertEquals(PriceComparison.Unavailable(Reason.NO_DATA), compare("6", type = FuelType.ETANOL))
        assertEquals(PriceComparison.Unavailable(Reason.NO_DATA), compare("6", table = table.copy(weeks = emptyList())))
    }
}
