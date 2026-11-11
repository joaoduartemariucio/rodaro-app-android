package br.com.joaoduartemariucio.rodaro.presentation.vehicles.form.fipe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.app.LocalDependencies
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeDetail
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import br.com.joaoduartemariucio.rodaro.presentation.common.FailureMessage
import br.com.joaoduartemariucio.rodaro.presentation.common.LoadingIndicator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FipePickerDialog(
    kind: VehicleKind,
    onPicked: (FipeDetail) -> Unit,
    onDismiss: () -> Unit,
) {
    val dependencies = LocalDependencies.current
    val viewModel = remember { FipePickerViewModel(kind, dependencies.fipeCatalog, dependencies.analytics) }
    val coroutines = rememberCoroutineScope()

    LaunchedEffect(viewModel) { viewModel.start() }

    Dialog(
        onDismissRequest = { if (!viewModel.back()) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        val step =
                            when (viewModel.step) {
                                FipePickerViewModel.Step.BRAND -> R.string.fipe_step_brand
                                FipePickerViewModel.Step.MODEL -> R.string.fipe_step_model
                                FipePickerViewModel.Step.YEAR -> R.string.fipe_step_year
                            }
                        Text(stringResource(step), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (!viewModel.back()) onDismiss() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                listOfNotNull(viewModel.brand?.name, viewModel.model?.name.takeIf { viewModel.step == FipePickerViewModel.Step.YEAR })
                    .takeIf { viewModel.step != FipePickerViewModel.Step.BRAND }
                    ?.let {
                        Text(
                            it.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                OutlinedTextField(
                    value = viewModel.query,
                    onValueChange = { viewModel.query = it },
                    label = { Text(stringResource(R.string.fipe_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                val error = viewModel.error
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        viewModel.isLoading -> {
                            LoadingIndicator(Modifier.align(Alignment.Center))
                        }

                        error != null -> {
                            Column(Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                FailureMessage(error)
                                TextButton(
                                    onClick = { coroutines.launch { viewModel.retry() } },
                                ) { Text(stringResource(R.string.fipe_retry)) }
                            }
                        }

                        viewModel.visibleOptions.isEmpty() -> {
                            Text(stringResource(R.string.fipe_empty), modifier = Modifier.align(Alignment.Center))
                        }

                        else -> {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(viewModel.visibleOptions, key = { it.code }) { option ->
                                    Text(
                                        option.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    coroutines.launch {
                                                        viewModel.select(option)?.let {
                                                            onPicked(it)
                                                            onDismiss()
                                                        }
                                                    }
                                                }.padding(horizontal = 16.dp, vertical = 14.dp),
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.fipe_source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
