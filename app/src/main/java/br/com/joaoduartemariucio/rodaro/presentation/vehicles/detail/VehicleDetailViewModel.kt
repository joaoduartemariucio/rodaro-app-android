package br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import br.com.joaoduartemariucio.rodaro.app.NoAnalytics
import br.com.joaoduartemariucio.rodaro.domain.common.entities.sortedChronologically
import br.com.joaoduartemariucio.rodaro.domain.common.services.Analytics
import br.com.joaoduartemariucio.rodaro.domain.common.services.DateProvider
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import br.com.joaoduartemariucio.rodaro.domain.costs.repositories.ExpenseRepository
import br.com.joaoduartemariucio.rodaro.domain.dues.calculations.DueSchedule
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueItem
import br.com.joaoduartemariucio.rodaro.domain.dues.repositories.DueItemRepository
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.fueling.repositories.FuelEntryRepository
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.MaintenanceStatus
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceRule
import br.com.joaoduartemariucio.rodaro.domain.maintenance.repositories.ServiceRepository
import br.com.joaoduartemariucio.rodaro.domain.vehicles.calculations.Odometer
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.FuelCapability
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.OdometerSample
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.domain.vehicles.repositories.VehicleRepository
import br.com.joaoduartemariucio.rodaro.presentation.reminders.ServiceReminderSync
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.VehicleStats
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
) : ViewModel() {
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

    private var fuel: List<FuelEntry> = emptyList()
    private var services: List<ServiceEntry> = emptyList()

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

                this.fuel = fuel
                this.services = services

                val zone = dateProvider.zone
                val now = dateProvider.now
                val sections = VehicleTimeline.sections(fuel, services, zone, expenses)

                State.Loaded(
                    Content(
                        stats = VehicleStats(vehicle, fuel, services, rules, now, zone, expenses),
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
        reminders?.sync()
    }

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
