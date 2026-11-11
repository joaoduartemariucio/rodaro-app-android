package br.com.joaoduartemariucio.rodaro.domain.fueling.calculations

import br.com.joaoduartemariucio.rodaro.domain.common.entities.sortedChronologically
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelGroup
import java.util.UUID

object Consumption {
    fun intervals(records: List<FuelEntry>): List<ConsumptionInterval> {
        val anomalous = anomalies(records).map { it.record.id }.toSet()
        val intervals = mutableListOf<ConsumptionInterval>()
        var previous: FuelEntry? = null
        var previousPartials = emptyList<FuelEntry>()
        var open: FuelEntry? = null
        var partials = mutableListOf<FuelEntry>()
        var litersSinceOpen = 0.0

        for (record in records.sortedChronologically().filter { it.liters > 0 }) {
            val start = open
            if (start == null) {
                if (record.isFullTank) open = record
                continue
            }

            litersSinceOpen += record.liters
            if (!record.isFullTank) {
                partials += record
                continue
            }

            val tank = previous?.let { listOf(it) + previousPartials + start + partials }
            val measured = listOf(start) + partials + record
            val qualification = qualify(tank, measured, anomalous)
            val interval =
                ConsumptionInterval(
                    startDate = start.date,
                    endDate = record.date,
                    startOdometer = start.odometer,
                    endOdometer = record.odometer,
                    liters = litersSinceOpen,
                    fuelGroup = qualification.group,
                    isAfterFuelSwitch = qualification.isAfterSwitch,
                )
            if (interval.distance > 0) {
                intervals += interval
            }

            previous = start
            previousPartials = partials
            open = record
            partials = mutableListOf()
            litersSinceOpen = 0.0
        }

        return intervals
    }

    private class Qualification(
        val group: FuelGroup? = null,
        val isAfterSwitch: Boolean = false,
    )

    private fun qualify(
        tank: List<FuelEntry>?,
        measured: List<FuelEntry>,
        anomalous: Set<UUID>,
    ): Qualification {
        tank ?: return Qualification()
        if (measured.any { it.id in anomalous }) return Qualification()
        val groups = tank.map { it.fuelType?.group ?: return Qualification() }.toSet()
        return if (groups.size == 1) Qualification(group = groups.single()) else Qualification(isAfterSwitch = true)
    }

    fun averageByGroup(records: List<FuelEntry>): Map<FuelGroup, Double> =
        intervals(records)
            .mapNotNull { interval -> interval.fuelGroup?.let { it to interval } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (group, intervals) -> averageOf(intervals)?.let { group to it } }
            .toMap()

    fun latestGroup(records: List<FuelEntry>): FuelGroup? =
        records
            .sortedChronologically()
            .lastOrNull { it.fuelType != null }
            ?.fuelType
            ?.group

    fun latest(records: List<FuelEntry>): ConsumptionInterval? = intervals(records).lastOrNull()

    fun average(records: List<FuelEntry>): Double? = averageOf(intervals(records))

    fun averageOf(intervals: List<ConsumptionInterval>): Double? {
        val liters = intervals.sumOf { it.liters }
        if (liters <= 0) return null

        return intervals.sumOf { it.distance } / liters
    }

    fun anomalies(records: List<FuelEntry>): List<ConsumptionAnomaly> {
        val anomalies = mutableListOf<ConsumptionAnomaly>()
        var previous: FuelEntry? = null

        for (record in records.sortedChronologically()) {
            if (record.liters <= 0) {
                anomalies += ConsumptionAnomaly(record, ConsumptionAnomaly.Kind.NonPositiveLiters)
            }

            val last = previous
            previous = record
            if (last == null) continue

            if (record.odometer < last.odometer) {
                anomalies += ConsumptionAnomaly(record, ConsumptionAnomaly.Kind.OdometerWentBackwards(last.odometer))
            } else if (record.odometer == last.odometer) {
                val isDuplicate =
                    record.date == last.date &&
                        record.liters == last.liters &&
                        record.pricePerLiter.compareTo(last.pricePerLiter) == 0
                anomalies +=
                    ConsumptionAnomaly(
                        record,
                        if (isDuplicate) {
                            ConsumptionAnomaly.Kind.Duplicate(last.id)
                        } else {
                            ConsumptionAnomaly.Kind.OdometerUnchanged(last.odometer)
                        },
                    )
            }
        }

        return anomalies
    }
}
