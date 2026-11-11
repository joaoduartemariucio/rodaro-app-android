package br.com.joaoduartemariucio.rodaro.presentation.vehicles.review

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import br.com.joaoduartemariucio.rodaro.app.NoAnalytics
import br.com.joaoduartemariucio.rodaro.domain.common.services.Analytics
import br.com.joaoduartemariucio.rodaro.domain.common.services.DateProvider
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import br.com.joaoduartemariucio.rodaro.domain.costs.repositories.ExpenseRepository
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.repositories.FuelEntryRepository
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.repositories.ServiceRepository
import br.com.joaoduartemariucio.rodaro.domain.reports.calculations.YearReview
import br.com.joaoduartemariucio.rodaro.domain.reports.calculations.YearReviews
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import kotlin.coroutines.cancellation.CancellationException

class YearReviewViewModel(
    val vehicle: Vehicle,
    private val fuelEntryRepository: FuelEntryRepository,
    private val serviceRepository: ServiceRepository,
    private val expenseRepository: ExpenseRepository,
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

    var state: State by mutableStateOf(State.Loading)
        private set
    var availableYears: List<Int> by mutableStateOf(emptyList())
        private set
    var selectedYear: Int? by mutableStateOf(null)
        private set
    var review: YearReview? by mutableStateOf(null)
        private set
    var shortfalls: Set<YearReview.Shortfall> by mutableStateOf(emptySet())
        private set

    private var fuel: List<FuelEntry> = emptyList()
    private var services: List<ServiceEntry> = emptyList()
    private var expenses: List<Expense> = emptyList()

    suspend fun load() {
        state =
            try {
                fuel = fuelEntryRepository.entries(vehicle.id)
                services = serviceRepository.entries(vehicle.id)
                expenses = expenseRepository.expenses(vehicle.id)
                availableYears = YearReviews.availableYears(fuel, services, expenses, dateProvider.now, dateProvider.zone)
                val year = selectedYear?.takeIf { it in availableYears } ?: availableYears.firstOrNull()
                if (year != null) select(year) else review = null
                State.Loaded
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                State.Failed(error)
            }
    }

    fun select(year: Int) {
        selectedYear = year
        val zone = dateProvider.zone
        shortfalls = YearReviews.shortfalls(vehicle, fuel, services, year, zone)
        review = YearReviews.review(vehicle, fuel, services, expenses, year, dateProvider.now, zone)
        val offset = dateProvider.now.atZone(zone).year - year
        analytics.event("year_review_viewed", mapOf("year_offset" to offset.toString()))
    }
}
