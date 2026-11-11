package br.com.joaoduartemariucio.rodaro.fakes

import br.com.joaoduartemariucio.rodaro.domain.backup.entities.BackupSnapshot
import br.com.joaoduartemariucio.rodaro.domain.backup.errors.BackupError
import br.com.joaoduartemariucio.rodaro.domain.backup.repositories.BackupRepository
import br.com.joaoduartemariucio.rodaro.domain.backup.services.DocumentStore
import kotlinx.coroutines.CompletableDeferred

class InMemoryBackupRepository(
    var stored: BackupSnapshot = BackupSnapshot(),
    var failure: Exception? = null,
) : BackupRepository {
    var replaceCount = 0
        private set

    override suspend fun snapshot(): BackupSnapshot {
        failure?.let { throw it }
        return stored
    }

    override suspend fun replaceAll(snapshot: BackupSnapshot) {
        failure?.let { throw it }
        stored = snapshot.copy(preferences = null, exportedAt = null)
        replaceCount += 1
    }
}

class InMemoryDocumentStore(
    files: Map<String, String> = emptyMap(),
    var failure: Exception? = null,
    var limit: Int = Int.MAX_VALUE,
) : DocumentStore {
    val files = files.toMutableMap()
    var writes = 0
        private set
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun write(
        location: String,
        text: String,
    ) {
        gate?.await()
        failure?.let { throw it }
        files[location] = text
        writes += 1
    }

    override suspend fun read(location: String): String {
        gate?.await()
        failure?.let { throw it }
        val text = files[location] ?: throw BackupError.Storage(IllegalStateException("No file at $location"))
        if (text.length > limit) throw BackupError.TooLarge
        return text
    }
}
