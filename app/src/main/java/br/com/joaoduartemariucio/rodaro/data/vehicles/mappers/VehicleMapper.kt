package br.com.joaoduartemariucio.rodaro.data.vehicles.mappers

import br.com.joaoduartemariucio.rodaro.data.vehicles.models.VehicleModel
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle

object VehicleMapper {
    fun entity(model: VehicleModel): Vehicle =
        Vehicle(
            id = model.id,
            name = model.name,
            kind = model.kind,
            plate = model.plate,
            initialOdometer = model.initialOdometer,
            tankCapacity = model.tankCapacity,
            createdAt = model.createdAt,
            isArchived = model.isArchived,
            fuelCapability = model.fuelCapability,
            uf = model.uf,
            modelYear = model.modelYear,
        )

    fun model(entity: Vehicle): VehicleModel =
        VehicleModel(
            id = entity.id,
            name = entity.name,
            kind = entity.kind,
            plate = entity.plate,
            initialOdometer = entity.initialOdometer,
            tankCapacity = entity.tankCapacity,
            createdAt = entity.createdAt,
            isArchived = entity.isArchived,
            fuelCapability = entity.fuelCapability,
            uf = entity.uf,
            modelYear = entity.modelYear,
        )
}
