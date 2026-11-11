package br.com.joaoduartemariucio.rodaro.domain.valuation.calculations

import br.com.joaoduartemariucio.rodaro.domain.common.calculations.toDecimal
import br.com.joaoduartemariucio.rodaro.domain.common.entities.sortedChronologically
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeQuote
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeSelection
import br.com.joaoduartemariucio.rodaro.domain.vehicles.calculations.Odometer
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.OdometerSample
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class DepreciationSummary(
    val start: FipeQuote,
    val latest: FipeQuote,
    val amount: BigDecimal,
    val months: Int,
    val kilometers: Double?,
    val perMonth: BigDecimal?,
    val perKilometer: BigDecimal?,
    val purchasePrice: BigDecimal?,
    val purchaseVsLatest: BigDecimal?,
    val startsAtPurchase: Boolean,
) {
    val isAppreciation: Boolean get() = amount.signum() < 0
}

enum class FipeRefresh {
    NONE,
    HISTORY,
    LATEST,
}

object Depreciation {
    const val MIN_MONTHS = 3
    const val MIN_KILOMETERS = 1000.0

    fun relevant(
        quotes: List<FipeQuote>,
        selection: FipeSelection,
    ): List<FipeQuote> =
        quotes
            .filter { it.fipeCode == selection.fipeCode && it.yearCode == selection.yearCode }
            .sortedBy { it.month }

    fun startQuote(
        quotes: List<FipeQuote>,
        startMonth: YearMonth?,
    ): FipeQuote? {
        val sorted = quotes.sortedBy { it.month }
        if (startMonth == null) return sorted.firstOrNull()
        return sorted.firstOrNull { it.month >= startMonth } ?: sorted.lastOrNull()
    }

    fun startMonth(
        vehicle: Vehicle,
        samples: List<OdometerSample>,
        zone: ZoneId,
    ): YearMonth? =
        vehicle.purchaseDate?.let(YearMonth::from)
            ?: samples.sortedChronologically().firstOrNull()?.let { YearMonth.from(it.date.atZone(zone)) }

    fun summary(
        vehicle: Vehicle,
        quotes: List<FipeQuote>,
        samples: List<OdometerSample>,
        zone: ZoneId,
    ): DepreciationSummary? {
        val selection = vehicle.fipe ?: return null
        val relevant = relevant(quotes, selection)
        val latest = relevant.lastOrNull() ?: return null
        val start = startQuote(relevant, startMonth(vehicle, samples, zone)) ?: return null

        val amount = start.price.subtract(latest.price)
        val months = ChronoUnit.MONTHS.between(start.month, latest.month).toInt()
        val kilometers = kilometers(start.month, latest.month, samples, zone)
        val hasEnoughData = months >= MIN_MONTHS && kilometers != null && kilometers >= MIN_KILOMETERS
        val purchaseMonth = vehicle.purchaseDate?.let(YearMonth::from)

        return DepreciationSummary(
            start = start,
            latest = latest,
            amount = amount,
            months = months,
            kilometers = kilometers,
            perMonth = if (hasEnoughData) amount.divide(BigDecimal(months), MathContext.DECIMAL128) else null,
            perKilometer =
                if (hasEnoughData && kilometers != null) amount.divide(kilometers.toDecimal(), MathContext.DECIMAL128) else null,
            purchasePrice = vehicle.purchasePrice,
            purchaseVsLatest = vehicle.purchasePrice?.subtract(latest.price),
            startsAtPurchase = purchaseMonth != null && start.month == purchaseMonth,
        )
    }

    fun refreshPlan(
        vehicle: Vehicle,
        quotes: List<FipeQuote>,
        now: Instant,
        zone: ZoneId,
    ): FipeRefresh {
        val selection = vehicle.fipe ?: return FipeRefresh.NONE
        val relevant = relevant(quotes, selection)
        val latest = relevant.lastOrNull() ?: return FipeRefresh.HISTORY
        val current = YearMonth.from(now.atZone(zone))
        val fetchedThisMonth = relevant.any { YearMonth.from(it.fetchedAt.atZone(zone)) == current }
        return if (latest.month < current && !fetchedThisMonth) FipeRefresh.LATEST else FipeRefresh.NONE
    }

    private fun kilometers(
        startMonth: YearMonth,
        endMonth: YearMonth,
        samples: List<OdometerSample>,
        zone: ZoneId,
    ): Double? {
        val from = startMonth.atDay(1).atStartOfDay(zone).toInstant()
        val until = endMonth.atDay(1).atStartOfDay(zone).toInstant()
        val inWindow = samples.sortedChronologically().filter { it.date in from..until }
        val start = Odometer.interpolated(from, samples) ?: inWindow.firstOrNull()?.odometer ?: return null
        val end = Odometer.interpolated(until, samples) ?: inWindow.lastOrNull()?.odometer ?: return null
        return maxOf(0.0, end - start)
    }
}
