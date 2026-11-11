package br.com.joaoduartemariucio.rodaro.presentation.vehicles.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.app.LocalDependencies
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.presentation.common.EmptyState
import br.com.joaoduartemariucio.rodaro.presentation.common.LoadingIndicator
import br.com.joaoduartemariucio.rodaro.presentation.common.Palette
import br.com.joaoduartemariucio.rodaro.presentation.common.SwipeAction
import br.com.joaoduartemariucio.rodaro.presentation.common.SwipeActionsBox
import br.com.joaoduartemariucio.rodaro.presentation.common.ads.AdNative
import br.com.joaoduartemariucio.rodaro.presentation.common.message
import br.com.joaoduartemariucio.rodaro.presentation.common.rememberLocalizer
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.form.VehicleFormDialog
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.form.VehicleFormViewModel
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.list.components.GarageOverviewCard
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.list.components.VehicleCard
import kotlinx.coroutines.launch

private sealed interface VehicleListSheet {
    data object Create : VehicleListSheet

    data class Edit(
        val vehicle: Vehicle,
    ) : VehicleListSheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleListScreen(
    scope: VehicleListViewModel.Scope,
    onOpenVehicle: (Vehicle) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val dependencies = LocalDependencies.current
    val viewModel =
        viewModel {
            VehicleListViewModel(
                scope = scope,
                vehicleRepository = dependencies.vehicleRepository,
                fuelEntryRepository = dependencies.fuelEntryRepository,
                serviceRepository = dependencies.serviceRepository,
                expenseRepository = dependencies.expenseRepository,
                dateProvider = dependencies.dateProvider,
                reminders = dependencies.reminderSync,
                analytics = dependencies.analytics,
            )
        }
    val coroutines = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<VehicleListSheet?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isActive = scope == VehicleListViewModel.Scope.ACTIVE

    LaunchedEffect(viewModel) { viewModel.load() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(if (isActive) R.string.vehicles else R.string.archived)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    }
                },
                actions = {
                    if (isActive) {
                        IconButton(onClick = { sheet = VehicleListSheet.Create }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_vehicle))
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val state = viewModel.state) {
                VehicleListViewModel.State.Loading -> {
                    LoadingIndicator(Modifier.align(Alignment.Center))
                }

                is VehicleListViewModel.State.Failed -> {
                    EmptyState(
                        title = stringResource(R.string.could_not_load_vehicles),
                        icon = Icons.Filled.Warning,
                        message = state.error.message(rememberLocalizer()),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                is VehicleListViewModel.State.Loaded -> {
                    if (state.rows.isEmpty()) {
                        EmptyState(
                            title = stringResource(if (isActive) R.string.no_vehicles_yet else R.string.nothing_archived),
                            icon = if (isActive) Icons.Filled.DirectionsCar else Icons.Filled.Archive,
                            message = stringResource(if (isActive) R.string.vehicles_empty_message else R.string.archived_empty_message),
                            modifier = Modifier.align(Alignment.Center),
                        ) {
                            if (isActive) {
                                Button(onClick = { sheet = VehicleListSheet.Create }) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Text(stringResource(R.string.add_vehicle))
                                }
                            }
                        }
                    } else {
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = {
                                coroutines.launch {
                                    isRefreshing = true
                                    viewModel.load()
                                    isRefreshing = false
                                }
                            },
                        ) {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                val overview = viewModel.overview
                                if (isActive && overview != null) {
                                    item(key = "overview") { CardSurface { GarageOverviewCard(overview) } }
                                }
                                items(state.rows, key = { it.id }) { row ->
                                    VehicleListRow(
                                        row = row,
                                        isActive = isActive,
                                        onOpen = { onOpenVehicle(row.vehicle) },
                                        onEdit = { sheet = VehicleListSheet.Edit(row.vehicle) },
                                        onArchive = { archived -> coroutines.launch { viewModel.setArchived(archived, row.vehicle) } },
                                    )
                                }
                                if (isActive) {
                                    item(key = "ad") { AdNative(Modifier.padding(top = 10.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val reload: () -> Unit = { coroutines.launch { viewModel.load() } }
    when (val current = sheet) {
        VehicleListSheet.Create -> {
            VehicleFormDialog(VehicleFormViewModel.Mode.Create, onSaved = reload, onDismiss = { sheet = null })
        }

        is VehicleListSheet.Edit -> {
            VehicleFormDialog(VehicleFormViewModel.Mode.Edit(current.vehicle), onSaved = reload, onDismiss = { sheet = null })
        }

        null -> {
            Unit
        }
    }
}

@Composable
private fun CardSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VehicleListRow(
    row: VehicleListViewModel.Row,
    isActive: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onArchive: (Boolean) -> Unit,
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    val archive =
        if (isActive) {
            SwipeAction(stringResource(R.string.archive), Icons.Filled.Archive, Palette.orange) { onArchive(true) }
        } else {
            SwipeAction(stringResource(R.string.unarchive), Icons.Filled.Unarchive, Palette.blue) { onArchive(false) }
        }
    val edit = SwipeAction(stringResource(R.string.edit), Icons.Filled.Edit, MaterialTheme.colorScheme.onSurfaceVariant, onEdit)

    Surface(
        modifier = Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        SwipeActionsBox(startAction = edit, endAction = archive) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Box(Modifier.combinedClickable(onClick = onOpen, onLongClick = { isMenuOpen = true })) {
                    VehicleCard(row)
                    DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(edit.label) },
                            leadingIcon = { Icon(edit.icon, contentDescription = null) },
                            onClick = {
                                isMenuOpen = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(archive.label) },
                            leadingIcon = { Icon(archive.icon, contentDescription = null) },
                            onClick = {
                                isMenuOpen = false
                                archive.onAction()
                            },
                        )
                    }
                }
            }
        }
    }
}
