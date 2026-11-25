package app.kluddra.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NoteEntity::class,
        NoteFtsEntity::class,
        ShoppingItemEntity::class,
        PendingMutationEntity::class,
        SyncMetadataEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class KluddraDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun syncDao(): SyncDao

    companion object {
        const val DB_NAME = "kluddra_local.db"

        fun create(context: Context): KluddraDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                KluddraDatabase::class.java,
                DB_NAME
            ).build()
    }
}
