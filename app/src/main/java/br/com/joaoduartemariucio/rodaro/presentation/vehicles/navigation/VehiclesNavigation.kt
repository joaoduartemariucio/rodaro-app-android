package br.com.joaoduartemariucio.rodaro.presentation.vehicles.navigation

import androidx.navigation3.runtime.EntryProviderScope
import br.com.joaoduartemariucio.rodaro.presentation.common.navigation.Navigator
import br.com.joaoduartemariucio.rodaro.presentation.common.navigation.Route
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.VehicleDetailScreen
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.list.VehicleListScreen
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.list.VehicleListViewModel
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.review.YearReviewScreen

fun EntryProviderScope<Route>.vehiclesEntries(navigator: Navigator) {
    entry<VehiclesRoute.VehicleList> {
        VehicleListScreen(
            scope = VehicleListViewModel.Scope.ACTIVE,
            onOpenVehicle = { navigator.open(VehiclesRoute.VehicleDetail(it)) },
        )
    }
    entry<VehiclesRoute.ArchivedVehicles> {
        VehicleListScreen(
            scope = VehicleListViewModel.Scope.ARCHIVED,
            onOpenVehicle = { navigator.open(VehiclesRoute.VehicleDetail(it)) },
        )
    }
    entry<VehiclesRoute.VehicleDetail> { route ->
        VehicleDetailScreen(
            vehicle = route.vehicle,
            onBack = navigator::back,
            onOpenYearReview = { navigator.open(VehiclesRoute.YearReview(it)) },
        )
    }
    entry<VehiclesRoute.YearReview> { route ->
        YearReviewScreen(vehicle = route.vehicle, onBack = navigator::back)
    }
}
