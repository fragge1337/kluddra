package app.kluddra.android.sync

import androidx.compose.runtime.Immutable
import androidx.room.withTransaction
import app.kluddra.android.data.local.KluddraDatabase
import app.kluddra.android.data.local.NoteEntity
import app.kluddra.android.data.local.ShoppingItemEntity
import app.kluddra.android.data.local.SyncMetadataEntity
import app.kluddra.android.data.prefs.KluddraPreferences
import app.kluddra.android.data.remote.ApiException
import app.kluddra.android.data.remote.AuthExpiredException
import app.kluddra.android.data.remote.KluddraApiClient
import app.kluddra.android.data.remote.NoteDto
import app.kluddra.android.data.remote.ShoppingItemDto
import app.kluddra.android.data.remote.SyncMutationDto
import app.kluddra.android.data.remote.SyncPushRequestDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

enum class SyncStateKind {
    IDLE,
    SYNCING,
    OFFLINE,
    ERROR,
    AUTH_EXPIRED
}

@Immutable
data class SyncStatus(
    val kind: SyncStateKind = SyncStateKind.IDLE,
    val lastSyncAt: Long? = null,
    val serverVersion: String? = null,
    val isServerOutdated: Boolean = false,
    val minRecommendedServerVersion: String = ServerVersionPolicy.MIN_RECOMMENDED_SERVER_VERSION,
    val message: String? = null
)

