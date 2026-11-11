package br.com.joaoduartemariucio.rodaro.presentation.trips

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import br.com.joaoduartemariucio.rodaro.app.NoAnalytics
import br.com.joaoduartemariucio.rodaro.domain.common.entities.sortedChronologically
import br.com.joaoduartemariucio.rodaro.domain.common.services.Analytics
import br.com.joaoduartemariucio.rodaro.domain.common.services.DateProvider
import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.Consumption
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelGroup
import br.com.joaoduartemariucio.rodaro.domain.fueling.repositories.FuelEntryRepository
import br.com.joaoduartemariucio.rodaro.domain.trips.calculations.TankPlan
import br.com.joaoduartemariucio.rodaro.domain.trips.calculations.TripCost
import br.com.joaoduartemariucio.rodaro.domain.trips.entities.PlaceSuggestion
import br.com.joaoduartemariucio.rodaro.domain.trips.entities.RoutePoint
import br.com.joaoduartemariucio.rodaro.domain.trips.entities.SavedRoute
import br.com.joaoduartemariucio.rodaro.domain.trips.repositories.RouteRepository
import br.com.joaoduartemariucio.rodaro.domain.trips.services.RouteEstimateProvider
import br.com.joaoduartemariucio.rodaro.domain.trips.services.UnavailableRouteEstimateProvider
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import br.com.joaoduartemariucio.rodaro.domain.vehicles.repositories.VehicleRepository
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.common.UnitConversion
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class TripCostViewModel(
    private val vehicleRepository: VehicleRepository,
    private val fuelEntryRepository: FuelEntryRepository,
    private val routeRepository: RouteRepository,
    private val routeEstimateProvider: RouteEstimateProvider = UnavailableRouteEstimateProvider,
    private val dateProvider: DateProvider,
    private val analytics: Analytics = NoAnalytics,
) : ViewModel() {
    sealed interface State {
        data object Loading : State

        data object Loaded : State

        data class Failed(
            val error: Throwable,
        ) : State
    }

    sealed interface LookupState {
        data object Idle : LookupState

        data object Working : LookupState

        data object Done : LookupState

        data class Failed(
            val error: Throwable,
        ) : LookupState
    }

    var state: State by mutableStateOf(State.Loading)
        private set

    var vehicles: List<Vehicle> by mutableStateOf(emptyList())
        private set

    var selectedVehicle: Vehicle? by mutableStateOf(null)
        private set

    var savedRoutes: List<SavedRoute> by mutableStateOf(emptyList())
        private set

    var distance by mutableStateOf("")
    var isRoundTrip by mutableStateOf(false)
    var consumption by mutableStateOf("")
    var pricePerLiter by mutableStateOf("")
    var origin by mutableStateOf("")
    var destination by mutableStateOf("")
    var hasTolls: Boolean? by mutableStateOf(null)
    private var oneWayTollCost: BigDecimal? by mutableStateOf(null)
    private var roundTripTollCost: BigDecimal? by mutableStateOf(null)
    val tollCost: BigDecimal? get() = if (isRoundTrip) roundTripTollCost else oneWayTollCost
    val totalCost: BigDecimal? get() = cost?.let { it.fuelCost + (tollCost ?: BigDecimal.ZERO) }
    var routePath: List<RoutePoint> by mutableStateOf(emptyList())
        private set
    var originSuggestions: List<PlaceSuggestion> by mutableStateOf(emptyList())
        private set
    var destinationSuggestions: List<PlaceSuggestion> by mutableStateOf(emptyList())
        private set

    var lookupState: LookupState by mutableStateOf(LookupState.Idle)
        private set

    var suggestionsWorking by mutableStateOf(false)
        private set

    private var originSearch: Job? = null
    private var destinationSearch: Job? = null

    var saving by mutableStateOf(false)
        private set

    var saveError: Throwable? by mutableStateOf(null)
        private set

    var suggestedConsumption: String? by mutableStateOf(null)
        private set
    var suggestedPrice: String? by mutableStateOf(null)
        private set
    var averageConsumptionSamples: Int by mutableStateOf(0)
        private set
    var suggestedConsumptionGroup: FuelGroup? by mutableStateOf(null)
        private set
    var lastPriceDate: Instant? by mutableStateOf(null)
        private set
    private var editingRouteId: UUID? by mutableStateOf(null)
    private var editingRouteCreatedAt: Instant? = null

    val parsedDistance: Double?
        get() = NumberInput.double(distance, NumberInput.Style.KILOMETERS)?.let(UnitConversion::distanceToKilometers)

    val parsedConsumption: Double?
        get() = NumberInput.double(consumption, NumberInput.Style.CONSUMPTION)?.let(UnitConversion::consumptionToKilometersPerLiter)

    val parsedPrice: BigDecimal?
        get() = NumberInput.decimal(pricePerLiter, NumberInput.Style.FUEL_PRICE)?.let(UnitConversion::perVolumeToLiters)

    val isUsingAverageConsumption: Boolean get() = suggestedConsumption != null && consumption == suggestedConsumption
    val isUsingLastPrice: Boolean get() = suggestedPrice != null && pricePerLiter == suggestedPrice
    val canUseAverageConsumption: Boolean get() = suggestedConsumption != null && !isUsingAverageConsumption
    val canUseLastPrice: Boolean get() = suggestedPrice != null && !isUsingLastPrice
    val isEditing: Boolean get() = editingRouteId != null

    val tankPlan: TankPlan?
        get() {
            val cost = cost ?: return null
            val consumption = parsedConsumption ?: return null
            return TankPlan.of(cost.liters, consumption, selectedVehicle?.tankCapacity)
        }

    val canLookupRealDistance: Boolean get() = origin.isNotBlank() && destination.isNotBlank() && lookupState !is LookupState.Working

    val canSaveRoute: Boolean get() = cost != null && selectedVehicle != null && canSaveInputs && !saving

    private val canSaveInputs: Boolean get() = origin.isNotBlank() && destination.isNotBlank() && parsedDistance != null

    fun reportCalculation() {
        analytics.event(
            "route_calculated",
            mapOf(
                "round_trip" to isRoundTrip.toString(),
                "has_vehicle" to (selectedVehicle != null).toString(),
            ),
        )
    }

    val cost: TripCost?
        get() {
            val distance = parsedDistance ?: return null
            val consumption = parsedConsumption ?: return null
            val price = parsedPrice ?: return null
            return TripCost.of(distance, isRoundTrip, consumption, price)
        }

    suspend fun load() {
        try {
            vehicles = vehicleRepository.all(includingArchived = false).filter { !it.isArchived }
            savedRoutes = routeRepository.routes()
            val current = selectedVehicle?.let { selected -> vehicles.firstOrNull { it.id == selected.id } }
            state = State.Loaded
            val next = current ?: vehicles.firstOrNull()
            when {
                next == null -> clearSuggestions()
                next != selectedVehicle -> select(next)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            state = State.Failed(error)
        }
    }

    suspend fun select(vehicle: Vehicle) {
        selectedVehicle = vehicle
        analytics.event("route_vehicle_selected")
        loadSuggestions(vehicle, overwrite = true)
    }

    fun useAverageConsumption() {
        suggestedConsumption?.let { consumption = it }
    }

    fun useLastPrice() {
        suggestedPrice?.let { pricePerLiter = it }
    }

    fun swapPlaces() {
        clearLookup()
        val previousOrigin = origin
        origin = destination
        destination = previousOrigin
        originSuggestions = emptyList()
        destinationSuggestions = emptyList()
    }

    suspend fun lookupRealDistance() {
        if (!canLookupRealDistance) return
        lookupState = LookupState.Working
        try {
            val estimate = routeEstimateProvider.estimate(origin.trim(), destination.trim(), selectedVehicle?.kind ?: VehicleKind.CAR)
            distance = NumberInput.text(UnitConversion.distance(estimate.distanceKm), NumberInput.Style.KILOMETERS)
            isRoundTrip = false
            hasTolls = estimate.hasTolls
            oneWayTollCost = estimate.tollCost
            roundTripTollCost = estimate.roundTripTollCost
            routePath = estimate.path
            lookupState = LookupState.Done
            analytics.event(
                "route_distance_looked_up",
                mapOf("has_tolls" to estimate.hasTolls.toString()),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            lookupState = LookupState.Failed(error)
        }
    }

    suspend fun updateOrigin(text: String) {
        if (text != origin) clearLookup()
        origin = text
        val search = replaceSearch(originSearch)
        originSearch = search
        try {
            originSuggestions = debouncedSuggestions(text)
        } finally {
            if (originSearch === search) originSearch = null
        }
    }

    suspend fun updateDestination(text: String) {
        if (text != destination) clearLookup()
        destination = text
        val search = replaceSearch(destinationSearch)
        destinationSearch = search
        try {
            destinationSuggestions = debouncedSuggestions(text)
        } finally {
            if (destinationSearch === search) destinationSearch = null
        }
    }

    fun selectOriginSuggestion(suggestion: PlaceSuggestion) {
        originSearch?.cancel()
        if (suggestion.label != origin) clearLookup()
        origin = suggestion.label
        originSuggestions = emptyList()
        analytics.event("route_place_suggestion_selected", mapOf("field" to "origin"))
    }

    fun selectDestinationSuggestion(suggestion: PlaceSuggestion) {
        destinationSearch?.cancel()
        if (suggestion.label != destination) clearLookup()
        destination = suggestion.label
        destinationSuggestions = emptyList()
        analytics.event("route_place_suggestion_selected", mapOf("field" to "destination"))
    }

    suspend fun saveRoute(): Boolean {
        val vehicle = selectedVehicle ?: return false
        val cost = cost ?: return false
        if (!canSaveInputs) return false
        val routeId = editingRouteId ?: UUID.randomUUID()
        val routeCreatedAt = editingRouteCreatedAt ?: dateProvider.now
        saving = true
        saveError = null
        return try {
            routeRepository.save(
                SavedRoute(
                    id = routeId,
                    vehicleId = vehicle.id,
                    origin = origin.trim(),
                    destination = destination.trim(),
                    distanceKm = parsedDistance ?: return false,
                    isRoundTrip = isRoundTrip,
                    hasTolls = hasTolls ?: false,
                    tollCost = tollCost ?: BigDecimal.ZERO,
                    kilometersPerLiter = parsedConsumption ?: return false,
                    pricePerLiter = parsedPrice ?: return false,
                    liters = cost.liters,
                    fuelCost = cost.fuelCost,
                    costPerKilometer = cost.costPerKilometer,
                    createdAt = routeCreatedAt,
                    path = routePath,
                ),
            )
            savedRoutes = routeRepository.routes()
            editingRouteId = routeId
            editingRouteCreatedAt = routeCreatedAt
            analytics.event(
                "route_saved",
                mapOf(
                    "round_trip" to isRoundTrip.toString(),
                    "has_tolls" to (hasTolls ?: false).toString(),
                ),
            )
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            saveError = error
            false
        } finally {
            saving = false
        }
    }

    suspend fun deleteRoute(id: UUID) {
        try {
            routeRepository.delete(id)
            savedRoutes = routeRepository.routes()
            analytics.event("route_deleted")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            saveError = error
        }
    }

    suspend fun restoreRoute(route: SavedRoute) {
        editingRouteId = route.id
        editingRouteCreatedAt = route.createdAt
        selectedVehicle = vehicles.firstOrNull { it.id == route.vehicleId }
        origin = route.origin
        destination = route.destination
        distance = NumberInput.text(UnitConversion.distance(route.distanceKm), NumberInput.Style.KILOMETERS)
        isRoundTrip = route.isRoundTrip
        hasTolls = route.hasTolls
        val savedToll = route.tollCost.takeIf { it.signum() > 0 }
        oneWayTollCost = if (route.isRoundTrip) null else savedToll
        roundTripTollCost = if (route.isRoundTrip) savedToll else null
        routePath = route.path
        consumption =
            if (route.kilometersPerLiter <= 0) {
                ""
            } else {
                NumberInput.text(UnitConversion.consumption(route.kilometersPerLiter), NumberInput.Style.CONSUMPTION)
            }
        pricePerLiter =
            if (route.pricePerLiter.signum() <= 0) {
                ""
            } else {
                NumberInput.text(UnitConversion.perVolume(route.pricePerLiter), NumberInput.Style.FUEL_PRICE)
            }
        selectedVehicle?.let { loadSuggestions(it, overwrite = false) }
    }

    private fun clearLookup() {
        hasTolls = null
        oneWayTollCost = null
        roundTripTollCost = null
        routePath = emptyList()
        if (lookupState !is LookupState.Working) lookupState = LookupState.Idle
    }

    private suspend fun loadSuggestions(
        vehicle: Vehicle,
        overwrite: Boolean,
    ) {
        try {
            val fuel = fuelEntryRepository.entries(vehicle.id)
            val intervals = Consumption.intervals(fuel)
            val group = Consumption.latestGroup(fuel)
            val groupIntervals = intervals.filter { group != null && it.fuelGroup == group }
            val groupAverage = Consumption.averageOf(groupIntervals)
            val used = if (groupAverage != null) groupIntervals else intervals
            val average = groupAverage ?: Consumption.averageOf(intervals)
            val last = fuel.sortedChronologically().lastOrNull()
            applySuggestions(
                consumption = average?.let { NumberInput.text(UnitConversion.consumption(it), NumberInput.Style.CONSUMPTION) },
                price = last?.pricePerLiter?.let { NumberInput.text(UnitConversion.perVolume(it), NumberInput.Style.FUEL_PRICE) },
                overwrite = overwrite,
            )
            averageConsumptionSamples = if (average == null) 0 else used.size
            suggestedConsumptionGroup = if (groupAverage != null) group else null
            lastPriceDate = last?.date
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            state = State.Failed(error)
        }
    }

    private fun applySuggestions(
        consumption: String?,
        price: String?,
        overwrite: Boolean = true,
    ) {
        if (overwrite && (this.consumption.isEmpty() || isUsingAverageConsumption)) this.consumption = consumption.orEmpty()
        if (overwrite && (pricePerLiter.isEmpty() || isUsingLastPrice)) pricePerLiter = price.orEmpty()
        suggestedConsumption = consumption
        suggestedPrice = price
    }

    private fun clearSuggestions() {
        applySuggestions(consumption = null, price = null)
        averageConsumptionSamples = 0
        suggestedConsumptionGroup = null
        lastPriceDate = null
        selectedVehicle = null
    }

    private suspend fun replaceSearch(previous: Job?): Job? {
        val current = currentCoroutineContext()[Job]
        if (previous !== current) previous?.cancel()
        return current
    }

    private suspend fun debouncedSuggestions(query: String): List<PlaceSuggestion> {
        val text = query.trim()
        if (text.length < MIN_SUGGESTION_QUERY) return emptyList()
        delay(SUGGESTION_DEBOUNCE_MILLIS)
        return suggestions(text)
    }

    private suspend fun suggestions(text: String): List<PlaceSuggestion> {
        suggestionsWorking = true
        return try {
            routeEstimateProvider.suggestions(text).take(MAX_SUGGESTIONS)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            emptyList()
        } finally {
            suggestionsWorking = false
        }
    }

    companion object {
        private const val MIN_SUGGESTION_QUERY = 3
        private const val MAX_SUGGESTIONS = 5
        const val SUGGESTION_DEBOUNCE_MILLIS = 400L
    }
}
