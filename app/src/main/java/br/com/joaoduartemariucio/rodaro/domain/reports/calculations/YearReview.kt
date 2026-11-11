package br.com.joaoduartemariucio.rodaro.domain.reports.calculations

import br.com.joaoduartemariucio.rodaro.domain.common.calculations.sum
import br.com.joaoduartemariucio.rodaro.domain.common.calculations.toDecimal
import br.com.joaoduartemariucio.rodaro.domain.costs.calculations.Cost
import br.com.joaoduartemariucio.rodaro.domain.costs.calculations.CostPeriod
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.Consumption
import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.ConsumptionInterval
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelGroup
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.vehicles.calculations.Odometer
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.OdometerSample
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class YearReview(
    val year: Int,
    val isPartial: Boolean,
    val distance: Distance,
    val spending: Spending?,
    val fuel: FuelSummary?,
    val consumption: Map<FuelGroup, GroupConsumption>,
    val costPerKilometer: BigDecimal?,
    val services: List<ServiceEntry>,
) {
    data class Distance(
        val kilometers: Double,
        val from: Instant,
        val fromIsReading: Boolean,
        val until: Instant,
        val untilIsReading: Boolean,
        val coveredDays: Long,
    ) {
        val perMonth: Double? get() = if (coveredDays > 0) kilometers / coveredDays * DAYS_PER_MONTH else null

        private companion object {
            const val DAYS_PER_MONTH = 30.4375
        }
    }

    data class Spending(
        val fuel: BigDecimal,
        val services: BigDecimal,
        val byCategory: Map<ExpenseCategory, BigDecimal>,
        val fines: BigDecimal,
        val total: BigDecimal,
        val mostExpensiveMonth: YearMonth?,
        val mostExpensiveAmount: BigDecimal?,
    )

    data class FuelSummary(
        val fuelUps: Int,
        val litersByGroup: Map<FuelGroup?, Double>,
        val averagePrice: Map<FuelType, BigDecimal>,
        val minimumPrice: Map<FuelType, BigDecimal>,
    )

    data class GroupConsumption(
        val kilometersPerLiter: Double,
        val intervals: Int,
        val best: ConsumptionInterval?,
        val worst: ConsumptionInterval?,
    )

    enum class Shortfall {
        DAYS,
        FUEL_UPS,
        READINGS,
    }
}

object YearReviews {
    const val MIN_DAYS = 90L
    const val MIN_FUEL_UPS = 4
    const val MIN_READING_GAP_DAYS = 30L
    private const val CAR_MIN_INTERVAL_KM = 100.0
    private const val MOTORCYCLE_MIN_INTERVAL_KM = 50.0
    private val CURRENT_YEAR_FROM: MonthDay = MonthDay.of(12, 15)

    fun minimumIntervalKilometers(kind: VehicleKind): Double =
        when (kind) {
            VehicleKind.CAR -> CAR_MIN_INTERVAL_KM
            VehicleKind.MOTORCYCLE -> MOTORCYCLE_MIN_INTERVAL_KM
        }

    fun distance(
        samples: List<OdometerSample>,
        year: Int,
        zone: ZoneId,
    ): YearReview.Distance? {
        val start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant()
        val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant()
        val inYear = readingsIn(samples, start, end)

        val interpolatedStart = Odometer.interpolated(start, samples)
        val fromReading = inYear.firstOrNull()
        val (from, fromOdometer) =
            when {
                interpolatedStart != null -> start to interpolatedStart
                fromReading != null -> fromReading.date to fromReading.odometer
                else -> return null
            }
        val interpolatedEnd = Odometer.interpolated(end, samples)
        val untilReading = inYear.lastOrNull()
        val (until, untilOdometer) =
            when {
                interpolatedEnd != null -> end to interpolatedEnd
                untilReading != null -> untilReading.date to untilReading.odometer
                else -> return null
            }
        if (until <= from) return null

        return YearReview.Distance(
            kilometers = maxOf(0.0, untilOdometer - fromOdometer),
            from = from,
            fromIsReading = interpolatedStart == null,
            until = until,
            untilIsReading = interpolatedEnd == null,
            coveredDays = ChronoUnit.DAYS.between(from.atZone(zone).toLocalDate(), until.atZone(zone).toLocalDate()),
        )
    }

    fun availableYears(
        fuel: List<FuelEntry>,
        services: List<ServiceEntry>,
        expenses: List<Expense>,
        now: Instant,
        zone: ZoneId,
    ): List<Int> {
        val today = now.atZone(zone).toLocalDate()
        val dates = fuel.map { it.date } + services.map { it.date } + expenses.map { it.date }
        return dates
            .map { it.atZone(zone).year }
            .toSet()
            .filter { year -> year < today.year || (year == today.year && !MonthDay.from(today).isBefore(CURRENT_YEAR_FROM)) }
            .sortedDescending()
    }

