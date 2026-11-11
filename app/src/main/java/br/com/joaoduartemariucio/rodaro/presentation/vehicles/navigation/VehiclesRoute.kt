package br.com.joaoduartemariucio.rodaro.presentation.vehicles.navigation

import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.presentation.common.navigation.Route

sealed interface VehiclesRoute : Route {
    data object VehicleList : VehiclesRoute

    data object ArchivedVehicles : VehiclesRoute

    data class VehicleDetail(
        val vehicle: Vehicle,
    ) : VehiclesRoute

    data class YearReview(
        val vehicle: Vehicle,
    ) : VehiclesRoute
}
