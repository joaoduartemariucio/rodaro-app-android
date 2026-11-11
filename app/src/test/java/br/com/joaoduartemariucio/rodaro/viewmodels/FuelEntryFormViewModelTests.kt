package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.calculations.assertDecimal
import br.com.joaoduartemariucio.rodaro.calculations.day
import br.com.joaoduartemariucio.rodaro.calculations.decimal
import br.com.joaoduartemariucio.rodaro.calculations.full
import br.com.joaoduartemariucio.rodaro.calculations.partial
import br.com.joaoduartemariucio.rodaro.calculations.rule
import br.com.joaoduartemariucio.rodaro.calculations.sample
import br.com.joaoduartemariucio.rodaro.calculations.service
import br.com.joaoduartemariucio.rodaro.calculations.testVehicleId
import br.com.joaoduartemariucio.rodaro.calculations.utc
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RepositoryError
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.Preferences
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.UnitSystem
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.FuelCapability
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.OdometerSample
import br.com.joaoduartemariucio.rodaro.fakes.FixedDateProvider
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryFuelEntryRepository
import br.com.joaoduartemariucio.rodaro.presentation.common.DisplayPreferences
import br.com.joaoduartemariucio.rodaro.presentation.common.FormState
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.common.ValidationIssue
import br.com.joaoduartemariucio.rodaro.presentation.fueling.form.FuelEntryFormViewModel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FuelEntryFormViewModelTests {
    @After
    fun tearDown() {
        DisplayPreferences.update(Preferences())
    }

    private fun makeViewModel(
        mode: FuelEntryFormViewModel.Mode = FuelEntryFormViewModel.Mode.Create(testVehicleId),
        repository: InMemoryFuelEntryRepository = InMemoryFuelEntryRepository(),
        otherReadings: List<OdometerSample> = emptyList(),
        suggestedPrice: BigDecimal? = null,
        capability: FuelCapability = FuelCapability.FLEX,
        suggestedPrices: Map<FuelType, BigDecimal> = emptyMap(),
        suggestedFuelType: FuelType? = FuelType.GASOLINA_COMUM,
    ) = FuelEntryFormViewModel(
        mode,
        repository,
        FixedDateProvider(day(30)),
        otherReadings,
        suggestedPrice,
        fuelCapability = capability,
        suggestedPrices = suggestedPrices,
        suggestedFuelType = suggestedFuelType,
    )

    private fun FuelEntryFormViewModel.fill(
        odometer: Double = 1_500.0,
        liters: Double = 40.0,
        price: BigDecimal = BigDecimal(5),
    ) {
        this.odometer = NumberInput.text(odometer, NumberInput.Style.KILOMETERS)
        this.liters = NumberInput.text(liters, NumberInput.Style.LITERS)
        pricePerLiter = NumberInput.text(price, NumberInput.Style.FUEL_PRICE)
    }

    @Test
    fun `a new form starts today, full tank, with every field required`() {
        val viewModel = makeViewModel()

        assertEquals(day(30), viewModel.date)
        assertTrue(viewModel.isFullTank)
        assertEquals(
            listOf(ValidationIssue.OdometerRequired, ValidationIssue.LitersRequired, ValidationIssue.PriceRequired),
            viewModel.issues,
        )
        assertFalse(viewModel.canSave)
        assertNull(viewModel.totalCost)
    }

    @Test
    fun `zero liters and zero price are rejected`() {
        val viewModel = makeViewModel()
        viewModel.fill(liters = 0.0, price = BigDecimal.ZERO)

        assertEquals(listOf(ValidationIssue.LitersNotPositive, ValidationIssue.PriceNotPositive), viewModel.issues)
        assertFalse(viewModel.canSave)
    }

    @Test
    fun `the total is liters times price`() {
        val viewModel = makeViewModel()
        viewModel.fill(liters = 40.0, price = BigDecimal(5))

        assertDecimal("200", viewModel.totalCost)
    }

    @Test
    fun `saving creates the entry for the vehicle`() =
        runTest {
            val repository = InMemoryFuelEntryRepository()
            val viewModel = makeViewModel(repository = repository)
            viewModel.fill()
            viewModel.isFullTank = false

            viewModel.save()

            val saved = repository.entries(testVehicleId)
            assertEquals(FormState.Saved, viewModel.state)
            assertEquals(1, saved.size)
            assertEquals(1_500.0, saved.first().odometer)
            assertEquals(40.0, saved.first().liters)
            assertDecimal("5", saved.first().pricePerLiter)
            assertFalse(saved.first().isFullTank)
        }

    @Test
    fun `editing pre-fills the fields and keeps the entry's identity`() =
        runTest {
            val existing = full(10, 1_200.0, 35.0, price = BigDecimal(6))
            val repository = InMemoryFuelEntryRepository(listOf(existing))
            val viewModel = makeViewModel(FuelEntryFormViewModel.Mode.Edit(existing), repository)

            assertEquals(1_200.0, viewModel.parsedOdometer)
            assertEquals(35.0, viewModel.parsedLiters)
            assertDecimal("6", viewModel.parsedPrice)
            assertEquals(day(10), viewModel.date)

            viewModel.liters = NumberInput.text(36.0, NumberInput.Style.LITERS)
            viewModel.save()

            val saved = repository.entries(testVehicleId)
            assertEquals(listOf(existing.id), saved.map { it.id })
            assertEquals(36.0, saved.first().liters)
        }

    @Test
    fun `an odometer out of order warns but still saves`() =
        runTest {
            val viewModel = makeViewModel(otherReadings = listOf(sample(40, 2_000.0)))
            viewModel.fill(odometer = 2_100.0)

            assertEquals(listOf(ValidationIssue.OdometerNotBeforeNext(2_000.0)), viewModel.issues)
            assertTrue(viewModel.canSave)

            viewModel.save()
            assertEquals(FormState.Saved, viewModel.state)
        }

    @Test
    fun `a blocking issue prevents saving`() =
        runTest {
            val repository = InMemoryFuelEntryRepository()
            val viewModel = makeViewModel(repository = repository)

            viewModel.save()

            assertEquals(FormState.Editing, viewModel.state)
            assertTrue(repository.entries(testVehicleId).isEmpty())
        }

    @Test
    fun `a repository failure is shown to the user`() =
        runTest {
            val viewModel = makeViewModel(repository = InMemoryFuelEntryRepository(failure = RepositoryError.VehicleNotFound(testVehicleId)))
            viewModel.fill()

            viewModel.save()

            assertIs<FormState.Failed>(viewModel.state)
        }

    @Test
    fun `a new fuel-up starts with the suggested price - an edit keeps its own`() {
        val created = makeViewModel(suggestedPrice = decimal("5.899"))
        assertDecimal("5.899", created.parsedPrice)
        assertTrue(created.isUsingSuggestedPrice)
        created.pricePerLiter = NumberInput.text(BigDecimal(6), NumberInput.Style.FUEL_PRICE)
        assertFalse(created.isUsingSuggestedPrice)

        val edited = makeViewModel(FuelEntryFormViewModel.Mode.Edit(full(10, 1_000.0, 30.0, price = BigDecimal(6))), suggestedPrice = BigDecimal(4))
        assertDecimal("6", edited.parsedPrice)
        assertFalse(edited.isUsingSuggestedPrice)
        assertFalse(makeViewModel().isUsingSuggestedPrice)
    }

    @Test
    fun `the form shows the previous reading and the distance driven since`() {
        val viewModel = makeViewModel(otherReadings = listOf(sample(10, 1_000.0), sample(60, 2_000.0)))

        assertEquals(1_000.0, viewModel.previousReading)
        assertNull(viewModel.distanceSincePrevious)

        viewModel.odometer = NumberInput.text(1_350.0, NumberInput.Style.KILOMETERS)
        assertEquals(350.0, viewModel.distanceSincePrevious)

        viewModel.odometer = NumberInput.text(900.0, NumberInput.Style.KILOMETERS)
        assertNull(viewModel.distanceSincePrevious)
    }

    @Test
    fun `imperial input is converted back to liters and kilometers when saving`() =
        runTest {
            DisplayPreferences.update(Preferences(unitSystem = UnitSystem.IMPERIAL))
            val repository = InMemoryFuelEntryRepository()
            val viewModel = makeViewModel(repository = repository)
            viewModel.odometer = NumberInput.text(100.0, NumberInput.Style.KILOMETERS)
            viewModel.liters = NumberInput.text(10.0, NumberInput.Style.LITERS)
            viewModel.pricePerLiter = NumberInput.text(BigDecimal(4), NumberInput.Style.FUEL_PRICE)

            viewModel.save()

            val saved = repository.entries(testVehicleId).first()
            assertEquals(160.9344, saved.odometer, 0.001)
            assertEquals(37.85411784, saved.liters, 0.001)
            assertEquals(4.0 / 3.785411784, saved.pricePerLiter.toDouble(), 0.000_001)
        }

    @Test
    fun `editing in the imperial system shows miles, gallons and price per gallon`() {
        DisplayPreferences.update(Preferences(unitSystem = UnitSystem.IMPERIAL))
        val existing = full(10, 160.9344, 37.85411784, price = BigDecimal(4))

        val viewModel = makeViewModel(FuelEntryFormViewModel.Mode.Edit(existing))

        assertEquals(160.9344, viewModel.parsedOdometer!!, 0.001)
        assertEquals(37.85411784, viewModel.parsedLiters!!, 0.001)
        assertEquals(4.0, viewModel.parsedPrice!!.toDouble(), 0.001)
    }

    @Test
    fun `the picker only offers the fuels the vehicle can take`() {
        assertEquals(FuelCapability.FLEX.fuelTypes, makeViewModel().availableFuelTypes)
        assertEquals(
            listOf(FuelType.DIESEL_S10, FuelType.DIESEL_S500),
            makeViewModel(capability = FuelCapability.DIESEL, suggestedFuelType = null).availableFuelTypes,
        )
    }

    @Test
    fun `a new fuel-up starts with the last fuel type, or the only one the vehicle takes`() {
        assertEquals(FuelType.ETANOL, makeViewModel(suggestedFuelType = FuelType.ETANOL).fuelType)
        assertEquals(FuelType.ETANOL, makeViewModel(capability = FuelCapability.ETANOL, suggestedFuelType = null).fuelType)
        assertNull(makeViewModel(capability = FuelCapability.DIESEL, suggestedFuelType = FuelType.ETANOL).fuelType)
    }

    @Test
    fun `a flex vehicle with no history must pick a fuel before saving`() =
        runTest {
            val repository = InMemoryFuelEntryRepository()
            val viewModel = makeViewModel(repository = repository, suggestedFuelType = null)
            viewModel.fill()

            assertEquals(listOf(ValidationIssue.FuelTypeRequired), viewModel.issues)
            assertFalse(viewModel.canSave)

            viewModel.fuelType = FuelType.ETANOL
            viewModel.save()

            assertEquals(FuelType.ETANOL, repository.entries(testVehicleId).single().fuelType)
        }

    @Test
    fun `editing a legacy fuel-up without a type is allowed and keeps it unknown`() =
        runTest {
            val legacy = full(10, 1_200.0, 35.0, price = BigDecimal(6))
            val repository = InMemoryFuelEntryRepository(listOf(legacy))
            val viewModel = makeViewModel(FuelEntryFormViewModel.Mode.Edit(legacy), repository)

            assertNull(viewModel.fuelType)
            assertTrue(viewModel.issues.isEmpty())
            viewModel.save()

            assertNull(repository.entries(testVehicleId).single().fuelType)
        }

    @Test
    fun `editing keeps a fuel type the vehicle no longer takes`() {
        val diesel = full(10, 1_200.0, 35.0, fuelType = FuelType.DIESEL_S10)

        val viewModel = makeViewModel(FuelEntryFormViewModel.Mode.Edit(diesel))

        assertEquals(FuelType.DIESEL_S10, viewModel.fuelType)
        assertEquals(FuelType.DIESEL_S10, viewModel.availableFuelTypes.last())
    }

    @Test
    fun `the price follows the fuel type while it is still the suggestion`() {
        val prices = mapOf(FuelType.GASOLINA_COMUM to decimal("6.19"), FuelType.ETANOL to decimal("4.09"))
        val viewModel = makeViewModel(suggestedPrice = decimal("4.09"), suggestedPrices = prices)

        assertDecimal("6.19", viewModel.parsedPrice)
        assertTrue(viewModel.isUsingSuggestedPrice)

        viewModel.fuelType = FuelType.ETANOL
        assertDecimal("4.09", viewModel.parsedPrice)
        assertTrue(viewModel.isUsingSuggestedPrice)

        viewModel.fuelType = FuelType.GASOLINA_PREMIUM
        assertEquals("", viewModel.pricePerLiter)
        assertFalse(viewModel.isUsingSuggestedPrice)

        viewModel.fuelType = FuelType.ETANOL
        assertDecimal("4.09", viewModel.parsedPrice)
        viewModel.pricePerLiter = NumberInput.text(decimal("3.99"), NumberInput.Style.FUEL_PRICE)
        viewModel.fuelType = FuelType.GASOLINA_COMUM
        assertDecimal("3.99", viewModel.parsedPrice)
    }

    @Test
    fun `the suggested price for a fuel type is shown per gallon under the imperial system`() {
        DisplayPreferences.update(Preferences(unitSystem = UnitSystem.IMPERIAL))
        val prices = mapOf(FuelType.GASOLINA_COMUM to decimal("1"), FuelType.ETANOL to decimal("2"))
        val viewModel = makeViewModel(suggestedPrices = prices)

        viewModel.fuelType = FuelType.ETANOL

        assertEquals(NumberInput.text(decimal("7.570823568"), NumberInput.Style.FUEL_PRICE), viewModel.pricePerLiter)
        assertEquals(2.0, viewModel.parsedPrice!!.toDouble(), 0.001)
    }

    @Test
    fun `createdAt is now on create and unchanged on edit`() =
        runTest {
            val repository = InMemoryFuelEntryRepository()
            val created = makeViewModel(repository = repository)
            created.fill()
            created.date = day(3)
            created.save()
            assertEquals(day(30), repository.entries(testVehicleId).single().createdAt)
            assertEquals(day(3), repository.entries(testVehicleId).single().date)

            val existing = full(10, 1_200.0, 35.0).copy(createdAt = day(12))
            val editRepository = InMemoryFuelEntryRepository(listOf(existing))
            val edited = makeViewModel(FuelEntryFormViewModel.Mode.Edit(existing), editRepository)
            edited.save()
            assertEquals(day(12), editRepository.entries(testVehicleId).single().createdAt)
        }
}
