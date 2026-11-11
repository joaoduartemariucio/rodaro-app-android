package br.com.joaoduartemariucio.rodaro.domain.maintenance.entities

import br.com.joaoduartemariucio.rodaro.domain.common.entities.Chronological
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ServiceEntry(
    val id: UUID = UUID.randomUUID(),
    val vehicleId: UUID,
    val kind: ServiceKind = ServiceKind.OTHER,
    override val date: Instant,
    override val odometer: Double,
    val cost: BigDecimal,
    val notes: String = "",
    val workshop: String? = null,
    val createdAt: Instant = date,
) : Chronological
