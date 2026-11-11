package br.com.joaoduartemariucio.rodaro.presentation.reports.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class PdfReportRenderer(
    private val page: PageSpec = PageSpec(),
) : ReportRenderer {
    override suspend fun render(
        document: ReportDocument,
        pageLabel: (Int, Int) -> String,
    ): ByteArray =
        withContext(Dispatchers.Default) {
            val measurer = TextMeasurer { text, style, width -> layout(text, style, width).height.toFloat() }
            val pages = ReportLayout.paginate(document, page, measurer)
            val pdf = PdfDocument()
            try {
                pages.forEachIndexed { index, blocks ->
                    val info = PdfDocument.PageInfo.Builder(page.width.toInt(), page.height.toInt(), index + 1).create()
                    val current = pdf.startPage(info)
                    draw(current.canvas, blocks)
                    footer(current.canvas, "${pageLabel(index + 1, pages.size)} · ${document.footer}")
                    pdf.finishPage(current)
                }
                ByteArrayOutputStream().use { stream ->
                    pdf.writeTo(stream)
                    stream.toByteArray()
                }
            } finally {
                pdf.close()
            }
        }

    private fun draw(
        canvas: Canvas,
        blocks: List<PlacedBlock>,
    ) {
        for (block in blocks) {
            val top = page.margin + block.y
            when (block) {
                is PlacedBlock.Text -> {
                    drawLayout(canvas, layout(block.text, block.style, page.contentWidth), page.margin, top)
                }

                is PlacedBlock.Row -> {
                    var x = page.margin
                    block.cells.forEachIndexed { index, cell ->
                        val width = block.widths[index]
                        drawLayout(
                            canvas,
                            layout(cell, block.style, width - 2 * ReportLayout.CELL_PADDING),
                            x + ReportLayout.CELL_PADDING,
                            top + ReportLayout.CELL_PADDING,
                        )
                        x += width
                    }
                    val bottom = top + block.height
                    canvas.drawLine(page.margin, bottom, page.margin + page.contentWidth, bottom, line(block.style))
                }
            }
        }
    }

    private fun footer(
        canvas: Canvas,
        text: String,
    ) {
        val footer = layout(text, ReportStyle.TABLE_CELL, page.contentWidth)
        drawLayout(canvas, footer, page.margin, page.height - page.margin - footer.height)
    }

    private fun drawLayout(
        canvas: Canvas,
        layout: StaticLayout,
        x: Float,
        y: Float,
    ) {
        canvas.withTranslation(x, y) { layout.draw(this) }
    }

    private fun layout(
        text: String,
        style: ReportStyle,
        width: Float,
    ): StaticLayout =
        StaticLayout.Builder
            .obtain(text, 0, text.length, paint(style), maxOf(1, width.toInt()))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()

    private fun paint(style: ReportStyle): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            when (style) {
                ReportStyle.TITLE -> {
                    textSize = TITLE_SIZE
                    typeface = Typeface.DEFAULT_BOLD
                }

                ReportStyle.EMPHASIS -> {
                    textSize = BODY_SIZE
                    typeface = Typeface.DEFAULT_BOLD
                }

                ReportStyle.BODY -> {
                    textSize = BODY_SIZE
                }

                ReportStyle.TABLE_HEADER -> {
                    textSize = TABLE_SIZE
                    typeface = Typeface.DEFAULT_BOLD
                }

                ReportStyle.TABLE_CELL -> {
                    textSize = TABLE_SIZE
                    color = Color.DKGRAY
                }
            }
        }

    private fun line(style: ReportStyle): Paint =
        Paint().apply {
            color = if (style == ReportStyle.TABLE_HEADER) Color.DKGRAY else Color.LTGRAY
            strokeWidth = if (style == ReportStyle.TABLE_HEADER) 1f else 0.5f
        }

    private companion object {
        const val TITLE_SIZE = 14f
        const val BODY_SIZE = 10f
        const val TABLE_SIZE = 8.5f
    }
}
