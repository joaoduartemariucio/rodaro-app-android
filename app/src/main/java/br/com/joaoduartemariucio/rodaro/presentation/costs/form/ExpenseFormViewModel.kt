package br.com.joaoduartemariucio.rodaro.presentation.costs.form

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.app.NoAnalytics
import br.com.joaoduartemariucio.rodaro.domain.common.services.Analytics
import br.com.joaoduartemariucio.rodaro.domain.common.services.DateProvider
import br.com.joaoduartemariucio.rodaro.domain.costs.calculations.ExpenseAccrual
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDetails
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDiscount
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineSeverity
import br.com.joaoduartemariucio.rodaro.domain.costs.repositories.ExpenseRepository
import br.com.joaoduartemariucio.rodaro.presentation.common.FormState
import br.com.joaoduartemariucio.rodaro.presentation.common.Localizer
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.common.UnitConversion
import br.com.joaoduartemariucio.rodaro.presentation.common.ValidationIssue
import br.com.joaoduartemariucio.rodaro.presentation.common.blocksSaving
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedFullDate
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class ExpenseFormViewModel(
    private val mode: Mode,
    private val repository: ExpenseRepository,
    private val dateProvider: DateProvider,
    private val analytics: Analytics = NoAnalytics,
) : ViewModel() {
    sealed interface Mode {
        data class Create(
            val vehicleId: UUID,
        ) : Mode

        data class Edit(
            val expense: Expense,
        ) : Mode
    }

    private val existing: Expense? = (mode as? Mode.Edit)?.expense
    private val existingFine: FineDetails? = existing?.fine

    var category by mutableStateOf(existing?.category ?: ExpenseCategory.PEDAGIO)
    var date: Instant by mutableStateOf(existing?.date ?: dateProvider.now)
    var amount by mutableStateOf(existing?.amount?.let { NumberInput.text(it.abs(), NumberInput.Style.MONEY) }.orEmpty())
    var isRefund by mutableStateOf((existing?.amount?.signum() ?: 0) < 0)
    var odometer by mutableStateOf(
        existing?.odometer?.let { NumberInput.text(UnitConversion.distance(it), NumberInput.Style.KILOMETERS) }.orEmpty(),
    )
    var note by mutableStateOf(existing?.note.orEmpty())
    var coverageStart: LocalDate? by mutableStateOf(existing?.coverageStart)
    var coverageEnd: LocalDate? by mutableStateOf(existing?.coverageEnd)

    var infractionDate: LocalDate? by mutableStateOf(existingFine?.infractionDate)
    var dueDate: LocalDate? by mutableStateOf(existingFine?.dueDate)
    var isPaid by mutableStateOf(existingFine?.isPaid ?: true)
    private var pointsText by mutableStateOf(existingFine?.points?.toString().orEmpty())
    private var selectedSeverity: FineSeverity? by mutableStateOf(existingFine?.severity)
    private var selectedDiscount by mutableStateOf(existingFine?.discount ?: FineDiscount.NONE)
    private var prefilledAmount: String? = null
    private var prefilledPoints: String? = null

    var points: String
        get() = pointsText
        set(value) {
            pointsText = value.filter { it in '0'..'9' }.take(MAX_POINT_DIGITS)
        }

    var severity: FineSeverity?
        get() = selectedSeverity
        set(value) {
            selectedSeverity = value
            prefillFine()
        }

    var discount: FineDiscount
        get() = selectedDiscount
        set(value) {
            selectedDiscount = value
            prefillFine()
        }

    var state: FormState by mutableStateOf(FormState.Editing)
        private set

    val isFine: Boolean get() = category == ExpenseCategory.MULTA

    val isFixed: Boolean get() = category.isFixed

    val showsDiscountCaveat: Boolean get() = isFine && discount == FineDiscount.FORTY

    val parsedAmount: BigDecimal?
        get() = NumberInput.decimal(amount, NumberInput.Style.MONEY)?.let { if (isRefund) it.negate() else it }

    val parsedOdometer: Double?
        get() = NumberInput.double(odometer, NumberInput.Style.KILOMETERS)?.let(UnitConversion::distanceToKilometers)

    val parsedPoints: Int? get() = points.toIntOrNull()

    val defaultCoverage: ClosedRange<LocalDate>?
        get() = ExpenseAccrual.coverage(draft(BigDecimal.ZERO).copy(coverageStart = null, coverageEnd = null), dateProvider.zone)

    val issues: List<ValidationIssue>
        get() =
            buildList {
                val amount = parsedAmount
                if (amount == null || amount.signum() == 0) add(ValidationIssue.AmountRequired)
                val start = coverageStart
                val end = coverageEnd
                if (isFixed && start != null && end != null && end.isBefore(start)) add(ValidationIssue.CoverageEndBeforeStart)
            }

    val canSave: Boolean get() = !issues.blocksSaving && !state.isSaving

    fun coverageHint(localizer: Localizer): String? {
        val coverage = defaultCoverage ?: return null
        val zone = dateProvider.zone
        return localizer.string(
            R.string.expense_coverage_default_hint,
            coverage.start
                .atStartOfDay(zone)
                .toInstant()
                .formattedFullDate(zone),
            coverage.endInclusive
                .atStartOfDay(zone)
                .toInstant()
                .formattedFullDate(zone),
        )
    }

    suspend fun save() {
        val amount = parsedAmount ?: return
        if (issues.blocksSaving) return

        state = FormState.Saving
        state =
            try {
                repository.save(draft(amount))
                analytics.event(
                    "expense_saved",
                    mapOf(
                        "mode" to if (mode is Mode.Create) "create" else "edit",
                        "category" to category.rawValue,
                    ),
                )
                FormState.Saved
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                FormState.Failed(error)
            }
    }

    private fun draft(amount: BigDecimal): Expense {
        val fine =
            if (isFine) {
                FineDetails(
                    infractionDate = infractionDate,
                    severity = severity,
                    points = parsedPoints,
                    dueDate = dueDate,
                    discount = discount,
                    isPaid = isPaid,
                )
            } else {
                null
            }
        val start = if (isFixed) coverageStart else null
        val end = if (isFixed) coverageEnd else null
        return when (mode) {
            is Mode.Create -> {
                Expense(
                    vehicleId = mode.vehicleId,
                    category = category,
                    date = date,
                    amount = amount,
                    odometer = parsedOdometer,
                    note = note.trim(),
                    coverageStart = start,
                    coverageEnd = end,
                    fine = fine,
                    createdAt = dateProvider.now,
                )
            }

            is Mode.Edit -> {
                mode.expense.copy(
                    category = category,
                    date = date,
                    amount = amount,
                    odometer = parsedOdometer,
                    note = note.trim(),
                    coverageStart = start,
                    coverageEnd = end,
                    fine = fine,
                )
            }
        }
    }

    private fun prefillFine() {
        val severity = selectedSeverity ?: return
        val factor = BigDecimal(PERCENT - discount.percent).movePointLeft(2)
        val amountText = NumberInput.text(severity.baseAmount.multiply(factor).setScale(2, RoundingMode.HALF_EVEN), NumberInput.Style.MONEY)
        if (amount.isEmpty() || amount == prefilledAmount) {
            amount = amountText
            prefilledAmount = amountText
        }
        val pointsValue = severity.points.toString()
        if (points.isEmpty() || points == prefilledPoints) {
            points = pointsValue
            prefilledPoints = pointsValue
        }
    }

    private companion object {
        const val PERCENT = 100
        const val MAX_POINT_DIGITS = 2
    }
}
