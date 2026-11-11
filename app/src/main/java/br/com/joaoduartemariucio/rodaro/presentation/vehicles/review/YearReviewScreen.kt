package br.com.joaoduartemariucio.rodaro.presentation.vehicles.review

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.app.LocalDependencies
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.reports.calculations.YearReview
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.presentation.common.EmptyState
import br.com.joaoduartemariucio.rodaro.presentation.common.LoadingIndicator
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionCard
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionHeader
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedConsumption
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedCurrency
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedDay
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedKilometers
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedKilometersPerMonth
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedLiters
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedMonthAndYear
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedPerKilometer
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedPerLiter
import br.com.joaoduartemariucio.rodaro.presentation.common.label
import br.com.joaoduartemariucio.rodaro.presentation.common.message
import br.com.joaoduartemariucio.rodaro.presentation.common.rememberLocalizer
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearReviewScreen(
    vehicle: Vehicle,
    onBack: () -> Unit,
) {
    val dependencies = LocalDependencies.current
    val viewModel =
        viewModel {
            YearReviewViewModel(
                vehicle = vehicle,
                fuelEntryRepository = dependencies.fuelEntryRepository,
                serviceRepository = dependencies.serviceRepository,
                expenseRepository = dependencies.expenseRepository,
                dateProvider = dependencies.dateProvider,
                analytics = dependencies.analytics,
            )
        }
    val zone = dependencies.dateProvider.zone

    LaunchedEffect(viewModel) { viewModel.load() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_title_vehicle, vehicle.name)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val state = viewModel.state) {
                YearReviewViewModel.State.Loading -> {
                    LoadingIndicator(Modifier.align(Alignment.Center))
                }

                is YearReviewViewModel.State.Failed -> {
                    EmptyState(
                        title = stringResource(R.string.review_title),
                        icon = Icons.Filled.Warning,
                        message = state.error.message(rememberLocalizer()),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                YearReviewViewModel.State.Loaded -> {
                    Loaded(viewModel, zone)
                }
            }
        }
    }
}

@Composable
private fun Loaded(
    viewModel: YearReviewViewModel,
    zone: ZoneId,
) {
    if (viewModel.availableYears.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            EmptyState(
                title = stringResource(R.string.review_title),
                icon = Icons.Filled.AutoGraph,
                message = stringResource(R.string.review_no_years),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "years") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (year in viewModel.availableYears) {
                    FilterChip(
                        selected = viewModel.selectedYear == year,
                        onClick = { viewModel.select(year) },
                        label = { Text(year.toString()) },
                    )
                }
            }
        }
        val review = viewModel.review
        if (review == null) {
            item(key = "missing") {
                SectionCard(Modifier.padding(top = 8.dp)) {
                    EmptyState(
                        title = stringResource(R.string.review_not_enough_data),
                        icon = Icons.Filled.AutoGraph,
                        message = missing(viewModel.shortfalls),
                    )
                }
            }
        } else {
            reviewSections(review, zone)
        }
    }
}

@Composable
private fun missing(shortfalls: Set<YearReview.Shortfall>): String =
    shortfalls
        .map {
            when (it) {
                YearReview.Shortfall.DAYS -> stringResource(R.string.review_need_days)
                YearReview.Shortfall.FUEL_UPS -> stringResource(R.string.review_need_fuel_ups)
                YearReview.Shortfall.READINGS -> stringResource(R.string.review_need_readings)
            }
        }.joinToString("\n")

