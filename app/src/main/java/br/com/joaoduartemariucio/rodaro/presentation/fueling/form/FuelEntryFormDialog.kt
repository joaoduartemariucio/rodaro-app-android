package br.com.joaoduartemariucio.rodaro.presentation.fueling.form

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.app.LocalDependencies
import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.PriceComparison
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.RegionalPriceTable
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.FuelCapability
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.OdometerSample
import br.com.joaoduartemariucio.rodaro.presentation.common.DateField
import br.com.joaoduartemariucio.rodaro.presentation.common.FailureMessage
import br.com.joaoduartemariucio.rodaro.presentation.common.FormDialog
import br.com.joaoduartemariucio.rodaro.presentation.common.FormState
import br.com.joaoduartemariucio.rodaro.presentation.common.LabeledNumberField
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.common.OdometerHint
import br.com.joaoduartemariucio.rodaro.presentation.common.Palette
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionCard
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionDivider
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionFooter
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionHeader
import br.com.joaoduartemariucio.rodaro.presentation.common.UnitConversion
import br.com.joaoduartemariucio.rodaro.presentation.common.ValidationFooter
import br.com.joaoduartemariucio.rodaro.presentation.common.ads.InterstitialAds
import br.com.joaoduartemariucio.rodaro.presentation.common.distanceUnit
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedCurrency
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedPerLiter
import br.com.joaoduartemariucio.rodaro.presentation.common.label
import br.com.joaoduartemariucio.rodaro.presentation.common.rememberLocalizer
import br.com.joaoduartemariucio.rodaro.presentation.common.volumePriceTitle
import br.com.joaoduartemariucio.rodaro.presentation.common.volumeTitle
import br.com.joaoduartemariucio.rodaro.presentation.common.volumeUnit
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FuelEntryFormDialog(
    mode: FuelEntryFormViewModel.Mode,
    otherReadings: List<OdometerSample>,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
    suggestedPrice: BigDecimal? = null,
    fuelCapability: FuelCapability = FuelCapability.FLEX,
    suggestedPrices: Map<FuelType, BigDecimal> = emptyMap(),
    suggestedFuelType: FuelType? = null,
    regionalTable: RegionalPriceTable? = null,
    uf: String? = null,
) {
    val dependencies = LocalDependencies.current
    val viewModel =
        remember {
            FuelEntryFormViewModel(
                mode = mode,
                repository = dependencies.fuelEntryRepository,
                dateProvider = dependencies.dateProvider,
                otherReadings = otherReadings,
                suggestedPrice = suggestedPrice,
                analytics = dependencies.analytics,
                fuelCapability = fuelCapability,
                suggestedPrices = suggestedPrices,
                suggestedFuelType = suggestedFuelType,
                regionalTable = regionalTable,
                uf = uf,
            )
        }
    val coroutines = rememberCoroutineScope()

    FormDialog(
        title = stringResource(if (mode is FuelEntryFormViewModel.Mode.Create) R.string.new_fuel_up else R.string.edit_fuel_up),
        state = viewModel.state,
        canSave = viewModel.canSave,
        onSave = {
            coroutines.launch {
                viewModel.save()
                if (viewModel.state == FormState.Saved) {
                    InterstitialAds.show()
                    onSaved()
                    onDismiss()
                }
            }
        },
        onCancel = onDismiss,
    ) {
        item {
            SectionCard(Modifier.padding(top = 8.dp)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val total = viewModel.totalCost
                    AnimatedContent(targetState = total?.formattedCurrency ?: "—", label = "total") { text ->
                        Text(
                            text,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            color = if (total == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(stringResource(R.string.fuel_type))
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (type in viewModel.availableFuelTypes) {
                    FilterChip(
                        selected = viewModel.fuelType == type,
                        onClick = { viewModel.fuelType = type },
                        label = { Text(stringResource(type.label)) },
                    )
                }
            }
        }

        item {
            SectionHeader(stringResource(R.string.fuel))
            SectionCard {
                LabeledNumberField(
                    title = volumeTitle(),
                    unit = volumeUnit(),
                    style = NumberInput.Style.LITERS,
                    text = viewModel.liters,
                    onTextChange = { viewModel.liters = it },
                )
                SectionDivider()
                LabeledNumberField(
                    title = volumePriceTitle(),
                    style = NumberInput.Style.FUEL_PRICE,
                    text = viewModel.pricePerLiter,
                    onTextChange = { viewModel.pricePerLiter = it },
                )
                SectionDivider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.LocalGasStation, contentDescription = null, tint = Palette.fuel)
                    Text(stringResource(R.string.full_tank), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = viewModel.isFullTank, onCheckedChange = { viewModel.isFullTank = it })
                }
            }
            if (viewModel.isUsingSuggestedPrice) {
                SectionFooter {
                    Text(
                        stringResource(R.string.price_from_last_fuel_up),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            viewModel.priceComparison?.let { comparison -> SectionFooter { RegionalPriceHint(comparison) } }
        }

        item {
            SectionHeader(stringResource(R.string.when_header))
            SectionCard {
                DateField(stringResource(R.string.date), viewModel.date, { viewModel.date = it }, dependencies.dateProvider.zone)
                SectionDivider()
                LabeledNumberField(
                    title = stringResource(R.string.odometer),
                    unit = distanceUnit(),
                    style = NumberInput.Style.KILOMETERS,
                    text = viewModel.odometer,
                    onTextChange = { viewModel.odometer = it },
                )
            }
            SectionFooter { OdometerHint(viewModel.previousReading, viewModel.distanceSincePrevious) }
        }

        val state = viewModel.state
        if (state is FormState.Failed) {
            item { SectionCard(Modifier.padding(top = 16.dp)) { FailureMessage(state.error) } }
        }

        if (viewModel.issues.isNotEmpty()) {
            item { SectionCard(Modifier.padding(top = 16.dp)) { ValidationFooter(viewModel.issues) } }
        }
    }
}

@Composable
private fun RegionalPriceHint(comparison: PriceComparison) {
    val localizer = rememberLocalizer()
    val text =
        when (comparison) {
            is PriceComparison.Unavailable -> {
                if (comparison.reason == PriceComparison.Reason.NO_UF) stringResource(R.string.anp_no_uf_hint) else return
            }

            is PriceComparison.Compared -> {
                val level =
                    when (comparison.level) {
                        PriceComparison.Level.BELOW -> stringResource(R.string.anp_below, comparison.uf)
                        PriceComparison.Level.AVERAGE -> stringResource(R.string.anp_average, comparison.uf)
                        PriceComparison.Level.ABOVE -> stringResource(R.string.anp_above, comparison.uf)
                    }
                val previous = if (comparison.isPreviousWeek) " ${stringResource(R.string.anp_previous_week)}" else ""
                val average =
                    stringResource(
                        R.string.anp_state_average,
                        comparison.uf,
                        comparison.regional.mean.formattedPerLiter(localizer),
                        UnitConversion.perVolume(comparison.tolerance).formattedCurrency,
                    )
                val week =
                    stringResource(
                        R.string.anp_source,
                        comparison.week.start.format(WEEK_FORMAT),
                        comparison.week.end.format(WEEK_FORMAT),
                    )
                "$level$previous · $average\n$week"
            }
        }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val WEEK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
