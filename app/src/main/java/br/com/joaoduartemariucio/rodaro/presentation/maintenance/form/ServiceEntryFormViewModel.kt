package br.com.joaoduartemariucio.rodaro.presentation.maintenance.form

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import br.com.joaoduartemariucio.rodaro.app.NoAnalytics
import br.com.joaoduartemariucio.rodaro.domain.common.services.Analytics
import br.com.joaoduartemariucio.rodaro.domain.common.services.DateProvider
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.ServiceApplicability
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.domain.maintenance.repositories.ServiceRepository
import br.com.joaoduartemariucio.rodaro.domain.vehicles.calculations.Odometer
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.OdometerSample
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import br.com.joaoduartemariucio.rodaro.presentation.common.FormState
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.common.UnitConversion
import br.com.joaoduartemariucio.rodaro.presentation.common.ValidationIssue
import br.com.joaoduartemariucio.rodaro.presentation.common.blocksSaving
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class ServiceEntryFormViewModel(
    private val mode: Mode,
    private val repository: ServiceRepository,
    private val dateProvider: DateProvider,
    private val otherReadings: List<OdometerSample>,
    private val analytics: Analytics = NoAnalytics,
    vehicleKind: VehicleKind = VehicleKind.CAR,
) : ViewModel() {
    sealed interface Mode {
        data class Create(
            val vehicleId: UUID,
        ) : Mode

        data class Edit(
            val entry: ServiceEntry,
        ) : Mode
    }

    var kind by mutableStateOf(if (mode is Mode.Edit) mode.entry.kind else ServiceKind.OIL_CHANGE)
    var date: Instant by mutableStateOf(if (mode is Mode.Edit) mode.entry.date else dateProvider.now)
    var odometer by mutableStateOf(
        if (mode is Mode.Edit) {
            NumberInput.text(UnitConversion.distance(mode.entry.odometer), NumberInput.Style.KILOMETERS)
        } else {
            ""
        },
    )
    var cost by mutableStateOf(if (mode is Mode.Edit) NumberInput.text(mode.entry.cost, NumberInput.Style.MONEY) else "")
    var notes by mutableStateOf(if (mode is Mode.Edit) mode.entry.notes else "")
    var workshop by mutableStateOf(if (mode is Mode.Edit) mode.entry.workshop.orEmpty() else "")

    val parsedWorkshop: String? get() = workshop.trim().takeIf { it.isNotEmpty() }

    var state: FormState by mutableStateOf(FormState.Editing)
        private set

    val availableKinds: List<ServiceKind> =
        ServiceApplicability.kinds(vehicleKind).let { kinds ->
            if (kind in kinds) kinds else kinds.dropLast(1) + kind + kinds.last()
        }

    val parsedOdometer: Double?
        get() = NumberInput.double(odometer, NumberInput.Style.KILOMETERS)?.let(UnitConversion::distanceToKilometers)

    val previousReading: Double? get() = Odometer.neighbors(date, otherReadings).previous

    val parsedCost: BigDecimal get() = NumberInput.decimal(cost, NumberInput.Style.MONEY) ?: BigDecimal.ZERO

    val issues: List<ValidationIssue>
        get() =
            buildList {
                addAll(ValidationIssue.odometer(parsedOdometer, date, otherReadings))
                if (parsedCost.signum() < 0) add(ValidationIssue.CostNegative)
            }

    val canSave: Boolean get() = !issues.blocksSaving && !state.isSaving

    suspend fun save() {
        val odometer = parsedOdometer ?: return
        if (issues.blocksSaving) return

        state = FormState.Saving
        state =
            try {
                repository.save(entry(odometer))
                analytics.event(
                    "service_saved",
                    mapOf(
                        "mode" to if (mode is Mode.Create) "create" else "edit",
                        "kind" to kind.name,
                    ),
                )
                FormState.Saved
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                FormState.Failed(error)
            }
    }

    private fun entry(odometer: Double): ServiceEntry {
        val trimmedNotes = notes.trim()
        return when (mode) {
            is Mode.Create -> {
                ServiceEntry(
                    vehicleId = mode.vehicleId,
                    kind = kind,
                    date = date,
                    odometer = odometer,
                    cost = parsedCost,
                    notes = trimmedNotes,
                    workshop = parsedWorkshop,
                    createdAt = dateProvider.now,
                )
            }

            is Mode.Edit -> {
                mode.entry.copy(
                    kind = kind,
                    date = date,
                    odometer = odometer,
                    cost = parsedCost,
                    notes = trimmedNotes,
                    workshop = parsedWorkshop,
                )
            }
        }
    }
}
