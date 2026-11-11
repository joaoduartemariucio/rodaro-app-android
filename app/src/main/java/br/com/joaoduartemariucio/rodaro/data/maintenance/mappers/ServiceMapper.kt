package br.com.joaoduartemariucio.rodaro.data.maintenance.mappers

import br.com.joaoduartemariucio.rodaro.data.maintenance.models.ServiceEntryModel
import br.com.joaoduartemariucio.rodaro.data.maintenance.models.ServiceRuleModel
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceRule

object ServiceMapper {
    fun entity(model: ServiceEntryModel): ServiceEntry =
        ServiceEntry(
            id = model.id,
            vehicleId = model.vehicleId,
            kind = model.kind,
            date = model.date,
            odometer = model.odometer,
            cost = model.cost,
            notes = model.notes,
            workshop = model.workshop,
            createdAt = model.createdAt,
        )

    fun model(entity: ServiceEntry): ServiceEntryModel =
        ServiceEntryModel(
            id = entity.id,
            vehicleId = entity.vehicleId,
            kind = entity.kind,
            date = entity.date,
            odometer = entity.odometer,
            cost = entity.cost,
            notes = entity.notes,
            workshop = entity.workshop,
            createdAt = entity.createdAt,
        )

    fun entity(model: ServiceRuleModel): ServiceRule =
        ServiceRule(
            id = model.id,
            vehicleId = model.vehicleId,
            kind = model.kind,
            kilometerInterval = model.kilometerInterval,
            monthInterval = model.monthInterval,
        )

    fun model(entity: ServiceRule): ServiceRuleModel =
        ServiceRuleModel(
            id = entity.id,
            vehicleId = entity.vehicleId,
            kind = entity.kind,
            kilometerInterval = entity.kilometerInterval,
            monthInterval = entity.monthInterval,
        )
}
