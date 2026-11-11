package br.com.joaoduartemariucio.rodaro.presentation.vehicles.form.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.presentation.common.LabeledNumberField
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.common.Palette
import br.com.joaoduartemariucio.rodaro.presentation.common.SymbolBadge
import br.com.joaoduartemariucio.rodaro.presentation.common.ValidationFooter
import br.com.joaoduartemariucio.rodaro.presentation.common.distanceUnit
import br.com.joaoduartemariucio.rodaro.presentation.common.icon
import br.com.joaoduartemariucio.rodaro.presentation.common.label
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.form.VehicleFormViewModel

@Composable
fun ServiceRuleRow(
    rule: VehicleFormViewModel.RuleDraft,
    kinds: List<ServiceKind>,
    hint: VehicleFormViewModel.ManualHint,
    onSelect: (ServiceKind) -> Unit,
    onRemove: () -> Unit,
) {
    var isPicking by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SymbolBadge(rule.kind.icon, Palette.service, size = 32.dp)
            Box {
                TextButton(onClick = { isPicking = true }) {
                    Text(stringResource(rule.kind.label))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.service_type))
                }
                DropdownMenu(expanded = isPicking, onDismissRequest = { isPicking = false }) {
                    for (kind in kinds) {
                        DropdownMenuItem(
                            text = { Text(stringResource(kind.label)) },
                            leadingIcon = { Icon(kind.icon, contentDescription = null) },
                            onClick = {
                                onSelect(kind)
                                isPicking = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.RemoveCircle,
                    contentDescription = stringResource(R.string.remove_reminder),
                    tint = Palette.red,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        LabeledNumberField(
            title = stringResource(R.string.every),
            unit = distanceUnit(),
            style = NumberInput.Style.KILOMETERS,
            text = rule.kilometerInterval,
            onTextChange = { rule.kilometerInterval = it },
        )
        LabeledNumberField(
            title = stringResource(R.string.or_every),
            unit = stringResource(R.string.unit_months),
            style = NumberInput.Style.MONTHS,
            text = rule.monthInterval,
            onTextChange = { rule.monthInterval = it },
        )
        if (hint != VehicleFormViewModel.ManualHint.NONE) {
            Text(
                stringResource(
                    if (hint == VehicleFormViewModel.ManualHint.STRONG) R.string.rule_default_hint_strong else R.string.rule_default_hint,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (hint == VehicleFormViewModel.ManualHint.STRONG) Palette.orange else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ValidationFooter(rule.issues)
    }
}
