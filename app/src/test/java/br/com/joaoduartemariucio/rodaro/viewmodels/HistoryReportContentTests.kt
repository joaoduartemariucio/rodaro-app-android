package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.calculations.testVehicleId
import br.com.joaoduartemariucio.rodaro.calculations.utc
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.domain.reports.calculations.HistoryOptions
import br.com.joaoduartemariucio.rodaro.domain.reports.calculations.VehicleHistories
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.fakes.TestLocalizer
import br.com.joaoduartemariucio.rodaro.presentation.reports.HistoryReportContent
import br.com.joaoduartemariucio.rodaro.presentation.reports.pdf.ReportBlock
import br.com.joaoduartemariucio.rodaro.presentation.reports.pdf.ReportDocument
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HistoryReportContentTests {
    private val english = TestLocalizer()
    private val portuguese = TestLocalizer("values-pt-rBR", Locale.forLanguageTag("pt-BR"))

    private fun at(date: String): Instant = LocalDate.parse(date).atStartOfDay(utc).toInstant()

    private val vehicle = Vehicle(id = testVehicleId, name = "Gol", plate = "ABC1D23", initialOdometer = 10_000.0, createdAt = at("2024-12-15"), modelYear = 2019)

    private val fills =
        (0..13).map {
            FuelEntry(
                vehicleId = testVehicleId,
                date =
                    YearMonth
                        .of(2024, 12)
                        .plusMonths(it.toLong())
                        .atDay(15)
                        .atStartOfDay(utc)
                        .toInstant(),
                odometer = 10_000.0 + 1_000.0 * it,
                liters = 40.0,
                pricePerLiter = BigDecimal(5),
                isFullTank = true,
                fuelType = if (it == 3) null else FuelType.GASOLINA_COMUM,
            )
        }

    private val services =
        listOf(
            ServiceEntry(vehicleId = testVehicleId, kind = ServiceKind.OIL_CHANGE, date = at("2025-02-01"), odometer = 12_200.0, cost = BigDecimal("250"), notes = "5W30", workshop = "Oficina do Zé", createdAt = at("2025-05-01")),
            ServiceEntry(vehicleId = testVehicleId, kind = ServiceKind.BRAKES, date = at("2025-03-01"), odometer = 9_000.0, cost = BigDecimal("400")),
        )

    private fun document(
        options: HistoryOptions = HistoryOptions(),
        localizer: TestLocalizer = english,
    ): ReportDocument = HistoryReportContent.document(VehicleHistories.build(vehicle, fills, services, options, at("2026-03-01"), utc), localizer, utc)

    private fun ReportDocument.table(title: String): ReportBlock.Table {
        val index = blocks.indexOfFirst { it is ReportBlock.Title && it.text == title }
        return assertIs<ReportBlock.Table>(blocks[index + 1])
    }

    @Test
    fun `the disclaimer comes first, verbatim, then the generation date and record count`() {
        val document = document(localizer = portuguese)

        assertEquals(
            ReportBlock.Paragraph(
                "Histórico informado pelo proprietário no app Rodaro; não verificado. Confira notas fiscais, manual de revisões e débitos no Detran.",
                emphasis = true,
            ),
            document.blocks.first(),
        )
        assertTrue(assertIs<ReportBlock.Paragraph>(document.blocks[1]).text.contains("16 registros"))
        assertEquals("Histórico do veículo", document.title)
        assertEquals("Rodaro · Gol", document.footer)
    }

    @Test
    fun `the words laudo and certificado never appear in the report strings or the document`() {
        for (folder in listOf("values", "values-pt-rBR")) {
            val strings =
                DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(File(TestLocalizer.resourceRoot(), "$folder/strings.xml"))
                    .getElementsByTagName("string")
            val texts =
                (0 until strings.length)
                    .map { strings.item(it) as Element }
                    .filter { it.getAttribute("name").startsWith("report_") }
                    .map { it.textContent.lowercase() }
            assertTrue(texts.isNotEmpty())
            assertTrue(texts.none { "laudo" in it || "certificad" in it || "certificate" in it || "certified" in it }, folder)
        }
        val text = document(HistoryOptions(true, true, true), portuguese).text.lowercase()
        assertFalse("laudo" in text || "certificad" in text)
    }

    @Test
    fun `the cost column and totals appear only when costs are on`() {
        val plain = document()
        assertFalse("Cost" in plain.table("Services").headers)
        assertTrue(plain.blocks.none { it is ReportBlock.Title && it.text == "Totals" })

        val withCosts = document(HistoryOptions(includeCosts = true, includeFuelLog = true))
        assertEquals("Cost", withCosts.table("Services").headers.last())
        assertTrue(withCosts.blocks.any { it is ReportBlock.Title && it.text == "Totals" })
        assertEquals(6, withCosts.table("Fuel-ups").headers.size)
        assertEquals(4, document(HistoryOptions(includeFuelLog = true)).table("Fuel-ups").headers.size)
    }

    @Test
    fun `a retroactive service is labelled and readings that went backwards are listed`() {
        val document = document()

        val rows = document.table("Services").rows
        assertTrue(rows.first()[2].endsWith("(logged later)"))
        assertFalse(rows.last()[2].contains("logged later"))
        assertEquals("Oficina do Zé", rows.first()[3])
        assertEquals("—", rows.last()[3])
        assertEquals(listOf("Fuel-up", "Service"), document.table("Odometer readings that went backwards").rows.map { it[1] })
        assertTrue(document.table("Distance per year").rows.any { it.first() == "2025" })
        assertTrue(document.text.contains("ABC1D**"))
        assertTrue(document(HistoryOptions(showFullPlate = true)).text.contains("ABC1D23"))
    }
}
