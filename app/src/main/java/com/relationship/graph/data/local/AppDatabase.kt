package com.relationship.graph.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
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
}

@Database(
    entities = [
        PersonEntity::class,
        TagEntity::class,
        PersonTagEntity::class,
        RelationTypeEntity::class,
        RelationshipEntity::class,
    ],
    version = 1,
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
                .build()
        }
    }
}
