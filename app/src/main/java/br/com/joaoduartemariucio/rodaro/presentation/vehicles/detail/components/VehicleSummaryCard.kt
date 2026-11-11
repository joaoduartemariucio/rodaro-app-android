package br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddRoad
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.presentation.common.MetricTileCard
import br.com.joaoduartemariucio.rodaro.presentation.common.Palette
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedConsumption
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedCurrency
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedKilometers
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedPerKilometer
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedPerLiter
import br.com.joaoduartemariucio.rodaro.presentation.common.rememberLocalizer
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.VehicleStats

@Composable
fun VehicleSummaryCard(stats: VehicleStats) {
    val localizer = rememberLocalizer()

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTileCard(
                title = stringResource(R.string.average_consumption),
                value = stats.averageConsumption?.formattedConsumption(localizer),
                caption =
                    stats.latestConsumption?.let {
                        stringResource(R.string.last_value, it.kilometersPerLiter.formattedConsumption(localizer))
                    },
                icon = Icons.Filled.Speed,
                tint = Palette.fuel,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            MetricTileCard(
                title = stringResource(R.string.cost_per_km),
                value = stats.cost.costPerKilometer?.formattedPerKilometer(localizer),
                caption =
                    if (stats.cost.distance > 0 && stats.cost.expensesInCost.signum() != 0) {
                        stringResource(R.string.over_distance_with_expenses, stats.cost.distance.formattedKilometers())
                    } else if (stats.cost.distance > 0) {
                        stringResource(R.string.over_distance, stats.cost.distance.formattedKilometers())
                    } else {
                        null
                    },
                icon = Icons.Filled.AddRoad,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTileCard(
                title = stringResource(R.string.total_spent),
                value = stats.cost.totalSpent.formattedCurrency,
                caption =
                    if (stats.cost.byCategory.isEmpty()) {
                        stringResource(
                            R.string.fuel_and_services,
                            stats.cost.fuelCost.formattedCurrency,
                            stats.cost.serviceCost.formattedCurrency,
                        )
                    } else {
                        stringResource(
                            R.string.fuel_services_and_expenses,
                            stats.cost.fuelCost.formattedCurrency,
                            stats.cost.serviceCost.formattedCurrency,
                            stats.cost.expensesSpent.formattedCurrency,
                        )
                    },
                icon = Icons.Filled.CreditCard,
                tint = Palette.service,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            MetricTileCard(
                title = stringResource(R.string.this_month),
                value = stats.spentThisMonth.formattedCurrency,
                caption = stats.lastPricePerLiter?.let { stringResource(R.string.last_fuel_price, it.formattedPerLiter(localizer)) },
                icon = Icons.Filled.CalendarMonth,
                tint = Palette.orange,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}
