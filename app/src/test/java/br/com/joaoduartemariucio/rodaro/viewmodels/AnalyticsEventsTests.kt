package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.calculations.day
import br.com.joaoduartemariucio.rodaro.calculations.decimal
import br.com.joaoduartemariucio.rodaro.calculations.full
import br.com.joaoduartemariucio.rodaro.calculations.rule
import br.com.joaoduartemariucio.rodaro.calculations.testVehicleId
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.CurrencyOption
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.UnitSystem
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.fakes.FakeAnalytics
import br.com.joaoduartemariucio.rodaro.fakes.FixedDateProvider
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryExpenseRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryFuelEntryRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryPreferencesRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryRouteRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryServiceRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryVehicleRepository
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.fueling.form.FuelEntryFormViewModel
import br.com.joaoduartemariucio.rodaro.presentation.maintenance.form.ServiceEntryFormViewModel
import br.com.joaoduartemariucio.rodaro.presentation.preferences.PreferencesViewModel
import br.com.joaoduartemariucio.rodaro.presentation.trips.TripCostViewModel
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.form.VehicleFormViewModel
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.list.VehicleListViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyticsEventsTests {
    @Test
    fun `saving a fuel-up reports the mode and the full-tank flag`() =
        runTest {
            val analytics = FakeAnalytics()
            val viewModel =
                FuelEntryFormViewModel(
                    FuelEntryFormViewModel.Mode.Create(testVehicleId),
                    InMemoryFuelEntryRepository(),
                    FixedDateProvider(day(30)),
                    emptyList(),
                    null,
                    analytics,
                    suggestedFuelType = FuelType.ETANOL,
                )
            viewModel.odometer = NumberInput.text(100.0, NumberInput.Style.KILOMETERS)
            viewModel.liters = NumberInput.text(10.0, NumberInput.Style.LITERS)
            viewModel.pricePerLiter = NumberInput.text(BigDecimal(5), NumberInput.Style.FUEL_PRICE)

            viewModel.save()

            assertEquals(listOf("fuel_up_saved"), analytics.names())
            assertEquals("create", analytics.parameters("fuel_up_saved")?.get("mode"))
            assertEquals("true", analytics.parameters("fuel_up_saved")?.get("full_tank"))
            assertEquals("etanol", analytics.parameters("fuel_up_saved")?.get("fuel_type"))
        }

    @Test
    fun `saving a service reports its kind`() =
        runTest {
            val analytics = FakeAnalytics()
            val viewModel =
                ServiceEntryFormViewModel(
                    ServiceEntryFormViewModel.Mode.Create(testVehicleId),
                    InMemoryServiceRepository(),
                    FixedDateProvider(day(30)),
                    emptyList(),
                    analytics,
                )
            viewModel.odometer = NumberInput.text(100.0, NumberInput.Style.KILOMETERS)
            viewModel.cost = NumberInput.text(BigDecimal(200), NumberInput.Style.MONEY)

            viewModel.save()

            assertEquals(listOf("service_saved"), analytics.names())
            assertEquals("OIL_CHANGE", analytics.parameters("service_saved")?.get("kind"))
        }

    @Test
    fun `saving a vehicle reports whether it was created or edited`() =
        runTest {
            val analytics = FakeAnalytics()
            val viewModel =
                VehicleFormViewModel(
                    VehicleFormViewModel.Mode.Create,
                    emptyList(),
                    InMemoryVehicleRepository(),
                    InMemoryServiceRepository(),
                    FixedDateProvider(day(30)),
                    analytics,
                )
            viewModel.name = "Gol"
            viewModel.addRule()
            viewModel.rules[0].kilometerInterval = NumberInput.text(1_000.0, NumberInput.Style.KILOMETERS)

            viewModel.save()

            assertTrue(analytics.names().contains("vehicle_created"))
            assertEquals("1", analytics.parameters("vehicle_created")?.get("rules"))
        }

    @Test
    fun `archiving a vehicle reports the new state`() =
        runTest {
            val analytics = FakeAnalytics()
            val vehicle = Vehicle(id = testVehicleId, name = "Car", createdAt = day(0))
            val viewModel =
                VehicleListViewModel(
                    VehicleListViewModel.Scope.ACTIVE,
                    InMemoryVehicleRepository(listOf(vehicle)),
                    InMemoryFuelEntryRepository(),
                    InMemoryServiceRepository(),
                    InMemoryExpenseRepository(),
                    FixedDateProvider(day(30)),
                    null,
                    analytics,
                )

            viewModel.setArchived(true, vehicle)

            assertEquals(listOf("vehicle_archived"), analytics.names())
        }

    @Test
    fun `choosing a vehicle for the route reports the selection`() =
        runTest {
            val analytics = FakeAnalytics()
            val vehicle = Vehicle(id = testVehicleId, name = "Car", createdAt = day(0))
            val viewModel =
                TripCostViewModel(
                    vehicleRepository = InMemoryVehicleRepository(listOf(vehicle)),
                    fuelEntryRepository = InMemoryFuelEntryRepository(),
                    routeRepository = InMemoryRouteRepository(),
                    dateProvider = FixedDateProvider(day(30)),
                    analytics = analytics,
                )
            viewModel.load()

            assertEquals(listOf("route_vehicle_selected"), analytics.names())
        }

    @Test
    fun `a computed route reports whether it is a round trip`() =
        runTest {
            val analytics = FakeAnalytics()
            val viewModel =
                TripCostViewModel(
                    vehicleRepository = InMemoryVehicleRepository(),
                    fuelEntryRepository = InMemoryFuelEntryRepository(),
                    routeRepository = InMemoryRouteRepository(),
                    dateProvider = FixedDateProvider(day(30)),
                    analytics = analytics,
                )
            viewModel.load()
            viewModel.distance = NumberInput.text(100.0, NumberInput.Style.KILOMETERS)
            viewModel.isRoundTrip = true
            viewModel.consumption = NumberInput.text(10.0, NumberInput.Style.CONSUMPTION)
            viewModel.pricePerLiter = NumberInput.text(BigDecimal(5), NumberInput.Style.FUEL_PRICE)

            viewModel.reportCalculation()

            assertEquals(listOf("route_calculated"), analytics.names())
            assertEquals("true", analytics.parameters("route_calculated")?.get("round_trip"))
        }

    @Test
    fun `changing a preference reports which key changed`() =
        runTest {
            val analytics = FakeAnalytics()
            val viewModel =
                PreferencesViewModel(
                    InMemoryPreferencesRepository(),
                    {},
                    {},
                    analytics = analytics,
                )
            viewModel.load()

            viewModel.setUnitSystem(UnitSystem.IMPERIAL)
            viewModel.setCurrency(CurrencyOption.USD)

            assertEquals(listOf("preference_changed", "preference_changed"), analytics.names())
            assertEquals("unit_system", analytics.events[0].parameters["key"])
            assertEquals("currency", analytics.events[1].parameters["key"])
        }

    @Test
    fun `a fuel-up edit reports the edit mode`() =
        runTest {
            val analytics = FakeAnalytics()
            val existing = full(10, 1_000.0, 40.0, price = decimal("5.2"))
            val viewModel =
                FuelEntryFormViewModel(
                    FuelEntryFormViewModel.Mode.Edit(existing),
                    InMemoryFuelEntryRepository(),
                    FixedDateProvider(day(30)),
                    emptyList(),
                    null,
                    analytics,
                )

            viewModel.save()

            assertEquals("edit", analytics.parameters("fuel_up_saved")?.get("mode"))
        }
}
