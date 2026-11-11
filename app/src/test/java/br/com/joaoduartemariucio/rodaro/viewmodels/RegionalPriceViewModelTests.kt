package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.calculations.day
import br.com.joaoduartemariucio.rodaro.calculations.testVehicleId
import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.PriceComparison
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.RegionalPrice
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.RegionalPriceTable
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.RegionalWeek
import br.com.joaoduartemariucio.rodaro.domain.fueling.services.RegionalPriceSource
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.CurrencyOption
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.Preferences
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.UnitSystem
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.fakes.FixedDateProvider
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryDueItemRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryExpenseRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryFuelEntryRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryServiceRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryVehicleRepository
import br.com.joaoduartemariucio.rodaro.presentation.common.DisplayPreferences
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.fueling.form.FuelEntryFormViewModel
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.VehicleDetailViewModel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegionalPriceViewModelTests {
    private val table =
        RegionalPriceTable(
            day(40),
            listOf(
                RegionalWeek(
                    LocalDate.parse("2001-01-28"),
                    LocalDate.parse("2001-02-03"),
                    mapOf("SP" to mapOf(FuelType.GASOLINA_COMUM to RegionalPrice(BigDecimal("2.000"), BigDecimal("0.100"), 50))),
                ),
            ),
        )

    private class FakeSource(
        val table: RegionalPriceTable?,
    ) : RegionalPriceSource {
        var calls = 0

        override suspend fun table(): RegionalPriceTable? {
            calls += 1
            return table
        }
    }

    private val vehicle = Vehicle(id = testVehicleId, name = "Gol", createdAt = day(0), uf = "SP")

    private fun entry(
        price: String,
        type: FuelType? = FuelType.GASOLINA_COMUM,
    ) = FuelEntry(vehicleId = vehicle.id, date = day(30), odometer = 1_000.0, liters = 30.0, pricePerLiter = BigDecimal(price), isFullTank = true, fuelType = type)

    @Before
    fun setUp() {
        DisplayPreferences.update(Preferences(currency = CurrencyOption.BRL))
    }

    @After
    fun tearDown() {
        DisplayPreferences.update(Preferences())
    }

    private fun detail(
        vehicle: Vehicle = this.vehicle,
        fuel: List<FuelEntry>,
        source: RegionalPriceSource?,
    ) = VehicleDetailViewModel(
        vehicle = vehicle,
        vehicleRepository = InMemoryVehicleRepository(listOf(vehicle)),
        fuelEntryRepository = InMemoryFuelEntryRepository(fuel),
        serviceRepository = InMemoryServiceRepository(),
        expenseRepository = InMemoryExpenseRepository(),
        dueItemRepository = InMemoryDueItemRepository(),
        dateProvider = FixedDateProvider(day(40)),
        regionalPriceSource = source,
    )

    @Test
    fun `the detail compares every fill-up it can with the state average`() =
        runTest {
            val cheap = entry("1.850")
            val unknown = entry("2.500", type = null)
            val viewModel = detail(fuel = listOf(cheap, unknown), source = FakeSource(table))
            viewModel.load()
            assertTrue(viewModel.priceComparisons.isEmpty())

            viewModel.loadRegionalPrices()

            assertEquals(table, viewModel.regionalTable)
            assertEquals(setOf(cheap.id), viewModel.priceComparisons.keys)
            assertEquals(PriceComparison.Level.BELOW, viewModel.priceComparisons.getValue(cheap.id).level)
            viewModel.load()
            assertEquals(setOf(cheap.id), viewModel.priceComparisons.keys)
        }

    @Test
    fun `no comparison and no request without a state, outside reais or without a source`() =
        runTest {
            val withoutUf = FakeSource(table)
            detail(vehicle.copy(uf = null), listOf(entry("1.850")), withoutUf).apply { load() }.loadRegionalPrices()
            assertEquals(0, withoutUf.calls)

            DisplayPreferences.update(Preferences(currency = CurrencyOption.USD))
            val dollars = FakeSource(table)
            val viewModel = detail(fuel = listOf(entry("1.850")), source = dollars)
            viewModel.load()
            viewModel.loadRegionalPrices()
            assertEquals(0, dollars.calls)
            assertTrue(viewModel.priceComparisons.isEmpty())

            DisplayPreferences.update(Preferences(currency = CurrencyOption.BRL))
            val none = detail(fuel = listOf(entry("1.850")), source = FakeSource(null))
            none.load()
            none.loadRegionalPrices()
            assertTrue(none.priceComparisons.isEmpty())
            detail(fuel = emptyList(), source = null).apply { load() }.loadRegionalPrices()
        }

    private fun form(
        uf: String? = "SP",
        table: RegionalPriceTable? = this.table,
    ) = FuelEntryFormViewModel(
        mode = FuelEntryFormViewModel.Mode.Create(vehicle.id),
        repository = InMemoryFuelEntryRepository(),
        dateProvider = FixedDateProvider(day(30)),
        otherReadings = emptyList(),
        suggestedFuelType = FuelType.GASOLINA_COMUM,
        regionalTable = table,
        uf = uf,
    )

    @Test
    fun `the form hint follows the typed price and fuel type`() {
        val viewModel = form()
        assertNull(viewModel.priceComparison)

        viewModel.pricePerLiter = NumberInput.text(BigDecimal("2.050"), NumberInput.Style.FUEL_PRICE)
        assertEquals(PriceComparison.Level.AVERAGE, assertIs<PriceComparison.Compared>(viewModel.priceComparison).level)
        viewModel.pricePerLiter = NumberInput.text(BigDecimal("2.199"), NumberInput.Style.FUEL_PRICE)
        assertEquals(PriceComparison.Level.ABOVE, assertIs<PriceComparison.Compared>(viewModel.priceComparison).level)

        viewModel.fuelType = FuelType.GASOLINA_PREMIUM
        assertEquals(PriceComparison.Unavailable(PriceComparison.Reason.NOT_SURVEYED), viewModel.priceComparison)
    }

    @Test
    fun `the form says to set the state, and stays quiet without a table or outside reais`() {
        val noUf = form(uf = null)
        noUf.pricePerLiter = NumberInput.text(BigDecimal("2.050"), NumberInput.Style.FUEL_PRICE)
        assertEquals(PriceComparison.Unavailable(PriceComparison.Reason.NO_UF), noUf.priceComparison)

        val noTable = form(table = null)
        noTable.pricePerLiter = NumberInput.text(BigDecimal("2.050"), NumberInput.Style.FUEL_PRICE)
        assertNull(noTable.priceComparison)

        DisplayPreferences.update(Preferences(currency = CurrencyOption.EUR))
        val euros = form()
        euros.pricePerLiter = NumberInput.text(BigDecimal("2.050"), NumberInput.Style.FUEL_PRICE)
        assertNull(euros.priceComparison)
    }

    @Test
    fun `an imperial price per gallon is compared per liter`() {
        DisplayPreferences.update(Preferences(currency = CurrencyOption.BRL, unitSystem = UnitSystem.IMPERIAL))
        val viewModel = form()

        viewModel.pricePerLiter = NumberInput.text(BigDecimal("7.571"), NumberInput.Style.FUEL_PRICE)

        val compared = assertIs<PriceComparison.Compared>(viewModel.priceComparison)
        assertEquals(0, BigDecimal("2.000").compareTo(compared.price))
        assertEquals(PriceComparison.Level.AVERAGE, compared.level)
    }
}
