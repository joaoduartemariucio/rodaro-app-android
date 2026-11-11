package br.com.joaoduartemariucio.rodaro.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.joaoduartemariucio.rodaro.calculations.BackupFixtures
import br.com.joaoduartemariucio.rodaro.data.backup.repositories.RoomBackupRepository
import br.com.joaoduartemariucio.rodaro.data.database.RodaroDatabase
import br.com.joaoduartemariucio.rodaro.data.fueling.repositories.RoomFuelEntryRepository
import br.com.joaoduartemariucio.rodaro.data.maintenance.repositories.RoomServiceRepository
import br.com.joaoduartemariucio.rodaro.data.trips.repositories.RoomRouteRepository
import br.com.joaoduartemariucio.rodaro.data.vehicles.repositories.RoomVehicleRepository
import br.com.joaoduartemariucio.rodaro.domain.backup.entities.BackupContents
import br.com.joaoduartemariucio.rodaro.domain.backup.entities.BackupSnapshot
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RepositoryError
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
class RoomBackupRepositoryTests {
    private val database = RodaroDatabase.create(ApplicationProvider.getApplicationContext<Context>(), inMemory = true)
    private val backup = RoomBackupRepository(database.backup())
    private val vehicles = RoomVehicleRepository(database.vehicles())
    private val fuel = RoomFuelEntryRepository(database.fuelEntries(), database.vehicles())
    private val services = RoomServiceRepository(database.services(), database.vehicles())
    private val routes = RoomRouteRepository(database.tripRoutes())

    private val stored = BackupFixtures.snapshot(preferences = null, exportedAt = null)

    @After
    fun close() = database.close()

    private suspend fun seed() {
        vehicles.save(BackupFixtures.gol)
        vehicles.save(BackupFixtures.cb)
        fuel.save(BackupFixtures.fuel)
        services.save(BackupFixtures.service)
        services.save(listOf(BackupFixtures.rule), BackupFixtures.golId)
        routes.save(BackupFixtures.route)
    }

    @Test
    fun `the snapshot has every table, archived vehicles and saved routes included`() =
        runTest {
            seed()

            assertEquals(stored, backup.snapshot())
        }

    @Test
    fun `replacing leaves exactly the rows of the snapshot`() =
        runTest {
            seed()
            val newcomer = Vehicle(name = "Onix", createdAt = Instant.parse("2026-01-01T00:00:00Z"))
            val incoming =
                BackupSnapshot(
                    vehicles = listOf(newcomer),
                    fuelEntries = listOf(BackupFixtures.fuel.copy(id = UUID.randomUUID(), vehicleId = newcomer.id)),
                )

            backup.replaceAll(incoming)

            assertEquals(incoming, backup.snapshot())
            assertEquals(listOf(newcomer), vehicles.all(includingArchived = true))
            assertEquals(emptyList(), routes.routes())
        }

    @Test
    fun `replacing with an empty snapshot clears every table`() =
        runTest {
            seed()

            backup.replaceAll(BackupSnapshot())

            assertEquals(BackupContents(0, 0, 0, 0, 0), backup.snapshot().contents)
        }

    @Test
    fun `a failing replace keeps the previous data`() =
        runTest {
            seed()
            val orphan = BackupFixtures.fuel.copy(id = UUID.randomUUID(), vehicleId = UUID.randomUUID())

            assertFailsWith<RepositoryError.PersistenceFailed> {
                backup.replaceAll(BackupSnapshot(vehicles = listOf(BackupFixtures.gol), fuelEntries = listOf(orphan)))
            }

            assertEquals(stored, backup.snapshot())
        }
}
