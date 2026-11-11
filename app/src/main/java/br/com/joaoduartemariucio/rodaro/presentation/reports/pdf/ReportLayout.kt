package br.com.joaoduartemariucio.rodaro.presentation.reports.pdf

enum class ReportStyle {
    TITLE,
    BODY,
    EMPHASIS,
    TABLE_HEADER,
    TABLE_CELL,
}

fun interface TextMeasurer {
    fun height(
        text: String,
        style: ReportStyle,
        width: Float,
    ): Float
}

data class PageSpec(
    val width: Float = 595f,
    val height: Float = 842f,
    val margin: Float = 40f,
    val footerHeight: Float = 24f,
) {
    val contentWidth: Float get() = width - 2 * margin
    val contentHeight: Float get() = height - 2 * margin - footerHeight
}

sealed interface PlacedBlock {
    val y: Float
    val height: Float

    data class Text(
        val text: String,
        val style: ReportStyle,
        override val y: Float,
        override val height: Float,
    ) : PlacedBlock

    data class Row(
        val cells: List<String>,
        val widths: List<Float>,
        val style: ReportStyle,
        override val y: Float,
        override val height: Float,
    ) : PlacedBlock
}

object ReportLayout {
    const val BLOCK_GAP = 10f
    const val CELL_PADDING = 3f
    private const val FACT_LABEL_WEIGHT = 0.4f
    private const val FACT_VALUE_WEIGHT = 0.6f

    private class Slot(
        val height: Float,
        val place: (Float) -> PlacedBlock,
    )

    private class Pages(
        val spec: PageSpec,
    ) {
        val pages = mutableListOf<MutableList<PlacedBlock>>(mutableListOf())
        var y = 0f

        val current: MutableList<PlacedBlock> get() = pages.last()

        fun fits(height: Float): Boolean = current.isEmpty() || y + height <= spec.contentHeight

        fun newPage() {
            if (current.isNotEmpty()) pages += mutableListOf<PlacedBlock>()
            y = 0f
        }

        fun place(slot: Slot) {
            current += slot.place(y)
            y += slot.height
        }

        fun gap() {
            if (current.isNotEmpty()) y += BLOCK_GAP
        }
    }

    fun paginate(
        document: ReportDocument,
        page: PageSpec,
        measure: TextMeasurer,
    ): List<List<PlacedBlock>> {
        val pages = Pages(page)
        document.blocks.forEachIndexed { index, block ->
            pages.gap()
            when (block) {
                is ReportBlock.Title -> {
                    val title = text(block.text, ReportStyle.TITLE, page, measure)
                    val next = document.blocks.getOrNull(index + 1)?.let { firstHeight(it, page, measure) } ?: 0f
                    if (!pages.fits(title.height + BLOCK_GAP + next)) pages.newPage()
                    pages.place(title)
                }

                is ReportBlock.Paragraph -> {
                    val paragraph = text(block.text, if (block.emphasis) ReportStyle.EMPHASIS else ReportStyle.BODY, page, measure)
                    if (!pages.fits(paragraph.height)) pages.newPage()
                    pages.place(paragraph)
                }

                is ReportBlock.Facts -> {
                    block.rows.forEach { (label, value) ->
                        val row =
                            row(listOf(label, value), listOf(FACT_LABEL_WEIGHT, FACT_VALUE_WEIGHT), ReportStyle.TABLE_CELL, page, measure)
                        if (!pages.fits(row.height)) pages.newPage()
                        pages.place(row)
                    }
                }

                is ReportBlock.Table -> {
                    val header = row(block.headers, block.weights, ReportStyle.TABLE_HEADER, page, measure)
                    val rows = block.rows.map { row(it, block.weights, ReportStyle.TABLE_CELL, page, measure) }
                    if (!pages.fits(header.height + (rows.firstOrNull()?.height ?: 0f))) pages.newPage()
                    pages.place(header)
                    rows.forEach { row ->
                        if (!pages.fits(row.height)) {
                            pages.newPage()
                            pages.place(header)
                        }
                        pages.place(row)
                    }
                }
            }
        }
        return pages.pages.filter { it.isNotEmpty() }.ifEmpty { listOf(emptyList()) }
    }

    private fun firstHeight(
        block: ReportBlock,
        page: PageSpec,
        measure: TextMeasurer,
    ): Float =
        when (block) {
            is ReportBlock.Title -> {
                text(block.text, ReportStyle.TITLE, page, measure).height
            }

            is ReportBlock.Paragraph -> {
                text(block.text, if (block.emphasis) ReportStyle.EMPHASIS else ReportStyle.BODY, page, measure).height
            }

            is ReportBlock.Facts -> {
                block.rows.firstOrNull()?.let { (label, value) ->
                    row(listOf(label, value), listOf(FACT_LABEL_WEIGHT, FACT_VALUE_WEIGHT), ReportStyle.TABLE_CELL, page, measure).height
                } ?: 0f
            }

            is ReportBlock.Table -> {
                row(block.headers, block.weights, ReportStyle.TABLE_HEADER, page, measure).height +
                    (block.rows.firstOrNull()?.let { row(it, block.weights, ReportStyle.TABLE_CELL, page, measure).height } ?: 0f)
            }
        }

    private fun text(
        text: String,
        style: ReportStyle,
        page: PageSpec,
        measure: TextMeasurer,
    ): Slot {
        val height = measure.height(text, style, page.contentWidth)
        return Slot(height) { y -> PlacedBlock.Text(text, style, y, height) }
    }

    private fun row(
        cells: List<String>,
        weights: List<Float>,
        style: ReportStyle,
        page: PageSpec,
        measure: TextMeasurer,
    ): Slot {
        val total = weights.sum().takeIf { it > 0 } ?: 1f
        val widths = cells.indices.map { page.contentWidth * (weights.getOrElse(it) { 0f } / total) }
        val height =
            cells.indices.maxOfOrNull { measure.height(cells[it], style, widths[it] - 2 * CELL_PADDING) }?.plus(2 * CELL_PADDING) ?: 0f
        return Slot(height) { y -> PlacedBlock.Row(cells, widths, style, y, height) }
    }
}
