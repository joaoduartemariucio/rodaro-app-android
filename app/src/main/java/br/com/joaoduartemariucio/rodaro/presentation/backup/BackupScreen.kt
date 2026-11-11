package br.com.joaoduartemariucio.rodaro.presentation.backup

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.app.LocalDependencies
import br.com.joaoduartemariucio.rodaro.domain.backup.entities.BackupContents
import br.com.joaoduartemariucio.rodaro.presentation.common.Palette
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionCard
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionDivider
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedFullDate
import br.com.joaoduartemariucio.rodaro.presentation.common.message
import br.com.joaoduartemariucio.rodaro.presentation.common.rememberLocalizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val dependencies = LocalDependencies.current
    val localizer = rememberLocalizer()
    val viewModel =
        viewModel {
            BackupViewModel(
                backupRepository = dependencies.backupRepository,
                codec = dependencies.backupCodec,
                documents = dependencies.documentStore,
                preferencesRepository = dependencies.preferencesRepository,
                dateProvider = dependencies.dateProvider,
                applyLanguage = dependencies.localeLanguageApplier::apply,
                syncReminders = { dependencies.reminderSync.sync() },
                isStoreAvailable = !dependencies.isUsingFallbackStore,
                analytics = dependencies.analytics,
            )
        }
    val snackbar = remember { SnackbarHostState() }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { viewModel.launchExport(it.toString()) }
        }
    val spreadsheetLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri?.let { viewModel.launchSpreadsheetExport(it.toString(), localizer) }
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.launchPrepareImport(it.toString()) }
        }
    val state = viewModel.state
    val working = state == BackupViewModel.State.Working
    val enabled = viewModel.isStoreAvailable && !working
    val unavailable = stringResource(R.string.backup_unavailable)

    BackHandler(enabled = working) {}

    if (state is BackupViewModel.State.Done) {
        val message =
            stringResource(
                when (state.result) {
                    BackupViewModel.Result.EXPORTED -> R.string.backup_exported
                    BackupViewModel.Result.SPREADSHEET_EXPORTED -> R.string.backup_spreadsheet_exported
                    BackupViewModel.Result.IMPORTED -> R.string.backup_imported
                },
            )
        LaunchedEffect(state) {
            snackbar.showSnackbar(message)
            viewModel.dismiss()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !working,
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (working) LinearProgressIndicator(Modifier.fillMaxWidth())

            SectionCard {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudSync, contentDescription = null, tint = Palette.blue)
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.backup_automatic_title),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.backup_automatic_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionCard {
                ActionRow(
                    icon = Icons.Filled.Save,
                    title = stringResource(R.string.backup_export),
                    caption = if (viewModel.isStoreAvailable) stringResource(R.string.backup_export_caption) else unavailable,
                    enabled = enabled,
                    onClick = { exportLauncher.launch(viewModel.backupFileName()) },
                )
                SectionDivider()
                ActionRow(
                    icon = Icons.Filled.Restore,
                    title = stringResource(R.string.backup_import),
                    caption = if (viewModel.isStoreAvailable) stringResource(R.string.backup_import_caption) else unavailable,
                    enabled = enabled,
                    onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*")) },
                )
            }

            SectionCard {
                ActionRow(
                    icon = Icons.Filled.TableChart,
                    title = stringResource(R.string.backup_spreadsheet),
                    caption = if (viewModel.isStoreAvailable) stringResource(R.string.backup_spreadsheet_caption) else unavailable,
                    enabled = enabled,
                    onClick = { spreadsheetLauncher.launch(viewModel.spreadsheetFileName(localizer)) },
                )
            }
        }
    }

    when (state) {
        is BackupViewModel.State.ConfirmingImport -> {
            AlertDialog(
                onDismissRequest = viewModel::cancelImport,
                title = { Text(stringResource(R.string.backup_confirm_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.backup_confirm_message))
                        Column {
                            state.exportedAt?.let { date ->
                                Text(
                                    stringResource(R.string.backup_confirm_file, date.formattedFullDate(dependencies.dateProvider.zone)),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(contentsLabel(state.incoming), style = MaterialTheme.typography.bodyMedium)
                        }
                        Column {
                            Text(stringResource(R.string.backup_confirm_current), fontWeight = FontWeight.SemiBold)
                            Text(contentsLabel(state.current), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.launchConfirmImport() }) {
                        Text(stringResource(R.string.backup_confirm_action), color = Palette.red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelImport) { Text(stringResource(R.string.cancel)) }
                },
            )
        }

        is BackupViewModel.State.Failed -> {
            AlertDialog(
                onDismissRequest = viewModel::dismiss,
                text = { Text(state.error.message(localizer)) },
                confirmButton = {
                    TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.ok)) }
                },
            )
        }

        else -> {
            Unit
        }
    }
}

@Composable
private fun contentsLabel(contents: BackupContents): String =
    stringResource(
        R.string.backup_contents,
        contents.vehicles,
        contents.fuelEntries,
        contents.serviceEntries,
        contents.serviceRules,
        contents.savedRoutes,
        contents.expenses,
        contents.dueItems,
    )

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    caption: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
    }
}
