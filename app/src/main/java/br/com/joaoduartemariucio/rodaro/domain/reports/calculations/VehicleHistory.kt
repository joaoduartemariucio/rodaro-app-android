package br.com.joaoduartemariucio.rodaro.domain.reports.calculations

import br.com.joaoduartemariucio.rodaro.domain.common.calculations.sum
import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.Consumption
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelGroup
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.vehicles.calculations.Odometer
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class HistoryOptions(
    val includeCosts: Boolean = false,
    val includeFuelLog: Boolean = false,
    val showFullPlate: Boolean = false,
)

data class VehicleHistory(
    val vehicle: Vehicle,
    val plate: String?,
    val options: HistoryOptions,
    val generatedAt: Instant,
    val recordCount: Int,
    val trackedSince: Instant,
    val firstRecord: Instant?,
    val odometerTimeline: List<OdometerLine>,
    val regressions: List<OdometerRegression>,
    val services: List<ServiceLine>,
    val consumptionByGroup: Map<FuelGroup, Double>,
    val overallConsumption: Double?,
    val kilometersByYear: Map<Int, Double>,
    val fuelUps: Int,
    val fuelLog: List<FuelLine>?,
    val totals: Totals?,
) {
    data class OdometerLine(
        val month: YearMonth,
        val odometer: Double,
        val date: Instant,
    )

    data class OdometerRegression(
        val date: Instant,
        val source: Source,
        val previous: Double,
        val odometer: Double,
    )

    enum class Source {
        VEHICLE,
        FUEL,
        SERVICE,
    }

    data class ServiceLine(
        val entry: ServiceEntry,
        val isRetroactive: Boolean,
    )

    data class FuelLine(
        val entry: FuelEntry,
        val isRetroactive: Boolean,
    )

    data class Totals(
        val fuel: BigDecimal,
        val services: BigDecimal,
    )
}

object VehicleHistories {
    const val RETROACTIVE_DAYS = 30L

    fun isRetroactive(
        date: Instant,
        createdAt: Instant,
    ): Boolean = Duration.between(date, createdAt) > Duration.ofDays(RETROACTIVE_DAYS)

    fun build(
        vehicle: Vehicle,
        fuel: List<FuelEntry>,
        services: List<ServiceEntry>,
        options: HistoryOptions,
        now: Instant,
        zone: ZoneId,
    ): VehicleHistory {
        val samples = Odometer.samples(vehicle, fuel, services)
        val sampleYears = samples.map { it.date.atZone(zone).year }
        val firstYear = sampleYears.minOrNull()
        val lastYear = sampleYears.maxOrNull()
        val kilometersByYear =
            if (firstYear == null || lastYear == null) {
                emptyMap()
            } else {
                (firstYear..lastYear)
                    .mapNotNull { year -> YearReviews.distance(samples, year, zone)?.let { year to it.kilometers } }
                    .toMap()
            }
        val records = fuel.map { it.date } + services.map { it.date }

        return VehicleHistory(
            vehicle = vehicle,
            plate = if (options.showFullPlate) PlateMask.normalized(vehicle.plate) else PlateMask.mask(vehicle.plate),
            options = options,
            generatedAt = now,
            recordCount = fuel.size + services.size,
            trackedSince = vehicle.createdAt,
            firstRecord = records.minOrNull(),
            odometerTimeline =
                Odometer
                    .forward(samples)
                    .groupBy { YearMonth.from(it.date.atZone(zone)) }
                    .map { (month, readings) -> readings.last().let { VehicleHistory.OdometerLine(month, it.odometer, it.date) } }
                    .sortedBy { it.month },
            regressions = regressions(vehicle, fuel, services),
            services = services.sortedBy { it.date }.map { VehicleHistory.ServiceLine(it, isRetroactive(it.date, it.createdAt)) },
            consumptionByGroup = Consumption.averageByGroup(fuel),
            overallConsumption = Consumption.average(fuel),
            kilometersByYear = kilometersByYear,
            fuelUps = fuel.size,
            fuelLog =
                if (options.includeFuelLog) {
                    fuel.sortedBy { it.date }.map { VehicleHistory.FuelLine(it, isRetroactive(it.date, it.createdAt)) }
                } else {
                    null
                },
            totals =
                if (options.includeCosts) {
                    VehicleHistory.Totals(fuel.map { it.totalCost }.sum(), services.map { it.cost }.sum())
                } else {
                    null
                },
        )
    }

    private fun regressions(
        vehicle: Vehicle,
        fuel: List<FuelEntry>,
        services: List<ServiceEntry>,
    ): List<VehicleHistory.OdometerRegression> {
        val readings =
            (
                listOf(Triple(vehicle.createdAt, vehicle.initialOdometer, VehicleHistory.Source.VEHICLE)) +
                    fuel.map { Triple(it.date, it.odometer, VehicleHistory.Source.FUEL) } +
                    services.map { Triple(it.date, it.odometer, VehicleHistory.Source.SERVICE) }
            ).sortedWith(compareBy({ it.first }, { it.second }))
        val regressions = mutableListOf<VehicleHistory.OdometerRegression>()
        var highest: Double? = null
        for ((date, odometer, source) in readings) {
            val previous = highest
            if (previous != null && odometer < previous) {
                regressions += VehicleHistory.OdometerRegression(date, source, previous, odometer)
            } else {
                highest = odometer
            }
        }
        return regressions
    }
}
