package br.com.joaoduartemariucio.rodaro.domain.backup.services

import br.com.joaoduartemariucio.rodaro.domain.backup.entities.BackupSnapshot

interface BackupCodec {
    suspend fun encode(snapshot: BackupSnapshot): String

    suspend fun decode(text: String): BackupSnapshot
}
