package br.com.joaoduartemariucio.rodaro.presentation.vehicles.form

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import br.com.joaoduartemariucio.rodaro.app.NoAnalytics
import br.com.joaoduartemariucio.rodaro.domain.common.services.Analytics
import br.com.joaoduartemariucio.rodaro.domain.common.services.DateProvider
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.ServiceApplicability
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.ServiceDefaults
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceRule
import br.com.joaoduartemariucio.rodaro.domain.maintenance.repositories.ServiceRepository
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeDetail
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipePrice
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeSelection
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.FuelCapability
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import br.com.joaoduartemariucio.rodaro.domain.vehicles.repositories.VehicleRepository
import br.com.joaoduartemariucio.rodaro.presentation.common.FormState
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.common.UnitConversion
import br.com.joaoduartemariucio.rodaro.presentation.common.ValidationIssue
import br.com.joaoduartemariucio.rodaro.presentation.common.blocksSaving
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class VehicleFormViewModel(
    private val mode: Mode,
    existingRules: List<ServiceRule> = emptyList(),
    private val vehicleRepository: VehicleRepository,
    private val serviceRepository: ServiceRepository,
    private val dateProvider: DateProvider,
    private val analytics: Analytics = NoAnalytics,
) : ViewModel() {
    enum class ManualHint {
        NONE,
        NORMAL,
        STRONG,
    }

    class RuleDraft(
        val id: UUID = UUID.randomUUID(),
        kind: ServiceKind = ServiceKind.OIL_CHANGE,
        kilometerInterval: String = "",
        monthInterval: String = "",
    ) {
        var kind by mutableStateOf(kind)
            private set
        var kilometerInterval by mutableStateOf(kilometerInterval)
        var monthInterval by mutableStateOf(monthInterval)

        private var suggestion: Pair<String, String>? by mutableStateOf(null)

        constructor(rule: ServiceRule) : this(
            id = rule.id,
            kind = rule.kind,
            kilometerInterval =
                rule.kilometerInterval
                    ?.let {
                        NumberInput.text(
                            UnitConversion.distance(it),
                            NumberInput.Style.KILOMETERS,
                        )
                    }.orEmpty(),
            monthInterval = NumberInput.text(rule.monthInterval?.toBigDecimal(), NumberInput.Style.MONTHS),
        )

        val isShowingSuggestion: Boolean
            get() = suggestion?.let { it.first == kilometerInterval && it.second == monthInterval } == true

        private val isUntouched: Boolean
            get() {
                val previous = suggestion
                val kilometersUntouched = kilometerInterval.isEmpty() || kilometerInterval == previous?.first
                val monthsUntouched = monthInterval.isEmpty() || monthInterval == previous?.second
                return kilometersUntouched && monthsUntouched
            }

        fun select(
            kind: ServiceKind,
            vehicleKind: VehicleKind,
        ) {
            this.kind = kind
            applySuggestion(vehicleKind)
        }

        fun applySuggestion(vehicleKind: VehicleKind) {
            if (!isUntouched) return
            val interval = ServiceDefaults.interval(kind, vehicleKind)
            val kilometers =
                interval
                    ?.kilometers
                    ?.let { NumberInput.text(UnitConversion.distance(it), NumberInput.Style.KILOMETERS) }
                    .orEmpty()
            val months = NumberInput.text(interval?.months?.toBigDecimal(), NumberInput.Style.MONTHS)
            kilometerInterval = kilometers
            monthInterval = months
            suggestion = if (interval == null) null else kilometers to months
        }

        val parsedKilometerInterval: Double?
            get() =
                NumberInput.double(kilometerInterval, NumberInput.Style.KILOMETERS)?.let(UnitConversion::distanceToKilometers)?.takeIf {
                    it >
                        0
                }

        val parsedMonthInterval: Int?
            get() = NumberInput.double(monthInterval, NumberInput.Style.MONTHS)?.takeIf { it > 0 }?.toInt()

        val issues: List<ValidationIssue>
            get() =
                if (parsedKilometerInterval == null && parsedMonthInterval == null) {
                    listOf(ValidationIssue.RuleWithoutInterval)
                } else {
                    emptyList()
                }
    }

    sealed interface Mode {
        data object Create : Mode

        data class Edit(
            val vehicle: Vehicle,
        ) : Mode
    }

    var name by mutableStateOf("")
    private var selectedKind by mutableStateOf(VehicleKind.CAR)
    var kind: VehicleKind
        get() = selectedKind
        set(value) {
            if (value != selectedKind) clearFipe()
            selectedKind = value
            if (fuelCapability !in FuelCapability.available(value)) fuelCapability = FuelCapability.FLEX
            rules.forEach { it.applySuggestion(value) }
        }
    var fuelCapability by mutableStateOf(FuelCapability.FLEX)
    var uf: String? by mutableStateOf(null)
    private var modelYearText by mutableStateOf("")
    var modelYear: String
        get() = modelYearText
        set(value) {
            modelYearText = value.filter { it in '0'..'9' }.take(MODEL_YEAR_DIGITS)
        }
    var plate by mutableStateOf("")
    var fipe: FipeSelection? by mutableStateOf(null)
        private set
    var fipePreviewPrice: FipePrice? by mutableStateOf(null)
        private set
    var purchaseDate: LocalDate? by mutableStateOf(null)
    var purchasePrice by mutableStateOf("")
    var initialOdometer by mutableStateOf("")
    var tankCapacity by mutableStateOf("")
    val rules = mutableStateListOf<RuleDraft>()

    var state: FormState by mutableStateOf(FormState.Editing)
        private set

    init {
        if (mode is Mode.Edit) {
            val vehicle = mode.vehicle
            name = vehicle.name
            kind = vehicle.kind
            plate = vehicle.plate.orEmpty()
            initialOdometer = NumberInput.text(UnitConversion.distance(vehicle.initialOdometer), NumberInput.Style.KILOMETERS)
            tankCapacity = NumberInput.text(vehicle.tankCapacity?.let(UnitConversion::volume), NumberInput.Style.LITERS)
            fuelCapability = vehicle.fuelCapability
            uf = vehicle.uf
            modelYear = vehicle.modelYear?.toString().orEmpty()
            fipe = vehicle.fipe
            purchaseDate = vehicle.purchaseDate
            purchasePrice = NumberInput.text(vehicle.purchasePrice, NumberInput.Style.MONEY)
        }
        rules.addAll(existingRules.sortedBy { it.kind.rawValue }.map(::RuleDraft))
    }

    val trimmedName: String get() = name.trim()

    val parsedPlate: String? get() = plate.trim().takeIf { it.isNotEmpty() }?.uppercase()

    val parsedInitialOdometer: Double
        get() = NumberInput.double(initialOdometer, NumberInput.Style.KILOMETERS)?.let(UnitConversion::distanceToKilometers) ?: 0.0

    val parsedTankCapacity: Double?
        get() = NumberInput.double(tankCapacity, NumberInput.Style.LITERS)?.let(UnitConversion::volumeToLiters)

    val availableCapabilities: List<FuelCapability> get() = FuelCapability.available(kind)

    val parsedModelYear: Int? get() = modelYear.toIntOrNull()

    val parsedPurchasePrice: BigDecimal? get() = NumberInput.decimal(purchasePrice, NumberInput.Style.MONEY)

    val today: LocalDate get() = dateProvider.now.atZone(dateProvider.zone).toLocalDate()

    fun applyFipe(detail: FipeDetail) {
        fipe = detail.selection
        fipePreviewPrice = detail.price
        if (modelYear.isEmpty()) {
            modelYear =
                detail.selection.modelYear
                    ?.toString()
                    .orEmpty()
        }
    }

    fun clearFipe() {
        fipe = null
        fipePreviewPrice = null
    }

    private val modelYearRange: IntRange
        get() = MIN_MODEL_YEAR..(dateProvider.now.atZone(dateProvider.zone).year + 1)

    val issues: List<ValidationIssue>
        get() =
            buildList {
                if (trimmedName.isEmpty()) add(ValidationIssue.NameRequired)
                val year = parsedModelYear
                val range = modelYearRange
                if (year != null && year !in range) add(ValidationIssue.ModelYearOutOfRange(range.first, range.last))
                if (purchaseDate?.isAfter(today) == true) add(ValidationIssue.PurchaseDateInFuture)
                if (parsedPurchasePrice?.let { it.signum() <= 0 } == true) add(ValidationIssue.PurchasePriceNotPositive)
                addAll(rules.flatMap { it.issues })
            }

    val canSave: Boolean get() = !issues.blocksSaving && !state.isSaving

    val availableRuleKinds: List<ServiceKind> get() = ServiceApplicability.kinds(kind)

    fun ruleKinds(draft: RuleDraft): List<ServiceKind> {
        val kinds = availableRuleKinds
        return if (draft.kind in kinds) kinds else kinds.dropLast(1) + draft.kind + kinds.last()
    }

    fun selectRuleKind(
        draft: RuleDraft,
        ruleKind: ServiceKind,
    ) {
        draft.select(ruleKind, kind)
    }

    fun manualHint(draft: RuleDraft): ManualHint =
        when {
            !draft.isShowingSuggestion -> ManualHint.NONE
            ServiceDefaults.needsStrongManualHint(draft.kind, kind) -> ManualHint.STRONG
            else -> ManualHint.NORMAL
        }

    fun addRule() {
        rules += RuleDraft().also { it.applySuggestion(kind) }
    }

    fun removeRules(offsets: Set<Int>) {
        offsets.sortedDescending().filter { it in rules.indices }.forEach { rules.removeAt(it) }
    }

    suspend fun save() {
        if (issues.blocksSaving) return

        state = FormState.Saving
        val vehicle = vehicle()
        state =
            try {
                vehicleRepository.save(vehicle)
                serviceRepository.save(
                    rules.map { draft ->
                        ServiceRule(
                            id = draft.id,
                            vehicleId = vehicle.id,
                            kind = draft.kind,
                            kilometerInterval = draft.parsedKilometerInterval,
                            monthInterval = draft.parsedMonthInterval,
                        )
                    },
                    vehicle.id,
                )
                analytics.event(
                    if (mode is Mode.Create) "vehicle_created" else "vehicle_edited",
                    mapOf("kind" to kind.name, "rules" to rules.size.toString()),
                )
                FormState.Saved
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                FormState.Failed(error)
            }
    }

    private fun vehicle(): Vehicle =
        when (mode) {
            Mode.Create -> {
                Vehicle(
                    name = trimmedName,
                    kind = kind,
                    plate = parsedPlate,
                    initialOdometer = parsedInitialOdometer,
                    tankCapacity = parsedTankCapacity,
                    createdAt = dateProvider.now,
                    fuelCapability = fuelCapability,
                    uf = uf,
                    modelYear = parsedModelYear,
                    fipe = fipe,
                    purchaseDate = purchaseDate,
                    purchasePrice = parsedPurchasePrice,
                )
            }

            is Mode.Edit -> {
                mode.vehicle.copy(
                    name = trimmedName,
                    kind = kind,
                    plate = parsedPlate,
                    initialOdometer = parsedInitialOdometer,
                    tankCapacity = parsedTankCapacity,
                    fuelCapability = fuelCapability,
                    uf = uf,
                    modelYear = parsedModelYear,
                    fipe = fipe,
                    purchaseDate = purchaseDate,
                    purchasePrice = parsedPurchasePrice,
                )
            }
        }

    private companion object {
        const val MIN_MODEL_YEAR = 1900
        const val MODEL_YEAR_DIGITS = 4
    }
}
