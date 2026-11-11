package br.com.joaoduartemariucio.rodaro.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import br.com.joaoduartemariucio.rodaro.data.backup.models.BackupDao
import br.com.joaoduartemariucio.rodaro.data.costs.models.ExpenseDao
import br.com.joaoduartemariucio.rodaro.data.costs.models.ExpenseModel
import br.com.joaoduartemariucio.rodaro.data.dues.models.DueItemDao
import br.com.joaoduartemariucio.rodaro.data.dues.models.DueItemModel
import br.com.joaoduartemariucio.rodaro.data.fueling.models.FuelEntryDao
import br.com.joaoduartemariucio.rodaro.data.fueling.models.FuelEntryModel
import br.com.joaoduartemariucio.rodaro.data.maintenance.models.ServiceDao
import br.com.joaoduartemariucio.rodaro.data.maintenance.models.ServiceEntryModel
import br.com.joaoduartemariucio.rodaro.data.maintenance.models.ServiceRuleModel
import br.com.joaoduartemariucio.rodaro.data.trips.models.TripRouteDao
import br.com.joaoduartemariucio.rodaro.data.trips.models.TripRouteModel
import br.com.joaoduartemariucio.rodaro.data.valuation.models.FipeQuoteDao
import br.com.joaoduartemariucio.rodaro.data.valuation.models.FipeQuoteModel
import br.com.joaoduartemariucio.rodaro.data.vehicles.models.VehicleDao
import br.com.joaoduartemariucio.rodaro.data.vehicles.models.VehicleModel

@Database(
    entities = [
        VehicleModel::class,
        FuelEntryModel::class,
        ServiceEntryModel::class,
        ServiceRuleModel::class,
        TripRouteModel::class,
        ExpenseModel::class,
        DueItemModel::class,
        FipeQuoteModel::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RodaroDatabase : RoomDatabase() {
    abstract fun vehicles(): VehicleDao

    abstract fun fuelEntries(): FuelEntryDao

    abstract fun services(): ServiceDao

    abstract fun tripRoutes(): TripRouteDao

    abstract fun backup(): BackupDao

    abstract fun expenses(): ExpenseDao

    abstract fun dueItems(): DueItemDao

    abstract fun fipeQuotes(): FipeQuoteDao

    companion object {
        private const val FILE_NAME = "rodaro.db"

        internal val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `trip_routes` (
                            `id` TEXT NOT NULL,
                            `vehicleId` TEXT NOT NULL,
                            `origin` TEXT NOT NULL,
                            `destination` TEXT NOT NULL,
                            `distanceKm` REAL NOT NULL,
                            `isRoundTrip` INTEGER NOT NULL,
                            `hasTolls` INTEGER NOT NULL,
                            `liters` REAL NOT NULL,
                            `fuelCost` TEXT NOT NULL,
                            `costPerKilometer` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent(),
                    )
                }
            }

        internal val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `trip_routes` ADD COLUMN `tollCost` TEXT NOT NULL DEFAULT '0'")
                }
            }

        internal val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `trip_routes` ADD COLUMN `kilometersPerLiter` REAL NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `trip_routes` ADD COLUMN `pricePerLiter` TEXT NOT NULL DEFAULT '0'")
                }
            }

        internal val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `trip_routes` ADD COLUMN `path` TEXT NOT NULL DEFAULT ''")
                }
            }

        internal val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `fuelCapability` TEXT NOT NULL DEFAULT 'flex'")
                    db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `uf` TEXT")
                    db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `modelYear` INTEGER")
                    db.execSQL("ALTER TABLE `fuel_entries` ADD COLUMN `fuelType` TEXT")
                    db.execSQL("ALTER TABLE `fuel_entries` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("UPDATE `fuel_entries` SET `createdAt` = `date`")
                    db.execSQL("ALTER TABLE `service_entries` ADD COLUMN `workshop` TEXT")
                    db.execSQL("ALTER TABLE `service_entries` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("UPDATE `service_entries` SET `createdAt` = `date`")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `expenses` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, " +
                            "`category` TEXT NOT NULL, `date` INTEGER NOT NULL, `amount` TEXT NOT NULL, `odometer` REAL, " +
                            "`note` TEXT NOT NULL, `coverageStart` TEXT, `coverageEnd` TEXT, `fineInfractionDate` TEXT, " +
                            "`fineSeverity` TEXT, `finePoints` INTEGER, `fineDueDate` TEXT, `fineDiscount` TEXT, " +
                            "`fineIsPaid` INTEGER, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                            "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_vehicleId` ON `expenses` (`vehicleId`)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `due_items` (`id` TEXT NOT NULL, `vehicleId` TEXT, `kind` TEXT NOT NULL, " +
                            "`dueDate` TEXT NOT NULL, `amount` TEXT, `paidAt` INTEGER, `installmentNumber` INTEGER, " +
                            "`installmentCount` INTEGER, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`), " +
                            "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_due_items_vehicleId` ON `due_items` (`vehicleId`)")
                }
            }

        internal val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `fipeCode` TEXT")
                    db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `fipeYearCode` TEXT")
                    db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `fipeBrandName` TEXT")
                    db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `fipeModelName` TEXT")
                    db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `fipeYearName` TEXT")
                    db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `purchaseDate` TEXT")
                    db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `purchasePrice` TEXT")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `fipe_quotes` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, " +
                            "`fipeCode` TEXT NOT NULL, `yearCode` TEXT NOT NULL, `referenceMonth` TEXT NOT NULL, " +
                            "`price` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                            "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_fipe_quotes_vehicleId` ON `fipe_quotes` (`vehicleId`)")
                }
            }

        fun create(
            context: Context,
            inMemory: Boolean = false,
        ): RodaroDatabase {
            val builder =
                if (inMemory) {
                    Room.inMemoryDatabaseBuilder(context, RodaroDatabase::class.java)
                } else {
                    Room.databaseBuilder(context, RodaroDatabase::class.java, FILE_NAME)
                }
            return builder
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
        }
    }
}
