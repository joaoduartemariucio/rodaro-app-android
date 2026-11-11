package br.com.joaoduartemariucio.rodaro.data.vehicles.mappers

import br.com.joaoduartemariucio.rodaro.data.vehicles.models.VehicleModel
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeSelection
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
            fipe = fipe(model),
            purchaseDate = model.purchaseDate,
            purchasePrice = model.purchasePrice,
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
            fipeCode = entity.fipe?.fipeCode,
            fipeYearCode = entity.fipe?.yearCode,
            fipeBrandName = entity.fipe?.brandName,
            fipeModelName = entity.fipe?.modelName,
            fipeYearName = entity.fipe?.yearName,
            purchaseDate = entity.purchaseDate,
            purchasePrice = entity.purchasePrice,
        )

    private fun fipe(model: VehicleModel): FipeSelection? {
        val code = model.fipeCode ?: return null
        val yearCode = model.fipeYearCode ?: return null
        val brand = model.fipeBrandName ?: return null
        val name = model.fipeModelName ?: return null
        val year = model.fipeYearName ?: return null
        return FipeSelection(code, yearCode, brand, name, year)
    }
}
