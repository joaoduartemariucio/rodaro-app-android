package br.com.joaoduartemariucio.rodaro.domain.reports.services

interface ReportFileStore {
    suspend fun write(
        location: String,
        bytes: ByteArray,
    )

    suspend fun shareable(
        fileName: String,
        bytes: ByteArray,
    ): String
}
