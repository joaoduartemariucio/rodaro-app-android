package br.com.joaoduartemariucio.rodaro.presentation.trips

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.app.LocalDependencies
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RouteLookupError
import br.com.joaoduartemariucio.rodaro.domain.trips.entities.PlaceSuggestion
import br.com.joaoduartemariucio.rodaro.domain.trips.entities.SavedRoute
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.presentation.common.Chip
import br.com.joaoduartemariucio.rodaro.presentation.common.EmptyState
import br.com.joaoduartemariucio.rodaro.presentation.common.Formats
import br.com.joaoduartemariucio.rodaro.presentation.common.LabeledNumberField
import br.com.joaoduartemariucio.rodaro.presentation.common.LoadingIndicator
import br.com.joaoduartemariucio.rodaro.presentation.common.MetricTileCard
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.common.Palette
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionCard
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionDivider
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionFooter
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionHeader
import br.com.joaoduartemariucio.rodaro.presentation.common.SymbolBadge
import br.com.joaoduartemariucio.rodaro.presentation.common.ads.AdBanner
import br.com.joaoduartemariucio.rodaro.presentation.common.ads.InterstitialAds
import br.com.joaoduartemariucio.rodaro.presentation.common.consumptionUnit
import br.com.joaoduartemariucio.rodaro.presentation.common.dismissesKeyboard
import br.com.joaoduartemariucio.rodaro.presentation.common.distanceUnit
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedCurrency
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedDay
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedKilometers
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedLiters
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedPerKilometer
import br.com.joaoduartemariucio.rodaro.presentation.common.icon
import br.com.joaoduartemariucio.rodaro.presentation.common.label
import br.com.joaoduartemariucio.rodaro.presentation.common.message
import br.com.joaoduartemariucio.rodaro.presentation.common.perDistanceTitle
import br.com.joaoduartemariucio.rodaro.presentation.common.rememberLocalizer
import br.com.joaoduartemariucio.rodaro.presentation.common.volumePriceTitle
import br.com.joaoduartemariucio.rodaro.presentation.common.volumeTitle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripCostScreen(
    initialRoute: SavedRoute? = null,
    onBack: (() -> Unit)? = null,
) {
    val dependencies = LocalDependencies.current
    val viewModel =
        viewModel {
            TripCostViewModel(
                vehicleRepository = dependencies.vehicleRepository,
                fuelEntryRepository = dependencies.fuelEntryRepository,
                routeRepository = dependencies.routeRepository,
                routeEstimateProvider = dependencies.routeEstimateProvider,
                dateProvider = dependencies.dateProvider,
                analytics = dependencies.analytics,
            )
        }
    val coroutines = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(viewModel, initialRoute) {
        viewModel.load()
        initialRoute?.let { viewModel.restoreRoute(it) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(if (viewModel.isEditing) R.string.edit_route_title else R.string.new_route_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
            if (viewModel.state == TripCostViewModel.State.Loaded) {
                ResultBar(
                    viewModel = viewModel,
                    onSave = {
                        coroutines.launch {
                            if (viewModel.saveRoute()) {
                                InterstitialAds.show()
                                onBack?.invoke()
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val state = viewModel.state) {
                TripCostViewModel.State.Loading -> {
                    LoadingIndicator(Modifier.align(Alignment.Center))
                }

                is TripCostViewModel.State.Failed -> {
                    EmptyState(
                        title = stringResource(R.string.could_not_load_vehicles),
                        icon = Icons.Filled.Warning,
                        message = state.error.message(rememberLocalizer()),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                TripCostViewModel.State.Loaded -> {
                    TripForm(
                        viewModel,
                        onSelect = { vehicle -> coroutines.launch { viewModel.select(vehicle) } },
                        onOriginChange = { text -> coroutines.launch { viewModel.updateOrigin(text) } },
                        onDestinationChange = { text -> coroutines.launch { viewModel.updateDestination(text) } },
                        onOriginSuggestion = viewModel::selectOriginSuggestion,
                        onDestinationSuggestion = viewModel::selectDestinationSuggestion,
                        onLookup = { coroutines.launch { viewModel.lookupRealDistance() } },
                    )
                }
            }
        }
    }
}

@Composable
private fun TripForm(
    viewModel: TripCostViewModel,
    onSelect: (Vehicle) -> Unit,
    onOriginChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onOriginSuggestion: (PlaceSuggestion) -> Unit,
    onDestinationSuggestion: (PlaceSuggestion) -> Unit,
    onLookup: () -> Unit,
) {
    LaunchedEffect(viewModel.cost != null) {
        if (viewModel.cost != null) {
            viewModel.reportCalculation()
            InterstitialAds.show()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().dismissesKeyboard(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (viewModel.vehicles.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.vehicle))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(viewModel.vehicles, key = { it.id }) { vehicle ->
                        VehicleOption(
                            vehicle = vehicle,
                            selected = viewModel.selectedVehicle?.id == vehicle.id,
                            onClick = { onSelect(vehicle) },
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(stringResource(R.string.trip))
            SectionCard {
                RouteFields(
                    viewModel = viewModel,
                    onOriginChange = onOriginChange,
                    onDestinationChange = onDestinationChange,
                    onOriginSuggestion = onOriginSuggestion,
                    onDestinationSuggestion = onDestinationSuggestion,
                )
                LookupRow(viewModel, onLookup)
                SectionDivider()
                LabeledNumberField(
                    title = stringResource(R.string.distance),
                    unit = distanceUnit(),
                    style = NumberInput.Style.KILOMETERS,
                    text = viewModel.distance,
                    onTextChange = { viewModel.distance = it },
                )
                viewModel.hasTolls?.let { hasTolls ->
                    Chip(
                        viewModel.tollCost?.takeIf { hasTolls }?.let { stringResource(R.string.route_tolls_cost, it.formattedCurrency) }
                            ?: stringResource(if (hasTolls) R.string.route_has_tolls else R.string.route_no_tolls),
                        Icons.Filled.Toll,
                        if (hasTolls) Palette.orange else Palette.green,
                        Modifier.padding(top = 4.dp),
                    )
                }
                if (viewModel.routePath.isNotEmpty()) {
                    RouteMapCard(
                        path = viewModel.routePath,
                        origin = viewModel.origin,
                        destination = viewModel.destination,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    listOf(false, true).forEachIndexed { index, roundTrip ->
                        SegmentedButton(
                            selected = viewModel.isRoundTrip == roundTrip,
                            onClick = { viewModel.isRoundTrip = roundTrip },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        ) {
                            Text(stringResource(if (roundTrip) R.string.round_trip else R.string.one_way))
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(stringResource(R.string.fuel))
            SectionCard {
                LabeledNumberField(
                    title = stringResource(R.string.consumption),
                    unit = consumptionUnit(),
                    style = NumberInput.Style.CONSUMPTION,
                    text = viewModel.consumption,
                    onTextChange = { viewModel.consumption = it },
                )
                ConsumptionSource(viewModel)
                SectionDivider()
                LabeledNumberField(
                    title = volumePriceTitle(),
                    style = NumberInput.Style.FUEL_PRICE,
                    text = viewModel.pricePerLiter,
                    onTextChange = { viewModel.pricePerLiter = it },
                )
                PriceSource(viewModel)
            }
        }

        item { Breakdown(viewModel) }

        viewModel.saveError?.let { error ->
            item {
                SectionFooter {
                    Text(
                        stringResource(R.string.save_calculation_failed) + " " + error.message(rememberLocalizer()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        item {
            SectionFooter {
                Text(
                    stringResource(R.string.route_cost_scope_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { AdBanner(Modifier.padding(top = 16.dp)) }
    }
}

@Composable
private fun VehicleOption(
    vehicle: Vehicle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.widthIn(min = 150.dp, max = 220.dp),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SymbolBadge(vehicle.kind.icon, tint)
            Column {
                Text(
                    vehicle.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    vehicle.tankCapacity?.let { stringResource(R.string.vehicle_tank, it.formattedLiters(0)) }
                        ?: stringResource(R.string.vehicle_no_tank),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RouteFields(
    viewModel: TripCostViewModel,
    onOriginChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onOriginSuggestion: (PlaceSuggestion) -> Unit,
    onDestinationSuggestion: (PlaceSuggestion) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            PlaceField(
                title = stringResource(R.string.route_origin),
                icon = Icons.Filled.TripOrigin,
                text = viewModel.origin,
                onTextChange = onOriginChange,
                suggestions = viewModel.originSuggestions,
                onSuggestionSelected = onOriginSuggestion,
            )
            PlaceField(
                title = stringResource(R.string.route_destination),
                icon = Icons.Filled.Place,
                text = viewModel.destination,
                onTextChange = onDestinationChange,
                suggestions = viewModel.destinationSuggestions,
                onSuggestionSelected = onDestinationSuggestion,
            )
        }
        IconButton(
            onClick = viewModel::swapPlaces,
            enabled = viewModel.origin.isNotBlank() || viewModel.destination.isNotBlank(),
        ) {
            Icon(Icons.Filled.SwapVert, contentDescription = stringResource(R.string.swap_places))
        }
    }
    if (viewModel.suggestionsWorking) {
        Text(
            stringResource(R.string.searching_places),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun LookupRow(
    viewModel: TripCostViewModel,
    onLookup: () -> Unit,
) {
    when (val lookup = viewModel.lookupState) {
        is TripCostViewModel.LookupState.Working -> {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    stringResource(R.string.looking_up_distance),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        is TripCostViewModel.LookupState.Failed -> {
            Text(
                when (lookup.error) {
                    is RouteLookupError.NoApiKey -> stringResource(R.string.lookup_no_api_key)
                    is RouteLookupError.InvalidApiKey -> stringResource(R.string.lookup_invalid_api_key)
                    is RouteLookupError.QuotaExceeded -> stringResource(R.string.lookup_quota_exceeded)
                    is RouteLookupError.NetworkUnavailable -> stringResource(R.string.lookup_network_unavailable)
                    is RouteLookupError.PlaceNotFound -> stringResource(R.string.lookup_place_not_found)
                    else -> stringResource(R.string.lookup_failed) + " " + lookup.error.message(rememberLocalizer())
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        else -> {
            Unit
        }
    }
    FilledTonalButton(
        onClick = onLookup,
        enabled = viewModel.canLookupRealDistance,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null)
        Text(stringResource(R.string.lookup_real_distance), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ConsumptionSource(viewModel: TripCostViewModel) {
    val suggested = viewModel.suggestedConsumption
    when {
        viewModel.isUsingAverageConsumption -> {
            val group = viewModel.suggestedConsumptionGroup
            SourceNote(
                text =
                    if (group == null) {
                        pluralStringResource(
                            R.plurals.consumption_history_basis,
                            viewModel.averageConsumptionSamples,
                            viewModel.averageConsumptionSamples,
                        )
                    } else {
                        pluralStringResource(
                            R.plurals.consumption_history_basis_group,
                            viewModel.averageConsumptionSamples,
                            viewModel.averageConsumptionSamples,
                            stringResource(group.label),
                        )
                    },
                icon = Icons.Filled.History,
                tint = Palette.green,
            )
        }

        viewModel.canUseAverageConsumption && suggested != null -> {
            SourceNote(
                text = stringResource(R.string.consumption_edited, "$suggested ${consumptionUnit()}"),
                icon = Icons.Filled.Edit,
                tint = Palette.orange,
                action = { TextButton(onClick = viewModel::useAverageConsumption) { Text(stringResource(R.string.use_average)) } },
            )
        }

        viewModel.selectedVehicle != null && viewModel.consumption.isEmpty() -> {
            SourceNote(
                text = stringResource(R.string.consumption_missing),
                icon = Icons.Filled.Warning,
                tint = Palette.orange,
            )
        }
    }
}

@Composable
private fun PriceSource(viewModel: TripCostViewModel) {
    val date = viewModel.lastPriceDate?.formattedDay() ?: return
    when {
        viewModel.isUsingLastPrice -> {
            SourceNote(
                text = stringResource(R.string.price_from_last_fuel_up_on, date),
                icon = Icons.Filled.History,
                tint = Palette.green,
            )
        }

        viewModel.canUseLastPrice -> {
            SourceNote(
                text = stringResource(R.string.price_edited, viewModel.suggestedPrice.orEmpty()),
                icon = Icons.Filled.Edit,
                tint = Palette.orange,
                action = { TextButton(onClick = viewModel::useLastPrice) { Text(stringResource(R.string.use_last_price)) } },
            )
        }
    }
}

@Composable
private fun Breakdown(viewModel: TripCostViewModel) {
    val cost = viewModel.cost
    AnimatedVisibility(visible = cost != null) {
        if (cost == null) return@AnimatedVisibility
        Column {
            SectionHeader(stringResource(R.string.trip_cost))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricTileCard(
                    title = stringResource(R.string.total_distance),
                    value = cost.distance.formattedKilometers(),
                    caption = stringResource(if (viewModel.isRoundTrip) R.string.round_trip else R.string.one_way),
                    icon = Icons.Filled.Route,
                    tint = Palette.blue,
                    modifier = Modifier.weight(1f),
                )
                MetricTileCard(
                    title = volumeTitle(),
                    value = cost.liters.formattedLiters(1),
                    caption = null,
                    icon = Icons.Filled.LocalGasStation,
                    tint = Palette.fuel,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricTileCard(
                    title = perDistanceTitle(),
                    value = cost.costPerKilometer.formattedPerKilometer(rememberLocalizer()),
                    caption = null,
                    icon = Icons.Filled.Paid,
                    tint = Palette.green,
                    modifier = Modifier.weight(1f),
                )
                MetricTileCard(
                    title = stringResource(R.string.consumption),
                    value = viewModel.consumption.takeIf { it.isNotEmpty() }?.let { "$it ${consumptionUnit()}" },
                    caption = null,
                    icon = Icons.Filled.Speed,
                    tint = Palette.service,
                    modifier = Modifier.weight(1f),
                )
            }
            if (viewModel.selectedVehicle != null) {
                SectionCard { TankPlanContent(viewModel.tankPlan) }
            }
        }
    }
}

@Composable
private fun ResultBar(
    viewModel: TripCostViewModel,
    onSave: () -> Unit,
) {
    val cost = viewModel.cost
    val plan = viewModel.tankPlan
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                val toll = viewModel.tollCost?.takeIf { it.signum() > 0 }
                Text(
                    stringResource(if (toll == null) R.string.fuel_cost else R.string.trip_total_cost),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedContent(targetState = viewModel.totalCost?.formattedCurrency ?: "—", label = "trip-cost") { text ->
                    Text(
                        text,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (cost == null) MaterialTheme.colorScheme.onSurfaceVariant else Palette.fuel,
                        maxLines = 1,
                    )
                }
                if (cost != null && toll != null) {
                    Text(
                        stringResource(R.string.trip_cost_breakdown, cost.fuelCost.formattedCurrency, toll.formattedCurrency),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (cost != null && toll == null && viewModel.hasTolls == true) {
                    Text(
                        stringResource(R.string.trip_tolls_not_included),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.orange,
                        maxLines = 1,
                    )
                }
                Text(
                    when {
                        cost == null -> stringResource(R.string.trip_cost_hint)

                        plan == null -> cost.liters.formattedLiters(1)

                        else -> "${cost.liters.formattedLiters(
                            1,
                        )} · ${stringResource(R.string.format_tanks, Formats.number(plan.tanks, 1))}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Button(
                onClick = onSave,
                enabled = viewModel.canSaveRoute,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                if (viewModel.saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Filled.Save, contentDescription = null)
                }
                Text(stringResource(R.string.save_calculation), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun PlaceField(
    title: String,
    icon: ImageVector,
    text: String,
    onTextChange: (String) -> Unit,
    suggestions: List<PlaceSuggestion>,
    onSuggestionSelected: (PlaceSuggestion) -> Unit,
) {
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text(title) },
            placeholder = { Text(stringResource(R.string.route_placeholder)) },
            leadingIcon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        suggestions.forEach { suggestion ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clickable { onSuggestionSelected(suggestion) }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Filled.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(suggestion.label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
