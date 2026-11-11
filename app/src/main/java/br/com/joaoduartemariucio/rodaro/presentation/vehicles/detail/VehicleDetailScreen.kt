package br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.app.LocalDependencies
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueItem
import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.PriceComparison
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceRule
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.presentation.common.EmptyState
import br.com.joaoduartemariucio.rodaro.presentation.common.LoadingIndicator
import br.com.joaoduartemariucio.rodaro.presentation.common.Palette
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionCard
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionDivider
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionHeader
import br.com.joaoduartemariucio.rodaro.presentation.common.SwipeAction
import br.com.joaoduartemariucio.rodaro.presentation.common.SwipeActionsBox
import br.com.joaoduartemariucio.rodaro.presentation.common.SymbolBadge
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedCurrency
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedMonthAndYear
import br.com.joaoduartemariucio.rodaro.presentation.common.message
import br.com.joaoduartemariucio.rodaro.presentation.common.rememberLocalizer
import br.com.joaoduartemariucio.rodaro.presentation.costs.form.ExpenseFormDialog
import br.com.joaoduartemariucio.rodaro.presentation.costs.form.ExpenseFormViewModel
import br.com.joaoduartemariucio.rodaro.presentation.dues.form.DueItemFormDialog
import br.com.joaoduartemariucio.rodaro.presentation.dues.form.DueItemFormViewModel
import br.com.joaoduartemariucio.rodaro.presentation.dues.payment.DuePaymentDialog
import br.com.joaoduartemariucio.rodaro.presentation.fueling.advisor.FuelAdvisorDialog
import br.com.joaoduartemariucio.rodaro.presentation.fueling.form.FuelEntryFormDialog
import br.com.joaoduartemariucio.rodaro.presentation.fueling.form.FuelEntryFormViewModel
import br.com.joaoduartemariucio.rodaro.presentation.maintenance.form.ServiceEntryFormDialog
import br.com.joaoduartemariucio.rodaro.presentation.maintenance.form.ServiceEntryFormViewModel
import br.com.joaoduartemariucio.rodaro.presentation.reports.HistoryReportDialog
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.VehicleStats
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.components.ConsumptionChartCard
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.components.DueItemRow
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.components.ExpenseRow
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.components.ExpenseSummaryCard
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.components.FipeValueCard
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.components.FuelEntryRow
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.components.ServiceDueRow
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.components.ServiceEntryRow
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.components.SpendingChartCard
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.components.VehicleHeroCard
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.components.VehicleSummaryCard
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.form.VehicleFormDialog
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.form.VehicleFormViewModel
import kotlinx.coroutines.launch
import java.util.UUID

private sealed interface DetailSheet {
    data object NewFuel : DetailSheet

    data object NewService : DetailSheet

    data class EditFuel(
        val entry: FuelEntry,
    ) : DetailSheet

    data class EditService(
        val entry: ServiceEntry,
    ) : DetailSheet

    data object FuelAdvisor : DetailSheet

    data object HistoryReport : DetailSheet

    data object NewExpense : DetailSheet

    data object NewDueItem : DetailSheet

    data class EditDueItem(
        val item: DueItem,
    ) : DetailSheet

    data class PayDueItem(
        val item: DueItem,
    ) : DetailSheet

    data class EditExpense(
        val expense: Expense,
    ) : DetailSheet

