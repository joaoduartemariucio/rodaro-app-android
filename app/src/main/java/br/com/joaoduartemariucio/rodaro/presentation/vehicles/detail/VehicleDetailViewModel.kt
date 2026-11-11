package br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import br.com.joaoduartemariucio.rodaro.app.NoAnalytics
import br.com.joaoduartemariucio.rodaro.domain.common.entities.sortedChronologically
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RemoteDataError
import br.com.joaoduartemariucio.rodaro.domain.common.services.Analytics
import br.com.joaoduartemariucio.rodaro.domain.common.services.DateProvider
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import br.com.joaoduartemariucio.rodaro.domain.costs.repositories.ExpenseRepository
import br.com.joaoduartemariucio.rodaro.domain.dues.calculations.DueSchedule
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueItem
import br.com.joaoduartemariucio.rodaro.domain.dues.repositories.DueItemRepository
import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.PriceComparison
import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.PriceComparisons
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.RegionalPriceTable
import br.com.joaoduartemariucio.rodaro.domain.fueling.repositories.FuelEntryRepository
import br.com.joaoduartemariucio.rodaro.domain.fueling.services.RegionalPriceSource
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.MaintenanceStatus
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceRule
import br.com.joaoduartemariucio.rodaro.domain.maintenance.repositories.ServiceRepository
import br.com.joaoduartemariucio.rodaro.domain.valuation.calculations.Depreciation
import br.com.joaoduartemariucio.rodaro.domain.valuation.calculations.FipeRefresh
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipePrice
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeQuote
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeSelection
import br.com.joaoduartemariucio.rodaro.domain.valuation.repositories.FipeQuoteRepository
import br.com.joaoduartemariucio.rodaro.domain.valuation.services.FipeCatalog
import br.com.joaoduartemariucio.rodaro.domain.valuation.services.UnavailableFipeCatalog
import br.com.joaoduartemariucio.rodaro.domain.vehicles.calculations.Odometer
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.FuelCapability
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.OdometerSample
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.domain.vehicles.repositories.VehicleRepository
import br.com.joaoduartemariucio.rodaro.presentation.common.UnitConversion
import br.com.joaoduartemariucio.rodaro.presentation.reminders.ServiceReminderSync
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.VehicleStats
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class VehicleDetailViewModel(
    vehicle: Vehicle,
    private val vehicleRepository: VehicleRepository,
    private val fuelEntryRepository: FuelEntryRepository,
    private val serviceRepository: ServiceRepository,
    private val expenseRepository: ExpenseRepository,
    private val dueItemRepository: DueItemRepository,
    private val dateProvider: DateProvider,
    private val reminders: ServiceReminderSync? = null,
    private val analytics: Analytics = NoAnalytics,
    private val fipeQuoteRepository: FipeQuoteRepository? = null,
    private val fipeCatalog: FipeCatalog = UnavailableFipeCatalog,
    private val backoff: RefreshBackoff = RefreshBackoff(),
    private val regionalPriceSource: RegionalPriceSource? = null,
) : ViewModel() {
    sealed interface ValuationStatus {
        data object Idle : ValuationStatus

        data object Refreshing : ValuationStatus

        data class Failed(
            val error: Throwable,
        ) : ValuationStatus
    }

    sealed interface State {
        data object Loading : State

        data class Loaded(
            val content: Content,
        ) : State

        data class Failed(
            val error: Throwable,
        ) : State
    }

    data class Content(
        val stats: VehicleStats,
        val sections: List<VehicleTimeline.MonthSection>,
        val spending: List<VehicleTimeline.MonthSpending>,
        val rules: List<ServiceRule>,
        val expenses: List<Expense> = emptyList(),
        val dueItems: List<DueEntry> = emptyList(),
        val paidDueItems: List<DueEntry> = emptyList(),
    )

    data class DueEntry(
        val item: DueItem,
        val level: MaintenanceStatus.Level?,
    )

    var vehicle: Vehicle by mutableStateOf(vehicle)
        private set

    var state: State by mutableStateOf(State.Loading)
        private set

    var filter: VehicleTimeline.Filter by mutableStateOf(VehicleTimeline.Filter.ALL)

    var valuationStatus: ValuationStatus by mutableStateOf(ValuationStatus.Idle)
        private set

    var regionalTable: RegionalPriceTable? by mutableStateOf(null)
        private set

    var priceComparisons: Map<UUID, PriceComparison.Compared> by mutableStateOf(emptyMap())
        private set

    private var fuel: List<FuelEntry> = emptyList()
    private var services: List<ServiceEntry> = emptyList()
    private var loadedRules: List<ServiceRule> = emptyList()
    private var expenses: List<Expense> = emptyList()
    private var quotes: List<FipeQuote> = emptyList()

    private val content: Content? get() = (state as? State.Loaded)?.content

    suspend fun load() {
        state =
            try {
                val vehicle = vehicleRepository.vehicle(vehicle.id) ?: vehicle
                this.vehicle = vehicle
                val fuel = fuelEntryRepository.entries(vehicle.id)
                val services = serviceRepository.entries(vehicle.id)
                val rules = serviceRepository.rules(vehicle.id)
                val expenses = expenseRepository.expenses(vehicle.id)
                val dues = dueItemRepository.items(vehicle.id)
                val quotes = fipeQuoteRepository?.quotes(vehicle.id).orEmpty()

                this.fuel = fuel
                this.services = services
                this.loadedRules = rules
                this.expenses = expenses
                this.quotes = quotes

                val zone = dateProvider.zone
                val now = dateProvider.now
                val sections = VehicleTimeline.sections(fuel, services, zone, expenses)

                State.Loaded(
                    Content(
                        stats = stats(vehicle, now, zone),
                        sections = sections,
                        spending = VehicleTimeline.spending(sections, SPENDING_MONTHS, now, zone),
                        rules = rules,
                        expenses = expenses,
                        dueItems =
                            dues
                                .filter { !it.isPaid }
                                .map { DueEntry(it, DueSchedule.level(it, today(now, zone))) }
                                .sortedWith(compareByDescending<DueEntry> { it.level }.thenBy { it.item.dueDate }),
                        paidDueItems = dues.filter { it.isPaid }.sortedByDescending { it.dueDate }.map { DueEntry(it, null) },
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                State.Failed(error)
            }
        updateComparisons()
        reminders?.sync()
    }

    val comparesRegionalPrices: Boolean get() = vehicle.uf != null && UnitConversion.usesBrazilianReal

    suspend fun loadRegionalPrices() {
        val source = regionalPriceSource ?: return
        if (!comparesRegionalPrices) return
        regionalTable = source.table()
        updateComparisons()
    }

    private fun updateComparisons() {
        val table = regionalTable
        priceComparisons =
            if (table == null || !comparesRegionalPrices) {
                emptyMap()
            } else {
                fuel
                    .mapNotNull { entry ->
                        val comparison =
                            PriceComparisons.compare(entry.pricePerLiter, entry.fuelType, entry.date, vehicle.uf, table, dateProvider.zone)
                        (comparison as? PriceComparison.Compared)?.let { entry.id to it }
                    }.toMap()
            }
    }

    suspend fun reloadAfterEdit() {
        val previous = vehicle.fipe
        load()
        if (vehicle.fipe != previous) {
            backoff.clear(vehicle.id)
            valuationStatus = ValuationStatus.Idle
            refreshValuation()
        }
    }

    suspend fun refreshValuation() {
        if (valuationStatus == ValuationStatus.Refreshing) return
        val repository = fipeQuoteRepository ?: return
        val vehicle = vehicle
        val selection = vehicle.fipe ?: return
        if (!UnitConversion.usesBrazilianReal) return
        val now = dateProvider.now
        val zone = dateProvider.zone
        val plan = Depreciation.refreshPlan(vehicle, quotes, now, zone)
        if (plan == FipeRefresh.NONE || !backoff.allows(vehicle.id, now)) return

        valuationStatus = ValuationStatus.Refreshing
        try {
            val prices = prices(plan, vehicle, selection)
            val fresh = prices.map { FipeQuote.of(vehicle.id, selection, it, now) }
            repository.save(fresh)
            quotes = (quotes.associateBy { it.id } + fresh.associateBy { it.id }).values.sortedBy { it.month }
            content?.let { state = State.Loaded(it.copy(stats = stats(vehicle, now, zone))) }
            valuationStatus = ValuationStatus.Idle
        } catch (error: CancellationException) {
            valuationStatus = ValuationStatus.Idle
            throw error
        } catch (error: Exception) {
            backoff.failed(vehicle.id, now)
            valuationStatus = ValuationStatus.Failed(error)
        }
    }

    suspend fun retryValuation() {
        backoff.clear(vehicle.id)
        valuationStatus = ValuationStatus.Idle
        refreshValuation()
    }

    val showsValuation: Boolean get() = vehicle.fipe != null && UnitConversion.usesBrazilianReal

    val latestQuote: FipeQuote?
        get() = vehicle.fipe?.let { Depreciation.relevant(quotes, it).lastOrNull() }

    private suspend fun prices(
        plan: FipeRefresh,
        vehicle: Vehicle,
        selection: FipeSelection,
    ): List<FipePrice> {
        if (plan == FipeRefresh.HISTORY) {
            val recent =
                try {
                    fipeCatalog.recentPrices(vehicle.kind, selection.fipeCode, selection.yearCode)
                } catch (error: RemoteDataError.NotFound) {
                    emptyList()
                }
            if (recent.isNotEmpty()) return recent
        }
        return listOf(fipeCatalog.latestPrice(vehicle.kind, selection.fipeCode, selection.yearCode))
    }

    private fun stats(
        vehicle: Vehicle,
        now: Instant,
        zone: ZoneId,
    ): VehicleStats =
        VehicleStats(
            vehicle,
            fuel,
            services,
            loadedRules,
            now,
            zone,
            expenses,
            depreciation = Depreciation.summary(vehicle, quotes, Odometer.samples(vehicle, fuel, services), zone),
        )

    val visibleSections: List<VehicleTimeline.MonthSection>
        get() = content?.sections?.mapNotNull { it.filtered(filter) }.orEmpty()

    val hasHistory: Boolean get() = content?.sections?.isNotEmpty() == true

    val lastPricePerLiter: BigDecimal? get() = content?.stats?.lastPricePerLiter

    val fuelEntries: List<FuelEntry> get() = fuel

    val showsFuelAdvisor: Boolean get() = vehicle.fuelCapability == FuelCapability.FLEX

    val suggestedFuelType: FuelType? get() = fuel.sortedChronologically().lastOrNull { it.fuelType != null }?.fuelType

    val suggestedPrices: Map<FuelType, BigDecimal>
        get() =
            fuel
                .sortedChronologically()
                .mapNotNull { entry -> entry.fuelType?.let { it to entry.pricePerLiter } }
                .toMap()

    val rules: List<ServiceRule> get() = content?.rules.orEmpty()

    val hasExpenses: Boolean get() = content?.expenses?.isNotEmpty() == true

    fun odometerReadings(excluding: VehicleTimeline.Item? = null): List<OdometerSample> =
        Odometer.samples(
            vehicle = vehicle,
            fuel = fuel.filter { it.id != excluding?.id },
            services = services.filter { it.id != excluding?.id },
        )

    suspend fun delete(item: VehicleTimeline.Item) {
        try {
            when (item) {
                is VehicleTimeline.Item.Fuel -> fuelEntryRepository.delete(item.entry.id)
                is VehicleTimeline.Item.Service -> serviceRepository.deleteEntry(item.entry.id)
                is VehicleTimeline.Item.Expense -> expenseRepository.delete(item.expense.id)
            }
            val kind =
                when (item) {
                    is VehicleTimeline.Item.Fuel -> "fuel"
                    is VehicleTimeline.Item.Service -> "service"
                    is VehicleTimeline.Item.Expense -> "expense"
                }
            analytics.event("timeline_entry_deleted", mapOf("kind" to kind))
            load()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            state = State.Failed(error)
        }
    }

    suspend fun deleteDueItem(item: DueItem) {
        try {
            dueItemRepository.delete(item.id)
            analytics.event("due_item_deleted", mapOf("kind" to item.kind.rawValue))
            load()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            state = State.Failed(error)
        }
    }

    private fun today(
        now: Instant,
        zone: ZoneId,
    ): LocalDate = now.atZone(zone).toLocalDate()

    companion object {
        const val SPENDING_MONTHS = 6
    }
}
