package br.com.joaoduartemariucio.rodaro.presentation.fueling.form

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import br.com.joaoduartemariucio.rodaro.app.NoAnalytics
import br.com.joaoduartemariucio.rodaro.domain.common.services.Analytics
import br.com.joaoduartemariucio.rodaro.domain.common.services.DateProvider
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.fueling.repositories.FuelEntryRepository
import br.com.joaoduartemariucio.rodaro.domain.vehicles.calculations.Odometer
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.FuelCapability
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.OdometerSample
import br.com.joaoduartemariucio.rodaro.presentation.common.FormState
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.common.UnitConversion
import br.com.joaoduartemariucio.rodaro.presentation.common.ValidationIssue
import br.com.joaoduartemariucio.rodaro.presentation.common.blocksSaving
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class FuelEntryFormViewModel(
    private val mode: Mode,
    private val repository: FuelEntryRepository,
    private val dateProvider: DateProvider,
    private val otherReadings: List<OdometerSample>,
    private val suggestedPrice: BigDecimal? = null,
    private val analytics: Analytics = NoAnalytics,
    fuelCapability: FuelCapability = FuelCapability.FLEX,
    private val suggestedPrices: Map<FuelType, BigDecimal> = emptyMap(),
    suggestedFuelType: FuelType? = null,
) : ViewModel() {
    sealed interface Mode {
        data class Create(
            val vehicleId: UUID,
        ) : Mode

        data class Edit(
            val entry: FuelEntry,
        ) : Mode
    }

    val availableFuelTypes: List<FuelType> =
        fuelCapability.fuelTypes.let { types ->
            val legacy = (mode as? Mode.Edit)?.entry?.fuelType
            if (legacy == null || legacy in types) types else types + legacy
        }

    private var selectedFuelType: FuelType? by mutableStateOf(
        when {
            mode is Mode.Edit -> mode.entry.fuelType
            suggestedFuelType != null && suggestedFuelType in availableFuelTypes -> suggestedFuelType
            else -> availableFuelTypes.singleOrNull()
        },
    )

    var date: Instant by mutableStateOf(if (mode is Mode.Edit) mode.entry.date else dateProvider.now)
    var odometer by mutableStateOf(
        if (mode is Mode.Edit) NumberInput.text(UnitConversion.distance(mode.entry.odometer), NumberInput.Style.KILOMETERS) else "",
    )
    var liters by mutableStateOf(
        if (mode is Mode.Edit) NumberInput.text(UnitConversion.volume(mode.entry.liters), NumberInput.Style.LITERS) else "",
    )
    var pricePerLiter by mutableStateOf(
        if (mode is Mode.Edit) {
            NumberInput.text(UnitConversion.perVolume(mode.entry.pricePerLiter), NumberInput.Style.FUEL_PRICE)
        } else {
            suggestedPriceText(selectedFuelType).orEmpty()
        },
    )
    var isFullTank by mutableStateOf(if (mode is Mode.Edit) mode.entry.isFullTank else true)

    var state: FormState by mutableStateOf(FormState.Editing)
        private set

    val parsedOdometer: Double?
        get() = NumberInput.double(odometer, NumberInput.Style.KILOMETERS)?.let(UnitConversion::distanceToKilometers)

    val parsedLiters: Double?
        get() = NumberInput.double(liters, NumberInput.Style.LITERS)?.let(UnitConversion::volumeToLiters)

    val parsedPrice: BigDecimal?
        get() = NumberInput.decimal(pricePerLiter, NumberInput.Style.FUEL_PRICE)?.let(UnitConversion::perVolumeToLiters)

    var fuelType: FuelType?
        get() = selectedFuelType
        set(value) {
            val previous = suggestedPriceText(selectedFuelType)
            selectedFuelType = value
            if (pricePerLiter.isEmpty() || pricePerLiter == previous) {
                pricePerLiter = suggestedPriceText(value).orEmpty()
            }
        }

    val isUsingSuggestedPrice: Boolean
        get() = suggestedPriceText(fuelType)?.let { it == pricePerLiter } == true

    val previousReading: Double? get() = Odometer.neighbors(date, otherReadings).previous

    val distanceSincePrevious: Double?
        get() {
            val odometer = parsedOdometer ?: return null
            val previous = previousReading ?: return null
            return if (odometer > previous) odometer - previous else null
        }

    val totalCost: BigDecimal?
        get() {
            val liters = parsedLiters ?: return null
            val price = parsedPrice ?: return null
            return entry(parsedOdometer ?: 0.0, liters, price).totalCost
        }

    val issues: List<ValidationIssue>
        get() =
            buildList {
                if (mode is Mode.Create && fuelType == null) add(ValidationIssue.FuelTypeRequired)
                addAll(ValidationIssue.odometer(parsedOdometer, date, otherReadings))

                val liters = parsedLiters
                if (liters == null) {
                    add(ValidationIssue.LitersRequired)
                } else if (liters <= 0) {
                    add(ValidationIssue.LitersNotPositive)
                }

                val price = parsedPrice
                if (price == null) {
                    add(ValidationIssue.PriceRequired)
                } else if (price.signum() <= 0) {
                    add(ValidationIssue.PriceNotPositive)
                }
            }

    val canSave: Boolean get() = !issues.blocksSaving && !state.isSaving

    suspend fun save() {
        val odometer = parsedOdometer ?: return
        val liters = parsedLiters ?: return
        val price = parsedPrice ?: return
        if (issues.blocksSaving) return

        state = FormState.Saving
        state =
            try {
                repository.save(entry(odometer, liters, price))
                analytics.event(
                    "fuel_up_saved",
                    mapOf(
                        "mode" to if (mode is Mode.Create) "create" else "edit",
                        "full_tank" to isFullTank.toString(),
                        "distance_since_previous" to (distanceSincePrevious?.toString() ?: "unknown"),
                        "fuel_type" to (fuelType?.rawValue ?: "unknown"),
                    ),
                )
                FormState.Saved
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                FormState.Failed(error)
            }
    }

    private fun entry(
        odometer: Double,
        liters: Double,
        price: BigDecimal,
    ): FuelEntry =
        when (mode) {
            is Mode.Create -> {
                FuelEntry(
                    vehicleId = mode.vehicleId,
                    date = date,
                    odometer = odometer,
                    liters = liters,
                    pricePerLiter = price,
                    isFullTank = isFullTank,
                    fuelType = fuelType,
                    createdAt = dateProvider.now,
                )
            }

            is Mode.Edit -> {
                mode.entry.copy(
                    date = date,
                    odometer = odometer,
                    liters = liters,
                    pricePerLiter = price,
                    isFullTank = isFullTank,
                    fuelType = fuelType,
                )
            }
        }

    private fun suggestedPriceText(type: FuelType?): String? {
        if (mode !is Mode.Create) return null
        val price = if (type != null && suggestedPrices.isNotEmpty()) suggestedPrices[type] else suggestedPrice
        return price?.let { NumberInput.text(UnitConversion.perVolume(it), NumberInput.Style.FUEL_PRICE) }
    }
}
