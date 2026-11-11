package br.com.joaoduartemariucio.rodaro.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    title: String,
    date: Instant,
    onDateChange: (Instant) -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var isPicking by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalButton(onClick = { isPicking = true }) { Text(date.formattedFullDate(zone)) }
    }

    if (isPicking) {
        val local = date.atZone(zone)
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    local
                        .toLocalDate()
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
                        .toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { isPicking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onDateChange(combine(day, local.toLocalTime(), zone))
                    }
                    isPicking = false
                }) { Text(stringResource(R.string.done)) }
            },
            dismissButton = { TextButton(onClick = { isPicking = false }) { Text(stringResource(R.string.cancel)) } },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun DateField(
    title: String,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    DateField(title, date.atStartOfDay(zone).toInstant(), { onDateChange(it.atZone(zone).toLocalDate()) }, zone)
}

@Composable
fun OptionalDateField(
    title: String,
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    if (date == null) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { onDateChange(today) }) { Text(stringResource(R.string.date_set)) }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { DateField(title, date, { onDateChange(it) }, zone) }
            IconButton(onClick = { onDateChange(null) }) {
                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.date_clear))
            }
        }
    }
}

private fun combine(
    day: LocalDate,
    time: java.time.LocalTime,
    zone: ZoneId,
): Instant = day.atTime(time).atZone(zone).toInstant()
