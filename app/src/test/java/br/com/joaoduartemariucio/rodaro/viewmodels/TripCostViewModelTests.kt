package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.calculations.assertDecimal
import br.com.joaoduartemariucio.rodaro.calculations.day
import br.com.joaoduartemariucio.rodaro.calculations.decimal
import br.com.joaoduartemariucio.rodaro.calculations.full
import br.com.joaoduartemariucio.rodaro.calculations.testVehicleId
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RepositoryError
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelGroup
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.Preferences
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.UnitSystem
import br.com.joaoduartemariucio.rodaro.domain.trips.entities.PlaceSuggestion
import br.com.joaoduartemariucio.rodaro.domain.trips.entities.RouteEstimate
import br.com.joaoduartemariucio.rodaro.domain.trips.entities.RoutePoint
import br.com.joaoduartemariucio.rodaro.domain.trips.entities.SavedRoute
import br.com.joaoduartemariucio.rodaro.domain.trips.repositories.RouteRepository
import br.com.joaoduartemariucio.rodaro.domain.trips.services.RouteEstimateProvider
import br.com.joaoduartemariucio.rodaro.domain.trips.services.UnavailableRouteEstimateProvider
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.fakes.FakeAnalytics
import br.com.joaoduartemariucio.rodaro.fakes.FakeRouteEstimateProvider
import br.com.joaoduartemariucio.rodaro.fakes.FixedDateProvider
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryFuelEntryRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryRouteRepository
import br.com.joaoduartemariucio.rodaro.fakes.InMemoryVehicleRepository
import br.com.joaoduartemariucio.rodaro.presentation.common.DisplayPreferences
import br.com.joaoduartemariucio.rodaro.presentation.common.NumberInput
import br.com.joaoduartemariucio.rodaro.presentation.trips.TripCostViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import java.math.BigDecimal
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripCostViewModelTests {
    @After
    fun tearDown() {
        DisplayPreferences.update(Preferences())
    }

    private val car = Vehicle(id = testVehicleId, name = "Car", createdAt = day(0))
    private val bike = Vehicle(name = "Bike", createdAt = day(1))

    private val carFuel =
        listOf(
            full(0, 1_000.0, 40.0, decimal("5.5")),
            full(10, 1_500.0, 40.0, decimal("6.1")),
        )

    private fun makeViewModel(
        vehicles: List<Vehicle> = listOf(car),
        fuel: List<FuelEntry> = carFuel,
        failure: Exception? = null,
        routeRepository: RouteRepository = InMemoryRouteRepository(),
        routeEstimateProvider: RouteEstimateProvider = FakeRouteEstimateProvider(),
    ) = TripCostViewModel(
        vehicleRepository = InMemoryVehicleRepository(vehicles, failure),
        fuelEntryRepository = InMemoryFuelEntryRepository(fuel),
        routeRepository = routeRepository,
        routeEstimateProvider = routeEstimateProvider,
        dateProvider = FixedDateProvider(day(30)),
    )

    @Test
    fun `load picks a vehicle and fills its average consumption and last price`() =
        runTest {
            val viewModel = makeViewModel()

            viewModel.load()

            assertIs<TripCostViewModel.State.Loaded>(viewModel.state)
            assertEquals(car, viewModel.selectedVehicle)
            assertEquals(12.5, viewModel.parsedConsumption)
            assertDecimal("6.1", viewModel.parsedPrice)
            assertTrue(viewModel.isUsingAverageConsumption)
            assertTrue(viewModel.isUsingLastPrice)
        }

    @Test
    fun `cost uses the distance, round trip and the filled values`() =
        runTest {
            val viewModel = makeViewModel()
            viewModel.load()

            viewModel.distance = NumberInput.text(250.0, NumberInput.Style.KILOMETERS)
            viewModel.isRoundTrip = true

            val cost = assertNotNull(viewModel.cost)
            assertEquals(500.0, cost.distance)
            assertEquals(40.0, cost.liters, 1e-9)
            assertDecimal("244", cost.fuelCost)
        }

    @Test
    fun `no cost until a distance is entered`() =
        runTest {
            val viewModel = makeViewModel()
            viewModel.load()

            assertNull(viewModel.cost)
        }

    @Test
    fun `a vehicle without history leaves consumption for the user`() =
        runTest {
            val viewModel = makeViewModel(vehicles = listOf(bike), fuel = emptyList())

            viewModel.load()

            assertEquals(bike, viewModel.selectedVehicle)
            assertEquals("", viewModel.consumption)
            assertEquals("", viewModel.pricePerLiter)
            assertFalse(viewModel.isUsingAverageConsumption)
        }

    @Test
    fun `switching vehicles keeps values the user typed`() =
        runTest {
            val viewModel = makeViewModel(vehicles = listOf(car, bike))
            viewModel.load()
            viewModel.select(car)
            val typed = NumberInput.text(9.0, NumberInput.Style.CONSUMPTION)
            viewModel.consumption = typed

            viewModel.select(bike)

            assertEquals(typed, viewModel.consumption)
            assertEquals("", viewModel.pricePerLiter)
        }

    @Test
    fun `switching vehicles replaces the suggested values`() =
        runTest {
            val viewModel = makeViewModel(vehicles = listOf(car, bike))
            viewModel.load()
            viewModel.select(bike)

            viewModel.select(car)

            assertEquals(12.5, viewModel.parsedConsumption)
            assertDecimal("6.1", viewModel.parsedPrice)
        }

    @Test
    fun `without vehicles the calculator still works by hand`() =
        runTest {
            val viewModel = makeViewModel(vehicles = emptyList(), fuel = emptyList())

            viewModel.load()

            assertIs<TripCostViewModel.State.Loaded>(viewModel.state)
            assertNull(viewModel.selectedVehicle)
        }

    @Test
    fun `a storage failure is shown`() =
        runTest {
            val viewModel = makeViewModel(failure = RepositoryError.PersistenceFailed(IllegalStateException()))

            viewModel.load()

            assertIs<TripCostViewModel.State.Failed>(viewModel.state)
        }

    @Test
    fun `imperial suggestions are converted back to the metric calculation`() =
        runTest {
            DisplayPreferences.update(Preferences(unitSystem = UnitSystem.IMPERIAL))
            val viewModel = makeViewModel()
            viewModel.load()

            assertEquals(12.5, viewModel.parsedConsumption!!, 0.01)
            assertEquals(6.1, viewModel.parsedPrice!!.toDouble(), 0.001)
        }

    @Test
    fun `miles, mpg and price per gallon are converted when the cost is computed`() =
        runTest {
            DisplayPreferences.update(Preferences(unitSystem = UnitSystem.IMPERIAL))
            val viewModel = makeViewModel(vehicles = emptyList(), fuel = emptyList())
            viewModel.load()
            viewModel.distance = NumberInput.text(100.0, NumberInput.Style.KILOMETERS)
            viewModel.consumption = NumberInput.text(10.0, NumberInput.Style.CONSUMPTION)
            viewModel.pricePerLiter = NumberInput.text(BigDecimal(4), NumberInput.Style.FUEL_PRICE)

            assertEquals(160.9344, viewModel.parsedDistance!!, 0.001)
            assertEquals(4.2517014, viewModel.parsedConsumption!!, 0.001)
            assertEquals(1.056688589, viewModel.parsedPrice!!.toDouble(), 0.000_001)
            assertNotNull(viewModel.cost)
        }

    @Test
    fun `looking up the real distance fills the trip and reports the toll flag`() =
        runTest {
            val analytics = FakeAnalytics()
            val provider = FakeRouteEstimateProvider()
            val viewModelWithAnalytics =
                TripCostViewModel(
                    vehicleRepository = InMemoryVehicleRepository(listOf(car)),
                    fuelEntryRepository = InMemoryFuelEntryRepository(carFuel),
                    routeRepository = InMemoryRouteRepository(),
                    routeEstimateProvider = provider,
                    dateProvider = FixedDateProvider(day(30)),
                    analytics = analytics,
                )
            viewModelWithAnalytics.load()
            viewModelWithAnalytics.origin = "São Paulo, SP"
            viewModelWithAnalytics.destination = "Rio de Janeiro, RJ"

            viewModelWithAnalytics.lookupRealDistance()

            assertEquals(listOf("São Paulo, SP" to "Rio de Janeiro, RJ"), provider.lookups)
            assertEquals(120.0, viewModelWithAnalytics.parsedDistance!!, 0.001)
            assertFalse(viewModelWithAnalytics.isRoundTrip)
            assertEquals(true, viewModelWithAnalytics.hasTolls)
            assertIs<TripCostViewModel.LookupState.Done>(viewModelWithAnalytics.lookupState)
            assertEquals("true", analytics.parameters("route_distance_looked_up")?.get("has_tolls"))
        }

    @Test
    fun `the looked up toll cost follows the trip direction and is saved with the route`() =
        runTest {
            val provider =
                FakeRouteEstimateProvider(
                    estimate =
                        RouteEstimate(
                            distanceKm = 430.0,
                            durationMinutes = 330,
                            hasTolls = true,
                            tollCost = BigDecimal("66.40"),
                            roundTripTollCost = BigDecimal("128.20"),
                            path = listOf(RoutePoint(-23.55, -46.63), RoutePoint(-22.9, -43.17)),
                        ),
                )
            val repository = InMemoryRouteRepository()
            val viewModel =
                TripCostViewModel(
                    vehicleRepository = InMemoryVehicleRepository(listOf(car)),
                    fuelEntryRepository = InMemoryFuelEntryRepository(carFuel),
                    routeRepository = repository,
                    routeEstimateProvider = provider,
                    dateProvider = FixedDateProvider(day(30)),
                )
            viewModel.load()
            viewModel.origin = "São Paulo, SP"
            viewModel.destination = "Rio de Janeiro, RJ"

            viewModel.lookupRealDistance()

            assertEquals(listOf(car.kind), provider.kinds)
            assertEquals(BigDecimal("66.40"), viewModel.tollCost)
            viewModel.isRoundTrip = true
            assertEquals(BigDecimal("128.20"), viewModel.tollCost)
            viewModel.consumption = "10"
            viewModel.pricePerLiter = "6"
            assertDecimal(
                viewModel.cost!!
                    .fuelCost
                    .add(BigDecimal("128.20"))
                    .toPlainString(),
                viewModel.totalCost!!,
            )
            assertTrue(viewModel.saveRoute())
            val saved = repository.routes().single()
            assertEquals(BigDecimal("128.20"), saved.tollCost)
            assertDecimal(saved.fuelCost.add(BigDecimal("128.20")).toPlainString(), saved.totalCost)
            assertEquals(2, saved.path.size)

            viewModel.restoreRoute(saved)
            assertEquals(BigDecimal("128.20"), viewModel.tollCost)
            viewModel.isRoundTrip = false
            assertNull(viewModel.tollCost)
            assertEquals(2, viewModel.routePath.size)

            viewModel.updateDestination("Campinas, SP")
            assertNull(viewModel.hasTolls)
            assertTrue(viewModel.routePath.isEmpty())
            assertIs<TripCostViewModel.LookupState.Idle>(viewModel.lookupState)
        }

    @Test
    fun `a failed lookup reports the error and keeps the distance`() =
        runTest {
            val provider = FakeRouteEstimateProvider(failure = java.io.IOException("offline"))
            val viewModel = makeViewModel(routeEstimateProvider = provider)
            viewModel.load()
            viewModel.origin = "São Paulo, SP"
            viewModel.destination = "Rio de Janeiro, RJ"

            viewModel.lookupRealDistance()

            assertIs<TripCostViewModel.LookupState.Failed>(viewModel.lookupState)
            assertEquals("", viewModel.distance)
            assertTrue(provider.lookups.isEmpty())
        }

    @Test
    fun `looking up without both places does nothing`() =
        runTest {
            val provider = FakeRouteEstimateProvider()
            val viewModel = makeViewModel(routeEstimateProvider = provider)
            viewModel.load()
            viewModel.origin = "São Paulo, SP"

            viewModel.lookupRealDistance()

            assertIs<TripCostViewModel.LookupState.Idle>(viewModel.lookupState)
            assertTrue(provider.lookups.isEmpty())
        }

    @Test
    fun `saving the calculation stores origin, destination and the snapshot`() =
        runTest {
            val analytics = FakeAnalytics()
            val repository = InMemoryRouteRepository()
            val viewModel =
                TripCostViewModel(
                    vehicleRepository = InMemoryVehicleRepository(listOf(car)),
                    fuelEntryRepository = InMemoryFuelEntryRepository(carFuel),
                    routeRepository = repository,
                    dateProvider = FixedDateProvider(day(30)),
                    analytics = analytics,
                )
            viewModel.load()
            viewModel.origin = "São Paulo, SP"
            viewModel.destination = "Rio de Janeiro, RJ"
            viewModel.distance = NumberInput.text(100.0, NumberInput.Style.KILOMETERS)
            viewModel.consumption = NumberInput.text(10.0, NumberInput.Style.CONSUMPTION)
            viewModel.pricePerLiter = NumberInput.text(BigDecimal(5), NumberInput.Style.FUEL_PRICE)
            viewModel.hasTolls = true

            assertTrue(viewModel.saveRoute())

            val saved = repository.routes().single()
            assertEquals("São Paulo, SP", saved.origin)
            assertEquals("Rio de Janeiro, RJ", saved.destination)
            assertEquals(100.0, saved.distanceKm, 0.001)
            assertEquals(testVehicleId, saved.vehicleId)
            assertEquals(day(30), saved.createdAt)
            assertTrue(saved.hasTolls)
            assertEquals(10.0, saved.kilometersPerLiter, 0.001)
            assertDecimal("5", saved.pricePerLiter)
            assertDecimal("50", saved.totalCost)
            assertDecimal("50", saved.fuelCost)
            assertTrue(analytics.names().contains("route_saved"))
            assertEquals("true", analytics.parameters("route_saved")?.get("has_tolls"))
        }

    @Test
    fun `saving without a vehicle or without places is refused`() =
        runTest {
            val repository = InMemoryRouteRepository()
            val withoutVehicle =
                TripCostViewModel(
                    vehicleRepository = InMemoryVehicleRepository(),
                    fuelEntryRepository = InMemoryFuelEntryRepository(),
                    routeRepository = repository,
                    dateProvider = FixedDateProvider(day(30)),
                )
            withoutVehicle.load()
            withoutVehicle.distance = NumberInput.text(100.0, NumberInput.Style.KILOMETERS)
            withoutVehicle.consumption = NumberInput.text(10.0, NumberInput.Style.CONSUMPTION)
            withoutVehicle.pricePerLiter = NumberInput.text(BigDecimal(5), NumberInput.Style.FUEL_PRICE)
            assertFalse(withoutVehicle.saveRoute())

            val withoutPlaces = makeViewModel()
            withoutPlaces.load()
            withoutPlaces.distance = NumberInput.text(100.0, NumberInput.Style.KILOMETERS)
            withoutPlaces.consumption = NumberInput.text(10.0, NumberInput.Style.CONSUMPTION)
            withoutPlaces.pricePerLiter = NumberInput.text(BigDecimal(5), NumberInput.Style.FUEL_PRICE)
            assertFalse(withoutPlaces.saveRoute())

            assertTrue(repository.routes().isEmpty())
        }

    @Test
    fun `deleting a saved route removes it and reports the action`() =
        runTest {
            val analytics = FakeAnalytics()
            val route = savedRoute()
            val repository = InMemoryRouteRepository(listOf(route))
            val viewModel =
                TripCostViewModel(
                    vehicleRepository = InMemoryVehicleRepository(listOf(car)),
                    fuelEntryRepository = InMemoryFuelEntryRepository(carFuel),
                    routeRepository = repository,
                    dateProvider = FixedDateProvider(day(30)),
                    analytics = analytics,
                )
            viewModel.load()
            assertEquals(1, viewModel.savedRoutes.size)

            viewModel.deleteRoute(route.id)

            assertTrue(viewModel.savedRoutes.isEmpty())
            assertTrue(analytics.names().contains("route_deleted"))
        }

    @Test
    fun `restoring a saved route refills the form`() =
        runTest {
            val viewModel = makeViewModel()
            viewModel.load()

            viewModel.restoreRoute(savedRoute())

            assertEquals("São Paulo, SP", viewModel.origin)
            assertEquals("Rio de Janeiro, RJ", viewModel.destination)
            assertEquals(100.0, viewModel.parsedDistance!!, 0.001)
            assertTrue(viewModel.isRoundTrip)
            assertEquals(true, viewModel.hasTolls)
            assertEquals(10.0, viewModel.parsedConsumption!!, 0.001)
            assertDecimal("5", viewModel.parsedPrice)
        }

    @Test
    fun `saving after restoring edits the same saved route id`() =
        runTest {
            val existing = savedRoute()
            val repository = InMemoryRouteRepository(listOf(existing))
            val viewModel =
                TripCostViewModel(
                    vehicleRepository = InMemoryVehicleRepository(listOf(car)),
                    fuelEntryRepository = InMemoryFuelEntryRepository(carFuel),
                    routeRepository = repository,
                    dateProvider = FixedDateProvider(day(30)),
                )
            viewModel.load()
            viewModel.restoreRoute(existing)
            viewModel.distance = NumberInput.text(120.0, NumberInput.Style.KILOMETERS)

            assertTrue(viewModel.saveRoute())

            val saved = repository.routes()
            assertEquals(1, saved.size)
            assertEquals(existing.id, saved.single().id)
            assertEquals(120.0, saved.single().distanceKm, 0.001)
        }

    @Test
    fun `typing origin and destination loads suggestions and selecting one fills the field`() =
        runTest {
            val provider =
                FakeRouteEstimateProvider(
                    suggestions =
                        listOf(
                            PlaceSuggestion("São Paulo, SP, Brasil"),
                            PlaceSuggestion("São Paulo das Missões, RS, Brasil"),
                        ),
                )
            val viewModel = makeViewModel(routeEstimateProvider = provider)

            viewModel.updateOrigin("sao")
            viewModel.updateDestination("rio")

            assertEquals(2, viewModel.originSuggestions.size)
            assertEquals(2, viewModel.destinationSuggestions.size)
            viewModel.selectOriginSuggestion(viewModel.originSuggestions.first())
            assertEquals("São Paulo, SP, Brasil", viewModel.origin)
            assertTrue(viewModel.originSuggestions.isEmpty())
            viewModel.selectDestinationSuggestion(viewModel.destinationSuggestions.first())
            assertEquals("São Paulo, SP, Brasil", viewModel.destination)
            assertTrue(viewModel.destinationSuggestions.isEmpty())
        }

    @Test
    fun `fast typing only searches the last text once the user pauses`() =
        runTest {
            val provider = FakeRouteEstimateProvider(suggestions = listOf(PlaceSuggestion("Campinas, SP, Brasil")))
            val viewModel = makeViewModel(routeEstimateProvider = provider)

            val first = launch { viewModel.updateOrigin("Cam") }
            advanceTimeBy(100)
            val second = launch { viewModel.updateOrigin("Campi") }
            advanceTimeBy(100)
            val third = launch { viewModel.updateOrigin("Campinas") }
            advanceUntilIdle()

            assertTrue(first.isCancelled)
            assertTrue(second.isCancelled)
            assertTrue(third.isCompleted && !third.isCancelled)
            assertEquals(listOf("Campinas"), provider.queries)
            assertEquals(1, viewModel.originSuggestions.size)
        }

    @Test
    fun `picking a suggestion cancels the search still waiting`() =
        runTest {
            val provider = FakeRouteEstimateProvider(suggestions = listOf(PlaceSuggestion("Campinas, SP, Brasil")))
            val viewModel = makeViewModel(routeEstimateProvider = provider)

            launch { viewModel.updateDestination("Campinas") }
            runCurrent()
            viewModel.selectDestinationSuggestion(PlaceSuggestion("Campinas, SP, Brasil"))
            advanceUntilIdle()

            assertTrue(provider.queries.isEmpty())
            assertTrue(viewModel.destinationSuggestions.isEmpty())
            assertEquals("Campinas, SP, Brasil", viewModel.destination)
        }

    @Test
    fun `text shorter than three letters clears suggestions without a request`() =
        runTest {
            val provider = FakeRouteEstimateProvider(suggestions = listOf(PlaceSuggestion("Campinas, SP, Brasil")))
            val viewModel = makeViewModel(routeEstimateProvider = provider)
            viewModel.updateOrigin("Campinas")

            viewModel.updateOrigin("Ca")

            assertEquals(listOf("Campinas"), provider.queries)
            assertTrue(viewModel.originSuggestions.isEmpty())
        }

    @Test
    fun `a suggestions failure returns an empty list and stops loading`() =
        runTest {
            val viewModel = makeViewModel(routeEstimateProvider = FakeRouteEstimateProvider(failure = IllegalStateException("offline")))

            viewModel.updateOrigin("sao")

            assertTrue(viewModel.originSuggestions.isEmpty())
            assertEquals(false, viewModel.suggestionsWorking)
        }

    @Test
    fun `a suggestions cancellation is rethrown`() =
        runTest {
            val viewModel = makeViewModel(routeEstimateProvider = FakeRouteEstimateProvider(failure = CancellationException("cancelled")))

            val error = runCatching { viewModel.updateOrigin("sao") }.exceptionOrNull()

            assertIs<CancellationException>(error)
        }

    @Test
    fun `unavailable provider returns empty suggestions`() =
        runTest {
            assertTrue(UnavailableRouteEstimateProvider.suggestions("sao").isEmpty())
        }

    @Test
    fun `the average says how many full tank intervals it is based on and when the price was paid`() =
        runTest {
            val viewModel = makeViewModel()

            viewModel.load()

            assertEquals(1, viewModel.averageConsumptionSamples)
            assertEquals(day(10), viewModel.lastPriceDate)
            assertFalse(viewModel.canUseAverageConsumption)
            assertFalse(viewModel.canUseLastPrice)
        }

    @Test
    fun `the suggested consumption uses the latest fuel when there is per-fuel data`() =
        runTest {
            val fuel =
                listOf(
                    full(0, 1_000.0, 40.0, fuelType = FuelType.GASOLINA_COMUM),
                    full(5, 1_560.0, 40.0, fuelType = FuelType.GASOLINA_COMUM),
                    full(10, 2_120.0, 40.0, fuelType = FuelType.ETANOL),
                    full(15, 2_520.0, 40.0, fuelType = FuelType.ETANOL),
                    full(20, 2_920.0, 40.0, fuelType = FuelType.ETANOL),
                )
            val viewModel = makeViewModel(fuel = fuel)

            viewModel.load()

            assertEquals(FuelGroup.ETANOL, viewModel.suggestedConsumptionGroup)
            assertEquals(1, viewModel.averageConsumptionSamples)
            assertEquals(10.0, viewModel.parsedConsumption!!, 0.05)
        }

    @Test
    fun `without per-fuel data the overall average is used`() =
        runTest {
            val viewModel = makeViewModel()

            viewModel.load()

            assertNull(viewModel.suggestedConsumptionGroup)
            assertEquals(12.5, viewModel.parsedConsumption!!, 0.05)
        }

    @Test
    fun `an edited consumption and price can go back to the vehicle history`() =
        runTest {
            val viewModel = makeViewModel()
            viewModel.load()
            viewModel.consumption = NumberInput.text(9.0, NumberInput.Style.CONSUMPTION)
            viewModel.pricePerLiter = NumberInput.text(BigDecimal("7"), NumberInput.Style.FUEL_PRICE)

            assertTrue(viewModel.canUseAverageConsumption)
            assertTrue(viewModel.canUseLastPrice)

            viewModel.useAverageConsumption()
            viewModel.useLastPrice()

            assertEquals(12.5, viewModel.parsedConsumption)
            assertDecimal("6.1", viewModel.parsedPrice)
            assertTrue(viewModel.isUsingAverageConsumption)
            assertTrue(viewModel.isUsingLastPrice)
        }

    @Test
    fun `the tank plan uses the selected vehicle capacity`() =
        runTest {
            val viewModel = makeViewModel(vehicles = listOf(car.copy(tankCapacity = 30.0)))
            viewModel.load()

            viewModel.distance = NumberInput.text(250.0, NumberInput.Style.KILOMETERS)
            viewModel.isRoundTrip = true

            val plan = assertNotNull(viewModel.tankPlan)
            assertEquals(40.0 / 30.0, plan.tanks, 1e-9)
            assertEquals(2, plan.fillUps)
            assertEquals(375.0, plan.rangePerTank, 1e-9)
        }

    @Test
    fun `no tank plan without a tank capacity or a cost`() =
        runTest {
            val viewModel = makeViewModel()
            viewModel.load()

            assertNull(viewModel.tankPlan)

            viewModel.distance = NumberInput.text(250.0, NumberInput.Style.KILOMETERS)

            assertNull(viewModel.tankPlan)
        }

    @Test
    fun `swapping places exchanges origin and destination`() =
        runTest {
            val viewModel = makeViewModel()
            viewModel.origin = "São Paulo, SP"
            viewModel.destination = "Santos, SP"

            viewModel.swapPlaces()

            assertEquals("Santos, SP", viewModel.origin)
            assertEquals("São Paulo, SP", viewModel.destination)
            assertTrue(viewModel.originSuggestions.isEmpty())
            assertTrue(viewModel.destinationSuggestions.isEmpty())
        }

    @Test
    fun `restoring keeps the saved values but offers the current vehicle history`() =
        runTest {
            val viewModel = makeViewModel()
            viewModel.load()

            viewModel.restoreRoute(savedRoute())

            assertTrue(viewModel.isEditing)
            assertEquals(10.0, viewModel.parsedConsumption!!, 0.001)
            assertTrue(viewModel.canUseAverageConsumption)
            assertTrue(viewModel.canUseLastPrice)
            assertEquals(1, viewModel.averageConsumptionSamples)
        }

    @Test
    fun `without vehicles there is no history to offer`() =
        runTest {
            val viewModel = makeViewModel(vehicles = emptyList())

            viewModel.load()

            assertEquals(0, viewModel.averageConsumptionSamples)
            assertNull(viewModel.lastPriceDate)
            assertFalse(viewModel.isEditing)
        }

    private fun savedRoute() =
        SavedRoute(
            id = UUID.randomUUID(),
            vehicleId = testVehicleId,
            origin = "São Paulo, SP",
            destination = "Rio de Janeiro, RJ",
            distanceKm = 100.0,
            isRoundTrip = true,
            hasTolls = true,
            tollCost = BigDecimal.ZERO,
            kilometersPerLiter = 10.0,
            pricePerLiter = BigDecimal("5"),
            liters = 20.0,
            fuelCost = BigDecimal("100"),
            costPerKilometer = BigDecimal("0.5"),
            createdAt = day(10),
        )
}