private fun LazyListScope.reviewSections(
    review: YearReview,
    zone: ZoneId,
) {
    item(key = "distance") {
        val localizer = rememberLocalizer()
        SectionHeader(stringResource(R.string.review_km))
        SectionCard {
            Text(
                review.distance.kilometers.formattedKilometers(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            val range =
                listOfNotNull(
                    if (review.distance.fromIsReading) {
                        stringResource(
                            R.string.review_since,
                            review.distance.from.formattedDay(zone),
                        )
                    } else {
                        null
                    },
                    if (review.isPartial) {
                        stringResource(R.string.review_until_now)
                    } else if (review.distance.untilIsReading) {
                        stringResource(R.string.review_until, review.distance.until.formattedDay(zone))
                    } else {
                        null
                    },
                )
            if (range.isNotEmpty()) Caption(range.joinToString(" · "))
            review.distance.perMonth?.let { Caption(it.formattedKilometersPerMonth(localizer)) }
            review.costPerKilometer?.let { Fact(stringResource(R.string.review_cost_per_km), it.formattedPerKilometer(localizer)) }
        }
    }

    review.spending?.let { spending ->
        item(key = "spending") {
            SectionHeader(stringResource(R.string.review_spending))
            SectionCard {
                Fact(stringResource(R.string.fuel), spending.fuel.formattedCurrency)
                Fact(stringResource(R.string.service), spending.services.formattedCurrency)
                spending.byCategory
                    .filterKeys { it != ExpenseCategory.MULTA }
                    .forEach { (category, amount) -> Fact(stringResource(category.label), amount.formattedCurrency) }
                Fact(stringResource(R.string.total), spending.total.formattedCurrency, emphasis = true)
                if (spending.fines.signum() != 0) Caption(stringResource(R.string.review_fines_separate, spending.fines.formattedCurrency))
                val month = spending.mostExpensiveMonth
                val amount = spending.mostExpensiveAmount
                if (month != null && amount != null) {
                    Caption(
                        stringResource(
                            R.string.review_most_expensive_month,
                            month
                                .atDay(1)
                                .atStartOfDay(zone)
                                .toInstant()
                                .formattedMonthAndYear(zone),
                            amount.formattedCurrency,
                        ),
                    )
                }
            }
        }
    }

    review.fuel?.let { fuel ->
        item(key = "fuel") {
            val localizer = rememberLocalizer()
            SectionHeader(stringResource(R.string.review_fuel_ups, fuel.fuelUps))
            SectionCard {
                fuel.litersByGroup.forEach { (group, liters) ->
                    Fact(
                        stringResource(
                            R.string.review_liters,
                            group?.let { stringResource(it.label) } ?: stringResource(R.string.review_unknown_fuel),
                        ),
                        liters.formattedLiters(),
                    )
                }
                fuel.averagePrice.forEach { (type, price) ->
                    Fact(
                        stringResource(type.label),
                        stringResource(
                            R.string.review_price_range,
                            price.formattedPerLiter(localizer),
                            fuel.minimumPrice[type]?.formattedPerLiter(localizer).orEmpty(),
                        ),
                    )
                }
            }
        }
    }

    if (review.consumption.isNotEmpty()) {
        item(key = "consumption") {
            val localizer = rememberLocalizer()
            SectionHeader(stringResource(R.string.review_consumption))
            SectionCard {
                review.consumption.forEach { (group, consumption) ->
                    Fact(stringResource(group.label), consumption.kilometersPerLiter.formattedConsumption(localizer), emphasis = true)
                    consumption.best?.let {
                        Caption(
                            stringResource(
                                R.string.review_best_tank,
                                it.kilometersPerLiter.formattedConsumption(localizer),
                                it.endDate.formattedDay(zone),
                            ),
                        )
                    }
                    consumption.worst?.let {
                        Caption(
                            stringResource(
                                R.string.review_worst_tank,
                                it.kilometersPerLiter.formattedConsumption(localizer),
                                it.endDate.formattedDay(zone),
                            ),
                        )
                    }
                }
            }
        }
    }

    if (review.services.isNotEmpty()) {
        item(key = "services") {
            SectionHeader(stringResource(R.string.review_services, review.services.size))
            SectionCard {
                review.services.forEach { entry ->
                    Fact(stringResource(entry.kind.label), "${entry.date.formattedDay(zone)} · ${entry.odometer.formattedKilometers()}")
                }
            }
        }
    }
}

@Composable
private fun Fact(
    title: String,
    value: String,
    emphasis: Boolean = false,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (emphasis) FontWeight.Bold else FontWeight.SemiBold)
    }
}

@Composable
private fun Caption(text: String) {
    Column {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