class SyncEngine(
    private val db: KluddraDatabase,
    private val api: KluddraApiClient,
    private val preferences: KluddraPreferences
) {
    private val syncMutex = Mutex()
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    fun resetStatus() {
        _status.value = SyncStatus(SyncStateKind.IDLE)
    }

    /**
     * Live sync state enriched with persisted lastSyncAt and server version compatibility
     * from Room (immediately available on app startup, even while offline).
     */
    val observedStatus: Flow<SyncStatus> = combine(_status, db.syncDao().observeMetadata()) { live, meta ->
        val resolvedVersion = live.serverVersion ?: meta?.serverVersion
        val check = ServerVersionPolicy.evaluate(resolvedVersion, meta?.serverApiVersion)
        live.copy(
            lastSyncAt = live.lastSyncAt ?: meta?.lastSyncAt,
            serverVersion = check.serverVersion,
            isServerOutdated = check.isOutdated,
            minRecommendedServerVersion = check.minRecommendedVersion
        )
    }.distinctUntilChanged()

    /**
     * Executes an atomic Push (outbox) followed by paginated Pull (delta by server_seq).
     * Safe to call concurrently or interrupt at any point; all mutations are idempotent.
     */
    suspend fun syncNow(): Boolean = syncMutex.withLock {
        // Local-only mode: no server configured, stay completely quiet and offline.
        if (preferences.getCredentials() == null) {
            _status.value = SyncStatus(SyncStateKind.IDLE)
            return true
        }

        val syncDao = db.syncDao()
        val prevMeta = syncDao.getMetadata()
        _status.value = SyncStatus(SyncStateKind.SYNCING, lastSyncAt = prevMeta?.lastSyncAt)

        return try {
            pushPendingMutations()
            pullDeltaChanges()

            val now = System.currentTimeMillis()
            val updatedMeta = (syncDao.getMetadata() ?: SyncMetadataEntity()).copy(lastSyncAt = now)
            syncDao.saveMetadata(updatedMeta)
            _status.value = SyncStatus(SyncStateKind.IDLE, lastSyncAt = now)
            true
        } catch (e: AuthExpiredException) {
            _status.value = SyncStatus(
                kind = SyncStateKind.AUTH_EXPIRED,
                lastSyncAt = prevMeta?.lastSyncAt,
                message = e.message
            )
            false
        } catch (e: IOException) {
            _status.value = SyncStatus(
                kind = SyncStateKind.OFFLINE,
                lastSyncAt = prevMeta?.lastSyncAt,
                message = e.message
            )
            false
        } catch (e: ApiException) {
            _status.value = SyncStatus(
                kind = if (e.statusCode == 401) SyncStateKind.AUTH_EXPIRED else SyncStateKind.ERROR,
                lastSyncAt = prevMeta?.lastSyncAt,
                message = e.message
            )
            false
        } catch (e: Exception) {
            _status.value = SyncStatus(
                kind = SyncStateKind.OFFLINE,
                lastSyncAt = prevMeta?.lastSyncAt,
                message = e.message
            )
            false
        }
    }

    private suspend fun pushPendingMutations() {
        val syncDao = db.syncDao()
        val noteDao = db.noteDao()
        val shoppingDao = db.shoppingDao()

        while (true) {
            val batch = syncDao.getPendingMutations(limit = 100)
            if (batch.isEmpty()) break

            val dtoMutations = batch.map { mut ->
                SyncMutationDto(
                    mutationId = mut.mutationId,
                    entity = mut.entityType,
                    op = mut.op,
                    entityId = mut.entityId,
                    baseVersion = mut.baseVersion,
                    payload = api.json.parseToJsonElement(mut.payloadJson)
                )
            }

            val response = api.syncPush(
                SyncPushRequestDto(
                    clientDevice = api.deviceName(),
                    mutations = dtoMutations
                )
            )

            db.withTransaction {
                val handledIds = mutableListOf<String>()
                for (res in response.results) {
                    handledIds.add(res.mutationId)
                    res.note?.let { noteDao.upsert(it.toEntity()) }
                    res.conflictCopy?.let { noteDao.upsert(it.toEntity()) }
                    res.shoppingItem?.let { shoppingDao.upsert(it.toEntity()) }
                }
                if (handledIds.isNotEmpty()) {
                    syncDao.deleteMutations(handledIds)
                }
            }
        }
    }

    private suspend fun pullDeltaChanges() {
        val syncDao = db.syncDao()
        val noteDao = db.noteDao()
        val shoppingDao = db.shoppingDao()

        var meta = syncDao.getMetadata() ?: SyncMetadataEntity()
        var sinceSeq = meta.lastPulledSeq

        while (true) {
            val pull = api.syncPull(sinceSeq = sinceSeq, limit = 200)

            // Detect restored server backup or replaced server instance -> full safe re-pull from 0.
            val instanceChanged = meta.serverInstanceId != null && meta.serverInstanceId != pull.instanceId
            val seqRegressed = pull.currentSeq < sinceSeq
            if ((instanceChanged || seqRegressed) && sinceSeq > 0L) {
                sinceSeq = 0L
                meta = meta.copy(lastPulledSeq = 0L, serverInstanceId = pull.instanceId)
                syncDao.saveMetadata(meta)
                continue
            }

            var maxSeenSeq = sinceSeq
            db.withTransaction {
                val pendingNoteIds = syncDao.getPendingEntityIds("note").toSet()
                val pendingShoppingIds = syncDao.getPendingEntityIds("shopping_item").toSet()

                if (pull.notes.isNotEmpty()) {
                    val entities = pull.notes.mapNotNull { dto ->
                        if (dto.serverSeq > maxSeenSeq) maxSeenSeq = dto.serverSeq
                        if (dto.id in pendingNoteIds) null else dto.toEntity()
                    }
                    if (entities.isNotEmpty()) noteDao.upsertAll(entities)
                }
                if (pull.shoppingItems.isNotEmpty()) {
                    val entities = pull.shoppingItems.mapNotNull { dto ->
                        if (dto.serverSeq > maxSeenSeq) maxSeenSeq = dto.serverSeq
                        if (dto.id in pendingShoppingIds) null else dto.toEntity()
                    }
                    if (entities.isNotEmpty()) shoppingDao.upsertAll(entities)
                }

                val nextSeq = if (pull.hasMore) maxSeenSeq else maxOf(maxSeenSeq, pull.currentSeq)
                meta = meta.copy(
                    lastPulledSeq = nextSeq,
                    serverInstanceId = pull.instanceId,
                    serverVersion = pull.serverVersion?.takeIf { it.isNotBlank() } ?: meta.serverVersion,
                    serverApiVersion = pull.apiVersion?.takeIf { it.isNotBlank() } ?: meta.serverApiVersion
                )
                syncDao.saveMetadata(meta)
                sinceSeq = nextSeq
            }

            if (!pull.hasMore) break
        }

        // Fallback for older servers that don't yet include server_version in /api/v1/sync
        if (meta.serverVersion.isNullOrBlank()) {
            val info = api.fetchCurrentServerInfo()
            if (info != null) {
                meta = meta.copy(
                    serverVersion = info.serverVersion,
                    serverApiVersion = info.apiVersion
                )
                syncDao.saveMetadata(meta)
            }
        }
    }

    private fun NoteDto.toEntity(): NoteEntity = NoteEntity(
        id = id,
        title = title,
        body = body,
        color = color,
        isPinned = isPinned,
        isReadOnly = isReadOnly,
        version = version,
        serverSeq = serverSeq,
        conflictOfNoteId = conflictOfNoteId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        trashedAt = trashedAt,
        purgedAt = purgedAt,
        isDirty = false
    )

    private fun ShoppingItemDto.toEntity(): ShoppingItemEntity = ShoppingItemEntity(
        id = id,
        text = text,
        isChecked = isChecked,
        sortOrder = sortOrder,
        version = version,
        serverSeq = serverSeq,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        isDirty = false
    )
}
