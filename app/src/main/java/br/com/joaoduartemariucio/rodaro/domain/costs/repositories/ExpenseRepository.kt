package br.com.joaoduartemariucio.rodaro.domain.costs.repositories

import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import java.util.UUID

interface ExpenseRepository {
    suspend fun expenses(vehicleId: UUID): List<Expense>

    suspend fun save(expense: Expense)

    suspend fun delete(id: UUID)
}
