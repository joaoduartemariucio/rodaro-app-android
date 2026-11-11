package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.calculations.assertDecimal
import br.com.joaoduartemariucio.rodaro.calculations.day
import br.com.joaoduartemariucio.rodaro.calculations.dueItem
import br.com.joaoduartemariucio.rodaro.calculations.expense
import br.com.joaoduartemariucio.rodaro.domain.common.errors.AccountError
import br.com.joaoduartemariucio.rodaro.domain.common.services.Account
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDetails
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueKind
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.Preferences
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.fakes.FakeAccountService
import br.com.joaoduartemariucio.rodaro.fakes.FakeReminderScheduler
import br.com.joaoduartemariucio.rodaro.fakes.FixedDateProvider
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryDueItemRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryExpenseRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryFuelEntryRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryPreferencesRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryServiceRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryVehicleRepository
import br.com.joaoduartemariucio.rodaro.fakes.TestLocalizer
import br.com.joaoduartemariucio.rodaro.presentation.profile.ProfileViewModel
import br.com.joaoduartemariucio.rodaro.presentation.profile.ProfileViewModel.DeleteState
import br.com.joaoduartemariucio.rodaro.presentation.profile.ProfileViewModel.NotificationsState
import br.com.joaoduartemariucio.rodaro.presentation.reminders.ServiceReminderSync
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileViewModelTests {
    private var cleared = 0

    private fun passwordAccount() = Account(id = "password-id", email = "driver@example.com", displayName = null, provider = Account.Provider.PASSWORD)

    private fun googleAccount() = Account(id = "google-id", email = "driver@gmail.com", displayName = "Driver", provider = Account.Provider.GOOGLE)

    private val vehicles = InMemoryVehicleRepository()
    private val fuel = InMemoryFuelEntryRepository()
    private val services = InMemoryServiceRepository()
    private val expenses = InMemoryExpenseRepository()
    private val dues = InMemoryDueItemRepository()
    private val preferences = InMemoryPreferencesRepository()

    private fun makeViewModel(service: FakeAccountService = FakeAccountService()) =
        ProfileViewModel(
            accountService = service,
            vehicleRepository = vehicles,
            fuelEntryRepository = fuel,
            serviceRepository = services,
            expenseRepository = expenses,
            dueItemRepository = dues,
            preferencesRepository = preferences,
            clearLocalData = { cleared++ },
        )

    @Test
    fun `loads the signed in account`() =
        runTest {
            val service = FakeAccountService(passwordAccount())
            val viewModel = makeViewModel(service)

            viewModel.refresh()

            assertEquals(passwordAccount(), viewModel.account)
            assertEquals(listOf("current"), service.calls)
        }

    @Test
    fun `treats a failed account read as signed out`() =
        runTest {
            val service = FakeAccountService(passwordAccount()).apply { currentError = IllegalStateException("boom") }
            val viewModel = makeViewModel(service)

            viewModel.refresh()

            assertNull(viewModel.account)
        }

    @Test
    fun `signs out and refreshes the account`() =
        runTest {
            val service = FakeAccountService(passwordAccount())
            val viewModel = makeViewModel(service)
            viewModel.refresh()
            assertEquals(passwordAccount(), viewModel.account)

            viewModel.signOut()

            assertNull(viewModel.account)
            assertTrue("signOut" in service.calls)
            assertNull(viewModel.error)
        }

    @Test
    fun `reports a failed sign out`() =
        runTest {
            val failure = IllegalStateException("offline")
            val service = FakeAccountService(passwordAccount()).apply { signOutError = failure }
            val viewModel = makeViewModel(service)
            viewModel.refresh()

            viewModel.signOut()

            assertEquals(failure, viewModel.error)
            viewModel.dismissError()
            assertNull(viewModel.error)
        }

    @Test
    fun `deletes the account and clears the local data`() =
        runTest {
            val service = FakeAccountService(passwordAccount())
            val viewModel = makeViewModel(service)
            viewModel.refresh()

            viewModel.delete()

            assertEquals(1, cleared)
            assertEquals(DeleteState.Done, viewModel.deleteState)
            assertNull(viewModel.account)
            assertTrue("deleteAccount" in service.calls)
            assertTrue(viewModel.removedAccount)
            viewModel.dismissDeleteResult()
            assertEquals(DeleteState.Idle, viewModel.deleteState)
        }

    @Test
    fun `deletes the data on this device even without an account`() =
        runTest {
            val service = FakeAccountService()
            val viewModel = makeViewModel(service)
            viewModel.refresh()

            assertTrue(viewModel.canDelete)
            viewModel.delete()

            assertEquals(1, cleared)
            assertEquals(DeleteState.Done, viewModel.deleteState)
            assertFalse(viewModel.removedAccount)
            assertFalse("deleteAccount" in service.calls)
        }

    @Test
    fun `asks for the password when Firebase wants a recent login`() =
        runTest {
            val service = FakeAccountService(passwordAccount()).apply { deleteError = AccountError.RecentLoginRequired }
            val viewModel = makeViewModel(service)

            viewModel.delete()
            assertEquals(DeleteState.NeedsPassword, viewModel.deleteState)
            assertEquals(0, cleared)

            service.deleteError = null
            viewModel.confirmWithPassword("secret1")

            assertEquals(DeleteState.Done, viewModel.deleteState)
            assertEquals(1, cleared)
            assertTrue("reauthenticateWithPassword" in service.calls)
        }

    @Test
    fun `asks for Google when the account came from Google`() =
        runTest {
            val service = FakeAccountService(googleAccount()).apply { deleteError = AccountError.RecentLoginRequired }
            val viewModel = makeViewModel(service)
            viewModel.refresh()

            viewModel.delete()
            assertEquals(DeleteState.NeedsGoogle, viewModel.deleteState)

            service.deleteError = null
            viewModel.confirmWithGoogle()

            assertEquals(DeleteState.Done, viewModel.deleteState)
            assertTrue("reauthenticateWithGoogle" in service.calls)
            assertEquals(1, cleared)
        }

    @Test
    fun `reports a failed reauthentication without clearing anything`() =
        runTest {
            val service = FakeAccountService(passwordAccount()).apply { deleteError = AccountError.RecentLoginRequired }
            val viewModel = makeViewModel(service)
            viewModel.delete()
            val failure = AccountError.InvalidCredentials
            service.reauthPasswordError = failure

            viewModel.confirmWithPassword("wrong")

            assertEquals(failure, viewModel.error)
            assertEquals(DeleteState.Idle, viewModel.deleteState)
            assertEquals(0, cleared)
        }

    @Test
    fun `reports a failed delete without touching the local data`() =
        runTest {
            val failure = AccountError.Network
            val service = FakeAccountService(passwordAccount()).apply { deleteError = failure }
            val viewModel = makeViewModel(service)

            viewModel.delete()

            assertEquals(failure, viewModel.error)
            assertEquals(DeleteState.Idle, viewModel.deleteState)
            assertEquals(0, cleared)
        }

    @Test
    fun `leaves the delete flow when the dialog is cancelled`() =
        runTest {
            val service = FakeAccountService(passwordAccount()).apply { deleteError = AccountError.RecentLoginRequired }
            val viewModel = makeViewModel(service)
            viewModel.refresh()

            viewModel.delete()
            assertEquals(DeleteState.NeedsPassword, viewModel.deleteState)

            viewModel.cancelDelete()

            assertEquals(DeleteState.Idle, viewModel.deleteState)
        }

    @Test
    fun `summarizes the garage across active and archived vehicles`() =
        runTest {
            val car = Vehicle(name = "Car", createdAt = day(0))
            val bike = Vehicle(name = "Bike", createdAt = day(1), isArchived = true)
            vehicles.save(car)
            vehicles.save(bike)
            fuel.save(FuelEntry(vehicleId = car.id, date = day(2), odometer = 1_000.0, liters = 10.0, pricePerLiter = BigDecimal("5.00"), isFullTank = true))
            fuel.save(FuelEntry(vehicleId = bike.id, date = day(3), odometer = 500.0, liters = 4.0, pricePerLiter = BigDecimal("6.00"), isFullTank = true))
            services.save(ServiceEntry(vehicleId = car.id, date = day(4), odometer = 1_200.0, cost = BigDecimal("150.00")))
            val viewModel = makeViewModel()

            viewModel.load()

            val summary = viewModel.summary
            assertEquals(1, summary?.vehicleCount)
            assertEquals(3, summary?.recordCount)
            assertDecimal("224.00", summary?.totalSpent)
        }

    @Test
    fun `the garage total is cash spent, with paid fines, financing and accessories but not unpaid fines`() =
        runTest {
            val car = Vehicle(name = "Car", createdAt = day(0))
            vehicles.save(car)
            fuel.save(FuelEntry(vehicleId = car.id, date = day(2), odometer = 1_000.0, liters = 10.0, pricePerLiter = BigDecimal("5.00"), isFullTank = true))
            expenses.save(expense(ExpenseCategory.MULTA, "100", dayOffset = 3, fine = FineDetails(), vehicleId = car.id))
            expenses.save(expense(ExpenseCategory.MULTA, "300", dayOffset = 3, fine = FineDetails(isPaid = false), vehicleId = car.id))
            expenses.save(expense(ExpenseCategory.FINANCIAMENTO, "500", dayOffset = 4, vehicleId = car.id))
            expenses.save(expense(ExpenseCategory.ACESSORIOS, "80", dayOffset = 5, vehicleId = car.id))
            val viewModel = makeViewModel()

            viewModel.load()

            assertEquals(5, viewModel.summary?.recordCount)
            assertDecimal("730", viewModel.summary?.totalSpent)
        }

    @Test
    fun `driver license items load and a change refreshes them and syncs the reminders`() =
        runTest {
            val scheduler = FakeReminderScheduler()
            val sync =
                ServiceReminderSync(
                    vehicleRepository = vehicles,
                    fuelEntryRepository = fuel,
                    serviceRepository = services,
                    dueItemRepository = dues,
                    expenseRepository = expenses,
                    scheduler = scheduler,
                    dateProvider = FixedDateProvider(day(1)),
                    localizer = TestLocalizer(),
                    preferences = preferences,
                )
            val viewModel =
                ProfileViewModel(
                    accountService = FakeAccountService(),
                    vehicleRepository = vehicles,
                    fuelEntryRepository = fuel,
                    serviceRepository = services,
                    expenseRepository = expenses,
                    dueItemRepository = dues,
                    preferencesRepository = preferences,
                    clearLocalData = {},
                    reminders = sync,
                )
            val old = dueItem(DueKind.CNH, LocalDate.parse("2000-06-01"), vehicleId = null, paidAt = day(0))
            dues.save(old)
            viewModel.load()
            assertEquals(listOf(old), viewModel.driverItems)
            assertEquals(1, viewModel.summary?.recordCount)

            val current = dueItem(DueKind.CNH, LocalDate.parse("2001-06-01"), vehicleId = null)
            dues.save(current)
            viewModel.driverItemsChanged()

            assertEquals(listOf(current, old), viewModel.driverItems)
            assertEquals(2, viewModel.summary?.recordCount)
            assertEquals(6, scheduler.scheduled.size)
        }

    @Test
    fun `driver items that cannot be read show as none`() =
        runTest {
            dues.failure = IllegalStateException("disk")
            val viewModel = makeViewModel()

            viewModel.load()

            assertTrue(viewModel.driverItems.isEmpty())
        }

    @Test
    fun `shows an empty garage as zeros`() =
        runTest {
            val viewModel = makeViewModel()

            viewModel.load()

            assertEquals(ProfileViewModel.Summary(0, 0, BigDecimal.ZERO), viewModel.summary)
        }

    @Test
    fun `hides the garage summary when the records cannot be read`() =
        runTest {
            vehicles.failure = IllegalStateException("boom")
            val viewModel = makeViewModel()

            viewModel.load()

            assertNull(viewModel.summary)
        }

    @Test
    fun `loads the saved preferences and keeps the defaults when they cannot be read`() =
        runTest {
            preferences.stored = Preferences(pushEnabled = false, remindersEnabled = false)
            val viewModel = makeViewModel()

            viewModel.load()
            assertEquals(NotificationsState.OFF, viewModel.notifications)

            preferences.failure = IllegalStateException("boom")
            val fresh = makeViewModel()
            fresh.load()
            assertEquals(Preferences(), fresh.preferences)
            assertEquals(NotificationsState.ON, fresh.notifications)
        }

    @Test
    fun `reports notifications as partly on when only one kind is enabled`() =
        runTest {
            preferences.stored = Preferences(pushEnabled = false, remindersEnabled = true)
            val viewModel = makeViewModel()

            viewModel.load()

            assertEquals(NotificationsState.PARTIAL, viewModel.notifications)
        }

    @Test
    fun `builds initials from the first and last name`() =
        runTest {
            val viewModel = makeViewModel(FakeAccountService(googleAccount().copy(displayName = "Ana Maria Souza")))

            viewModel.load()

            assertEquals("AS", viewModel.initials)
        }

    @Test
    fun `falls back to the first letter of the e-mail when there is no name`() =
        runTest {
            val viewModel = makeViewModel(FakeAccountService(passwordAccount()))

            viewModel.load()

            assertEquals("D", viewModel.initials)
        }

    @Test
    fun `has no initials when signed out or without a name and e-mail`() =
        runTest {
            val signedOut = makeViewModel()
            signedOut.load()
            assertNull(signedOut.initials)

            val anonymous = makeViewModel(FakeAccountService(passwordAccount().copy(email = null)))
            anonymous.load()
            assertNull(anonymous.initials)
        }

    @Test
    fun `refreshes the garage summary after deleting the local data`() =
        runTest {
            vehicles.save(Vehicle(name = "Car", createdAt = day(0)))
            val viewModel = makeViewModel()
            viewModel.load()
            assertEquals(1, viewModel.summary?.vehicleCount)
            vehicles.failure = IllegalStateException("cleared")

            viewModel.delete()

            assertNull(viewModel.summary)
            assertEquals(DeleteState.Done, viewModel.deleteState)
        }
}
