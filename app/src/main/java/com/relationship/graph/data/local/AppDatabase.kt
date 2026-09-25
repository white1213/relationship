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

    @TypeConverter
    fun genderToString(value: Gender): String = value.name

    @TypeConverter
    fun stringToGender(value: String): Gender = Gender.valueOf(value)

    @TypeConverter
    fun relationshipSourceToString(value: RelationshipSource): String = value.name

    @TypeConverter
    fun stringToRelationshipSource(value: String): RelationshipSource =
        RelationshipSource.valueOf(value)

    @TypeConverter
    fun marriageKinshipModeToString(value: MarriageKinshipMode): String = value.name

    @TypeConverter
    fun stringToMarriageKinshipMode(value: String): MarriageKinshipMode =
        MarriageKinshipMode.valueOf(value)

    @TypeConverter
    fun ageComparisonToString(value: AgeComparison): String = value.name

    @TypeConverter
    fun stringToAgeComparison(value: String): AgeComparison = AgeComparison.valueOf(value)
}

@Database(
    entities = [
        PersonEntity::class,
        TagEntity::class,
        PersonTagEntity::class,
        RelationTypeEntity::class,
        RelationshipEntity::class,
        GraphPositionEntity::class,
        InferenceDismissalEntity::class,
        RelativeAgeOrderEntity::class,
    ],
    version = 5,
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `people` ADD COLUMN `gender` TEXT NOT NULL " +
                        "DEFAULT 'UNSPECIFIED'",
                )
                database.execSQL(
                    "ALTER TABLE `relation_types` ADD COLUMN `isInferenceOnly` " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE `relationships` ADD COLUMN `source` TEXT NOT NULL " +
                        "DEFAULT 'MANUAL'",
                )
                database.execSQL(
                    "ALTER TABLE `relationships` ADD COLUMN `labelOverride` TEXT",
                )
                database.execSQL(
                    "ALTER TABLE `relationships` ADD COLUMN `inverseLabelOverride` TEXT",
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `inference_dismissals` (
                        `fromPersonId` TEXT NOT NULL,
                        `toPersonId` TEXT NOT NULL,
                        `ruleId` TEXT NOT NULL,
                        `evidenceFingerprint` TEXT NOT NULL,
                        `dismissedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`fromPersonId`, `toPersonId`, `ruleId`),
                        FOREIGN KEY(`fromPersonId`) REFERENCES `people`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`toPersonId`) REFERENCES `people`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_inference_dismissals_fromPersonId` " +
                        "ON `inference_dismissals` (`fromPersonId`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_inference_dismissals_toPersonId` " +
                        "ON `inference_dismissals` (`toPersonId`)",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `relative_age_orders` (
                        `firstPersonId` TEXT NOT NULL,
                        `secondPersonId` TEXT NOT NULL,
                        `comparison` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`firstPersonId`, `secondPersonId`),
                        FOREIGN KEY(`firstPersonId`) REFERENCES `people`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`secondPersonId`) REFERENCES `people`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_relative_age_orders_firstPersonId` " +
                        "ON `relative_age_orders` (`firstPersonId`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_relative_age_orders_secondPersonId` " +
                        "ON `relative_age_orders` (`secondPersonId`)",
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `relationships` ADD COLUMN `marriageKinshipMode` " +
                        "TEXT NOT NULL DEFAULT 'RESPECTIVE'",
                )
                database.execSQL(
                    """
                    UPDATE `relationships`
                    SET `marriageKinshipMode` = 'FOLLOW_HUSBAND'
                    WHERE `relationTypeId` = 'preset_spouse'
                      AND EXISTS (
                          SELECT 1 FROM `people` AS `firstPerson`
                          INNER JOIN `people` AS `secondPerson`
                              ON `secondPerson`.`id` = `relationships`.`toPersonId`
                          WHERE `firstPerson`.`id` = `relationships`.`fromPersonId`
                            AND (
                                (`firstPerson`.`gender` = 'MALE'
                                    AND `secondPerson`.`gender` = 'FEMALE')
                                OR
                                (`firstPerson`.`gender` = 'FEMALE'
                                    AND `secondPerson`.`gender` = 'MALE')
                            )
                      )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    UPDATE `relationships`
                    SET `labelOverride` = NULL,
                        `inverseLabelOverride` = NULL
                    WHERE `source` = 'CONFIRMED_INFERENCE'
                      AND `relationTypeId` = 'preset_sibling_in_law'
                      AND (
                          `labelOverride` LIKE '%配偶的%'
                          OR `inverseLabelOverride` LIKE '%配偶的%'
                      )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    UPDATE `relation_types`
                    SET `name` = '姻亲同辈', `inverseName` = NULL
                    WHERE `id` = 'preset_sibling_in_law'
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    UPDATE `relation_types`
                    SET `inverseName` = '侄子/侄女'
                    WHERE `id` IN ('preset_aunt', 'preset_aunt_husband')
                    """.trimIndent(),
                )
            }
        }
    }
}
