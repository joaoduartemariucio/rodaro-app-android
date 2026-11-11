package br.com.joaoduartemariucio.rodaro.domain.backup.repositories

import br.com.joaoduartemariucio.rodaro.domain.backup.entities.BackupSnapshot

interface BackupRepository {
    suspend fun snapshot(): BackupSnapshot

    suspend fun replaceAll(snapshot: BackupSnapshot)
}