    data class EditVehicle(
        val rules: List<ServiceRule>,
    ) : DetailSheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailScreen(
    vehicle: Vehicle,
    onBack: () -> Unit,
    onOpenYearReview: (Vehicle) -> Unit = {},
) {
    val dependencies = LocalDependencies.current
    val viewModel =
        viewModel {
            VehicleDetailViewModel(
                vehicle = vehicle,
                vehicleRepository = dependencies.vehicleRepository,
                fuelEntryRepository = dependencies.fuelEntryRepository,
                serviceRepository = dependencies.serviceRepository,
                expenseRepository = dependencies.expenseRepository,
                dueItemRepository = dependencies.dueItemRepository,
                dateProvider = dependencies.dateProvider,
                reminders = dependencies.reminderSync,
                analytics = dependencies.analytics,
                fipeQuoteRepository = dependencies.fipeQuoteRepository,
                fipeCatalog = dependencies.fipeCatalog,
                backoff = dependencies.fipeRefreshBackoff,
                regionalPriceSource = dependencies.regionalPriceSource,
            )
        }
    val coroutines = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<DetailSheet?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showsMenu by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(viewModel) {
        viewModel.load()
        launch { viewModel.refreshValuation() }
        launch { viewModel.loadRegionalPrices() }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = { Text(viewModel.vehicle.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
                actions = {
                    IconButton(onClick = { sheet = DetailSheet.EditVehicle(viewModel.rules) }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_vehicle))
                    }
                    Box {
                        IconButton(onClick = { showsMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = showsMenu, onDismissRequest = { showsMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.review_menu)) },
                                leadingIcon = { Icon(Icons.Filled.AutoGraph, contentDescription = null) },
                                onClick = {
                                    showsMenu = false
                                    onOpenYearReview(viewModel.vehicle)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.report_menu)) },
                                leadingIcon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) },
                                onClick = {
                                    showsMenu = false
                                    sheet = DetailSheet.HistoryReport
                                },
                            )
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
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AddButton(stringResource(R.string.fuel_up), Icons.Filled.LocalGasStation, Palette.fuel, Modifier.weight(1f)) {
                    sheet = DetailSheet.NewFuel
                }
                AddButton(stringResource(R.string.service), Icons.Filled.Build, Palette.service, Modifier.weight(1f)) {
                    sheet = DetailSheet.NewService
                }
                AddButton(stringResource(R.string.expense), Icons.Filled.Receipt, Palette.expense, Modifier.weight(1f)) {
                    sheet = DetailSheet.NewExpense
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val state = viewModel.state) {
                VehicleDetailViewModel.State.Loading -> {
                    LoadingIndicator(Modifier.align(Alignment.Center))
                }

                is VehicleDetailViewModel.State.Failed -> {
                    EmptyState(
                        title = stringResource(R.string.could_not_load_vehicle),
                        icon = Icons.Filled.Warning,
                        message = state.error.message(rememberLocalizer()),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                is VehicleDetailViewModel.State.Loaded -> {
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
                        Loaded(
                            content = state.content,
                            viewModel = viewModel,
                            onSheet = { sheet = it },
                            onDelete = { item -> coroutines.launch { viewModel.delete(item) } },
                            onDeleteDue = { item -> coroutines.launch { viewModel.deleteDueItem(item) } },
                        )
                    }
                }
            }
        }
    }

    val reload: () -> Unit = { coroutines.launch { viewModel.load() } }
    val dismiss = { sheet = null }
    when (val current = sheet) {
        DetailSheet.NewFuel -> {
            FuelEntryFormDialog(
                mode = FuelEntryFormViewModel.Mode.Create(viewModel.vehicle.id),
                otherReadings = viewModel.odometerReadings(),
                suggestedPrice = viewModel.lastPricePerLiter,
                fuelCapability = viewModel.vehicle.fuelCapability,
                suggestedPrices = viewModel.suggestedPrices,
                suggestedFuelType = viewModel.suggestedFuelType,
                regionalTable = viewModel.regionalTable,
                uf = viewModel.vehicle.uf,
                onSaved = reload,
                onDismiss = dismiss,
            )
        }

        is DetailSheet.EditFuel -> {
            FuelEntryFormDialog(
                mode = FuelEntryFormViewModel.Mode.Edit(current.entry),
                otherReadings = viewModel.odometerReadings(excluding = VehicleTimeline.Item.Fuel(current.entry)),
                fuelCapability = viewModel.vehicle.fuelCapability,
                regionalTable = viewModel.regionalTable,
                uf = viewModel.vehicle.uf,
                onSaved = reload,
                onDismiss = dismiss,
            )
        }

        DetailSheet.NewService -> {
            ServiceEntryFormDialog(
                mode = ServiceEntryFormViewModel.Mode.Create(viewModel.vehicle.id),
                otherReadings = viewModel.odometerReadings(),
                vehicleKind = viewModel.vehicle.kind,
                onSaved = reload,
                onDismiss = dismiss,
            )
        }

        is DetailSheet.EditService -> {
            ServiceEntryFormDialog(
                mode = ServiceEntryFormViewModel.Mode.Edit(current.entry),
                otherReadings = viewModel.odometerReadings(excluding = VehicleTimeline.Item.Service(current.entry)),
                vehicleKind = viewModel.vehicle.kind,
                onSaved = reload,
                onDismiss = dismiss,
            )
        }

        DetailSheet.NewDueItem -> {
            DueItemFormDialog(mode = DueItemFormViewModel.Mode.Create(viewModel.vehicle), onSaved = reload, onDismiss = dismiss)
        }

        is DetailSheet.EditDueItem -> {
            DueItemFormDialog(mode = DueItemFormViewModel.Mode.Edit(current.item, viewModel.vehicle), onSaved = reload, onDismiss = dismiss)
        }

        is DetailSheet.PayDueItem -> {
            DuePaymentDialog(item = current.item, onChanged = reload, onDismiss = dismiss)
        }

        DetailSheet.NewExpense -> {
            ExpenseFormDialog(mode = ExpenseFormViewModel.Mode.Create(viewModel.vehicle.id), onSaved = reload, onDismiss = dismiss)
        }

        is DetailSheet.EditExpense -> {
            ExpenseFormDialog(mode = ExpenseFormViewModel.Mode.Edit(current.expense), onSaved = reload, onDismiss = dismiss)
        }

        DetailSheet.HistoryReport -> {
            HistoryReportDialog(vehicle = viewModel.vehicle, onDismiss = dismiss)
        }

        DetailSheet.FuelAdvisor -> {
            FuelAdvisorDialog(vehicle = viewModel.vehicle, fuel = viewModel.fuelEntries, onDismiss = dismiss)
        }

        is DetailSheet.EditVehicle -> {
            VehicleFormDialog(
                mode = VehicleFormViewModel.Mode.Edit(viewModel.vehicle),
                existingRules = current.rules,
                onSaved = { coroutines.launch { viewModel.reloadAfterEdit() } },
                onDismiss = dismiss,
            )
        }

        null -> {
            Unit
        }
    }
}

