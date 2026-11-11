package br.com.joaoduartemariucio.rodaro.presentation.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import br.com.joaoduartemariucio.rodaro.app.NoAnalytics
import br.com.joaoduartemariucio.rodaro.domain.common.errors.AccountError
import br.com.joaoduartemariucio.rodaro.domain.common.services.Account
import br.com.joaoduartemariucio.rodaro.domain.common.services.AccountService
import br.com.joaoduartemariucio.rodaro.domain.common.services.Analytics
import br.com.joaoduartemariucio.rodaro.domain.costs.calculations.Cost
import br.com.joaoduartemariucio.rodaro.domain.costs.repositories.ExpenseRepository
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueItem
import br.com.joaoduartemariucio.rodaro.domain.dues.repositories.DueItemRepository
import br.com.joaoduartemariucio.rodaro.domain.fueling.repositories.FuelEntryRepository
import br.com.joaoduartemariucio.rodaro.domain.maintenance.repositories.ServiceRepository
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.Preferences
import br.com.joaoduartemariucio.rodaro.domain.preferences.repositories.PreferencesRepository
import br.com.joaoduartemariucio.rodaro.domain.vehicles.repositories.VehicleRepository
import br.com.joaoduartemariucio.rodaro.presentation.reminders.ServiceReminderSync
import java.math.BigDecimal
import kotlin.coroutines.cancellation.CancellationException

class ProfileViewModel(
    private val accountService: AccountService,
    private val vehicleRepository: VehicleRepository,
    private val fuelEntryRepository: FuelEntryRepository,
    private val serviceRepository: ServiceRepository,
    private val expenseRepository: ExpenseRepository,
    private val dueItemRepository: DueItemRepository,
    private val preferencesRepository: PreferencesRepository,
    private val clearLocalData: suspend () -> Unit,
    private val analytics: Analytics = NoAnalytics,
    private val reminders: ServiceReminderSync? = null,
) : ViewModel() {
    data class Summary(
        val vehicleCount: Int,
        val recordCount: Int,
        val totalSpent: BigDecimal,
    )

    enum class NotificationsState {
        ON,
        PARTIAL,
        OFF,
    }

    sealed interface DeleteState {
        data object Idle : DeleteState

        data object Working : DeleteState

        data object NeedsPassword : DeleteState

        data object NeedsGoogle : DeleteState

        data object Done : DeleteState
    }

    var account: Account? by mutableStateOf(null)
        private set

    var deleteState: DeleteState by mutableStateOf(DeleteState.Idle)
        private set

    var error: Throwable? by mutableStateOf(null)
        private set

    var summary: Summary? by mutableStateOf(null)
        private set

    var preferences: Preferences by mutableStateOf(Preferences())
        private set

    var driverItems: List<DueItem> by mutableStateOf(emptyList())
        private set

    val notifications: NotificationsState
        get() =
            when {
                preferences.pushEnabled && preferences.remindersEnabled -> NotificationsState.ON
                preferences.pushEnabled || preferences.remindersEnabled -> NotificationsState.PARTIAL
                else -> NotificationsState.OFF
            }

    val initials: String?
        get() {
            val account = account ?: return null
            val name = account.displayName?.takeIf { it.isNotBlank() }
            val source = name ?: account.email?.substringBefore('@')?.takeIf { it.isNotBlank() } ?: return null
            val words = source.split(' ', '.', '_', '-').filter { it.isNotBlank() }
            val letters =
                if (name != null && words.size > 1) {
                    "${words.first().first()}${words.last().first()}"
                } else {
                    words.firstOrNull()?.take(1).orEmpty()
                }
            return letters.uppercase().takeIf { it.isNotEmpty() }
        }

    suspend fun load() {
        refresh()
        loadPreferences()
        loadSummary()
        loadDriverItems()
    }

    suspend fun driverItemsChanged() {
        loadDriverItems()
        loadSummary()
        reminders?.sync()
    }

    private suspend fun loadDriverItems() {
        driverItems =
            try {
                dueItemRepository.driverItems().sortedWith(compareBy<DueItem> { it.isPaid }.thenByDescending { it.dueDate })
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                emptyList()
            }
    }

    private suspend fun loadPreferences() {
        try {
            preferences = preferencesRepository.get()
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
        }
    }

    private suspend fun loadSummary() {
        summary =
            try {
                val vehicles = vehicleRepository.all(includingArchived = true)
                var records = 0
                var spent = BigDecimal.ZERO
                vehicles.forEach { vehicle ->
                    val fuel = fuelEntryRepository.entries(vehicle.id)
                    val services = serviceRepository.entries(vehicle.id)
                    val expenses = expenseRepository.expenses(vehicle.id)
                    records += fuel.size + services.size + expenses.size
                    spent += Cost.summary(fuel, services, expenses).totalSpent
                }
                records += dueItemRepository.all().size
                Summary(
                    vehicleCount = vehicles.count { !it.isArchived },
                    recordCount = records,
                    totalSpent = spent,
                )
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                null
            }
    }

    suspend fun refresh() {
        account =
            try {
                accountService.current()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                null
            }
    }

    suspend fun signOut() {
        error = null
        try {
            accountService.signOut()
            analytics.event("account_signed_out")
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            error = failure
        }
        refresh()
    }

    var removedAccount by mutableStateOf(false)
        private set

    val canDelete: Boolean get() = deleteState != DeleteState.Working

    suspend fun delete() {
        if (deleteState == DeleteState.Working) return
        refresh()
        if (account == null) {
            performDelete(event = "local_data_deleted") { removedAccount = false }
        } else {
            performDelete {
                accountService.deleteAccount()
                removedAccount = true
            }
        }
    }

    suspend fun confirmWithPassword(password: String) =
        performDelete {
            accountService.reauthenticateWithPassword(password)
            accountService.deleteAccount()
            removedAccount = true
        }

    suspend fun confirmWithGoogle() =
        performDelete {
            accountService.reauthenticateWithGoogle()
            accountService.deleteAccount()
            removedAccount = true
        }

    fun cancelDelete() {
        if (deleteState != DeleteState.Working) deleteState = DeleteState.Idle
    }

    fun dismissDeleteResult() {
        if (deleteState == DeleteState.Done) deleteState = DeleteState.Idle
    }

    fun dismissError() {
        error = null
    }

    private suspend fun performDelete(
        event: String = "account_deleted",
        action: suspend () -> Unit,
    ) {
        if (deleteState == DeleteState.Working) return
        deleteState = DeleteState.Working
        error = null
        try {
            action()
            clearLocalData()
            load()
            deleteState = DeleteState.Done
            analytics.event(event)
        } catch (recentLogin: AccountError.RecentLoginRequired) {
            analytics.event("account_delete_reauth_required")
            deleteState =
                if (account?.provider == Account.Provider.GOOGLE) {
                    DeleteState.NeedsGoogle
                } else {
                    DeleteState.NeedsPassword
                }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            error = failure
            deleteState = DeleteState.Idle
        }
    }
}
