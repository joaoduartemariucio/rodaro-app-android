package br.com.joaoduartemariucio.rodaro.domain.vehicles.calculations

import br.com.joaoduartemariucio.rodaro.domain.common.entities.sortedChronologically
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.OdometerSample
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import java.time.Duration
import java.time.Instant

object Odometer {
    fun current(samples: List<OdometerSample>): Double? = samples.sortedChronologically().lastOrNull()?.odometer

    fun neighbors(
        date: Instant,
        samples: List<OdometerSample>,
    ): OdometerNeighbors {
        val sorted = samples.sortedChronologically()
        return OdometerNeighbors(
            previous = sorted.lastOrNull { it.date <= date }?.odometer,
            next = sorted.firstOrNull { it.date > date }?.odometer,
        )
    }

    fun interpolated(
        at: Instant,
        samples: List<OdometerSample>,
    ): Double? {
        val forward = forward(samples)
        val first = forward.firstOrNull() ?: return null
        if (at < first.date || at > forward.last().date) return null

        val previous = forward.last { it.date <= at }
        val next = forward.firstOrNull { it.date > at } ?: return previous.odometer
        if (previous.date == at) return previous.odometer

        val span = Duration.between(previous.date, next.date).toMillis().toDouble()
        val elapsed = Duration.between(previous.date, at).toMillis().toDouble()
        return previous.odometer + (next.odometer - previous.odometer) * elapsed / span
    }

    fun forward(samples: List<OdometerSample>): List<OdometerSample> {
        val forward = mutableListOf<OdometerSample>()
        for (sample in samples.sortedChronologically()) {
            if (forward.isEmpty() || sample.odometer >= forward.last().odometer) forward += sample
        }
        return forward
    }

    fun samples(
        vehicle: Vehicle,
        fuel: List<FuelEntry>,
        services: List<ServiceEntry>,
    ): List<OdometerSample> =
        listOf(OdometerSample(vehicle.createdAt, vehicle.initialOdometer)) +
            fuel.map { OdometerSample(it.date, it.odometer) } +
            services.map { OdometerSample(it.date, it.odometer) }
}
