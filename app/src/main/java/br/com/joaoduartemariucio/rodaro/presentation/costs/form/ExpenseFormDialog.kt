package br.com.joaoduartemariucio.rodaro.presentation.costs.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.app.LocalDependencies
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDiscount
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineSeverity
import br.com.joaoduartemariucio.rodaro.presentation.common.DateField
import br.com.joaoduartemariucio.rodaro.presentation.common.FailureMessage
import br.com.joaoduartemariucio.rodaro.presentation.common.FormDialog
import br.com.joaoduartemariucio.rodaro.presentation.common.FormState
import br.com.joaoduartemariucio.rodaro.presentation.common.LabeledNumberField
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.common.OptionalDateField
import br.com.joaoduartemariucio.rodaro.presentation.common.Palette
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionCard
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionDivider
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionFooter
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionHeader
import br.com.joaoduartemariucio.rodaro.presentation.common.ValidationFooter
import br.com.joaoduartemariucio.rodaro.presentation.common.distanceUnit
import br.com.joaoduartemariucio.rodaro.presentation.common.icon
import br.com.joaoduartemariucio.rodaro.presentation.common.keepsKeyboard
import br.com.joaoduartemariucio.rodaro.presentation.common.label
import br.com.joaoduartemariucio.rodaro.presentation.common.rememberLocalizer
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseFormDialog(
    mode: ExpenseFormViewModel.Mode,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dependencies = LocalDependencies.current
    val viewModel =
        remember {
            ExpenseFormViewModel(
                mode = mode,
                repository = dependencies.expenseRepository,
                dateProvider = dependencies.dateProvider,
                analytics = dependencies.analytics,
            )
        }
    val coroutines = rememberCoroutineScope()
    val localizer = rememberLocalizer()
    val zone = dependencies.dateProvider.zone
    val today =
        dependencies.dateProvider.now
            .atZone(zone)
            .toLocalDate()

    FormDialog(
        title = stringResource(if (mode is ExpenseFormViewModel.Mode.Create) R.string.new_expense else R.string.edit_expense),
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
        item {
            SectionHeader(stringResource(R.string.expense_category))
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (category in ExpenseCategory.entries) {
                    FilterChip(
                        selected = viewModel.category == category,
                        onClick = { viewModel.category = category },
                        label = { Text(stringResource(category.label)) },
                        leadingIcon = { Icon(category.icon, contentDescription = null, tint = Palette.expense) },
                    )
                }
            }
        }

        item {
            SectionCard(Modifier.padding(top = 16.dp)) {
                LabeledNumberField(
                    title = stringResource(R.string.expense_amount),
                    style = NumberInput.Style.MONEY,
                    text = viewModel.amount,
                    onTextChange = { viewModel.amount = it },
                )
                SectionDivider()
                SwitchRow(stringResource(R.string.expense_refund), viewModel.isRefund) { viewModel.isRefund = it }
                SectionDivider()
                DateField(stringResource(R.string.date), viewModel.date, { viewModel.date = it }, zone)
                SectionDivider()
                LabeledNumberField(
                    title = stringResource(R.string.expense_odometer_optional),
                    unit = distanceUnit(),
                    style = NumberInput.Style.KILOMETERS,
                    text = viewModel.odometer,
                    onTextChange = { viewModel.odometer = it },
                )
            }
        }

        if (viewModel.isFixed) {
            item {
                SectionHeader(stringResource(R.string.expense_coverage))
                SectionCard {
                    OptionalDateField(stringResource(R.string.expense_coverage_start), viewModel.coverageStart, {
                        viewModel.coverageStart =
                            it
                    }, today, zone)
                    SectionDivider()
                    OptionalDateField(
                        stringResource(R.string.expense_coverage_end),
                        viewModel.coverageEnd,
                        { viewModel.coverageEnd = it },
                        today,
                        zone,
                    )
                }
                viewModel.coverageHint(localizer)?.let { hint ->
                    SectionFooter {
                        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (viewModel.isFine) {
            item {
                SectionHeader(stringResource(R.string.expense_fine_details))
                SectionCard {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (severity in FineSeverity.entries) {
                            FilterChip(
                                selected = viewModel.severity == severity,
                                onClick = { viewModel.severity = severity },
                                label = { Text(stringResource(severity.label)) },
                            )
                        }
                    }
                    SectionDivider()
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (discount in FineDiscount.entries) {
                            FilterChip(
                                selected = viewModel.discount == discount,
                                onClick = { viewModel.discount = discount },
                                label = { Text(stringResource(discount.label)) },
                            )
                        }
                    }
                    SectionDivider()
                    OutlinedTextField(
                        value = viewModel.points,
                        onValueChange = { viewModel.points = it },
                        label = { Text(stringResource(R.string.expense_fine_points)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().keepsKeyboard(),
                    )
                    SectionDivider()
                    OptionalDateField(stringResource(R.string.expense_fine_infraction_date), viewModel.infractionDate, {
                        viewModel.infractionDate =
                            it
                    }, today, zone)
                    SectionDivider()
                    OptionalDateField(
                        stringResource(R.string.expense_fine_due_date),
                        viewModel.dueDate,
                        { viewModel.dueDate = it },
                        today,
                        zone,
                    )
                    SectionDivider()
                    SwitchRow(stringResource(R.string.expense_fine_paid), viewModel.isPaid) { viewModel.isPaid = it }
                }
                SectionFooter {
                    Text(
                        stringResource(
                            if (viewModel.showsDiscountCaveat) R.string.expense_fine_sne_caveat else R.string.expense_fine_points_note,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (viewModel.showsDiscountCaveat) Palette.orange else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    maxLines = 5,
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

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
