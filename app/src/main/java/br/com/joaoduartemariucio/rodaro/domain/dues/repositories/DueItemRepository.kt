package br.com.joaoduartemariucio.rodaro.domain.dues.repositories

import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueItem
import java.util.UUID

interface DueItemRepository {
    suspend fun items(vehicleId: UUID): List<DueItem>

    suspend fun driverItems(): List<DueItem>

    suspend fun all(): List<DueItem>

    suspend fun save(item: DueItem)

    suspend fun delete(id: UUID)
}
