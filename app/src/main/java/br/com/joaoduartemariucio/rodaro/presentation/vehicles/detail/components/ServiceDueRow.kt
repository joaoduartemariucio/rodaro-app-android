package br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddRoad
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.MaintenanceForecast
import br.com.joaoduartemariucio.rodaro.presentation.common.ProgressBar
import br.com.joaoduartemariucio.rodaro.presentation.common.StatusCapsule
import br.com.joaoduartemariucio.rodaro.presentation.common.SymbolBadge
import br.com.joaoduartemariucio.rodaro.presentation.common.color
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedDay
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedKilometers
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedRemainingKilometers
import br.com.joaoduartemariucio.rodaro.presentation.common.icon
import br.com.joaoduartemariucio.rodaro.presentation.common.label
import br.com.joaoduartemariucio.rodaro.presentation.common.rememberLocalizer
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.VehicleStats

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServiceDueRow(item: VehicleStats.ServiceDueItem) {
    val localizer = rememberLocalizer()
    val level = item.status.level
    val forecast = item.due.forecast
    val rule = item.due.rule

    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {
        },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SymbolBadge(rule.kind.icon, level.color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(rule.kind.label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val kilometers = rule.kilometerInterval
                val months = rule.monthInterval
                val interval =
                    when {
                        kilometers != null && months != null -> {
                            pluralStringResource(R.plurals.every_km_or_months, months, kilometers.formattedKilometers(), months)
                        }

                        kilometers != null -> {
                            stringResource(R.string.every_km, kilometers.formattedKilometers())
                        }

                        months != null -> {
                            pluralStringResource(R.plurals.every_months, months, months)
                        }

                        else -> {
                            ""
                        }
                    }
                Text(interval, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusCapsule(level)
        }

        ProgressBar(item.status.progress, level.color)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item.status.remainingKilometers?.let { Detail(Icons.Filled.AddRoad, it.formattedRemainingKilometers(localizer)) }
            val dueDate = forecast.dueDate
            if (dueDate != null) {
                val day =
                    if (forecast.trigger == MaintenanceForecast.Trigger.DISTANCE) {
                        stringResource(R.string.around_date, dueDate.formattedDay())
                    } else {
                        dueDate.formattedDay()
                    }
                Detail(Icons.Filled.CalendarMonth, "$day · ${item.relativeDue(localizer)}")
            } else {
                Detail(Icons.Filled.Speed, stringResource(R.string.log_another_reading))
            }
        }
    }
}

@Composable
private fun Detail(
    icon: ImageVector,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
