package br.com.joaoduartemariucio.rodaro.presentation.dues.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.app.LocalDependencies
import br.com.joaoduartemariucio.rodaro.presentation.common.DateField
import br.com.joaoduartemariucio.rodaro.presentation.common.FailureMessage
import br.com.joaoduartemariucio.rodaro.presentation.common.FormDialog
import br.com.joaoduartemariucio.rodaro.presentation.common.FormState
import br.com.joaoduartemariucio.rodaro.presentation.common.LabeledNumberField
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionCard
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionDivider
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionFooter
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionHeader
import br.com.joaoduartemariucio.rodaro.presentation.common.ValidationFooter
import br.com.joaoduartemariucio.rodaro.presentation.common.icon
import br.com.joaoduartemariucio.rodaro.presentation.common.keepsKeyboard
import br.com.joaoduartemariucio.rodaro.presentation.common.label
import br.com.joaoduartemariucio.rodaro.presentation.common.rememberLocalizer
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DueItemFormDialog(
    mode: DueItemFormViewModel.Mode,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dependencies = LocalDependencies.current
    val viewModel =
        remember { DueItemFormViewModel(mode, dependencies.dueItemRepository, dependencies.dateProvider, dependencies.analytics) }
    val coroutines = rememberCoroutineScope()
    val localizer = rememberLocalizer()

    FormDialog(
        title = stringResource(if (mode is DueItemFormViewModel.Mode.Create) R.string.new_due_item else R.string.edit_due_item),
        state = viewModel.state,
        canSave = viewModel.canSave,
        onSave = {
            coroutines.launch {
                viewModel.save()
                if (viewModel.state == FormState.Saved) {
                    onSaved()
                    onDismiss()
                }
            }
        },
        onCancel = onDismiss,
    ) {
        if (viewModel.availableKinds.size > 1) {
            item {
                SectionHeader(stringResource(R.string.due_kind))
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (kind in viewModel.availableKinds) {
                        FilterChip(
                            selected = viewModel.kind == kind,
                            onClick = { viewModel.kind = kind },
                            label = { Text(stringResource(kind.label)) },
                            leadingIcon = { Icon(kind.icon, contentDescription = null) },
                        )
                    }
                }
            }
        }

        item {
            SectionCard(Modifier.padding(top = 16.dp)) {
                DateField(stringResource(R.string.due_date), viewModel.dueDate, { viewModel.dueDate = it }, dependencies.dateProvider.zone)
                SectionDivider()
                LabeledNumberField(
                    title = stringResource(R.string.due_amount_optional),
                    style = NumberInput.Style.MONEY,
                    text = viewModel.amount,
                    onTextChange = { viewModel.amount = it },
                )
            }
            SectionFooter {
                Text(
                    viewModel.calendarHint(localizer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!viewModel.isDriverItem) {
            item {
                SectionHeader(stringResource(R.string.due_installment))
                SectionCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = viewModel.installmentNumber,
                            onValueChange = { viewModel.installmentNumber = it },
                            label = { Text(stringResource(R.string.due_installment_number)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).keepsKeyboard(),
                        )
                        OutlinedTextField(
                            value = viewModel.installmentCount,
                            onValueChange = { viewModel.installmentCount = it },
                            label = { Text(stringResource(R.string.due_installment_count)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).keepsKeyboard(),
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(stringResource(R.string.notes))
            SectionCard {
                OutlinedTextField(
                    value = viewModel.note,
                    onValueChange = { viewModel.note = it },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().keepsKeyboard(),
                )
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