    fun shortfalls(
        vehicle: Vehicle,
        fuel: List<FuelEntry>,
        services: List<ServiceEntry>,
        year: Int,
        zone: ZoneId,
    ): Set<YearReview.Shortfall> {
        val samples = Odometer.samples(vehicle, fuel, services)
        val start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant()
        val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant()
        val shortfalls = mutableSetOf<YearReview.Shortfall>()

        val distance = distance(samples, year, zone)
        if (distance == null || distance.coveredDays < MIN_DAYS) shortfalls += YearReview.Shortfall.DAYS
        if (fuel.count { it.date >= start && it.date < end } < MIN_FUEL_UPS) shortfalls += YearReview.Shortfall.FUEL_UPS
        val readings = readingsIn(samples, start, end)
        val first = readings.firstOrNull()
        val last = readings.lastOrNull()
        val gap = if (first != null && last != null) ChronoUnit.DAYS.between(first.date.atZone(zone), last.date.atZone(zone)) else 0
        if (readings.size < 2 || gap < MIN_READING_GAP_DAYS) shortfalls += YearReview.Shortfall.READINGS
        return shortfalls
    }

    fun review(
        vehicle: Vehicle,
        fuel: List<FuelEntry>,
        services: List<ServiceEntry>,
        expenses: List<Expense>,
        year: Int,
        now: Instant,
        zone: ZoneId,
    ): YearReview? {
        if (shortfalls(vehicle, fuel, services, year, zone).isNotEmpty()) return null
        val samples = Odometer.samples(vehicle, fuel, services)
        val distance = distance(samples, year, zone) ?: return null
        val start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant()
        val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant()
        val inYear = { date: Instant -> date >= start && date < end }

        val yearFuel = fuel.filter { inYear(it.date) }
        val yearServices = services.filter { inYear(it.date) }.sortedBy { it.date }
        val yearExpenses = expenses.filter { it.isPaid && inYear(it.date) }

        return YearReview(
            year = year,
            isPartial = now.atZone(zone).year == year,
            distance = distance,
            spending = spending(yearFuel, yearServices, yearExpenses, zone),
            fuel = fuelSummary(yearFuel),
            consumption = consumption(vehicle.kind, fuel, start, end),
            costPerKilometer =
                Cost
                    .summary(
                        fuel,
                        services,
                        expenses,
                        samples,
                        CostPeriod(distance.from, distance.until),
                        zone,
                    ).costPerKilometer,
            services = yearServices,
        )
    }

    private fun readingsIn(
        samples: List<OdometerSample>,
        start: Instant,
        end: Instant,
    ): List<OdometerSample> = Odometer.forward(samples).filter { it.date >= start && it.date < end }

    private fun spending(
        fuel: List<FuelEntry>,
        services: List<ServiceEntry>,
        expenses: List<Expense>,
        zone: ZoneId,
    ): YearReview.Spending? {
        if (fuel.isEmpty() && services.isEmpty() && expenses.isEmpty()) return null
        val fuelTotal = fuel.map { it.totalCost }.sum()
        val serviceTotal = services.map { it.cost }.sum()
        val byCategory = expenses.groupBy { it.category }.mapValues { (_, items) -> items.map { it.amount }.sum() }
        val months =
            (
                fuel.map { it.date to it.totalCost } +
                    services.map { it.date to it.cost } +
                    expenses.map { it.date to it.amount }
            ).groupBy({ YearMonth.from(it.first.atZone(zone)) }, { it.second })
                .mapValues { (_, amounts) -> amounts.sum() }
        val top = months.entries.filter { it.value.signum() > 0 }.maxWithOrNull(compareBy({ it.value }, { -it.key.monthValue }))
        return YearReview.Spending(
            fuel = fuelTotal,
            services = serviceTotal,
            byCategory = byCategory,
            fines = byCategory[ExpenseCategory.MULTA] ?: BigDecimal.ZERO,
            total = fuelTotal + serviceTotal + byCategory.values.sum(),
            mostExpensiveMonth = top?.key,
            mostExpensiveAmount = top?.value,
        )
    }

    private fun fuelSummary(fuel: List<FuelEntry>): YearReview.FuelSummary? {
        if (fuel.isEmpty()) return null
        val typed =
            fuel
                .mapNotNull { entry -> entry.fuelType?.let { it to entry } }
                .groupBy({ it.first }, { it.second })
        return YearReview.FuelSummary(
            fuelUps = fuel.size,
            litersByGroup = fuel.groupBy { it.fuelType?.group }.mapValues { (_, entries) -> entries.sumOf { it.liters } },
            averagePrice =
                typed
                    .mapNotNull { (type, entries) ->
                        val liters = entries.sumOf { it.liters }
                        if (liters <=
                            0
                        ) {
                            null
                        } else {
                            type to entries.map { it.totalCost }.sum().divide(liters.toDecimal(), MathContext.DECIMAL128)
                        }
                    }.toMap(),
            minimumPrice = typed.mapValues { (_, entries) -> entries.minOf { it.pricePerLiter } },
        )
    }

    private fun consumption(
        kind: VehicleKind,
        fuel: List<FuelEntry>,
        start: Instant,
        end: Instant,
    ): Map<FuelGroup, YearReview.GroupConsumption> {
        val minimum = minimumIntervalKilometers(kind)
        return Consumption
            .intervals(fuel)
            .filter { it.endDate >= start && it.endDate < end }
            .mapNotNull { interval -> interval.fuelGroup?.let { it to interval } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (group, intervals) ->
                val average = Consumption.averageOf(intervals) ?: return@mapNotNull null
                val eligible = intervals.filter { it.distance >= minimum }
                val ranked = if (eligible.size >= 2) eligible.sortedBy { it.kilometersPerLiter } else emptyList()
                group to YearReview.GroupConsumption(average, intervals.size, ranked.lastOrNull(), ranked.firstOrNull())
            }.toMap()
    }
}
