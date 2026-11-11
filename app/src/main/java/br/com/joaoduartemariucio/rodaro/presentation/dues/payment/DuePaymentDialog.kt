package br.com.joaoduartemariucio.rodaro.presentation.dues.payment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.app.LocalDependencies
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueItem
import br.com.joaoduartemariucio.rodaro.presentation.common.DateField
import br.com.joaoduartemariucio.rodaro.presentation.common.FailureMessage
import br.com.joaoduartemariucio.rodaro.presentation.common.FormDialog
import br.com.joaoduartemariucio.rodaro.presentation.common.FormState
import br.com.joaoduartemariucio.rodaro.presentation.common.LabeledNumberField
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionCard
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionDivider
import br.com.joaoduartemariucio.rodaro.presentation.common.ValidationFooter
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedFullDate
import br.com.joaoduartemariucio.rodaro.presentation.common.label
import kotlinx.coroutines.launch

@Composable
fun DuePaymentDialog(
    item: DueItem,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dependencies = LocalDependencies.current
    val viewModel =
        remember {
            DuePaymentViewModel(
                item,
                dependencies.dueItemRepository,
                dependencies.expenseRepository,
                dependencies.dateProvider,
                dependencies.analytics,
            )
        }
    val coroutines = rememberCoroutineScope()
    val zone = dependencies.dateProvider.zone

    val suggestion = viewModel.suggestion
    if (suggestion != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(item.kind.label)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(
                            R.string.due_next_year_prompt,
                            suggestion.dueDate
                                .atStartOfDay(zone)
                                .toInstant()
                                .formattedFullDate(zone),
                        ),
                    )
                    val failure = viewModel.state
                    if (failure is FormState.Failed) FailureMessage(failure.error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutines.launch {
                        viewModel.acceptSuggestion()
                        if (viewModel.state == FormState.Saved) {
                            onChanged()
                            onDismiss()
                        }
                    }
                }) { Text(stringResource(R.string.due_next_year_create)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.declineSuggestion()
                    onDismiss()
                }) { Text(stringResource(R.string.due_next_year_skip)) }
            },
        )
        return
    }

    FormDialog(
        title = stringResource(R.string.due_pay_title),
        state = viewModel.state,
        canSave = viewModel.canConfirm,
        onSave = {
            coroutines.launch {
                viewModel.confirm()
                if (viewModel.state == FormState.Saved) {
                    onChanged()
                    if (viewModel.suggestion == null) onDismiss()
                }
            }
        },
        onCancel = onDismiss,
        saveLabel = stringResource(R.string.due_pay_confirm),
    ) {
        item {
            SectionCard(Modifier.padding(top = 16.dp)) {
                DateField(stringResource(R.string.due_paid_on), viewModel.paidOn, { viewModel.paidOn = it }, zone)
                SectionDivider()
                LabeledNumberField(
                    title = stringResource(R.string.expense_amount),
                    style = NumberInput.Style.MONEY,
                    text = viewModel.amount,
                    onTextChange = { viewModel.amount = it },
                )
                if (viewModel.offersExpense) {
                    SectionDivider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.due_create_expense),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = viewModel.createsExpense, onCheckedChange = { viewModel.createsExpense = it })
                    }
                }
            }
        }

        val state = viewModel.state
        if (state is FormState.Failed) {
            item { SectionCard(Modifier.padding(top = 16.dp)) { FailureMessage(state.error) } }
        }

        if (viewModel.issues.isNotEmpty()) {
            item { SectionCard(Modifier.padding(top = 16.dp)) { ValidationFooter(viewModel.issues) } }
        }
    }
}
