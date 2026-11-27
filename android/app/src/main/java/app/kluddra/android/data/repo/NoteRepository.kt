package app.kluddra.android.data.repo

import androidx.room.withTransaction
import app.kluddra.android.data.local.KluddraDatabase
import app.kluddra.android.data.local.NoteEntity
import app.kluddra.android.data.local.PendingMutationEntity
import app.kluddra.android.data.prefs.KluddraPreferences
import app.kluddra.android.data.remote.ColorPayloadDto
import app.kluddra.android.data.remote.FlagPayloadDto
import app.kluddra.android.data.remote.NoteUpsertPayloadDto
import app.kluddra.android.data.util.UuidV7
import app.kluddra.android.sync.SyncScheduler
import app.kluddra.android.ui.theme.NoteColor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

class NoteRepository(
    private val db: KluddraDatabase,
    private val syncScheduler: SyncScheduler,
    private val preferences: KluddraPreferences
) {
    private val noteDao = db.noteDao()
    private val syncDao = db.syncDao()
    private val json = Json { encodeDefaults = true }

    /**
     * Enqueues all locally created or modified notes into the sync outbox when a user upgrades
     * from Local-only mode to a self-hosted Kluddra server.
     */
    suspend fun enqueueAllLocalForSync() {
        val localNotes = noteDao.getAllNonPurgedNotes().filter { it.isDirty || it.version == 0L }
        if (localNotes.isEmpty()) return
        val now = System.currentTimeMillis()
        db.withTransaction {
            for (note in localNotes) {
                val payload = json.encodeToString(
                    NoteUpsertPayloadDto(
                        title = note.title,
                        body = note.body,
                        color = note.color,
                        isPinned = note.isPinned,
                        isReadOnly = note.isReadOnly,
                        createdAt = note.createdAt,
                        updatedAt = note.updatedAt
                    )
                )
                syncDao.deleteSuperseded("note", note.id, "upsert")
                syncDao.enqueue(
                    PendingMutationEntity(
                        mutationId = UuidV7.generate(),
                        entityType = "note",
                        op = "upsert",
                        entityId = note.id,
                        baseVersion = note.version,
                        payloadJson = payload,
                        createdAt = now
                    )
                )
                if (note.trashedAt != null) {
                    syncDao.enqueue(
                        PendingMutationEntity(
                            mutationId = UuidV7.generate(),
                            entityType = "note",
                            op = "trash",
                            entityId = note.id,
                            baseVersion = note.version,
                            payloadJson = "{}",
                            createdAt = now + 1L
                        )
                    )
                }
            }
        }
    }

    /**
     * Marks all kept local notes dirty when signing out to Local-only mode so they will cleanly
     * sync if the user connects to a server later.
     */
    suspend fun markAllLocalDirty() {
        val kept = noteDao.getAllNonPurgedNotes().filter { !it.isDirty }
        if (kept.isEmpty()) return
        db.withTransaction {
            noteDao.upsertAll(kept.map { it.copy(isDirty = true) })
        }
    }

    fun observeActiveNotes(): Flow<List<NoteEntity>> = noteDao.observeActiveNotes()

    fun searchActiveNotes(rawQuery: String): Flow<List<NoteEntity>> {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) return noteDao.observeActiveNotes()
        val ftsTokens = trimmed
            .split(Regex("\\s+"))
            .map { it.replace(Regex("[^\\p{L}\\p{N}]"), "") }
            .filter { it.isNotEmpty() }
        if (ftsTokens.isEmpty()) return noteDao.searchActiveNotesLike(trimmed)
        val ftsQuery = ftsTokens.joinToString(" ") { "$it*" }
        return noteDao.searchActiveNotesFts(ftsQuery)
    }

    fun observeTrashedNotes(): Flow<List<NoteEntity>> = noteDao.observeTrashedNotes()

    fun observeTrashedCount(): Flow<Int> = noteDao.observeTrashedCount()

    fun observeNote(id: String): Flow<NoteEntity?> = noteDao.observeNoteById(id)

    suspend fun getNote(id: String): NoteEntity? = noteDao.getNoteById(id)

    suspend fun pickSmartNewNoteColor(): String {
        val slots = noteDao.getRecentActiveColorSlots(limit = 8)
        val unpinnedColors = slots
            .filter { !it.isPinned }
            .map { NoteColor.resolveForNote(it.color, it.id).key }
        val neighbors = if (unpinnedColors.isNotEmpty()) {
            unpinnedColors
        } else {
            slots.map { NoteColor.resolveForNote(it.color, it.id).key }
        }
        return NoteColor.pickAvoidingNeighbors(neighbors).key
    }

    /**
     * Saves note locally and enqueues an idempotent sync mutation.
     * - Never persists an empty brand-new note.
     * - Refuses content edits while note is write-protected (isReadOnly == true).
     */
    suspend fun saveNote(
        id: String,
        title: String,
        body: String,
        color: String = "",
        isPinned: Boolean = false
    ): NoteEntity? {
        val existing = noteDao.getNoteById(id)
        if (existing?.isReadOnly == true) {
            return existing
        }

        val isEmpty = title.isBlank() && body.isBlank()
        if (isEmpty && existing == null) {
            return null
        }
        val resolvedColor = color.ifBlank { existing?.color.orEmpty() }
        if (existing != null &&
            existing.title == title &&
            existing.body == body &&
            existing.color == resolvedColor &&
            existing.isPinned == isPinned
        ) {
            return existing
        }

        val now = System.currentTimeMillis()
        val updated = NoteEntity(
            id = id,
            title = title,
            body = body,
            color = resolvedColor,
            isPinned = existing?.isPinned ?: isPinned,
            isReadOnly = existing?.isReadOnly ?: false,
            version = existing?.version ?: 0L,
            serverSeq = existing?.serverSeq ?: 0L,
            conflictOfNoteId = existing?.conflictOfNoteId,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            trashedAt = null,
            purgedAt = null,
            isDirty = true
        )

        val signedIn = preferences.isSignedInOnce()
        val payload = if (signedIn) {
            json.encodeToString(
                NoteUpsertPayloadDto(
                    title = updated.title,
                    body = updated.body,
                    color = updated.color,
                    isPinned = updated.isPinned,
                    isReadOnly = updated.isReadOnly,
                    createdAt = updated.createdAt,
                    updatedAt = updated.updatedAt
                )
            )
        } else {
            ""
        }

        db.withTransaction {
            noteDao.upsert(updated)
            if (signedIn) {
                syncDao.deleteSuperseded("note", id, "upsert")
                syncDao.enqueue(
                    PendingMutationEntity(
                        mutationId = UuidV7.generate(),
                        entityType = "note",
                        op = "upsert",
                        entityId = id,
                        baseVersion = updated.version,
                        payloadJson = payload,
                        createdAt = now
                    )
                )
            }
        }
        if (signedIn) {
            syncScheduler.enqueueImmediateSync(delayMs = 1_200L)
        }
        return updated
    }

    suspend fun setColor(id: String, color: String) {
        val existing = noteDao.getNoteById(id) ?: return
        if (existing.color == color) return
        val now = System.currentTimeMillis()
        val updated = existing.copy(color = color, isDirty = true)
        val signedIn = preferences.isSignedInOnce()

        db.withTransaction {
            noteDao.upsert(updated)
            if (signedIn) {
                val payload = json.encodeToString(ColorPayloadDto(color))
                syncDao.deleteSuperseded("note", id, "set_color")
                syncDao.enqueue(
                    PendingMutationEntity(
                        mutationId = UuidV7.generate(),
                        entityType = "note",
                        op = "set_color",
                        entityId = id,
                        baseVersion = existing.version,
                        payloadJson = payload,
                        createdAt = now
                    )
                )
            }
        }
        if (signedIn) {
            syncScheduler.enqueueImmediateSync(delayMs = 400L)
        }
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        val existing = noteDao.getNoteById(id) ?: return
        if (existing.isPinned == pinned) return
        val now = System.currentTimeMillis()
        val updated = existing.copy(isPinned = pinned, isDirty = true)
        val signedIn = preferences.isSignedInOnce()

        db.withTransaction {
            noteDao.upsert(updated)
            if (signedIn) {
                val payload = json.encodeToString(FlagPayloadDto(pinned))
                syncDao.deleteSuperseded("note", id, "set_pin")
                syncDao.enqueue(
                    PendingMutationEntity(
                        mutationId = UuidV7.generate(),
                        entityType = "note",
                        op = "set_pin",
                        entityId = id,
                        baseVersion = existing.version,
                        payloadJson = payload,
                        createdAt = now
                    )
                )
            }
        }
        if (signedIn) {
            syncScheduler.enqueueImmediateSync(delayMs = 400L)
        }
    }

    suspend fun setReadOnly(id: String, readOnly: Boolean) {
        val existing = noteDao.getNoteById(id) ?: return
        if (existing.isReadOnly == readOnly) return
        val now = System.currentTimeMillis()
        val updated = existing.copy(isReadOnly = readOnly, isDirty = true)
        val signedIn = preferences.isSignedInOnce()

        db.withTransaction {
            noteDao.upsert(updated)
            if (signedIn) {
                val payload = json.encodeToString(FlagPayloadDto(readOnly))
                syncDao.deleteSuperseded("note", id, "set_lock")
                syncDao.enqueue(
                    PendingMutationEntity(
                        mutationId = UuidV7.generate(),
                        entityType = "note",
                        op = "set_lock",
                        entityId = id,
                        baseVersion = existing.version,
                        payloadJson = payload,
                        createdAt = now
                    )
                )
            }
        }
        if (signedIn) {
            syncScheduler.enqueueImmediateSync(delayMs = 400L)
        }
    }

    suspend fun moveToTrash(id: String) {
        val existing = noteDao.getNoteById(id) ?: return
        val now = System.currentTimeMillis()
        val trashed = existing.copy(trashedAt = now, isPinned = false, isDirty = true)
        val signedIn = preferences.isSignedInOnce()

        db.withTransaction {
            noteDao.upsert(trashed)
            if (signedIn) {
                syncDao.enqueue(
                    PendingMutationEntity(
                        mutationId = UuidV7.generate(),
                        entityType = "note",
                        op = "trash",
                        entityId = id,
                        baseVersion = existing.version,
                        payloadJson = "{}",
                        createdAt = now
                    )
                )
            }
        }
        if (signedIn) {
            syncScheduler.enqueueImmediateSync(delayMs = 400L)
        }
    }

    suspend fun restoreFromTrash(id: String) {
        val existing = noteDao.getNoteById(id) ?: return
        val now = System.currentTimeMillis()
        val restored = existing.copy(trashedAt = null, isDirty = true)
        val signedIn = preferences.isSignedInOnce()

        db.withTransaction {
            noteDao.upsert(restored)
            if (signedIn) {
                syncDao.enqueue(
                    PendingMutationEntity(
                        mutationId = UuidV7.generate(),
                        entityType = "note",
                        op = "restore",
                        entityId = id,
                        baseVersion = existing.version,
                        payloadJson = "{}",
                        createdAt = now
                    )
                )
            }
        }
        if (signedIn) {
            syncScheduler.enqueueImmediateSync(delayMs = 400L)
        }
    }

    suspend fun purgeNote(id: String) {
        val existing = noteDao.getNoteById(id) ?: return
        val now = System.currentTimeMillis()
        val purged = existing.copy(
            title = "",
            body = "",
            isPinned = false,
            trashedAt = existing.trashedAt ?: now,
            purgedAt = now,
            isDirty = true
        )
        val signedIn = preferences.isSignedInOnce()

        db.withTransaction {
            noteDao.upsert(purged)
            if (signedIn) {
                syncDao.enqueue(
                    PendingMutationEntity(
                        mutationId = UuidV7.generate(),
                        entityType = "note",
                        op = "purge",
                        entityId = id,
                        baseVersion = existing.version,
                        payloadJson = "{}",
                        createdAt = now
                    )
                )
            }
        }
        if (signedIn) {
            syncScheduler.enqueueImmediateSync(delayMs = 400L)
        }
    }

    suspend fun emptyTrash(trashedNotes: List<NoteEntity>) {
        if (trashedNotes.isEmpty()) return
        val now = System.currentTimeMillis()
        val signedIn = preferences.isSignedInOnce()
        db.withTransaction {
            for (note in trashedNotes) {
                noteDao.upsert(
                    note.copy(
                        title = "",
                        body = "",
                        isPinned = false,
                        purgedAt = now,
                        isDirty = true
                    )
                )
                if (signedIn) {
                    syncDao.enqueue(
                        PendingMutationEntity(
                            mutationId = UuidV7.generate(),
                            entityType = "note",
                            op = "purge",
                            entityId = note.id,
                            baseVersion = note.version,
                            payloadJson = "{}",
                            createdAt = now
                        )
                    )
                }
            }
        }
        if (signedIn) {
            syncScheduler.enqueueImmediateSync(delayMs = 400L)
        }
    }
}
