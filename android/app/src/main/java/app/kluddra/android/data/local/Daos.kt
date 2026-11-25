package app.kluddra.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query(
        """
        SELECT * FROM notes
        WHERE trashed_at IS NULL AND purged_at IS NULL
        ORDER BY is_pinned DESC, updated_at DESC
        """
    )
    fun observeActiveNotes(): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT notes.* FROM notes
        JOIN notes_fts ON notes.rowid = notes_fts.rowid
        WHERE notes.trashed_at IS NULL
          AND notes.purged_at IS NULL
          AND notes_fts MATCH :ftsQuery
        ORDER BY notes.is_pinned DESC, notes.updated_at DESC
        """
    )
    fun searchActiveNotesFts(ftsQuery: String): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE trashed_at IS NULL AND purged_at IS NULL
          AND (title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%')
        ORDER BY is_pinned DESC, updated_at DESC
        """
    )
    fun searchActiveNotesLike(query: String): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE trashed_at IS NOT NULL AND purged_at IS NULL
        ORDER BY trashed_at DESC
        """
    )
    fun observeTrashedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT COUNT(*) FROM notes WHERE trashed_at IS NOT NULL AND purged_at IS NULL")
    fun observeTrashedCount(): Flow<Int>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun observeNoteById(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE purged_at IS NULL ORDER BY is_pinned DESC, updated_at DESC")
    suspend fun getAllNonPurgedNotes(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notes: List<NoteEntity>)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun hardDeleteById(id: String)

    @Query("DELETE FROM notes")
    suspend fun clearAll()
}

@Dao
interface ShoppingDao {
    @Query(
        """
        SELECT * FROM shopping_items
        WHERE deleted_at IS NULL
        ORDER BY is_checked ASC, sort_order ASC, id ASC
        """
    )
    fun observeActiveItems(): Flow<List<ShoppingItemEntity>>

    @Query(
        """
        SELECT * FROM shopping_items
        WHERE deleted_at IS NULL
        ORDER BY is_checked ASC, sort_order ASC, id ASC
        """
    )
    suspend fun getActiveItemsOnce(): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ShoppingItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ShoppingItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ShoppingItemEntity>)

    @Query("DELETE FROM shopping_items")
    suspend fun clearAll()
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM pending_mutations ORDER BY created_at ASC LIMIT :limit")
    suspend fun getPendingMutations(limit: Int = 100): List<PendingMutationEntity>

    @Query("SELECT COUNT(*) FROM pending_mutations")
    fun observePendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(mutation: PendingMutationEntity)

    @Query("DELETE FROM pending_mutations WHERE mutation_id IN (:ids)")
    suspend fun deleteMutations(ids: List<String>)

    @Query("DELETE FROM pending_mutations WHERE entity_type = :entityType AND entity_id = :entityId AND op = :op")
    suspend fun deleteSuperseded(entityType: String, entityId: String, op: String)

    @Query("UPDATE pending_mutations SET retry_count = retry_count + 1 WHERE mutation_id IN (:ids)")
    suspend fun incrementRetryCount(ids: List<String>)

    @Query("DELETE FROM pending_mutations")
    suspend fun clearOutbox()

    @Query("SELECT * FROM sync_metadata WHERE id = 1 LIMIT 1")
    suspend fun getMetadata(): SyncMetadataEntity?

    @Query("SELECT * FROM sync_metadata WHERE id = 1 LIMIT 1")
    fun observeMetadata(): Flow<SyncMetadataEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMetadata(metadata: SyncMetadataEntity)

    @Query("DELETE FROM sync_metadata")
    suspend fun clearMetadata()
}
