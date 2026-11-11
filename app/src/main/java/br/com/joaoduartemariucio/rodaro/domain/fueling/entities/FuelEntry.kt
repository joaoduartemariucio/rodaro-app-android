package br.com.joaoduartemariucio.rodaro.domain.fueling.entities

import br.com.joaoduartemariucio.rodaro.domain.common.calculations.toDecimal
import br.com.joaoduartemariucio.rodaro.domain.common.entities.Chronological
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class FuelEntry(
    val id: UUID = UUID.randomUUID(),
    val vehicleId: UUID,
    override val date: Instant,
    override val odometer: Double,
    val liters: Double,
    val pricePerLiter: BigDecimal,
    val isFullTank: Boolean,
    val fuelType: FuelType? = null,
    val createdAt: Instant = date,
) : Chronological {
    val totalCost: BigDecimal get() = liters.toDecimal().multiply(pricePerLiter)
}
