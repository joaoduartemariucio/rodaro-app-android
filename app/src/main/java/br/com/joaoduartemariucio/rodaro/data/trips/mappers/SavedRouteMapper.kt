package br.com.joaoduartemariucio.rodaro.data.trips.mappers

import br.com.joaoduartemariucio.rodaro.data.network.Polyline
import br.com.joaoduartemariucio.rodaro.data.trips.models.TripRouteModel
import br.com.joaoduartemariucio.rodaro.domain.trips.entities.SavedRoute

object SavedRouteMapper {
    fun entity(model: TripRouteModel): SavedRoute =
        SavedRoute(
            id = model.id,
            vehicleId = model.vehicleId,
            origin = model.origin,
            destination = model.destination,
            distanceKm = model.distanceKm,
            isRoundTrip = model.isRoundTrip,
            hasTolls = model.hasTolls,
            tollCost = model.tollCost,
            kilometersPerLiter = model.kilometersPerLiter,
            pricePerLiter = model.pricePerLiter,
            liters = model.liters,
            fuelCost = model.fuelCost,
            costPerKilometer = model.costPerKilometer,
            createdAt = model.createdAt,
            path = Polyline.decode(model.path, Polyline.PRECISION_6),
        )

    fun model(entity: SavedRoute): TripRouteModel =
        TripRouteModel(
            id = entity.id,
            vehicleId = entity.vehicleId,
            origin = entity.origin,
            destination = entity.destination,
            distanceKm = entity.distanceKm,
            isRoundTrip = entity.isRoundTrip,
            hasTolls = entity.hasTolls,
            tollCost = entity.tollCost,
            kilometersPerLiter = entity.kilometersPerLiter,
            pricePerLiter = entity.pricePerLiter,
            liters = entity.liters,
            fuelCost = entity.fuelCost,
            costPerKilometer = entity.costPerKilometer,
            createdAt = entity.createdAt,
            path = Polyline.encode(entity.path, Polyline.PRECISION_6),
        )
}
