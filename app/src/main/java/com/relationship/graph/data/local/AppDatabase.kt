package com.relationship.graph.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.relationship.graph.data.security.SecretStore
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class Converters {
    @TypeConverter
    fun categoryToString(value: RelationCategory): String = value.name

    @TypeConverter
    fun stringToCategory(value: String): RelationCategory = RelationCategory.valueOf(value)

    @TypeConverter
    fun directionToString(value: RelationDirection): String = value.name

    @TypeConverter
    fun stringToDirection(value: String): RelationDirection = RelationDirection.valueOf(value)

    @TypeConverter
    fun graphModeToString(value: GraphMode): String = value.name

    @TypeConverter
    fun stringToGraphMode(value: String): GraphMode = GraphMode.valueOf(value)
}

@Database(
    entities = [
        PersonEntity::class,
        TagEntity::class,
        PersonTagEntity::class,
        RelationTypeEntity::class,
        RelationshipEntity::class,
        GraphPositionEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun relationshipDao(): RelationshipDao

    companion object {
        fun create(context: Context, secretStore: SecretStore): AppDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = secretStore.getOrCreateDatabasePassphrase()
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "relationship-graph.db",
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2)
                .build()
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `graph_positions` (
                        `personId` TEXT NOT NULL,
                        `mode` TEXT NOT NULL,
                        `x` REAL NOT NULL,
                        `y` REAL NOT NULL,
                        `isManuallyPinned` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`personId`, `mode`),
                        FOREIGN KEY(`personId`) REFERENCES `people`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_graph_positions_personId` " +
                        "ON `graph_positions` (`personId`)",
                )
                database.execSQL(
                    """
                    INSERT OR REPLACE INTO `graph_positions`
                        (`personId`, `mode`, `x`, `y`, `isManuallyPinned`, `updatedAt`)
                    SELECT `id`, 'ALL', `graphX`, `graphY`, 0, `updatedAt`
                    FROM `people`
                    """.trimIndent(),
                )
                database.execSQL("ALTER TABLE `people` DROP COLUMN `graphX`")
                database.execSQL("ALTER TABLE `people` DROP COLUMN `graphY`")
                database.execSQL("ALTER TABLE `people` DROP COLUMN `positionInitialized`")
            }
        }
    }
}
