package br.com.joaoduartemariucio.rodaro.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import br.com.joaoduartemariucio.rodaro.data.database.RodaroDatabase
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.FuelCapability
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RodaroDatabaseMigrationTests {
    @Test
    fun `migrating from 1 to 2 keeps vehicles and creates trip routes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration =
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(MIGRATION_DB)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(CREATE_V1_VEHICLES)
                            db.execSQL(
                                "INSERT INTO vehicles (id, name, kind, plate, initialOdometer, tankCapacity, createdAt, isArchived) " +
                                    "VALUES ('00000000-0000-0000-0000-000000000001', 'Gol', 'car', NULL, 1000.0, 55.0, 0, 0)",
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build()

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val database = openHelper.writableDatabase

        RodaroDatabase.MIGRATION_1_2.migrate(database)

        database.query("SELECT name FROM vehicles").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Gol", cursor.getString(0))
        }
        database.query("SELECT COUNT(*) FROM trip_routes").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        database.close()
        openHelper.close()
        context.deleteDatabase(MIGRATION_DB)
    }

    @Test
    fun `migrating from 2 to 3 keeps routes and adds toll cost default`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration =
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(MIGRATION_DB)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(2) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(CREATE_V2_TRIP_ROUTES)
                            db.execSQL(
                                "INSERT INTO trip_routes (id, vehicleId, origin, destination, distanceKm, isRoundTrip, hasTolls, liters, fuelCost, costPerKilometer, createdAt) " +
                                    "VALUES ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'A', 'B', 120.0, 0, 1, 10.0, '50', '0.4', 0)",
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build()

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val database = openHelper.writableDatabase

        RodaroDatabase.MIGRATION_2_3.migrate(database)

        database.query("SELECT fuelCost, tollCost FROM trip_routes").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("50", cursor.getString(0))
            assertEquals("0", cursor.getString(1))
        }
        database.close()
        openHelper.close()
        context.deleteDatabase(MIGRATION_DB)
    }

    @Test
    fun `migrating from 3 to 4 keeps routes and adds consumption snapshot defaults`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration =
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(MIGRATION_DB)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(3) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(CREATE_V3_TRIP_ROUTES)
                            db.execSQL(
                                "INSERT INTO trip_routes (id, vehicleId, origin, destination, distanceKm, isRoundTrip, hasTolls, tollCost, liters, fuelCost, costPerKilometer, createdAt) " +
                                    "VALUES ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'A', 'B', 120.0, 0, 1, '0', 10.0, '50', '0.4', 0)",
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build()

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val database = openHelper.writableDatabase

        RodaroDatabase.MIGRATION_3_4.migrate(database)

        database.query("SELECT fuelCost, kilometersPerLiter, pricePerLiter FROM trip_routes").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("50", cursor.getString(0))
            assertEquals(0.0, cursor.getDouble(1), 0.001)
            assertEquals("0", cursor.getString(2))
        }
        database.close()
        openHelper.close()
        context.deleteDatabase(MIGRATION_DB)
    }

    @Test
    fun `migrating from 4 to 5 keeps routes with an empty path`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration =
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(MIGRATION_DB)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(4) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(CREATE_V3_TRIP_ROUTES)
                            RodaroDatabase.MIGRATION_3_4.migrate(db)
                            db.execSQL(
                                "INSERT INTO trip_routes (id, vehicleId, origin, destination, distanceKm, isRoundTrip, hasTolls, tollCost, kilometersPerLiter, pricePerLiter, liters, fuelCost, costPerKilometer, createdAt) " +
                                    "VALUES ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'A', 'B', 120.0, 0, 1, '0', 12.0, '6', 10.0, '60', '0.5', 0)",
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build()

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val database = openHelper.writableDatabase

        RodaroDatabase.MIGRATION_4_5.migrate(database)

        database.query("SELECT origin, path FROM trip_routes").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("A", cursor.getString(0))
            assertEquals("", cursor.getString(1))
        }
        database.close()
        openHelper.close()
        context.deleteDatabase(MIGRATION_DB)
    }

    private fun version5(context: Context): SupportSQLiteOpenHelper {
        val configuration =
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(MIGRATION_DB)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(5) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            CREATE_V5.forEach(db::execSQL)
                            db.execSQL(
                                "INSERT INTO vehicles (id, name, kind, plate, initialOdometer, tankCapacity, createdAt, isArchived) " +
                                    "VALUES ('$VEHICLE_ID', 'Gol', 'car', NULL, 1000.0, 55.0, 0, 0)",
                            )
                            db.execSQL(
                                "INSERT INTO fuel_entries (id, vehicleId, date, odometer, liters, pricePerLiter, isFullTank) " +
                                    "VALUES ('$FUEL_ID', '$VEHICLE_ID', 86400000, 1500.0, 40.0, '5.899', 1)",
                            )
                            db.execSQL(
                                "INSERT INTO service_entries (id, vehicleId, kind, date, odometer, cost, notes) " +
                                    "VALUES ('$SERVICE_ID', '$VEHICLE_ID', 'oilChange', 172800000, 1600.0, '120.00', 'Synthetic')",
                            )
                            db.execSQL(
                                "INSERT INTO service_rules (id, vehicleId, kind, kilometerInterval, monthInterval) " +
                                    "VALUES ('00000000-0000-0000-0000-000000000004', '$VEHICLE_ID', 'oilChange', 10000.0, 12)",
                            )
                            db.execSQL(
                                "INSERT INTO trip_routes (id, vehicleId, origin, destination, distanceKm, isRoundTrip, hasTolls, tollCost, kilometersPerLiter, pricePerLiter, liters, fuelCost, costPerKilometer, createdAt, path) " +
                                    "VALUES ('00000000-0000-0000-0000-000000000005', '$VEHICLE_ID', 'A', 'B', 120.0, 0, 1, '0', 12.0, '6', 10.0, '60', '0.5', 0, '')",
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    @Test
    fun `migrating from 5 to 6 keeps records, marks vehicles flex, leaves fuel type unknown and copies date into createdAt`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val openHelper = version5(context)
        val database = openHelper.writableDatabase

        RodaroDatabase.MIGRATION_5_6.migrate(database)

        database.query("SELECT name, fuelCapability, uf, modelYear FROM vehicles").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Gol", cursor.getString(0))
            assertEquals("flex", cursor.getString(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
        }
        database.query("SELECT fuelType, createdAt, date FROM fuel_entries").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertEquals(86_400_000L, cursor.getLong(1))
            assertEquals(cursor.getLong(2), cursor.getLong(1))
        }
        database.query("SELECT workshop, createdAt FROM service_entries").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertEquals(172_800_000L, cursor.getLong(1))
        }
        database.query("SELECT COUNT(*) FROM expenses").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        database.query("SELECT COUNT(*) FROM due_items").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        database.close()
        openHelper.close()
        context.deleteDatabase(MIGRATION_DB)
    }

    @Test
    fun `a version 5 database migrates through 6 to 7 and opens with Room`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val openHelper = version5(context)
            openHelper.writableDatabase.close()
            openHelper.close()

            val room =
                Room
                    .databaseBuilder(context, RodaroDatabase::class.java, MIGRATION_DB)
                    .addMigrations(RodaroDatabase.MIGRATION_5_6, RodaroDatabase.MIGRATION_6_7)
                    .build()
            try {
                val vehicleId = UUID.fromString(VEHICLE_ID)
                val vehicle = room.vehicles().byId(vehicleId)
                val fuel = room.fuelEntries().entries(vehicleId).single()
                val service = room.services().entries(vehicleId).single()

                assertEquals(FuelCapability.FLEX, vehicle?.fuelCapability)
                assertNull(fuel.fuelType)
                assertEquals(Instant.ofEpochMilli(86_400_000L), fuel.createdAt)
                assertNull(service.workshop)
                assertEquals(service.date, service.createdAt)
                assertEquals(1, room.services().rules(vehicleId).size)
                assertEquals(1, room.tripRoutes().all().size)
                assertTrue(room.expenses().expenses(vehicleId).isEmpty())
                assertTrue(room.dueItems().all().isEmpty())
                assertNull(vehicle?.fipeCode)
                assertTrue(room.fipeQuotes().byVehicle(vehicleId).isEmpty())
            } finally {
                room.close()
                context.deleteDatabase(MIGRATION_DB)
            }
        }

    private fun version6(context: Context): SupportSQLiteOpenHelper {
        val configuration =
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(MIGRATION_DB)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(6) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            CREATE_V6.forEach(db::execSQL)
                            db.execSQL(
                                "INSERT INTO vehicles (id, name, kind, plate, initialOdometer, tankCapacity, createdAt, isArchived, fuelCapability, uf, modelYear) " +
                                    "VALUES ('$VEHICLE_ID', 'Gol', 'car', 'ABC1D23', 1000.0, 55.0, 0, 0, 'diesel', 'SP', 2014)",
                            )
                            db.execSQL(
                                "INSERT INTO fuel_entries (id, vehicleId, date, odometer, liters, pricePerLiter, isFullTank, fuelType, createdAt) " +
                                    "VALUES ('$FUEL_ID', '$VEHICLE_ID', 86400000, 1500.0, 40.0, '5.899', 1, 'dieselS10', 86400000)",
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    @Test
    fun `migrating from 6 to 7 keeps vehicles and leaves FIPE empty`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val openHelper = version6(context)
        val database = openHelper.writableDatabase

        RodaroDatabase.MIGRATION_6_7.migrate(database)

        database.query("SELECT name, uf, fipeCode, fipeYearCode, fipeBrandName, fipeModelName, fipeYearName, purchaseDate, purchasePrice FROM vehicles").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Gol", cursor.getString(0))
            assertEquals("SP", cursor.getString(1))
            (2..8).forEach { assertTrue(cursor.isNull(it)) }
        }
        database.query("SELECT COUNT(*) FROM fipe_quotes").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        database.close()
        openHelper.close()
        context.deleteDatabase(MIGRATION_DB)
    }

    @Test
    fun `a migrated version 6 database opens with Room`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val openHelper = version6(context)
            openHelper.writableDatabase.close()
            openHelper.close()

            val room =
                Room
                    .databaseBuilder(context, RodaroDatabase::class.java, MIGRATION_DB)
                    .addMigrations(RodaroDatabase.MIGRATION_6_7)
                    .build()
            try {
                val vehicleId = UUID.fromString(VEHICLE_ID)
                val vehicle = room.vehicles().byId(vehicleId)

                assertEquals(FuelCapability.DIESEL, vehicle?.fuelCapability)
                assertEquals(2014, vehicle?.modelYear)
                assertNull(vehicle?.fipeCode)
                assertNull(vehicle?.purchasePrice)
                assertEquals(1, room.fuelEntries().entries(vehicleId).size)
                assertTrue(room.fipeQuotes().byVehicle(vehicleId).isEmpty())
            } finally {
                room.close()
                context.deleteDatabase(MIGRATION_DB)
            }
        }

    companion object {
        private const val MIGRATION_DB = "migration-test.db"
        private const val VEHICLE_ID = "00000000-0000-0000-0000-000000000001"
        private const val FUEL_ID = "00000000-0000-0000-0000-000000000002"
        private const val SERVICE_ID = "00000000-0000-0000-0000-000000000003"
        private val CREATE_V5 =
            listOf(
                "CREATE TABLE IF NOT EXISTS `vehicles` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `plate` TEXT, " +
                    "`initialOdometer` REAL NOT NULL, `tankCapacity` REAL, `createdAt` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                "CREATE TABLE IF NOT EXISTS `fuel_entries` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `date` INTEGER NOT NULL, " +
                    "`odometer` REAL NOT NULL, `liters` REAL NOT NULL, `pricePerLiter` TEXT NOT NULL, `isFullTank` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_fuel_entries_vehicleId` ON `fuel_entries` (`vehicleId`)",
                "CREATE TABLE IF NOT EXISTS `service_entries` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`date` INTEGER NOT NULL, `odometer` REAL NOT NULL, `cost` TEXT NOT NULL, `notes` TEXT NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_service_entries_vehicleId` ON `service_entries` (`vehicleId`)",
                "CREATE TABLE IF NOT EXISTS `service_rules` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`kilometerInterval` REAL, `monthInterval` INTEGER, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_service_rules_vehicleId` ON `service_rules` (`vehicleId`)",
                "CREATE TABLE IF NOT EXISTS `trip_routes` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `origin` TEXT NOT NULL, " +
                    "`destination` TEXT NOT NULL, `distanceKm` REAL NOT NULL, `isRoundTrip` INTEGER NOT NULL, `hasTolls` INTEGER NOT NULL, " +
                    "`tollCost` TEXT NOT NULL, `kilometersPerLiter` REAL NOT NULL, `pricePerLiter` TEXT NOT NULL, `liters` REAL NOT NULL, " +
                    "`fuelCost` TEXT NOT NULL, `costPerKilometer` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `path` TEXT NOT NULL DEFAULT '', " +
                    "PRIMARY KEY(`id`))",
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'abd0a60feeaabecac80a0d8c6f2673de')",
            )
        private val CREATE_V6 =
            listOf(
                "CREATE TABLE IF NOT EXISTS `vehicles` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `plate`" +
                    " TEXT, `initialOdometer` REAL NOT NULL, `tankCapacity` REAL, `createdAt` INTEGER NOT NULL, `isArchived` INTEGE" +
                    "R NOT NULL, `fuelCapability` TEXT NOT NULL DEFAULT 'flex', `uf` TEXT, `modelYear` INTEGER, PRIMARY KEY(`id`))",
                "CREATE TABLE IF NOT EXISTS `fuel_entries` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `date` INTEGER NOT N" +
                    "ULL, `odometer` REAL NOT NULL, `liters` REAL NOT NULL, `pricePerLiter` TEXT NOT NULL, `isFullTank` INTEGER NOT" +
                    " NULL, `fuelType` TEXT, `createdAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`), FOREIGN KEY(`vehicleId`) RE" +
                    "FERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_fuel_entries_vehicleId` ON `fuel_entries` (`vehicleId`)",
                "CREATE TABLE IF NOT EXISTS `service_entries` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `kind` TEXT NOT N" +
                    "ULL, `date` INTEGER NOT NULL, `odometer` REAL NOT NULL, `cost` TEXT NOT NULL, `notes` TEXT NOT NULL, `workshop" +
                    "` TEXT, `createdAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`), FOREIGN KEY(`vehicleId`) REFERENCES `vehicl" +
                    "es`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_service_entries_vehicleId` ON `service_entries` (`vehicleId`)",
                "CREATE TABLE IF NOT EXISTS `service_rules` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `kind` TEXT NOT NUL" +
                    "L, `kilometerInterval` REAL, `monthInterval` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`vehicleId`) REFERENCES `" +
                    "vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_service_rules_vehicleId` ON `service_rules` (`vehicleId`)",
                "CREATE TABLE IF NOT EXISTS `trip_routes` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `origin` TEXT NOT NUL" +
                    "L, `destination` TEXT NOT NULL, `distanceKm` REAL NOT NULL, `isRoundTrip` INTEGER NOT NULL, `hasTolls` INTEGER" +
                    " NOT NULL, `tollCost` TEXT NOT NULL, `kilometersPerLiter` REAL NOT NULL, `pricePerLiter` TEXT NOT NULL, `liter" +
                    "s` REAL NOT NULL, `fuelCost` TEXT NOT NULL, `costPerKilometer` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `p" +
                    "ath` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`id`))",
                "CREATE TABLE IF NOT EXISTS `expenses` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `category` TEXT NOT NULL" +
                    ", `date` INTEGER NOT NULL, `amount` TEXT NOT NULL, `odometer` REAL, `note` TEXT NOT NULL, `coverageStart` TEXT" +
                    ", `coverageEnd` TEXT, `fineInfractionDate` TEXT, `fineSeverity` TEXT, `finePoints` INTEGER, `fineDueDate` TEXT" +
                    ", `fineDiscount` TEXT, `fineIsPaid` INTEGER, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`veh" +
                    "icleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_expenses_vehicleId` ON `expenses` (`vehicleId`)",
                "CREATE TABLE IF NOT EXISTS `due_items` (`id` TEXT NOT NULL, `vehicleId` TEXT, `kind` TEXT NOT NULL, `dueDate` " +
                    "TEXT NOT NULL, `amount` TEXT, `paidAt` INTEGER, `installmentNumber` INTEGER, `installmentCount` INTEGER, `note" +
                    "` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`vehicleId`) REFERENCES `vehicle" +
                    "s`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_due_items_vehicleId` ON `due_items` (`vehicleId`)",
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '6a6195c2b780fd544045f9a817245300')",
            )
        private const val CREATE_V1_VEHICLES =
            "CREATE TABLE IF NOT EXISTS `vehicles` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                "`plate` TEXT, `initialOdometer` REAL NOT NULL, `tankCapacity` REAL, `createdAt` INTEGER NOT NULL, " +
                "`isArchived` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        private const val CREATE_V2_TRIP_ROUTES =
            "CREATE TABLE IF NOT EXISTS `trip_routes` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `origin` TEXT NOT NULL, " +
                "`destination` TEXT NOT NULL, `distanceKm` REAL NOT NULL, `isRoundTrip` INTEGER NOT NULL, `hasTolls` INTEGER NOT NULL, " +
                "`liters` REAL NOT NULL, `fuelCost` TEXT NOT NULL, `costPerKilometer` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        private const val CREATE_V3_TRIP_ROUTES =
            "CREATE TABLE IF NOT EXISTS `trip_routes` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `origin` TEXT NOT NULL, " +
                "`destination` TEXT NOT NULL, `distanceKm` REAL NOT NULL, `isRoundTrip` INTEGER NOT NULL, `hasTolls` INTEGER NOT NULL, " +
                "`tollCost` TEXT NOT NULL, `liters` REAL NOT NULL, `fuelCost` TEXT NOT NULL, `costPerKilometer` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
    }
}
