package br.com.joaoduartemariucio.rodaro.domain.dues.entities

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class DueKind(
    val rawValue: String,
) {
    IPVA("ipva"),
    LICENCIAMENTO("licensing"),
    SEGURO("insurance"),
    CNH("driverLicense"),
    ;

    companion object {
        fun of(raw: String?): DueKind? = entries.firstOrNull { it.rawValue == raw }
    }
}

data class DueItem(
    val id: UUID = UUID.randomUUID(),
    val vehicleId: UUID?,
    val kind: DueKind,
    val dueDate: LocalDate,
    val amount: BigDecimal? = null,
    val paidAt: Instant? = null,
    val installmentNumber: Int? = null,
    val installmentCount: Int? = null,
    val note: String = "",
    val createdAt: Instant,
) {
    val isPaid: Boolean get() = paidAt != null
}