@Composable
private fun AddButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null)
        Text(
            text,
            modifier = Modifier.padding(start = 6.dp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Loaded(
    content: VehicleDetailViewModel.Content,
    viewModel: VehicleDetailViewModel,
    onSheet: (DetailSheet) -> Unit,
    onDelete: (VehicleTimeline.Item) -> Unit,
    onDeleteDue: (DueItem) -> Unit,
) {
    val stats = content.stats
    val zone = LocalDependencies.current.dateProvider.zone
    val coroutines = rememberCoroutineScope()
    var showsPaidDues by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)) {
        item(key = "hero") { SectionCard(contentPadding = PaddingValues(0.dp)) { VehicleHeroCard(viewModel.vehicle, stats) } }
        item(key = "summary") { Box(Modifier.padding(top = 12.dp)) { VehicleSummaryCard(stats) } }

        val fipe = viewModel.vehicle.fipe
        if (fipe != null && viewModel.showsValuation) {
            item(key = "fipe") {
                SectionCard(Modifier.padding(top = 16.dp)) {
                    FipeValueCard(
                        selection = fipe,
                        latest = viewModel.latestQuote,
                        summary = stats.depreciation,
                        status = viewModel.valuationStatus,
                        zone = zone,
                        onRetry = { coroutines.launch { viewModel.retryValuation() } },
                    )
                }
            }
        }

        item(key = "maintenance") {
            SectionHeader(stringResource(R.string.maintenance))
            SectionCard {
                if (stats.dues.isEmpty()) {
                    TextButton(onClick = { onSheet(DetailSheet.EditVehicle(content.rules)) }) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                        Text(stringResource(R.string.set_up_service_reminders), modifier = Modifier.padding(start = 8.dp))
                    }
                } else {
                    stats.dues.forEachIndexed { index, item ->
                        if (index > 0) SectionDivider()
                        ServiceDueRow(item)
                    }
                }
            }
        }

        item(key = "dues-header") {
            SectionHeader(stringResource(R.string.due_section_title))
        }
        items(content.dueItems, key = { "due-${it.item.id}" }) { entry ->
            DueRowSurface(entry, zone, onSheet, onDeleteDue, canPay = true)
        }
        if (content.paidDueItems.isNotEmpty()) {
            item(key = "dues-paid-toggle") {
                TextButton(onClick = { showsPaidDues = !showsPaidDues }, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(if (showsPaidDues) R.string.due_hide_paid else R.string.due_show_paid, content.paidDueItems.size))
                }
            }
            if (showsPaidDues) {
                items(content.paidDueItems, key = { "due-${it.item.id}" }) { entry ->
                    DueRowSurface(entry, zone, onSheet, onDeleteDue, canPay = false)
                }
            }
        }
        item(key = "dues-add") {
            TextButton(onClick = { onSheet(DetailSheet.NewDueItem) }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Icon(Icons.Filled.EventAvailable, contentDescription = null)
                Text(stringResource(R.string.due_add), modifier = Modifier.padding(start = 8.dp))
            }
        }

        if (viewModel.showsFuelAdvisor) {
            item(key = "advisor") {
                SectionCard(Modifier.padding(top = 16.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onSheet(DetailSheet.FuelAdvisor) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SymbolBadge(Icons.Filled.LocalGasStation, Palette.green)
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.advisor_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(R.string.advisor_caption),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }

        if (stats.consumptionHistory.size >= 2) {
            item(key = "consumption") {
                SectionCard(Modifier.padding(top = 16.dp), contentPadding = PaddingValues(0.dp)) {
                    ConsumptionChartCard(stats.consumptionHistory, stats.averageConsumption)
                }
            }
        }

        if (content.spending.any { it.total.signum() > 0 }) {
            item(key = "spending") {
                SectionCard(Modifier.padding(top = 16.dp), contentPadding = PaddingValues(0.dp)) { SpendingChartCard(content.spending) }
            }
        }

        if (viewModel.hasExpenses && stats.cost.byCategory.isNotEmpty()) {
            item(key = "expenses") {
                SectionCard(Modifier.padding(top = 16.dp), contentPadding = PaddingValues(0.dp)) { ExpenseSummaryCard(stats.cost) }
            }
        }

        if (viewModel.hasHistory) {
            item(key = "history") {
                SectionHeader(stringResource(R.string.history))
                val label = stringResource(R.string.show)
                SingleChoiceSegmentedButtonRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics { contentDescription = label },
                ) {
                    val filters = VehicleTimeline.Filter.entries
                    filters.forEachIndexed { index, filter ->
                        SegmentedButton(
                            selected = viewModel.filter == filter,
                            onClick = { viewModel.filter = filter },
                            shape = SegmentedButtonDefaults.itemShape(index, filters.size),
                            label = { Text(stringResource(filter.label)) },
                        )
                    }
                }
            }
            for (section in viewModel.visibleSections) {
                item(key = "month-${section.month}") {
                    SectionHeader(section.month.formattedMonthAndYear(), trailing = section.total.formattedCurrency)
                }
                items(section.items, key = { it.key }) { item ->
                    HistoryRow(item, stats, viewModel.priceComparisons, onSheet, onDelete)
                }
            }
        } else {
            item(key = "empty") {
                SectionCard(Modifier.padding(top = 16.dp)) {
                    EmptyState(
                        title = stringResource(R.string.no_history_yet),
                        icon = Icons.AutoMirrored.Filled.ListAlt,
                        message = stringResource(R.string.history_empty_message),
                    )
                }
            }
        }
    }
}

