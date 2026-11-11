package br.com.joaoduartemariucio.rodaro.presentation.dues.form

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.app.NoAnalytics
import br.com.joaoduartemariucio.rodaro.domain.common.services.Analytics
import br.com.joaoduartemariucio.rodaro.domain.common.services.DateProvider
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueItem
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueKind
import br.com.joaoduartemariucio.rodaro.domain.dues.repositories.DueItemRepository
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.presentation.common.FormState
import br.com.joaoduartemariucio.rodaro.presentation.common.Localizer
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.common.ValidationIssue
import br.com.joaoduartemariucio.rodaro.presentation.common.blocksSaving
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

class DueItemFormViewModel(
    private val mode: Mode,
    private val repository: DueItemRepository,
    private val dateProvider: DateProvider,
    private val analytics: Analytics = NoAnalytics,
) : ViewModel() {
    sealed interface Mode {
        val vehicle: Vehicle?

        data class Create(
            override val vehicle: Vehicle?,
            val kind: DueKind? = null,
        ) : Mode

        data class Edit(
            val item: DueItem,
            override val vehicle: Vehicle?,
        ) : Mode
    }

    private val existing: DueItem? = (mode as? Mode.Edit)?.item

    val availableKinds: List<DueKind> =
        if (mode.vehicle == null) listOf(DueKind.CNH) else listOf(DueKind.IPVA, DueKind.LICENCIAMENTO, DueKind.SEGURO)

    var kind by mutableStateOf(existing?.kind ?: (mode as? Mode.Create)?.kind?.takeIf { it in availableKinds } ?: availableKinds.first())
    var dueDate: LocalDate by mutableStateOf(existing?.dueDate ?: dateProvider.now.atZone(dateProvider.zone).toLocalDate())
    var amount by mutableStateOf(existing?.amount?.let { NumberInput.text(it, NumberInput.Style.MONEY) }.orEmpty())
    var note by mutableStateOf(existing?.note.orEmpty())
    private var numberText by mutableStateOf(existing?.installmentNumber?.toString().orEmpty())
    private var countText by mutableStateOf(existing?.installmentCount?.toString().orEmpty())

    var installmentNumber: String
        get() = numberText
        set(value) {
            numberText = digits(value)
        }

    var installmentCount: String
        get() = countText
        set(value) {
            countText = digits(value)
        }

    var state: FormState by mutableStateOf(FormState.Editing)
        private set

    val isDriverItem: Boolean get() = mode.vehicle == null

    val parsedAmount: BigDecimal? get() = NumberInput.decimal(amount, NumberInput.Style.MONEY)?.takeIf { it.signum() > 0 }

    val parsedInstallmentNumber: Int? get() = installmentNumber.toIntOrNull()

    val parsedInstallmentCount: Int? get() = installmentCount.toIntOrNull()

    val plateFinalDigit: Char? get() = mode.vehicle?.plate?.lastOrNull { it.isDigit() }

    val uf: String? get() = mode.vehicle?.uf

    val issues: List<ValidationIssue>
        get() {
            val number = parsedInstallmentNumber
            val count = parsedInstallmentCount
            val isInvalid =
                (number == null) != (count == null) ||
                    (number != null && count != null && (number < 1 || count < 1 || number > count))
            return if (isInvalid) listOf(ValidationIssue.InstallmentInvalid) else emptyList()
        }

    val canSave: Boolean get() = !issues.blocksSaving && !state.isSaving

    fun calendarHint(localizer: Localizer): String {
        if (isDriverItem) return localizer.string(R.string.due_cnh_hint)
        val region = uf ?: localizer.string(R.string.due_your_state)
        val digit = plateFinalDigit ?: return localizer.string(R.string.due_calendar_hint, region)
        return localizer.string(R.string.due_plate_hint, digit.toString(), region)
    }

    suspend fun save() {
        if (issues.blocksSaving) return

        state = FormState.Saving
        state =
            try {
                repository.save(item())
                analytics.event(
                    "due_item_saved",
                    mapOf("mode" to if (mode is Mode.Create) "create" else "edit", "kind" to kind.rawValue),
                )
                FormState.Saved
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                FormState.Failed(error)
            }
    }

    private fun item(): DueItem {
        val base =
            existing ?: DueItem(
                vehicleId = mode.vehicle?.id,
                kind = kind,
                dueDate = dueDate,
                createdAt = dateProvider.now,
            )
        return base.copy(
            kind = kind,
            dueDate = dueDate,
            amount = parsedAmount,
            installmentNumber = parsedInstallmentNumber,
            installmentCount = parsedInstallmentCount,
            note = note.trim(),
        )
    }

    private companion object {
        const val MAX_INSTALLMENT_DIGITS = 2

        fun digits(text: String): String = text.filter { it in '0'..'9' }.take(MAX_INSTALLMENT_DIGITS)
    }
}
