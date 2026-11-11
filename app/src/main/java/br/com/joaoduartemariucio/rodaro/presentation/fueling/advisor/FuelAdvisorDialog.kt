package br.com.joaoduartemariucio.rodaro.presentation.fueling.advisor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.app.LocalDependencies
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelGroup
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.presentation.common.FormDialog
import br.com.joaoduartemariucio.rodaro.presentation.common.FormState
import br.com.joaoduartemariucio.rodaro.presentation.common.LabeledNumberField
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.common.Palette
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionCard
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionDivider
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionFooter
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionHeader
import br.com.joaoduartemariucio.rodaro.presentation.common.rememberLocalizer

@Composable
fun FuelAdvisorDialog(
    vehicle: Vehicle,
    fuel: List<FuelEntry>,
    onDismiss: () -> Unit,
) {
    val dependencies = LocalDependencies.current
    val viewModel = remember { FuelAdvisorViewModel(vehicle, fuel, dependencies.dateProvider, dependencies.analytics) }
    val localizer = rememberLocalizer()

    FormDialog(
        title = stringResource(R.string.advisor_title),
        state = FormState.Editing,
        canSave = true,
        onSave = onDismiss,
        onCancel = onDismiss,
        saveLabel = stringResource(R.string.done),
    ) {
        item {
            SectionHeader(stringResource(R.string.advisor_prices))
            SectionCard {
                LabeledNumberField(
                    title = stringResource(R.string.advisor_ethanol_price),
                    style = NumberInput.Style.FUEL_PRICE,
                    text = viewModel.ethanolPrice,
                    onTextChange = { viewModel.ethanolPrice = it },
                )
                SectionDivider()
                LabeledNumberField(
                    title = stringResource(R.string.advisor_gasoline_price),
                    style = NumberInput.Style.FUEL_PRICE,
                    text = viewModel.gasolinePrice,
                    onTextChange = { viewModel.gasolinePrice = it },
                )
            }
        }

        item {
            SectionCard(Modifier.padding(top = 16.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val verdict = viewModel.verdict(localizer)
                    Text(
                        verdict ?: stringResource(R.string.advisor_enter_prices),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color =
                            when (viewModel.advice?.cheaper) {
                                FuelGroup.ETANOL -> Palette.green
                                FuelGroup.GASOLINA -> Palette.fuel
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                    )
                    viewModel.thresholdText?.let {
                        Text(stringResource(R.string.advisor_threshold, it), style = MaterialTheme.typography.bodyMedium)
                    }
                    viewModel.costPerKilometer(localizer)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            SectionFooter {
                Text(
                    viewModel.basis(localizer),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (viewModel.showsAlternatingHint) Palette.orange else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
