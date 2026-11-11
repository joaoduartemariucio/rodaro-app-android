package br.com.joaoduartemariucio.rodaro.data.costs.mappers

import br.com.joaoduartemariucio.rodaro.data.costs.models.ExpenseModel
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDetails
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDiscount

object ExpenseMapper {
    fun entity(model: ExpenseModel): Expense =
        Expense(
            id = model.id,
            vehicleId = model.vehicleId,
            category = model.category,
            date = model.date,
            amount = model.amount,
            odometer = model.odometer,
            note = model.note,
            coverageStart = model.coverageStart,
            coverageEnd = model.coverageEnd,
            fine = fine(model),
            createdAt = model.createdAt,
        )

    fun model(entity: Expense): ExpenseModel {
        val fine = entity.fine.takeIf { entity.category == ExpenseCategory.MULTA }
        return ExpenseModel(
            id = entity.id,
            vehicleId = entity.vehicleId,
            category = entity.category,
            date = entity.date,
            amount = entity.amount,
            odometer = entity.odometer,
            note = entity.note,
            coverageStart = entity.coverageStart,
            coverageEnd = entity.coverageEnd,
            fineInfractionDate = fine?.infractionDate,
            fineSeverity = fine?.severity,
            finePoints = fine?.points,
            fineDueDate = fine?.dueDate,
            fineDiscount = fine?.discount,
            fineIsPaid = fine?.isPaid,
            createdAt = entity.createdAt,
        )
    }

    private fun fine(model: ExpenseModel): FineDetails? {
        if (model.category != ExpenseCategory.MULTA) return null
        val isPaid = model.fineIsPaid ?: return null
        return FineDetails(
            infractionDate = model.fineInfractionDate,
            severity = model.fineSeverity,
            points = model.finePoints,
            dueDate = model.fineDueDate,
            discount = model.fineDiscount ?: FineDiscount.NONE,
            isPaid = isPaid,
        )
    }
}
