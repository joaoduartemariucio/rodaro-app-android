package br.com.joaoduartemariucio.rodaro.presentation.dues.payment

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import br.com.joaoduartemariucio.rodaro.app.NoAnalytics
import br.com.joaoduartemariucio.rodaro.domain.common.services.Analytics
import br.com.joaoduartemariucio.rodaro.domain.common.services.DateProvider
import br.com.joaoduartemariucio.rodaro.domain.costs.repositories.ExpenseRepository
import br.com.joaoduartemariucio.rodaro.domain.dues.calculations.DueSchedule
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueItem
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueKind
import br.com.joaoduartemariucio.rodaro.domain.dues.repositories.DueItemRepository
import br.com.joaoduartemariucio.rodaro.presentation.common.FormState
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.common.ValidationIssue
import br.com.joaoduartemariucio.rodaro.presentation.common.blocksSaving
import java.math.BigDecimal
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

class DuePaymentViewModel(
    private val item: DueItem,
    private val dueItemRepository: DueItemRepository,
    private val expenseRepository: ExpenseRepository,
    private val dateProvider: DateProvider,
    private val analytics: Analytics = NoAnalytics,
) : ViewModel() {
    val offersExpense: Boolean = item.vehicleId != null && item.kind != DueKind.CNH

    var paidOn: Instant by mutableStateOf(dateProvider.now)
    var amount by mutableStateOf(item.amount?.let { NumberInput.text(it, NumberInput.Style.MONEY) }.orEmpty())
    var createsExpense by mutableStateOf(offersExpense)

    var suggestion: DueItem? by mutableStateOf(null)
        private set

    var state: FormState by mutableStateOf(FormState.Editing)
        private set

    val parsedAmount: BigDecimal? get() = NumberInput.decimal(amount, NumberInput.Style.MONEY)?.takeIf { it.signum() > 0 }

    private val isCreatingExpense: Boolean get() = offersExpense && createsExpense

    val issues: List<ValidationIssue>
        get() = if (isCreatingExpense && parsedAmount == null) listOf(ValidationIssue.AmountRequired) else emptyList()

    val canConfirm: Boolean get() = !issues.blocksSaving && !state.isSaving

    suspend fun confirm() {
        if (issues.blocksSaving) return

        state = FormState.Saving
        state =
            try {
                val amount = parsedAmount
                if (isCreatingExpense && amount != null) {
                    DueSchedule.expense(item, paidOn, amount, dateProvider.zone)?.let { expenseRepository.save(it) }
                }
                dueItemRepository.save(item.copy(paidAt = paidOn, amount = amount ?: item.amount))
                suggestion = DueSchedule.nextYear(item, dateProvider.now)
                analytics.event(
                    "due_item_paid",
                    mapOf("kind" to item.kind.rawValue, "expense" to isCreatingExpense.toString()),
                )
                FormState.Saved
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                FormState.Failed(error)
            }
    }

    suspend fun acceptSuggestion() {
        val next = suggestion ?: return
        state =
            try {
                dueItemRepository.save(next)
                suggestion = null
                analytics.event("due_item_next_year_created", mapOf("kind" to next.kind.rawValue))
                FormState.Saved
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                FormState.Failed(error)
            }
    }

    fun declineSuggestion() {
        suggestion = null
    }
}
