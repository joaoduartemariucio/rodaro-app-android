package br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sell
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
import br.com.joaoduartemariucio.rodaro.domain.valuation.calculations.DepreciationSummary
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeQuote
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeSelection
import br.com.joaoduartemariucio.rodaro.presentation.common.FailureMessage
import br.com.joaoduartemariucio.rodaro.presentation.common.Palette
import br.com.joaoduartemariucio.rodaro.presentation.common.SymbolBadge
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedCurrency
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedDay
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedShortMonthAndYear
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.VehicleDetailViewModel.ValuationStatus
import java.time.ZoneId

@Composable
fun FipeValueCard(
    selection: FipeSelection,
    latest: FipeQuote?,
    summary: DepreciationSummary?,
    status: ValuationStatus,
    zone: ZoneId,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SymbolBadge(Icons.Filled.Sell, Palette.green)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.fipe_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${selection.modelName} · ${selection.yearName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            latest?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text(it.price.formattedCurrency, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.fipe_reference, it.month.formattedShortMonthAndYear(zone)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (latest == null) {
            when (status) {
                is ValuationStatus.Failed -> {
                    FailureMessage(status.error)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.fipe_retry)) }
                }

                else -> {
                    Caption(stringResource(R.string.fipe_loading))
                }
            }
        }

        summary?.let { depreciation ->
            depreciation.purchasePrice?.let { paid ->
                Caption(
                    stringResource(
                        R.string.fipe_purchase_vs_fipe,
                        paid.formattedCurrency,
                        depreciation.purchaseVsLatest?.formattedCurrency.orEmpty(),
                    ),
                )
            }
            if (depreciation.months > 0) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (depreciation.isAppreciation) R.string.fipe_appreciation else R.string.fipe_depreciation),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        depreciation.amount.abs().formattedCurrency,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                val start = depreciation.start.month.formattedShortMonthAndYear(zone)
                Caption(
                    stringResource(
                        if (depreciation.startsAtPurchase) R.string.fipe_since_purchase else R.string.fipe_since_first_value,
                        start,
                    ),
                )
                val perMonth = depreciation.perMonth
                val perKilometer = depreciation.perKilometer
                if (perMonth != null && perKilometer != null) {
                    Caption(
                        stringResource(R.string.fipe_per_month, perMonth.abs().formattedCurrency) + " · " +
                            stringResource(R.string.fipe_per_km, perKilometer.abs().formattedCurrency),
                    )
                } else {
                    Caption(stringResource(R.string.fipe_needs_more_data))
                }
            }
        }

        if (latest != null) {
            val fetched = latest.fetchedAt.formattedDay(zone)
            Caption(
                if (status is ValuationStatus.Failed) {
                    stringResource(R.string.fipe_offline_last_value, fetched)
                } else {
                    stringResource(R.string.fipe_updated_at, fetched)
                },
            )
        }
        Text(
            stringResource(R.string.fipe_source),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
