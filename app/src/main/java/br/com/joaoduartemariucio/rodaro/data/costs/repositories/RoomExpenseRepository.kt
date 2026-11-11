package br.com.joaoduartemariucio.rodaro.data.costs.repositories

import br.com.joaoduartemariucio.rodaro.data.common.repositories.persisting
import br.com.joaoduartemariucio.rodaro.data.costs.mappers.ExpenseMapper
import br.com.joaoduartemariucio.rodaro.data.costs.models.ExpenseDao
import br.com.joaoduartemariucio.rodaro.data.vehicles.models.VehicleDao
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RepositoryError
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import br.com.joaoduartemariucio.rodaro.domain.costs.repositories.ExpenseRepository
import java.util.UUID

class RoomExpenseRepository(
    private val dao: ExpenseDao,
    private val vehicles: VehicleDao,
) : ExpenseRepository {
    override suspend fun expenses(vehicleId: UUID): List<Expense> = persisting { dao.expenses(vehicleId).map(ExpenseMapper::entity) }

    override suspend fun save(expense: Expense) =
        persisting {
            val existing = dao.byId(expense.id)
            if (existing == null && vehicles.byId(expense.vehicleId) == null) {
                throw RepositoryError.VehicleNotFound(expense.vehicleId)
            }
            dao.upsert(ExpenseMapper.model(expense.copy(vehicleId = existing?.vehicleId ?: expense.vehicleId)))
        }

    override suspend fun delete(id: UUID) =
        persisting {
            if (dao.delete(id) == 0) throw RepositoryError.EntryNotFound(id)
        }
}
