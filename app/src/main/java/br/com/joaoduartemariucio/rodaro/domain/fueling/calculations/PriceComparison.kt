package br.com.joaoduartemariucio.rodaro.domain.fueling.calculations

import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.RegionalPrice
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.RegionalPriceTable
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.RegionalWeek
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

sealed interface PriceComparison {
    data class Compared(
        val level: Level,
        val price: BigDecimal,
        val regional: RegionalPrice,
        val uf: String,
        val week: RegionalWeek,
        val isPreviousWeek: Boolean,
        val tolerance: BigDecimal,
        val difference: BigDecimal,
        val percent: BigDecimal,
    ) : PriceComparison

    data class Unavailable(
        val reason: Reason,
    ) : PriceComparison

    enum class Level {
        BELOW,
        AVERAGE,
        ABOVE,
    }

    enum class Reason {
        NO_UF,
        UNKNOWN_FUEL_TYPE,
        NOT_SURVEYED,
        TOO_OLD,
        NO_DATA,
    }
}

object PriceComparisons {
    val FALLBACK_TOLERANCE: BigDecimal = BigDecimal("0.02")
    const val MAX_WEEKS_BACK = 52L
    private const val PRICE_DECIMALS = 3
    private val HUNDRED = BigDecimal(100)

    fun compare(
        pricePerLiter: BigDecimal,
        fuelType: FuelType?,
        date: Instant,
        uf: String?,
        table: RegionalPriceTable,
        zone: ZoneId,
    ): PriceComparison {
        uf ?: return PriceComparison.Unavailable(PriceComparison.Reason.NO_UF)
        fuelType ?: return PriceComparison.Unavailable(PriceComparison.Reason.UNKNOWN_FUEL_TYPE)
        if (fuelType == FuelType.GASOLINA_PREMIUM) return PriceComparison.Unavailable(PriceComparison.Reason.NOT_SURVEYED)

        val weekStart = date.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val newest = table.weeks.maxByOrNull { it.start } ?: return PriceComparison.Unavailable(PriceComparison.Reason.NO_DATA)
        if (weekStart.isBefore(newest.start.minusWeeks(MAX_WEEKS_BACK))) {
            return PriceComparison.Unavailable(PriceComparison.Reason.TOO_OLD)
        }

        val previousStart = weekStart.minusWeeks(1)
        val exact = table.weeks.firstOrNull { it.start == weekStart }?.let { week -> week.prices[uf]?.get(fuelType)?.let { week to it } }
        val (week, regional) =
            exact
                ?: table.weeks.firstOrNull { it.start == previousStart }?.let { week -> week.prices[uf]?.get(fuelType)?.let { week to it } }
                ?: return PriceComparison.Unavailable(PriceComparison.Reason.NO_DATA)

        val price = pricePerLiter.setScale(PRICE_DECIMALS, RoundingMode.HALF_EVEN)
        val deviation = regional.standardDeviation?.takeIf { it.signum() > 0 }
        val tolerance = deviation ?: regional.mean.multiply(FALLBACK_TOLERANCE)
        val difference = price.subtract(regional.mean)
        val level =
            when {
                difference.abs() <= tolerance -> PriceComparison.Level.AVERAGE
                difference.signum() < 0 -> PriceComparison.Level.BELOW
                else -> PriceComparison.Level.ABOVE
            }
        return PriceComparison.Compared(
            level = level,
            price = price,
            regional = regional,
            uf = uf,
            week = week,
            isPreviousWeek = exact == null,
            tolerance = tolerance,
            difference = difference,
            percent = difference.multiply(HUNDRED).divide(regional.mean, MathContext.DECIMAL128),
        )
    }
}
