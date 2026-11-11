package br.com.joaoduartemariucio.rodaro.data.fueling.mappers

import br.com.joaoduartemariucio.rodaro.data.fueling.models.FuelEntryModel
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry

object FuelEntryMapper {
    fun entity(model: FuelEntryModel): FuelEntry =
        FuelEntry(
            id = model.id,
            vehicleId = model.vehicleId,
            date = model.date,
            odometer = model.odometer,
            liters = model.liters,
            pricePerLiter = model.pricePerLiter,
            isFullTank = model.isFullTank,
            fuelType = model.fuelType,
            createdAt = model.createdAt,
        )

    fun model(entity: FuelEntry): FuelEntryModel =
        FuelEntryModel(
            id = entity.id,
            vehicleId = entity.vehicleId,
            date = entity.date,
            odometer = entity.odometer,
            liters = entity.liters,
            pricePerLiter = entity.pricePerLiter,
            isFullTank = entity.isFullTank,
            fuelType = entity.fuelType,
            createdAt = entity.createdAt,
        )
}
