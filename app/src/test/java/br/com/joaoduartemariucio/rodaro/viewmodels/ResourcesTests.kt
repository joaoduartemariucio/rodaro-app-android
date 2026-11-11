package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDiscount
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineSeverity
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueKind
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelGroup
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.MaintenanceStatus
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.FuelCapability
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import br.com.joaoduartemariucio.rodaro.fakes.TestLocalizer
import br.com.joaoduartemariucio.rodaro.presentation.common.icon
import br.com.joaoduartemariucio.rodaro.presentation.common.label
import br.com.joaoduartemariucio.rodaro.presentation.common.shortLabel
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResourcesTests {
    private val english = TestLocalizer()
    private val portuguese = TestLocalizer("values-pt-rBR")

    private fun names(folder: String): Map<String, String> {
        val document =
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(TestLocalizer.resourceRoot(), "$folder/strings.xml"))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .filter { it.getAttribute("translatable") != "false" }
            .associate { it.getAttribute("name") to it.textContent }
    }

    private fun placeholders(text: String) = Regex("%(\\d+\\$)?[sd]").findAll(text).count()

    @Test
    fun `every string has a pt-BR translation with the same placeholders`() {
        val en = names("values")
        val pt = names("values-pt-rBR")

        assertEquals(en.keys, pt.keys)
        for ((name, text) in en) {
            assertEquals(placeholders(text), placeholders(pt.getValue(name)), name)
        }
    }

    @Test
    fun `every service kind has its own label in both languages and an icon`() {
        for (localizer in listOf(english, portuguese)) {
            assertEquals(
                ServiceKind.entries.size,
                ServiceKind.entries
                    .map { localizer.string(it.label) }
                    .toSet()
                    .size,
            )
        }
        assertTrue(ServiceKind.entries.all { it.icon.name.isNotEmpty() })
    }

    @Test
    fun `every vehicle kind and status has a label and an icon`() {
        assertEquals(listOf("Car", "Motorcycle"), VehicleKind.entries.map { english.string(it.label) })
        assertEquals(listOf("Carro", "Moto"), VehicleKind.entries.map { portuguese.string(it.label) })
        assertEquals(
            2,
            VehicleKind.entries
                .map { it.icon }
                .toSet()
                .size,
        )
        assertEquals(
            3,
            MaintenanceStatus.Level.entries
                .map { portuguese.string(it.label) }
                .toSet()
                .size,
        )
    }

    private fun <T> assertUniqueLabels(
        values: List<T>,
        label: (T) -> Int,
    ) {
        for (localizer in listOf(english, portuguese)) {
            assertEquals(values.size, values.map { localizer.string(label(it)) }.toSet().size, values.toString())
        }
    }

    @Test
    fun `every fuel type, capability and group has its own label in both languages`() {
        assertUniqueLabels(FuelType.entries) { it.label }
        assertUniqueLabels(FuelType.entries) { it.shortLabel }
        assertUniqueLabels(FuelCapability.entries) { it.label }
        assertUniqueLabels(FuelGroup.entries) { it.label }
        assertEquals("Gasolina comum", portuguese.string(FuelType.GASOLINA_COMUM.label))
    }

    @Test
    fun `every expense category has its own label in both languages and an icon`() {
        assertUniqueLabels(ExpenseCategory.entries) { it.label }
        assertEquals(
            ExpenseCategory.entries.size,
            ExpenseCategory.entries
                .map { it.icon }
                .toSet()
                .size,
        )
        assertEquals("Multa", portuguese.string(ExpenseCategory.MULTA.label))
        assertUniqueLabels(FineSeverity.entries) { it.label }
        assertUniqueLabels(FineDiscount.entries) { it.label }
        assertUniqueLabels(DueKind.entries) { it.label }
        assertEquals(
            DueKind.entries.size,
            DueKind.entries
                .map { it.icon }
                .toSet()
                .size,
        )
    }
}
