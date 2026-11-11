package br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.presentation.common.Palette
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.VehicleTimeline

@Composable
fun SpendingChartCard(months: List<VehicleTimeline.MonthSpending>) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.spending), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                pluralStringResource(R.plurals.last_months, months.size, months.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SpendingChart(months, stringResource(R.string.spending_per_month))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(stringResource(R.string.fuel), Palette.fuel)
            Legend(stringResource(R.string.services), Palette.service)
            Legend(stringResource(R.string.filter_expenses), Palette.expense)
        }
    }
}

@Composable
private fun Legend(
    text: String,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
