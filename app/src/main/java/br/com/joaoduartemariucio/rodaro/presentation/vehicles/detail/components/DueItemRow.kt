package br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueItem
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.MaintenanceStatus
import br.com.joaoduartemariucio.rodaro.presentation.common.Palette
import br.com.joaoduartemariucio.rodaro.presentation.common.StatusCapsule
import br.com.joaoduartemariucio.rodaro.presentation.common.SymbolBadge
import br.com.joaoduartemariucio.rodaro.presentation.common.color
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedCurrency
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedFullDate
import br.com.joaoduartemariucio.rodaro.presentation.common.icon
import br.com.joaoduartemariucio.rodaro.presentation.common.label
import java.time.ZoneId

@Composable
fun DueItemRow(
    item: DueItem,
    level: MaintenanceStatus.Level?,
    zone: ZoneId,
    onPay: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SymbolBadge(item.kind.icon, level?.color ?: Palette.green)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val installment = item.installmentNumber?.let { number -> item.installmentCount?.let { count -> " $number/$count" } }.orEmpty()
            Text(
                stringResource(item.kind.label) + installment,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            val paidAt = item.paidAt
            Text(
                if (paidAt != null) {
                    stringResource(R.string.due_paid_on_date, paidAt.formattedFullDate(zone))
                } else {
                    stringResource(
                        R.string.due_on_date,
                        item.dueDate
                            .atStartOfDay(zone)
                            .toInstant()
                            .formattedFullDate(zone),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.amount?.let { Text(it.formattedCurrency, style = MaterialTheme.typography.bodySmall) }
        }
        if (level != null) StatusCapsule(level)
        if (onPay != null) TextButton(onClick = onPay) { Text(stringResource(R.string.due_pay)) }
    }
}
