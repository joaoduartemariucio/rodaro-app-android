package br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.domain.costs.calculations.CostSummary
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.presentation.common.Palette
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedCurrency
import br.com.joaoduartemariucio.rodaro.presentation.common.icon
import br.com.joaoduartemariucio.rodaro.presentation.common.label

@Composable
fun ExpenseSummaryCard(cost: CostSummary) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.expenses_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        for ((category, total) in cost.byCategory.filterKeys { it != ExpenseCategory.MULTA }.toSortedMap()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(category.icon, contentDescription = null, tint = Palette.expense)
                Text(stringResource(category.label), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(total.formattedCurrency, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        if (cost.fines.signum() != 0) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(ExpenseCategory.MULTA.icon, contentDescription = null, tint = Palette.red)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(ExpenseCategory.MULTA.label), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.expense_avoidable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(cost.fines.formattedCurrency, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
