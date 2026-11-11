package br.com.joaoduartemariucio.rodaro.presentation.reports

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.app.LocalDependencies
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.presentation.common.FailureMessage
import br.com.joaoduartemariucio.rodaro.presentation.common.FormDialog
import br.com.joaoduartemariucio.rodaro.presentation.common.FormState
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionCard
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionDivider
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionFooter
import br.com.joaoduartemariucio.rodaro.presentation.common.rememberLocalizer
import kotlinx.coroutines.launch

@Composable
fun HistoryReportDialog(
    vehicle: Vehicle,
    onDismiss: () -> Unit,
) {
    val dependencies = LocalDependencies.current
    val context = LocalContext.current
    val localizer = rememberLocalizer()
    val viewModel =
        remember {
            HistoryReportViewModel(
                vehicle = vehicle,
                fuelEntryRepository = dependencies.fuelEntryRepository,
                serviceRepository = dependencies.serviceRepository,
                files = dependencies.reportFileStore,
                renderer = dependencies.reportRenderer,
                dateProvider = dependencies.dateProvider,
                analytics = dependencies.analytics,
            )
        }
    val coroutines = rememberCoroutineScope()
    val chooserTitle = stringResource(R.string.report_title)
    val saveLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            uri?.let { coroutines.launch { if (viewModel.save(it.toString(), localizer)) onDismiss() } }
        }

    FormDialog(
        title = stringResource(R.string.report_title),
        state = viewModel.state,
        canSave = !viewModel.state.isSaving,
        saveLabel = stringResource(R.string.report_share),
        onSave = {
            coroutines.launch {
                val uri = viewModel.share(localizer) ?: return@launch
                val send =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri.toUri())
                        clipData = ClipData.newRawUri(viewModel.fileName, uri.toUri())
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                context.startActivity(Intent.createChooser(send, chooserTitle))
            }
        },
        onCancel = onDismiss,
    ) {
        item {
            SectionCard(Modifier.padding(top = 16.dp)) {
                Toggle(stringResource(R.string.report_include_costs), viewModel.includeCosts) { viewModel.includeCosts = it }
                SectionDivider()
                Toggle(stringResource(R.string.report_include_fuel_log), viewModel.includeFuelLog) { viewModel.includeFuelLog = it }
                SectionDivider()
                Toggle(stringResource(R.string.report_full_plate), viewModel.showFullPlate) { viewModel.showFullPlate = it }
            }
            SectionFooter {
                Text(
                    stringResource(R.string.report_never_included),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            TextButton(
                onClick = { saveLauncher.launch(viewModel.fileName) },
                enabled = !viewModel.state.isSaving,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(Icons.Filled.SaveAlt, contentDescription = null)
                Text(stringResource(R.string.report_save), modifier = Modifier.padding(start = 8.dp))
            }
        }

        val state = viewModel.state
        if (state is FormState.Failed) {
            item { SectionCard(Modifier.padding(top = 16.dp)) { FailureMessage(state.error) } }
        }
    }
}

@Composable
private fun Toggle(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
