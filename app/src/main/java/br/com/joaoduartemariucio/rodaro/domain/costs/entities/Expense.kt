package br.com.joaoduartemariucio.rodaro.domain.costs.entities

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Expense(
    val id: UUID = UUID.randomUUID(),
    val vehicleId: UUID,
    val category: ExpenseCategory,
    val date: Instant,
    val amount: BigDecimal,
    val odometer: Double? = null,
    val note: String = "",
    val coverageStart: LocalDate? = null,
    val coverageEnd: LocalDate? = null,
    val fine: FineDetails? = null,
    val createdAt: Instant = date,
) {
    val isPaid: Boolean get() = fine?.isPaid ?: true
}

data class FineDetails(
    val infractionDate: LocalDate? = null,
    val severity: FineSeverity? = null,
    val points: Int? = null,
    val dueDate: LocalDate? = null,
    val discount: FineDiscount = FineDiscount.NONE,
    val isPaid: Boolean = true,
)

enum class FineSeverity(
    val rawValue: String,
    val baseAmount: BigDecimal,
    val points: Int,
) {
    LEVE("light", BigDecimal("88.38"), 3),
    MEDIA("medium", BigDecimal("130.16"), 4),
    GRAVE("serious", BigDecimal("195.23"), 5),
    GRAVISSIMA("verySerious", BigDecimal("293.47"), 7),
    ;

    companion object {
        fun of(raw: String?): FineSeverity? = entries.firstOrNull { it.rawValue == raw }
    }
}

enum class FineDiscount(
    val rawValue: String,
    val percent: Int,
) {
    NONE("none", 0),
    TWENTY("twenty", 20),
    FORTY("forty", 40),
    ;

    companion object {
        fun of(raw: String?): FineDiscount = entries.firstOrNull { it.rawValue == raw } ?: NONE
    }
}
