package br.com.joaoduartemariucio.rodaro.presentation.reports.pdf

sealed interface ReportBlock {
    data class Title(
        val text: String,
    ) : ReportBlock

    data class Paragraph(
        val text: String,
        val emphasis: Boolean = false,
    ) : ReportBlock

    data class Facts(
        val rows: List<Pair<String, String>>,
    ) : ReportBlock

    data class Table(
        val headers: List<String>,
        val weights: List<Float>,
        val rows: List<List<String>>,
    ) : ReportBlock
}

data class ReportDocument(
    val title: String,
    val footer: String,
    val blocks: List<ReportBlock>,
) {
    val text: String
        get() =
            (
                listOf(title, footer) +
                    blocks.flatMap { block ->
                        when (block) {
                            is ReportBlock.Title -> listOf(block.text)
                            is ReportBlock.Paragraph -> listOf(block.text)
                            is ReportBlock.Facts -> block.rows.flatMap { listOf(it.first, it.second) }
                            is ReportBlock.Table -> block.headers + block.rows.flatten()
                        }
                    }
            ).joinToString("\n")
}
