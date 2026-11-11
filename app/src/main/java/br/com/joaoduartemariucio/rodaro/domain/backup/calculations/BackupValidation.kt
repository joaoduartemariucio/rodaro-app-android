package br.com.joaoduartemariucio.rodaro.domain.backup.calculations

import br.com.joaoduartemariucio.rodaro.domain.backup.entities.BackupSnapshot
import java.util.UUID

enum class BackupTable {
    VEHICLES,
    FUEL_ENTRIES,
    SERVICE_ENTRIES,
    SERVICE_RULES,
    SAVED_ROUTES,
    EXPENSES,
    DUE_ITEMS,
}

sealed interface BackupProblem {
    data class DuplicateId(
        val table: BackupTable,
        val id: UUID,
    ) : BackupProblem

    data class MissingVehicle(
        val table: BackupTable,
        val recordId: UUID,
        val vehicleId: UUID,
    ) : BackupProblem
}

object BackupValidation {
    fun problems(snapshot: BackupSnapshot): List<BackupProblem> {
        val vehicleIds = snapshot.vehicles.map { it.id }.toSet()
        return duplicates(BackupTable.VEHICLES, snapshot.vehicles.map { it.id }) +
            duplicates(BackupTable.FUEL_ENTRIES, snapshot.fuelEntries.map { it.id }) +
            duplicates(BackupTable.SERVICE_ENTRIES, snapshot.serviceEntries.map { it.id }) +
            duplicates(BackupTable.SERVICE_RULES, snapshot.serviceRules.map { it.id }) +
            duplicates(BackupTable.SAVED_ROUTES, snapshot.savedRoutes.map { it.id }) +
            duplicates(BackupTable.EXPENSES, snapshot.expenses.map { it.id }) +
            duplicates(BackupTable.DUE_ITEMS, snapshot.dueItems.map { it.id }) +
            missing(BackupTable.FUEL_ENTRIES, snapshot.fuelEntries.map { it.id to it.vehicleId }, vehicleIds) +
            missing(BackupTable.SERVICE_ENTRIES, snapshot.serviceEntries.map { it.id to it.vehicleId }, vehicleIds) +
            missing(BackupTable.SERVICE_RULES, snapshot.serviceRules.map { it.id to it.vehicleId }, vehicleIds) +
            missing(BackupTable.EXPENSES, snapshot.expenses.map { it.id to it.vehicleId }, vehicleIds) +
            missing(BackupTable.DUE_ITEMS, snapshot.dueItems.mapNotNull { item -> item.vehicleId?.let { item.id to it } }, vehicleIds)
    }

    private fun duplicates(
        table: BackupTable,
        ids: List<UUID>,
    ): List<BackupProblem> =
        ids
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .map { BackupProblem.DuplicateId(table, it) }

    private fun missing(
        table: BackupTable,
        records: List<Pair<UUID, UUID>>,
        vehicleIds: Set<UUID>,
    ): List<BackupProblem> =
        records
            .filter { (_, vehicleId) -> vehicleId !in vehicleIds }
            .map { (id, vehicleId) -> BackupProblem.MissingVehicle(table, id, vehicleId) }
}
