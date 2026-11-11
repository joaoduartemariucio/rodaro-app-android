package br.com.joaoduartemariucio.rodaro.data.dues.mappers

import br.com.joaoduartemariucio.rodaro.data.dues.models.DueItemModel
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueItem

object DueItemMapper {
    fun entity(model: DueItemModel): DueItem =
        DueItem(
            id = model.id,
            vehicleId = model.vehicleId,
            kind = model.kind,
            dueDate = model.dueDate,
            amount = model.amount,
            paidAt = model.paidAt,
            installmentNumber = model.installmentNumber,
            installmentCount = model.installmentCount,
            note = model.note,
            createdAt = model.createdAt,
        )

    fun model(entity: DueItem): DueItemModel =
        DueItemModel(
            id = entity.id,
            vehicleId = entity.vehicleId,
            kind = entity.kind,
            dueDate = entity.dueDate,
            amount = entity.amount,
            paidAt = entity.paidAt,
            installmentNumber = entity.installmentNumber,
            installmentCount = entity.installmentCount,
            note = entity.note,
            createdAt = entity.createdAt,
        )
}
