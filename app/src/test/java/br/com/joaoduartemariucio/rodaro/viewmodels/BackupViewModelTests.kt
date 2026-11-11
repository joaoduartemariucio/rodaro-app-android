package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.calculations.BackupFixtures
import br.com.joaoduartemariucio.rodaro.data.backup.serialization.JsonBackupCodec
import br.com.joaoduartemariucio.rodaro.domain.backup.entities.BackupContents
import br.com.joaoduartemariucio.rodaro.domain.backup.entities.BackupSnapshot
import br.com.joaoduartemariucio.rodaro.domain.backup.entities.PortablePreferences
import br.com.joaoduartemariucio.rodaro.domain.backup.errors.BackupError
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RepositoryError
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.CurrencyOption
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.LanguageOption
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.Preferences
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.UnitSystem
import br.com.joaoduartemariucio.rodaro.fakes.FakeAnalytics
import br.com.joaoduartemariucio.rodaro.fakes.FixedDateProvider
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryBackupRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryDocumentStore
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryPreferencesRepository
import br.com.joaoduartemariucio.rodaro.fakes.TestLocalizer
import br.com.joaoduartemariucio.rodaro.presentation.backup.BackupViewModel
import br.com.joaoduartemariucio.rodaro.presentation.common.DisplayPreferences
import br.com.joaoduartemariucio.rodaro.presentation.common.message
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTests {
    private val codec = JsonBackupCodec(appVersion = "1.0.0")
    private val now: Instant = Instant.parse("2026-11-10T01:30:00Z")
    private val analytics = FakeAnalytics()
    private val appliedLanguages = mutableListOf<LanguageOption>()
    private var reminderSyncs = 0

    @After
    fun tearDown() {
        DisplayPreferences.update(Preferences())
    }

    private fun makeViewModel(
        repository: InMemoryBackupRepository = InMemoryBackupRepository(BackupFixtures.snapshot(preferences = null, exportedAt = null)),
        documents: InMemoryDocumentStore = InMemoryDocumentStore(),
        preferences: InMemoryPreferencesRepository = InMemoryPreferencesRepository(),
        zone: ZoneId = ZoneId.of("America/Sao_Paulo"),
        isStoreAvailable: Boolean = true,
    ) = BackupViewModel(
        backupRepository = repository,
        codec = codec,
        documents = documents,
        preferencesRepository = preferences,
        dateProvider = FixedDateProvider(now, zone),
        applyLanguage = { appliedLanguages += it },
        syncReminders = { reminderSyncs += 1 },
        isStoreAvailable = isStoreAvailable,
        analytics = analytics,
    )

    private suspend fun backupFile(snapshot: BackupSnapshot = BackupFixtures.snapshot()): String = codec.encode(snapshot)

    @Test
    fun `exporting writes the snapshot with the preferences and the export date`() =
        runTest {
            val documents = InMemoryDocumentStore()
            val preferences = InMemoryPreferencesRepository(Preferences(currency = CurrencyOption.USD, usageStatisticsEnabled = false))
            val viewModel = makeViewModel(documents = documents, preferences = preferences)

            viewModel.export("content://files/backup.json")

            val written = codec.decode(documents.files.getValue("content://files/backup.json"))
            assertEquals(BackupFixtures.snapshot(preferences = null, exportedAt = null).vehicles, written.vehicles)
            assertEquals(PortablePreferences(currency = CurrencyOption.USD), written.preferences)
            assertEquals(now, written.exportedAt)
            assertEquals(BackupViewModel.State.Done(BackupViewModel.Result.EXPORTED), viewModel.state)
        }

    @Test
    fun `exporting logs backup_exported`() =
        runTest {
            val viewModel = makeViewModel()

            viewModel.export("content://files/backup.json")

            assertEquals(listOf("backup_exported"), analytics.names())
        }

    @Test
    fun `a failed write reports the error and logs no success`() =
        runTest {
            val documents = InMemoryDocumentStore(failure = BackupError.Storage(SecurityException("revoked")))
            val viewModel = makeViewModel(documents = documents)

            viewModel.export("content://files/backup.json")

            assertIs<BackupError.Storage>(assertIs<BackupViewModel.State.Failed>(viewModel.state).error)
            assertEquals(listOf("backup_export_failed"), analytics.names())
            assertEquals("storage", analytics.parameters("backup_export_failed")?.get("reason"))
        }

    @Test
    fun `a storage error has a localized message`() {
        assertEquals(
            "Could not access the chosen file. Try again or pick another location.",
            BackupError.Storage(SecurityException()).message(TestLocalizer()),
        )
    }

    @Test
    fun `every backup error has its own localized message`() {
        val localizer = TestLocalizer()

        assertEquals("This file is not a Rodaro backup or is damaged.", BackupError.Unreadable().message(localizer))
        assertEquals(
            "This backup was made by a newer version of Rodaro. Update the app to restore it.",
            BackupError.NewerFormat(2, 1).message(localizer),
        )
        assertEquals(
            "This backup has inconsistent records and was not restored. Nothing was changed.",
            BackupError.Invalid(emptyList()).message(localizer),
        )
        assertEquals("This file is too large to be a Rodaro backup.", BackupError.TooLarge.message(localizer))
    }

    @Test
    fun `file names carry today's date in the user's time zone`() {
        val viewModel = makeViewModel()

        assertEquals("rodaro-backup-2026-11-09.json", viewModel.backupFileName())
        assertEquals("rodaro-history-2026-11-09.csv", viewModel.spreadsheetFileName(TestLocalizer()))
    }

    @Test
    fun `exporting the spreadsheet writes the CSV and logs it`() =
        runTest {
            val documents = InMemoryDocumentStore()
            val viewModel = makeViewModel(documents = documents)

            viewModel.exportSpreadsheet("content://files/history.csv", TestLocalizer())

            assertTrue(documents.files.getValue("content://files/history.csv").startsWith("\uFEFFVehicle,Plate"))
            assertEquals(BackupViewModel.State.Done(BackupViewModel.Result.SPREADSHEET_EXPORTED), viewModel.state)
            assertEquals(listOf("backup_spreadsheet_exported"), analytics.names())
        }

    @Test
    fun `preparing a valid import asks for confirmation with both counts`() =
        runTest {
            val repository = InMemoryBackupRepository(BackupSnapshot(vehicles = listOf(BackupFixtures.gol)))
            val documents = InMemoryDocumentStore(mapOf("content://in" to backupFile()))
            val viewModel = makeViewModel(repository = repository, documents = documents)

            viewModel.prepareImport("content://in")

            assertEquals(
                BackupViewModel.State.ConfirmingImport(
                    incoming = BackupContents(2, 1, 1, 1, 1),
                    current = BackupContents(1, 0, 0, 0, 0),
                    exportedAt = Instant.parse("2025-05-01T18:00:00Z"),
                ),
                viewModel.state,
            )
        }

    @Test
    fun `preparing an import never writes to the repository`() =
        runTest {
            val repository = InMemoryBackupRepository()
            val viewModel = makeViewModel(repository = repository, documents = InMemoryDocumentStore(mapOf("content://in" to backupFile())))

            viewModel.prepareImport("content://in")

            assertEquals(0, repository.replaceCount)
            assertEquals(BackupSnapshot(), repository.stored)
        }

    @Test
    fun `an inconsistent file is refused and nothing is replaced`() =
        runTest {
            val orphan = BackupFixtures.fuel.copy(vehicleId = UUID.randomUUID())
            val repository = InMemoryBackupRepository()
            val documents = InMemoryDocumentStore(mapOf("content://in" to backupFile(BackupSnapshot(fuelEntries = listOf(orphan)))))
            val viewModel = makeViewModel(repository = repository, documents = documents)

            viewModel.prepareImport("content://in")
            viewModel.confirmImport()

            assertIs<BackupError.Invalid>(assertIs<BackupViewModel.State.Failed>(viewModel.state).error)
            assertEquals(0, repository.replaceCount)
            assertEquals("invalid", analytics.parameters("backup_import_failed")?.get("reason"))
        }

    @Test
    fun `a file from a newer version is refused`() =
        runTest {
            val documents = InMemoryDocumentStore(mapOf("content://in" to """{"format":"rodaro.backup","formatVersion":7}"""))
            val viewModel = makeViewModel(documents = documents)

            viewModel.prepareImport("content://in")

            assertEquals(BackupError.NewerFormat(7, 1), assertIs<BackupViewModel.State.Failed>(viewModel.state).error)
            assertEquals("newer_format", analytics.parameters("backup_import_failed")?.get("reason"))
        }

    @Test
    fun `unreadable and oversized files report their reason`() =
        runTest {
            val documents = InMemoryDocumentStore(mapOf("content://text" to "hello", "content://big" to "x".repeat(20)))
            val viewModel = makeViewModel(documents = documents)

            viewModel.prepareImport("content://text")
            assertEquals("unreadable", analytics.parameters("backup_import_failed")?.get("reason"))

            viewModel.dismiss()
            documents.limit = 10
            viewModel.prepareImport("content://big")
            assertEquals("too_large", analytics.parameters("backup_import_failed")?.get("reason"))
        }

    @Test
    fun `confirming replaces the data and logs the import`() =
        runTest {
            val repository = InMemoryBackupRepository()
            val viewModel = makeViewModel(repository = repository, documents = InMemoryDocumentStore(mapOf("content://in" to backupFile())))

            viewModel.prepareImport("content://in")
            viewModel.confirmImport()

            assertEquals(BackupFixtures.snapshot(preferences = null, exportedAt = null), repository.stored)
            assertEquals(1, repository.replaceCount)
            assertEquals(BackupViewModel.State.Done(BackupViewModel.Result.IMPORTED), viewModel.state)
            assertEquals(listOf("backup_imported"), analytics.names())
        }

    @Test
    fun `confirming restores the preferences but keeps this device's privacy switches`() =
        runTest {
            val preferences = InMemoryPreferencesRepository(Preferences(usageStatisticsEnabled = false, personalizedAdsEnabled = true))
            val viewModel =
                makeViewModel(
                    documents = InMemoryDocumentStore(mapOf("content://in" to backupFile())),
                    preferences = preferences,
                )

            viewModel.prepareImport("content://in")
            viewModel.confirmImport()

            assertEquals(
                Preferences(
                    pushEnabled = false,
                    remindersEnabled = true,
                    currency = CurrencyOption.BRL,
                    unitSystem = UnitSystem.METRIC,
                    language = LanguageOption.PORTUGUESE,
                    usageStatisticsEnabled = false,
                    personalizedAdsEnabled = true,
                ),
                preferences.stored,
            )
            assertEquals(CurrencyOption.BRL, DisplayPreferences.current.currency)
        }

    @Test
    fun `a preferences failure does not undo a restore`() =
        runTest {
            val repository = InMemoryBackupRepository()
            val documents = InMemoryDocumentStore(mapOf("content://in" to backupFile()))
            val preferences = InMemoryPreferencesRepository()
            val viewModel = makeViewModel(repository = repository, documents = documents, preferences = preferences)

            viewModel.prepareImport("content://in")
            preferences.failure = IllegalStateException("disk full")
            viewModel.confirmImport()

            assertEquals(1, repository.replaceCount)
            assertEquals(BackupViewModel.State.Done(BackupViewModel.Result.IMPORTED), viewModel.state)
            assertTrue(appliedLanguages.isEmpty())
        }

    @Test
    fun `confirming resyncs the reminders once`() =
        runTest {
            val viewModel = makeViewModel(documents = InMemoryDocumentStore(mapOf("content://in" to backupFile())))

            viewModel.prepareImport("content://in")
            viewModel.confirmImport()

            assertEquals(1, reminderSyncs)
        }

    @Test
    fun `the language is applied only when the restore changes it`() =
        runTest {
            val documents = InMemoryDocumentStore(mapOf("content://in" to backupFile()))
            val preferences = InMemoryPreferencesRepository()
            val viewModel = makeViewModel(documents = documents, preferences = preferences)

            viewModel.prepareImport("content://in")
            viewModel.confirmImport()
            viewModel.prepareImport("content://in")
            viewModel.confirmImport()

            assertEquals(listOf(LanguageOption.PORTUGUESE), appliedLanguages)
        }

    @Test
    fun `cancelling discards the pending file`() =
        runTest {
            val repository = InMemoryBackupRepository()
            val viewModel = makeViewModel(repository = repository, documents = InMemoryDocumentStore(mapOf("content://in" to backupFile())))

            viewModel.prepareImport("content://in")
            viewModel.cancelImport()
            viewModel.confirmImport()

            assertEquals(BackupViewModel.State.Idle, viewModel.state)
            assertEquals(0, repository.replaceCount)
        }

    @Test
    fun `confirming without a pending file does nothing`() =
        runTest {
            val repository = InMemoryBackupRepository()
            val viewModel = makeViewModel(repository = repository)

            viewModel.confirmImport()

            assertEquals(BackupViewModel.State.Idle, viewModel.state)
            assertEquals(0, repository.replaceCount)
            assertTrue(analytics.names().isEmpty())
        }

    @Test
    fun `a failed replace reports a storage reason`() =
        runTest {
            val repository = InMemoryBackupRepository()
            val viewModel = makeViewModel(repository = repository, documents = InMemoryDocumentStore(mapOf("content://in" to backupFile())))

            viewModel.prepareImport("content://in")
            repository.failure = RepositoryError.PersistenceFailed(IllegalStateException())
            viewModel.confirmImport()

            assertIs<RepositoryError.PersistenceFailed>(assertIs<BackupViewModel.State.Failed>(viewModel.state).error)
            assertEquals("storage", analytics.parameters("backup_import_failed")?.get("reason"))
            assertEquals(0, reminderSyncs)
            viewModel.dismiss()
            assertEquals(BackupViewModel.State.Idle, viewModel.state)
        }

    @Test
    fun `every action is refused when the app storage could not be opened`() =
        runTest {
            val repository = InMemoryBackupRepository()
            val documents = InMemoryDocumentStore(mapOf("content://in" to backupFile()))
            val viewModel = makeViewModel(repository = repository, documents = documents, isStoreAvailable = false)

            viewModel.export("content://out.json")
            viewModel.exportSpreadsheet("content://out.csv", TestLocalizer())
            viewModel.prepareImport("content://in")
            viewModel.confirmImport()

            assertEquals(BackupViewModel.State.Idle, viewModel.state)
            assertEquals(0, documents.writes)
            assertEquals(0, repository.replaceCount)
            assertTrue(analytics.names().isEmpty())
        }

    @Test
    fun `cancellation is rethrown and not reported as a failure`() =
        runTest {
            val repository = InMemoryBackupRepository(failure = CancellationException("left the screen"))
            val viewModel = makeViewModel(repository = repository)

            assertFailsWith<CancellationException> { viewModel.export("content://out.json") }

            assertEquals(BackupViewModel.State.Idle, viewModel.state)
            assertTrue(analytics.names().isEmpty())
        }

    @Test
    fun `a second action while one is running is ignored`() =
        runTest {
            val documents = InMemoryDocumentStore()
            documents.gate = CompletableDeferred()
            val viewModel = makeViewModel(documents = documents)

            val first = launch { viewModel.exportSpreadsheet("content://first.csv", TestLocalizer()) }
            runCurrent()
            assertEquals(BackupViewModel.State.Working, viewModel.state)
            viewModel.export("content://second.json")
            documents.gate?.complete(Unit)
            first.join()

            assertEquals(setOf("content://first.csv"), documents.files.keys)
            assertEquals(listOf("backup_spreadsheet_exported"), analytics.names())
        }

    @Test
    fun `the launching wrappers run each action in the view model scope`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            try {
                val repository = InMemoryBackupRepository()
                val documents = InMemoryDocumentStore(mapOf("content://in" to backupFile()))
                val viewModel = makeViewModel(repository = repository, documents = documents)

                viewModel.launchExport("content://out.json").join()
                viewModel.launchSpreadsheetExport("content://out.csv", TestLocalizer()).join()
                viewModel.launchPrepareImport("content://in").join()
                viewModel.launchConfirmImport().join()

                assertEquals(setOf("content://in", "content://out.json", "content://out.csv"), documents.files.keys)
                assertEquals(1, repository.replaceCount)
                assertNull(repository.stored.preferences)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `the spreadsheet follows the language of the screen that asks for it`() =
        runTest {
            val documents = InMemoryDocumentStore()
            val viewModel = makeViewModel(documents = documents)
            val portuguese = TestLocalizer("values-pt-rBR", Locale.forLanguageTag("pt-BR"))

            viewModel.exportSpreadsheet("content://files/historico.csv", portuguese)

            assertEquals("rodaro-historico-2026-11-09.csv", viewModel.spreadsheetFileName(portuguese))
            assertTrue(documents.files.getValue("content://files/historico.csv").startsWith("\uFEFFVeículo;Placa"))
        }

    @Test
    fun `cancellation while restoring the preferences is rethrown`() =
        runTest {
            val preferences = InMemoryPreferencesRepository()
            val viewModel =
                makeViewModel(
                    documents = InMemoryDocumentStore(mapOf("content://in" to backupFile())),
                    preferences = preferences,
                )

            viewModel.prepareImport("content://in")
            preferences.failure = CancellationException("left the screen")

            assertFailsWith<CancellationException> { viewModel.confirmImport() }
            assertEquals(0, reminderSyncs)
            assertTrue(appliedLanguages.isEmpty())
        }
}
