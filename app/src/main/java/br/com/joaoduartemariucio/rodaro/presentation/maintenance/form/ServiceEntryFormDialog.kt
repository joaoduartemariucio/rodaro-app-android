package br.com.joaoduartemariucio.rodaro.presentation.maintenance.form

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.app.LocalDependencies
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.OdometerSample
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import br.com.joaoduartemariucio.rodaro.presentation.common.DateField
import br.com.joaoduartemariucio.rodaro.presentation.common.FailureMessage
import br.com.joaoduartemariucio.rodaro.presentation.common.FormDialog
import br.com.joaoduartemariucio.rodaro.presentation.common.FormState
import br.com.joaoduartemariucio.rodaro.presentation.common.LabeledNumberField
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.common.OdometerHint
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionCard
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionDivider
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionFooter
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionHeader
import br.com.joaoduartemariucio.rodaro.presentation.common.ValidationFooter
import br.com.joaoduartemariucio.rodaro.presentation.common.ads.InterstitialAds
import br.com.joaoduartemariucio.rodaro.presentation.common.distanceUnit
import br.com.joaoduartemariucio.rodaro.presentation.common.keepsKeyboard
import br.com.joaoduartemariucio.rodaro.presentation.maintenance.form.components.ServiceKindGrid
import kotlinx.coroutines.launch

@Composable
fun ServiceEntryFormDialog(
    mode: ServiceEntryFormViewModel.Mode,
    otherReadings: List<OdometerSample>,
    vehicleKind: VehicleKind,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dependencies = LocalDependencies.current
    val viewModel =
        remember {
            ServiceEntryFormViewModel(
                mode = mode,
                repository = dependencies.serviceRepository,
                dateProvider = dependencies.dateProvider,
                otherReadings = otherReadings,
                analytics = dependencies.analytics,
                vehicleKind = vehicleKind,
            )
        }
    val coroutines = rememberCoroutineScope()

    FormDialog(
        title = stringResource(if (mode is ServiceEntryFormViewModel.Mode.Create) R.string.new_service else R.string.edit_service),
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
            SectionHeader(stringResource(R.string.service_type))
            SectionCard { ServiceKindGrid(viewModel.availableKinds, viewModel.kind) { viewModel.kind = it } }
        }

        item {
            SectionCard(Modifier.padding(top = 16.dp)) {
                DateField(stringResource(R.string.date), viewModel.date, { viewModel.date = it }, dependencies.dateProvider.zone)
                SectionDivider()
                LabeledNumberField(
                    title = stringResource(R.string.odometer),
                    unit = distanceUnit(),
                    style = NumberInput.Style.KILOMETERS,
                    text = viewModel.odometer,
                    onTextChange = { viewModel.odometer = it },
                )
                SectionDivider()
                LabeledNumberField(
                    title = stringResource(R.string.cost),
                    style = NumberInput.Style.MONEY,
                    text = viewModel.cost,
                    onTextChange = { viewModel.cost = it },
                )
            }
            SectionFooter { OdometerHint(viewModel.previousReading) }
        }

        item {
            SectionHeader(stringResource(R.string.service_workshop))
            SectionCard {
                OutlinedTextField(
                    value = viewModel.workshop,
                    onValueChange = { viewModel.workshop = it },
                    placeholder = { Text(stringResource(R.string.service_workshop_prompt)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().keepsKeyboard(),
                )
            }
        }

        item {
            SectionHeader(stringResource(R.string.notes))
            SectionCard {
                OutlinedTextField(
                    value = viewModel.notes,
                    onValueChange = { viewModel.notes = it },
                    placeholder = { Text(stringResource(R.string.notes_prompt)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().keepsKeyboard(),
                )
            }
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
