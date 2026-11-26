package app.kluddra.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NoteEntity::class,
        NoteFtsEntity::class,
        ShoppingItemEntity::class,
        PendingMutationEntity::class,
        SyncMetadataEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class KluddraDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun syncDao(): SyncDao

    companion object {
        const val DB_NAME = "kluddra_local.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN color TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_metadata ADD COLUMN server_version TEXT")
                db.execSQL("ALTER TABLE sync_metadata ADD COLUMN server_api_version TEXT")
            }
        }

        fun create(context: Context): KluddraDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                KluddraDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
