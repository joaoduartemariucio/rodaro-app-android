package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.calculations.day
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RepositoryError
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceRule
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.Preferences
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.UnitSystem
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeDetail
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipePrice
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeSelection
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.FuelCapability
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import br.com.joaoduartemariucio.rodaro.fakes.FixedDateProvider
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryServiceRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryVehicleRepository
import br.com.joaoduartemariucio.rodaro.presentation.common.DisplayPreferences
import br.com.joaoduartemariucio.rodaro.presentation.common.FormState
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.common.ValidationIssue
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.form.VehicleFormViewModel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VehicleFormViewModelTests {
    @After
    fun tearDown() {
        DisplayPreferences.update(Preferences())
    }

    private fun makeViewModel(
        mode: VehicleFormViewModel.Mode = VehicleFormViewModel.Mode.Create,
        existingRules: List<ServiceRule> = emptyList(),
        vehicleRepository: InMemoryVehicleRepository = InMemoryVehicleRepository(),
        serviceRepository: InMemoryServiceRepository = InMemoryServiceRepository(),
    ) = VehicleFormViewModel(mode, existingRules, vehicleRepository, serviceRepository, FixedDateProvider(day(30)))

    @Test
    fun `a new vehicle needs a name`() {
        val viewModel = makeViewModel()
        viewModel.name = "   "

        assertEquals(listOf(ValidationIssue.NameRequired), viewModel.issues)
        assertFalse(viewModel.canSave)
    }

    @Test
    fun `a reminder needs a distance or a time interval`() {
        val viewModel = makeViewModel()
        viewModel.name = "Gol"
        viewModel.addRule()
        viewModel.rules[0].kilometerInterval = ""
        viewModel.rules[0].monthInterval = ""

        assertEquals(listOf(ValidationIssue.RuleWithoutInterval), viewModel.issues)

        viewModel.rules[0].monthInterval = NumberInput.text(BigDecimal(6), NumberInput.Style.MONTHS)
        assertTrue(viewModel.issues.isEmpty())
    }

    @Test
    fun `saving a new vehicle stores it with its reminders`() =
        runTest {
            val vehicles = InMemoryVehicleRepository()
            val services = InMemoryServiceRepository()
            val viewModel = makeViewModel(vehicleRepository = vehicles, serviceRepository = services)
            viewModel.name = "  Gol  "
            viewModel.kind = VehicleKind.MOTORCYCLE
            viewModel.plate = " abc1d23 "
            viewModel.initialOdometer = NumberInput.text(56_000.0, NumberInput.Style.KILOMETERS)
            viewModel.tankCapacity = NumberInput.text(50.0, NumberInput.Style.LITERS)
            viewModel.addRule()
            viewModel.rules[0].kilometerInterval = NumberInput.text(10_000.0, NumberInput.Style.KILOMETERS)
            viewModel.rules[0].monthInterval = ""

            viewModel.save()

            val saved = assertNotNull(vehicles.all(includingArchived = true).firstOrNull())
            assertEquals(FormState.Saved, viewModel.state)
            assertEquals("Gol", saved.name)
            assertEquals(VehicleKind.MOTORCYCLE, saved.kind)
            assertEquals("ABC1D23", saved.plate)
            assertEquals(56_000.0, saved.initialOdometer)
            assertEquals(50.0, saved.tankCapacity)
            assertEquals(day(30), saved.createdAt)

            val rules = services.rules(saved.id)
            assertEquals(1, rules.size)
            assertEquals(10_000.0, rules.first().kilometerInterval)
            assertNull(rules.first().monthInterval)
        }

    @Test
    fun `editing pre-fills the vehicle and its reminders, sorted by kind`() =
        runTest {
            val vehicle =
                Vehicle(
                    name = "Jetta",
                    plate = "XYZ9A87",
                    initialOdometer = 144_000.0,
                    tankCapacity = 55.0,
                    createdAt = day(0),
                    isArchived = true,
                )
            val rules =
                listOf(
                    ServiceRule(vehicleId = vehicle.id, kind = ServiceKind.TIRES, monthInterval = 12),
                    ServiceRule(vehicleId = vehicle.id, kind = ServiceKind.BRAKES, kilometerInterval = 20_000.0),
                )
            val vehicles = InMemoryVehicleRepository(listOf(vehicle))
            val viewModel = makeViewModel(VehicleFormViewModel.Mode.Edit(vehicle), rules, vehicles)

            assertEquals("Jetta", viewModel.name)
            assertEquals("XYZ9A87", viewModel.plate)
            assertEquals(144_000.0, viewModel.parsedInitialOdometer)
            assertEquals(55.0, viewModel.parsedTankCapacity)
            assertEquals(listOf(ServiceKind.BRAKES, ServiceKind.TIRES), viewModel.rules.map { it.kind })
            assertEquals(12, viewModel.rules[1].parsedMonthInterval)

            viewModel.plate = ""
            viewModel.save()

            val saved = assertNotNull(vehicles.vehicle(vehicle.id))
            assertNull(saved.plate)
            assertEquals(day(0), saved.createdAt)
            assertTrue(saved.isArchived)
        }

    @Test
    fun `removing reminders ignores indices out of range`() {
        val viewModel = makeViewModel()
        viewModel.addRule()
        viewModel.addRule()
        val kept = viewModel.rules[1].id

        viewModel.removeRules(setOf(0, 5))

        assertEquals(listOf(kept), viewModel.rules.map { it.id })
    }

    @Test
    fun `a blocking issue prevents saving`() =
        runTest {
            val vehicles = InMemoryVehicleRepository()
            val viewModel = makeViewModel(vehicleRepository = vehicles)

            viewModel.save()

            assertEquals(FormState.Editing, viewModel.state)
            assertTrue(vehicles.all(includingArchived = true).isEmpty())
        }

    @Test
    fun `a repository failure is shown to the user`() =
        runTest {
            val viewModel = makeViewModel(vehicleRepository = InMemoryVehicleRepository(failure = RepositoryError.VehicleNotFound(UUID.randomUUID())))
            viewModel.name = "Gol"

            viewModel.save()

            assertIs<FormState.Failed>(viewModel.state)
        }

    @Test
    fun `imperial specs typed by the user are stored as kilometers and liters`() =
        runTest {
            DisplayPreferences.update(Preferences(unitSystem = UnitSystem.IMPERIAL))
            val vehicles = InMemoryVehicleRepository()
            val services = InMemoryServiceRepository()
            val viewModel = makeViewModel(vehicleRepository = vehicles, serviceRepository = services)
            viewModel.name = "F-150"
            viewModel.initialOdometer = NumberInput.text(100.0, NumberInput.Style.KILOMETERS)
            viewModel.tankCapacity = NumberInput.text(10.0, NumberInput.Style.LITERS)
            viewModel.addRule()
            viewModel.rules[0].kilometerInterval = NumberInput.text(621.0, NumberInput.Style.KILOMETERS)

            viewModel.save()

            val saved = assertNotNull(vehicles.all(includingArchived = true).firstOrNull())
            assertEquals(160.9344, saved.initialOdometer, 0.001)
            assertEquals(37.85411784, assertNotNull(saved.tankCapacity), 0.001)
            assertEquals(999.402624, assertNotNull(services.rules(saved.id).first().kilometerInterval), 0.001)
        }

    @Test
    fun `editing under the imperial system shows miles and gallons`() {
        DisplayPreferences.update(Preferences(unitSystem = UnitSystem.IMPERIAL))
        val vehicle = Vehicle(name = "Jetta", initialOdometer = 160.9344, tankCapacity = 37.85411784, createdAt = day(0))

        val viewModel = makeViewModel(VehicleFormViewModel.Mode.Edit(vehicle))

        assertEquals(160.9344, viewModel.parsedInitialOdometer, 0.001)
        assertEquals(37.85411784, viewModel.parsedTankCapacity!!, 0.001)
    }

    @Test
    fun `a new reminder starts with the suggested interval for the vehicle kind`() {
        val viewModel = makeViewModel()

        viewModel.addRule()

        assertEquals(ServiceKind.OIL_CHANGE, viewModel.rules[0].kind)
        assertEquals(10_000.0, viewModel.rules[0].parsedKilometerInterval)
        assertEquals(12, viewModel.rules[0].parsedMonthInterval)
        assertEquals(VehicleFormViewModel.ManualHint.NORMAL, viewModel.manualHint(viewModel.rules[0]))
    }

    @Test
    fun `changing the service replaces an untouched suggestion but never what the user typed`() {
        val viewModel = makeViewModel()
        viewModel.addRule()
        val rule = viewModel.rules[0]

        viewModel.selectRuleKind(rule, ServiceKind.TIMING_BELT)
        assertEquals(50_000.0, rule.parsedKilometerInterval)
        assertEquals(48, rule.parsedMonthInterval)
        assertEquals(VehicleFormViewModel.ManualHint.STRONG, viewModel.manualHint(rule))

        viewModel.selectRuleKind(rule, ServiceKind.BRAKE_FLUID)
        assertNull(rule.parsedKilometerInterval)
        assertEquals(24, rule.parsedMonthInterval)

        rule.monthInterval = NumberInput.text(BigDecimal(18), NumberInput.Style.MONTHS)
        assertEquals(VehicleFormViewModel.ManualHint.NONE, viewModel.manualHint(rule))
        viewModel.selectRuleKind(rule, ServiceKind.OIL_CHANGE)

        assertEquals(ServiceKind.OIL_CHANGE, rule.kind)
        assertNull(rule.parsedKilometerInterval)
        assertEquals(18, rule.parsedMonthInterval)
    }

    @Test
    fun `other clears an untouched suggestion and shows no hint`() {
        val viewModel = makeViewModel()
        viewModel.addRule()
        val rule = viewModel.rules[0]

        viewModel.selectRuleKind(rule, ServiceKind.OTHER)

        assertEquals("", rule.kilometerInterval)
        assertEquals("", rule.monthInterval)
        assertEquals(VehicleFormViewModel.ManualHint.NONE, viewModel.manualHint(rule))
    }

    @Test
    fun `switching to a motorcycle reapplies untouched suggestions and offers motorcycle services`() {
        val viewModel = makeViewModel()
        viewModel.addRule()
        viewModel.addRule()
        viewModel.rules[1].kilometerInterval = NumberInput.text(8_000.0, NumberInput.Style.KILOMETERS)

        viewModel.kind = VehicleKind.MOTORCYCLE

        assertEquals(3_000.0, viewModel.rules[0].parsedKilometerInterval)
        assertEquals(6, viewModel.rules[0].parsedMonthInterval)
        assertEquals(8_000.0, viewModel.rules[1].parsedKilometerInterval)
        assertEquals(VehicleFormViewModel.ManualHint.STRONG, viewModel.manualHint(viewModel.rules[0]))
        assertTrue(ServiceKind.CHAIN_KIT in viewModel.availableRuleKinds)
        assertFalse(ServiceKind.TIMING_BELT in viewModel.availableRuleKinds)
    }

    @Test
    fun `an existing reminder that no longer applies is kept and still offered`() =
        runTest {
            val vehicle = Vehicle(name = "CB 500", kind = VehicleKind.MOTORCYCLE, createdAt = day(0))
            val belt = ServiceRule(vehicleId = vehicle.id, kind = ServiceKind.TIMING_BELT, kilometerInterval = 40_000.0)
            val services = InMemoryServiceRepository(rules = listOf(belt))
            val viewModel = makeViewModel(VehicleFormViewModel.Mode.Edit(vehicle), listOf(belt), InMemoryVehicleRepository(listOf(vehicle)), services)

            val kinds = viewModel.ruleKinds(viewModel.rules[0])
            viewModel.save()

            assertEquals(ServiceKind.TIMING_BELT, kinds[kinds.size - 2])
            assertEquals(ServiceKind.OTHER, kinds.last())
            assertEquals(VehicleFormViewModel.ManualHint.NONE, viewModel.manualHint(viewModel.rules[0]))
            assertEquals(listOf(belt), services.rules(vehicle.id))
        }

    @Test
    fun `the suggested distance is shown in miles under the imperial system`() =
        runTest {
            DisplayPreferences.update(Preferences(unitSystem = UnitSystem.IMPERIAL))
            val viewModel = makeViewModel()
            viewModel.addRule()
            val rule = viewModel.rules[0]

            assertEquals(NumberInput.text(10_000.0 / 1.609344, NumberInput.Style.KILOMETERS), rule.kilometerInterval)
            assertEquals(VehicleFormViewModel.ManualHint.NORMAL, viewModel.manualHint(rule))

            viewModel.selectRuleKind(rule, ServiceKind.SPARK_PLUGS)
            assertEquals(NumberInput.text(30_000.0 / 1.609344, NumberInput.Style.KILOMETERS), rule.kilometerInterval)
            assertEquals(30_000.0, assertNotNull(rule.parsedKilometerInterval), 1.0)
        }

    @Test
    fun `motorcycles cannot be diesel and switching kind resets a diesel choice`() {
        val viewModel = makeViewModel()
        assertEquals(FuelCapability.entries, viewModel.availableCapabilities)

        viewModel.fuelCapability = FuelCapability.DIESEL
        viewModel.kind = VehicleKind.MOTORCYCLE

        assertEquals(FuelCapability.FLEX, viewModel.fuelCapability)
        assertFalse(FuelCapability.DIESEL in viewModel.availableCapabilities)

        viewModel.fuelCapability = FuelCapability.GASOLINA
        viewModel.kind = VehicleKind.CAR
        assertEquals(FuelCapability.GASOLINA, viewModel.fuelCapability)
    }

    @Test
    fun `the model year keeps four digits and must be between 1900 and next year`() {
        val viewModel = makeViewModel()
        viewModel.name = "Gol"

        viewModel.modelYear = "19a99x9"
        assertEquals("1999", viewModel.modelYear)
        assertEquals(1999, viewModel.parsedModelYear)
        assertTrue(viewModel.issues.isEmpty())

        viewModel.modelYear = "2002"
        assertTrue(viewModel.issues.isEmpty())
        viewModel.modelYear = "2003"
        assertEquals(listOf(ValidationIssue.ModelYearOutOfRange(1900, 2002)), viewModel.issues)
        assertFalse(viewModel.canSave)

        viewModel.modelYear = "1899"
        assertEquals(listOf(ValidationIssue.ModelYearOutOfRange(1900, 2002)), viewModel.issues)

        viewModel.modelYear = ""
        assertNull(viewModel.parsedModelYear)
        assertTrue(viewModel.issues.isEmpty())
    }

    @Test
    fun `capability, state and model year are saved and kept on edit`() =
        runTest {
            val vehicles = InMemoryVehicleRepository()
            val viewModel = makeViewModel(vehicleRepository = vehicles)
            viewModel.name = "Hilux"
            viewModel.fuelCapability = FuelCapability.DIESEL
            viewModel.uf = "MG"
            viewModel.modelYear = "2000"

            viewModel.save()

            val saved = assertNotNull(vehicles.all(includingArchived = true).firstOrNull())
            assertEquals(FuelCapability.DIESEL, saved.fuelCapability)
            assertEquals("MG", saved.uf)
            assertEquals(2000, saved.modelYear)

            val edit = makeViewModel(VehicleFormViewModel.Mode.Edit(saved), vehicleRepository = vehicles)
            assertEquals(FuelCapability.DIESEL, edit.fuelCapability)
            assertEquals("MG", edit.uf)
            assertEquals("2000", edit.modelYear)
            edit.uf = null
            edit.save()

            val edited = assertNotNull(vehicles.vehicle(saved.id))
            assertNull(edited.uf)
            assertEquals(FuelCapability.DIESEL, edited.fuelCapability)
            assertEquals(2000, edited.modelYear)
        }

    private val detail =
        FipeDetail(
            FipeSelection("005340-6", "2014-3", "VW - VolksWagen", "Amarok", "2014 Diesel"),
            FipePrice(YearMonth.of(2026, 11), BigDecimal("85386.00")),
        )

    @Test
    fun `applying a FIPE choice fills the model year only when it is empty`() {
        val viewModel = makeViewModel()
        viewModel.applyFipe(detail)

        assertEquals(detail.selection, viewModel.fipe)
        assertEquals(detail.price, viewModel.fipePreviewPrice)
        assertEquals("2014", viewModel.modelYear)

        viewModel.modelYear = "2015"
        viewModel.applyFipe(detail)
        assertEquals("2015", viewModel.modelYear)

        viewModel.clearFipe()
        assertNull(viewModel.fipe)
        assertNull(viewModel.fipePreviewPrice)
    }

    @Test
    fun `changing the vehicle kind clears the FIPE choice`() {
        val viewModel = makeViewModel()
        viewModel.applyFipe(detail)

        viewModel.kind = VehicleKind.CAR
        assertNotNull(viewModel.fipe)
        viewModel.kind = VehicleKind.MOTORCYCLE
        assertNull(viewModel.fipe)
    }

    @Test
    fun `FIPE and purchase fields are saved and kept on edit`() =
        runTest {
            val vehicles = InMemoryVehicleRepository()
            val viewModel = makeViewModel(vehicleRepository = vehicles)
            viewModel.name = "Amarok"
            viewModel.modelYear = "2000"
            viewModel.applyFipe(detail)
            viewModel.purchaseDate = LocalDate.of(2001, 1, 10)
            viewModel.purchasePrice = NumberInput.text(BigDecimal("92500.50"), NumberInput.Style.MONEY)

            viewModel.save()

            val saved = vehicles.all(includingArchived = true).single()
            assertEquals(detail.selection, saved.fipe)
            assertEquals(LocalDate.of(2001, 1, 10), saved.purchaseDate)
            assertEquals(0, BigDecimal("92500.50").compareTo(saved.purchasePrice))

            val edit = makeViewModel(mode = VehicleFormViewModel.Mode.Edit(saved), vehicleRepository = vehicles)
            assertEquals(detail.selection, edit.fipe)
            assertEquals(saved.purchaseDate, edit.purchaseDate)
            edit.name = "Amarok V6"
            edit.save()
            assertEquals(saved.copy(name = "Amarok V6"), vehicles.vehicle(saved.id))
        }

    @Test
    fun `a purchase in the future or for nothing blocks saving`() {
        val viewModel = makeViewModel()
        viewModel.name = "Gol"
        viewModel.purchaseDate = LocalDate.of(2001, 2, 1)
        assertTrue(ValidationIssue.PurchaseDateInFuture in viewModel.issues)
        assertFalse(viewModel.canSave)

        viewModel.purchaseDate = LocalDate.of(2001, 1, 31)
        viewModel.purchasePrice = NumberInput.text(BigDecimal.ZERO, NumberInput.Style.MONEY)
        assertEquals(listOf<ValidationIssue>(ValidationIssue.PurchasePriceNotPositive), viewModel.issues)
        assertFalse(viewModel.canSave)

        viewModel.purchasePrice = ""
        assertTrue(viewModel.canSave)
    }
}
