package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.calculations.assertDecimal
import br.com.joaoduartemariucio.rodaro.calculations.day
import br.com.joaoduartemariucio.rodaro.calculations.decimal
import br.com.joaoduartemariucio.rodaro.calculations.full
import br.com.joaoduartemariucio.rodaro.calculations.testVehicleId
import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.FuelRatioEstimate
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelGroup
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.Preferences
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.UnitSystem
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.fakes.FakeAnalytics
import br.com.joaoduartemariucio.rodaro.fakes.FixedDateProvider
import br.com.joaoduartemariucio.rodaro.fakes.TestLocalizer
import br.com.joaoduartemariucio.rodaro.presentation.common.DisplayPreferences
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedPerKilometer
import br.com.joaoduartemariucio.rodaro.presentation.fueling.advisor.FuelAdvisorViewModel
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FuelAdvisorViewModelTests {
    private val vehicle = Vehicle(id = testVehicleId, name = "Onix", createdAt = day(0))
    private val localizer = TestLocalizer()
    private val portuguese = TestLocalizer("values-pt-rBR")

    @After
    fun tearDown() {
        DisplayPreferences.update(Preferences())
    }

    private fun personalHistory(): List<FuelEntry> {
        val records = mutableListOf<FuelEntry>()
        var odometer = 1_000.0
        for (day in 0..40 step 10) {
            records += full(day, odometer, 40.0, price = decimal("3.99"), fuelType = if (day < 40) FuelType.ETANOL else FuelType.GASOLINA_COMUM)
            odometer += 400.0
        }
        odometer -= 400.0
        for (day in 50..80 step 10) {
            odometer += 420.0
            records += full(day, odometer, 30.0, price = decimal("5.89"), fuelType = FuelType.GASOLINA_COMUM)
        }
        return records
    }

    private fun makeViewModel(
        fuel: List<FuelEntry> = personalHistory(),
        analytics: FakeAnalytics = FakeAnalytics(),
    ) = FuelAdvisorViewModel(vehicle, fuel, FixedDateProvider(day(90)), analytics)

    @Test
    fun `prices start from the last fill-up of each fuel`() {
        val viewModel = makeViewModel()

        assertDecimal("3.99", viewModel.parsedEthanolPrice)
        assertDecimal("5.89", viewModel.parsedGasolinePrice)
        assertIs<FuelRatioEstimate.Basis.Personal>(viewModel.estimate.basis)
    }

    @Test
    fun `the advice follows the prices typed`() {
        val viewModel = makeViewModel()

        assertEquals(FuelGroup.ETANOL, viewModel.advice?.cheaper)
        assertEquals("Fill up with ethanol", viewModel.verdict(localizer))
        assertEquals(NumberInput.text(decimal("4.207"), NumberInput.Style.FUEL_PRICE), viewModel.thresholdText)
        assertEquals(
            "Ethanol ${decimal("0.399").formattedPerKilometer(localizer)} · Gasoline ${decimal("0.4207142857").formattedPerKilometer(localizer)}",
            viewModel.costPerKilometer(localizer),
        )

        viewModel.ethanolPrice = NumberInput.text(decimal("4.50"), NumberInput.Style.FUEL_PRICE)
        assertEquals("Fill up with gasoline", viewModel.verdict(localizer))

        viewModel.ethanolPrice = ""
        assertNull(viewModel.advice)
        assertNull(viewModel.verdict(localizer))
        assertNull(viewModel.thresholdText)
        assertNull(viewModel.costPerKilometer(localizer))
    }

    @Test
    fun `the basis is always explained`() {
        assertEquals("Seu veículo: o etanol rende 71% da gasolina (6 trechos medidos).", makeViewModel().basis(portuguese))

        val general = makeViewModel(fuel = listOf(full(0, 1_000.0, 40.0, fuelType = FuelType.ETANOL)))
        assertTrue(general.basis(localizer).startsWith("General rule: ethanol pays off up to 70% of the gasoline price. Log at least 3"))
        assertNull(general.costPerKilometer(localizer))
        assertEquals("", general.gasolinePrice)
    }

    @Test
    fun `alternating fuels shows the hint to fill up twice with the same fuel`() {
        val types = listOf(FuelType.ETANOL, FuelType.GASOLINA_COMUM)
        val alternating = (0..6).map { full(it * 10, 1_000.0 + it * 400, 40.0, fuelType = types[it % 2]) }

        val viewModel = makeViewModel(fuel = alternating)

        assertTrue(viewModel.showsAlternatingHint)
        assertTrue(viewModel.basis(portuguese).endsWith("Abasteça 2 vezes seguidas com o mesmo combustível para medir o consumo de cada um."))
        assertFalse(makeViewModel().showsAlternatingHint)
    }

    @Test
    fun `an inconsistent ratio says so`() {
        val inconsistent = personalHistory().map { if (it.fuelType == FuelType.GASOLINA_COMUM && it.liters == 30.0) it.copy(liters = 42.0) else it }

        assertTrue(makeViewModel(fuel = inconsistent).basis(localizer).endsWith("(inconsistent data), so the general rule is used."))
    }

    @Test
    fun `opening the advisor is reported with its basis`() {
        val analytics = FakeAnalytics()

        makeViewModel(analytics = analytics)
        makeViewModel(fuel = emptyList(), analytics = analytics)

        assertEquals(listOf("fuel_advisor_used", "fuel_advisor_used"), analytics.names())
        assertEquals("personal", analytics.events[0].parameters["basis"])
        assertEquals("general_not_enough_data", analytics.events[1].parameters["basis"])
    }

    @Test
    fun `prices are shown and read per gallon under the imperial system`() {
        DisplayPreferences.update(Preferences(unitSystem = UnitSystem.IMPERIAL))
        val viewModel = makeViewModel()

        assertEquals(NumberInput.text(decimal("15.10379301816"), NumberInput.Style.FUEL_PRICE), viewModel.ethanolPrice)
        assertEquals(3.99, assertNotNull(viewModel.parsedEthanolPrice).toDouble(), 0.001)
        assertEquals(FuelGroup.ETANOL, viewModel.advice?.cheaper)
        assertTrue(assertNotNull(viewModel.costPerKilometer(localizer)).contains("/mi"))
    }
}
