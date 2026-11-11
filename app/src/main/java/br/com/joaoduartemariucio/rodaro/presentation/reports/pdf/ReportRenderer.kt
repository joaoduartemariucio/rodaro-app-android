package br.com.joaoduartemariucio.rodaro.presentation.reports.pdf

interface ReportRenderer {
    suspend fun render(
        document: ReportDocument,
        pageLabel: (Int, Int) -> String,
    ): ByteArray
}
