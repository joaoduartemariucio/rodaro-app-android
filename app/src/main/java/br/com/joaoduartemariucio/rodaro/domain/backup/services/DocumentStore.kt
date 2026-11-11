package br.com.joaoduartemariucio.rodaro.domain.backup.services

interface DocumentStore {
    suspend fun write(
        location: String,
        text: String,
    )

    suspend fun read(location: String): String
}
