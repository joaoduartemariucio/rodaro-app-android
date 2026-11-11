package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.presentation.reports.pdf.PageSpec
import br.com.joaoduartemariucio.rodaro.presentation.reports.pdf.PlacedBlock
import br.com.joaoduartemariucio.rodaro.presentation.reports.pdf.ReportBlock
import br.com.joaoduartemariucio.rodaro.presentation.reports.pdf.ReportDocument
import br.com.joaoduartemariucio.rodaro.presentation.reports.pdf.ReportLayout
import br.com.joaoduartemariucio.rodaro.presentation.reports.pdf.ReportStyle
import br.com.joaoduartemariucio.rodaro.presentation.reports.pdf.TextMeasurer
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReportLayoutTests {
    private val page = PageSpec(width = 300f, height = 200f, margin = 20f, footerHeight = 10f)

    private val measure = TextMeasurer { text, style, _ -> if (style == ReportStyle.TITLE) 20f else 10f * text.lines().size }

    private fun paginate(vararg blocks: ReportBlock) = ReportLayout.paginate(ReportDocument("T", "F", blocks.toList()), page, measure)

    @Test
    fun `blocks flow down and break onto a new page when full`() {
        val pages = paginate(*Array(20) { ReportBlock.Paragraph("line $it") })

        assertTrue(pages.size > 1)
        pages.forEach { blocks -> assertTrue(blocks.all { it.y + it.height <= page.contentHeight }) }
        assertEquals(20, pages.sumOf { it.size })
        assertEquals(0f, pages[1].first().y)
    }

    @Test
    fun `a table repeats its header on every page and never splits a row`() {
        val rows = List(30) { listOf("a$it", "b$it\nsecond line") }
        val pages = paginate(ReportBlock.Table(listOf("A", "B"), listOf(1f, 3f), rows))

        assertTrue(pages.size > 1)
        for (blocks in pages) {
            val first = assertIs<PlacedBlock.Row>(blocks.first())
            assertEquals(listOf("A", "B"), first.cells)
            assertEquals(ReportStyle.TABLE_HEADER, first.style)
            assertTrue(blocks.all { it.y + it.height <= page.contentHeight })
        }
        val cells = pages.flatten().filterIsInstance<PlacedBlock.Row>().filter { it.style == ReportStyle.TABLE_CELL }
        assertEquals(30, cells.size)
        assertEquals(26f, cells.first().height)
        assertEquals(listOf(65f, 195f), cells.first().widths)
    }

    @Test
    fun `a title is kept with the block that follows it`() {
        val filler = Array(14) { ReportBlock.Paragraph("x") }
        val pages = paginate(*filler, ReportBlock.Title("Section"), ReportBlock.Facts(listOf("k" to "v")))

        val last = pages.last()
        assertEquals("Section", assertIs<PlacedBlock.Text>(last.first()).text)
        assertIs<PlacedBlock.Row>(last[1])
    }

    @Test
    fun `an empty document is one empty page and titles before tables measure the header and first row`() {
        assertEquals(listOf(emptyList()), paginate())
        val pages =
            paginate(
                ReportBlock.Title("A"),
                ReportBlock.Title("B"),
                ReportBlock.Paragraph("p", emphasis = true),
                ReportBlock.Title("C"),
                ReportBlock.Table(listOf("h"), listOf(1f), emptyList()),
                ReportBlock.Title("D"),
                ReportBlock.Facts(emptyList()),
            )
        assertEquals(
            ReportStyle.EMPHASIS,
            pages
                .flatten()
                .filterIsInstance<PlacedBlock.Text>()
                .first { it.text == "p" }
                .style,
        )
        assertEquals(4, pages.flatten().filterIsInstance<PlacedBlock.Text>().count { it.style == ReportStyle.TITLE })
    }
}
