package app.kluddra.android.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["trashed_at", "purged_at", "is_pinned", "updated_at"])
    ]
)
data class NoteEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val body: String,
    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean = false,
    @ColumnInfo(name = "is_read_only")
    val isReadOnly: Boolean = false,
    val version: Long = 0,
    @ColumnInfo(name = "server_seq")
    val serverSeq: Long = 0,
    @ColumnInfo(name = "conflict_of_note_id")
    val conflictOfNoteId: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "trashed_at")
    val trashedAt: Long? = null,
    @ColumnInfo(name = "purged_at")
    val purgedAt: Long? = null,
    @ColumnInfo(name = "is_dirty")
    val isDirty: Boolean = false
)

@Fts4(contentEntity = NoteEntity::class)
@Entity(tableName = "notes_fts")
data class NoteFtsEntity(
    val title: String,
    val body: String
)

@Entity(
    tableName = "shopping_items",
    indices = [
        Index(value = ["deleted_at", "is_checked", "sort_order"])
    ]
)
data class ShoppingItemEntity(
    @PrimaryKey
    val id: String,
    val text: String,
    @ColumnInfo(name = "is_checked")
    val isChecked: Boolean = false,
    @ColumnInfo(name = "sort_order")
    val sortOrder: String,
    val version: Long = 0,
    @ColumnInfo(name = "server_seq")
    val serverSeq: Long = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
    @ColumnInfo(name = "is_dirty")
    val isDirty: Boolean = false
)

@Entity(
    tableName = "pending_mutations",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["entity_type", "entity_id"])
    ]
)
data class PendingMutationEntity(
    @PrimaryKey
    @ColumnInfo(name = "mutation_id")
    val mutationId: String,
    @ColumnInfo(name = "entity_type")
    val entityType: String, // "note" | "shopping_item"
    val op: String,         // "upsert" | "set_pin" | "set_lock" | "trash" | "restore" | "purge" | "delete"
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "base_version")
    val baseVersion: Long,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "last_pulled_seq")
    val lastPulledSeq: Long = 0,
    @ColumnInfo(name = "last_sync_at")
    val lastSyncAt: Long? = null,
    @ColumnInfo(name = "server_instance_id")
    val serverInstanceId: String? = null
)
