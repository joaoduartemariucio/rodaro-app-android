package br.com.joaoduartemariucio.rodaro.presentation.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.app.NoAnalytics
import br.com.joaoduartemariucio.rodaro.domain.backup.calculations.BackupValidation
import br.com.joaoduartemariucio.rodaro.domain.backup.entities.BackupContents
import br.com.joaoduartemariucio.rodaro.domain.backup.entities.BackupSnapshot
import br.com.joaoduartemariucio.rodaro.domain.backup.entities.PortablePreferences
import br.com.joaoduartemariucio.rodaro.domain.backup.errors.BackupError
import br.com.joaoduartemariucio.rodaro.domain.backup.repositories.BackupRepository
import br.com.joaoduartemariucio.rodaro.domain.backup.services.BackupCodec
import br.com.joaoduartemariucio.rodaro.domain.backup.services.DocumentStore
import br.com.joaoduartemariucio.rodaro.domain.common.services.Analytics
import br.com.joaoduartemariucio.rodaro.domain.common.services.DateProvider
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.LanguageOption
import br.com.joaoduartemariucio.rodaro.domain.preferences.repositories.PreferencesRepository
import br.com.joaoduartemariucio.rodaro.presentation.common.DisplayPreferences
import br.com.joaoduartemariucio.rodaro.presentation.common.Localizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

class BackupViewModel(
    private val backupRepository: BackupRepository,
    private val codec: BackupCodec,
    private val documents: DocumentStore,
    private val preferencesRepository: PreferencesRepository,
    private val dateProvider: DateProvider,
    private val applyLanguage: (LanguageOption) -> Unit,
    private val syncReminders: suspend () -> Unit,
    val isStoreAvailable: Boolean = true,
    private val analytics: Analytics = NoAnalytics,
) : ViewModel() {
    sealed interface State {
        data object Idle : State

        data object Working : State

        data class ConfirmingImport(
            val incoming: BackupContents,
            val current: BackupContents,
            val exportedAt: Instant?,
        ) : State

        data class Done(
            val result: Result,
        ) : State

        data class Failed(
            val error: Throwable,
        ) : State
    }

    enum class Result {
        EXPORTED,
        SPREADSHEET_EXPORTED,
        IMPORTED,
    }

    var state: State by mutableStateOf(State.Idle)
        private set

    private var pending: BackupSnapshot? = null

    fun backupFileName(): String = "rodaro-backup-${today()}.json"

    fun spreadsheetFileName(localizer: Localizer): String = localizer.string(R.string.backup_spreadsheet_file_name, today())

    fun launchExport(location: String): Job = viewModelScope.launch { export(location) }

    fun launchSpreadsheetExport(
        location: String,
        localizer: Localizer,
    ): Job = viewModelScope.launch { exportSpreadsheet(location, localizer) }

    fun launchPrepareImport(location: String): Job = viewModelScope.launch { prepareImport(location) }

    fun launchConfirmImport(): Job = viewModelScope.launch { confirmImport() }

    suspend fun export(location: String) =
        perform(EXPORT_FAILED) {
            val snapshot =
                backupRepository.snapshot().copy(
                    preferences = PortablePreferences.from(preferencesRepository.get()),
                    exportedAt = dateProvider.now,
                )
            documents.write(location, codec.encode(snapshot))
            analytics.event("backup_exported")
            State.Done(Result.EXPORTED)
        }

    suspend fun exportSpreadsheet(
        location: String,
        localizer: Localizer,
    ) = perform(EXPORT_FAILED) {
        val snapshot = backupRepository.snapshot()
        val text = withContext(Dispatchers.Default) { BackupSpreadsheet.csv(snapshot, localizer, dateProvider.zone) }
        documents.write(location, text)
        analytics.event("backup_spreadsheet_exported")
        State.Done(Result.SPREADSHEET_EXPORTED)
    }

    suspend fun prepareImport(location: String) =
        perform(IMPORT_FAILED) {
            pending = null
            val incoming = codec.decode(documents.read(location))
            val problems = BackupValidation.problems(incoming)
            if (problems.isNotEmpty()) throw BackupError.Invalid(problems)
            val current = backupRepository.snapshot().contents
            pending = incoming
            State.ConfirmingImport(incoming.contents, current, incoming.exportedAt)
        }

    suspend fun confirmImport() {
        if (!isStoreAvailable || state == State.Working) return
        val incoming = pending ?: return
        pending = null
        var changedLanguage: LanguageOption? = null
        perform(IMPORT_FAILED) {
            backupRepository.replaceAll(incoming)
            changedLanguage = restore(incoming.preferences)
            syncReminders()
            analytics.event("backup_imported")
            State.Done(Result.IMPORTED)
        }
        changedLanguage?.let(applyLanguage)
    }

    fun cancelImport() {
        pending = null
        if (state is State.ConfirmingImport) state = State.Idle
    }

    fun dismiss() {
        if (state is State.Done || state is State.Failed) state = State.Idle
    }

    private suspend fun perform(
        failureEvent: String,
        block: suspend () -> State,
    ) {
        if (!isStoreAvailable || state == State.Working) return
        state = State.Working
        state =
            try {
                block()
            } catch (error: CancellationException) {
                state = State.Idle
                throw error
            } catch (error: Exception) {
                analytics.event(failureEvent, mapOf("reason" to reason(error)))
                State.Failed(error)
            }
    }

    private suspend fun restore(portable: PortablePreferences?): LanguageOption? {
        if (portable == null) return null
        return try {
            val current = preferencesRepository.get()
            val updated = portable.applied(current)
            preferencesRepository.save(updated)
            DisplayPreferences.update(updated)
            updated.language.takeIf { it != current.language }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            null
        }
    }

    private fun today(): String =
        dateProvider.now
            .atZone(dateProvider.zone)
            .toLocalDate()
            .toString()

    private fun reason(error: Exception): String =
        when (error) {
            is BackupError.Unreadable -> "unreadable"
            is BackupError.NewerFormat -> "newer_format"
            is BackupError.Invalid -> "invalid"
            is BackupError.TooLarge -> "too_large"
            else -> "storage"
        }

    private companion object {
        const val EXPORT_FAILED = "backup_export_failed"
        const val IMPORT_FAILED = "backup_import_failed"
    }
}
