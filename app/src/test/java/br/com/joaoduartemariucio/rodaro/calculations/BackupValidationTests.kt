package br.com.joaoduartemariucio.rodaro.calculations

import br.com.joaoduartemariucio.rodaro.domain.backup.calculations.BackupProblem
import br.com.joaoduartemariucio.rodaro.domain.backup.calculations.BackupTable
import br.com.joaoduartemariucio.rodaro.domain.backup.calculations.BackupValidation
import br.com.joaoduartemariucio.rodaro.domain.backup.entities.BackupContents
import br.com.joaoduartemariucio.rodaro.domain.backup.entities.PortablePreferences
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.CurrencyOption
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.LanguageOption
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.Preferences
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.UnitSystem
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupValidationTests {
    private val orphanId: UUID = UUID.fromString("99999999-9999-4999-8999-999999999999")

    @Test
    fun `a consistent snapshot has no problems`() {
        assertTrue(BackupValidation.problems(BackupFixtures.snapshot()).isEmpty())
    }

    @Test
    fun `a repeated vehicle id is reported`() {
        val snapshot = BackupFixtures.snapshot().let { it.copy(vehicles = it.vehicles + BackupFixtures.gol.copy(name = "Copy")) }

        assertEquals(
            listOf<BackupProblem>(BackupProblem.DuplicateId(BackupTable.VEHICLES, BackupFixtures.golId)),
            BackupValidation.problems(snapshot),
        )
    }

    @Test
    fun `a repeated fuel entry id is reported`() {
        val snapshot = BackupFixtures.snapshot().let { it.copy(fuelEntries = it.fuelEntries + BackupFixtures.fuel.copy(liters = 1.0)) }

        assertEquals(
            listOf<BackupProblem>(BackupProblem.DuplicateId(BackupTable.FUEL_ENTRIES, BackupFixtures.fuel.id)),
            BackupValidation.problems(snapshot),
        )
    }

    @Test
    fun `a fuel entry for a vehicle that is not in the file is reported`() {
        val snapshot = BackupFixtures.snapshot().copy(fuelEntries = listOf(BackupFixtures.fuel.copy(vehicleId = orphanId)))

        assertEquals(
            listOf<BackupProblem>(BackupProblem.MissingVehicle(BackupTable.FUEL_ENTRIES, BackupFixtures.fuel.id, orphanId)),
            BackupValidation.problems(snapshot),
        )
    }

    @Test
    fun `a service entry for a vehicle that is not in the file is reported`() {
        val snapshot = BackupFixtures.snapshot().copy(serviceEntries = listOf(BackupFixtures.service.copy(vehicleId = orphanId)))

        assertEquals(
            listOf<BackupProblem>(BackupProblem.MissingVehicle(BackupTable.SERVICE_ENTRIES, BackupFixtures.service.id, orphanId)),
            BackupValidation.problems(snapshot),
        )
    }

    @Test
    fun `a service rule for a vehicle that is not in the file is reported`() {
        val snapshot = BackupFixtures.snapshot().copy(serviceRules = listOf(BackupFixtures.rule.copy(vehicleId = orphanId)))

        assertEquals(
            listOf<BackupProblem>(BackupProblem.MissingVehicle(BackupTable.SERVICE_RULES, BackupFixtures.rule.id, orphanId)),
            BackupValidation.problems(snapshot),
        )
    }

    @Test
    fun `a saved route for an unknown vehicle is allowed`() {
        val snapshot = BackupFixtures.snapshot().copy(savedRoutes = listOf(BackupFixtures.route.copy(vehicleId = orphanId)))

        assertTrue(BackupValidation.problems(snapshot).isEmpty())
    }

    @Test
    fun `portable preferences keep the privacy switches of this device`() {
        val device = Preferences(usageStatisticsEnabled = false, personalizedAdsEnabled = true)

        val applied = PortablePreferences(currency = CurrencyOption.USD).applied(device)

        assertFalse(applied.usageStatisticsEnabled)
        assertTrue(applied.personalizedAdsEnabled)
    }

    @Test
    fun `portable preferences carry currency, units, language and notifications`() {
        val source =
            Preferences(
                pushEnabled = false,
                remindersEnabled = false,
                currency = CurrencyOption.EUR,
                unitSystem = UnitSystem.IMPERIAL,
                language = LanguageOption.ENGLISH,
                usageStatisticsEnabled = false,
                personalizedAdsEnabled = true,
            )

        val applied = PortablePreferences.from(source).applied(Preferences())

        assertEquals(source.copy(usageStatisticsEnabled = true, personalizedAdsEnabled = false), applied)
    }

    @Test
    fun `contents count every table`() {
        assertEquals(BackupContents(2, 1, 1, 1, 1), BackupFixtures.snapshot().contents)
    }

    @Test
    fun `a full snapshot with a driver license that has no vehicle is valid`() {
        assertTrue(BackupValidation.problems(BackupFixtures.fullSnapshot()).isEmpty())
    }

    @Test
    fun `repeated expense and due item ids are reported`() {
        val snapshot =
            BackupFixtures.fullSnapshot().let {
                it.copy(expenses = it.expenses + BackupFixtures.fine.copy(note = "Copy"), dueItems = it.dueItems + BackupFixtures.license)
            }

        assertEquals(
            listOf<BackupProblem>(
                BackupProblem.DuplicateId(BackupTable.EXPENSES, BackupFixtures.fine.id),
                BackupProblem.DuplicateId(BackupTable.DUE_ITEMS, BackupFixtures.license.id),
            ),
            BackupValidation.problems(snapshot),
        )
    }

    @Test
    fun `expenses and due items for a vehicle that is not in the file are reported`() {
        val snapshot =
            BackupFixtures.fullSnapshot().copy(
                expenses = listOf(BackupFixtures.insurance.copy(vehicleId = orphanId)),
                dueItems = listOf(BackupFixtures.ipva.copy(vehicleId = orphanId), BackupFixtures.license),
            )

        assertEquals(
            listOf<BackupProblem>(
                BackupProblem.MissingVehicle(BackupTable.EXPENSES, BackupFixtures.insurance.id, orphanId),
                BackupProblem.MissingVehicle(BackupTable.DUE_ITEMS, BackupFixtures.ipva.id, orphanId),
            ),
            BackupValidation.problems(snapshot),
        )
    }

    @Test
    fun `repeated FIPE quote ids and quotes without their vehicle are reported`() {
        val quote = BackupFixtures.fipeQuote
        val snapshot = BackupFixtures.fipeSnapshot().copy(fipeQuotes = listOf(quote, quote, quote.copy(id = orphanId, vehicleId = orphanId)))

        assertEquals(
            listOf<BackupProblem>(
                BackupProblem.DuplicateId(BackupTable.FIPE_QUOTES, quote.id),
                BackupProblem.MissingVehicle(BackupTable.FIPE_QUOTES, orphanId, orphanId),
            ),
            BackupValidation.problems(snapshot),
        )
        assertTrue(BackupValidation.problems(BackupFixtures.fipeSnapshot()).isEmpty())
    }
}