@Composable
private fun DueRowSurface(
    entry: VehicleDetailViewModel.DueEntry,
    zone: java.time.ZoneId,
    onSheet: (DetailSheet) -> Unit,
    onDelete: (DueItem) -> Unit,
    canPay: Boolean,
) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp).clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        SwipeActionsBox(
            endAction = SwipeAction(stringResource(R.string.delete), Icons.Filled.Delete, Palette.red) { onDelete(entry.item) },
        ) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Box(Modifier.clickable { onSheet(DetailSheet.EditDueItem(entry.item)) }.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    DueItemRow(entry.item, entry.level, zone, if (canPay) ({ onSheet(DetailSheet.PayDueItem(entry.item)) }) else null)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: VehicleTimeline.Item,
    stats: VehicleStats,
    comparisons: Map<UUID, PriceComparison.Compared>,
    onSheet: (DetailSheet) -> Unit,
    onDelete: (VehicleTimeline.Item) -> Unit,
) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp).clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        SwipeActionsBox(endAction = SwipeAction(stringResource(R.string.delete), Icons.Filled.Delete, Palette.red) { onDelete(item) }) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Box(
                    Modifier
                        .clickable {
                            when (item) {
                                is VehicleTimeline.Item.Fuel -> onSheet(DetailSheet.EditFuel(item.entry))
                                is VehicleTimeline.Item.Service -> onSheet(DetailSheet.EditService(item.entry))
                                is VehicleTimeline.Item.Expense -> onSheet(DetailSheet.EditExpense(item.expense))
                            }
                        }.padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    when (item) {
                        is VehicleTimeline.Item.Fuel -> {
                            FuelEntryRow(
                                item.entry,
                                stats.consumptionByEntry[item.entry.id],
                                comparisons[item.entry.id],
                            )
                        }

                        is VehicleTimeline.Item.Service -> {
                            ServiceEntryRow(item.entry)
                        }

                        is VehicleTimeline.Item.Expense -> {
                            ExpenseRow(item.expense)
                        }
                    }
                }
            }
        }
    }
}
