package app.kluddra.android.data.repo

import androidx.room.withTransaction
import app.kluddra.android.data.local.KluddraDatabase
import app.kluddra.android.data.local.PendingMutationEntity
import app.kluddra.android.data.local.ShoppingItemEntity
import app.kluddra.android.data.remote.ShoppingUpsertPayloadDto
import app.kluddra.android.data.util.FractionalOrder
import app.kluddra.android.data.util.UuidV7
import app.kluddra.android.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

class ShoppingRepository(
    private val db: KluddraDatabase,
    private val syncScheduler: SyncScheduler
) {
    private val shoppingDao = db.shoppingDao()
    private val syncDao = db.syncDao()
    private val json = Json { encodeDefaults = true }

    fun observeItems(): Flow<List<ShoppingItemEntity>> = shoppingDao.observeActiveItems()

    suspend fun addItem(rawText: String): ShoppingItemEntity? {
        val text = rawText.trim()
        if (text.isEmpty()) return null

        val now = System.currentTimeMillis()
        val currentUnchecked = shoppingDao.getActiveItemsOnce().filter { !it.isChecked }
        val sortOrder = FractionalOrder.after(currentUnchecked.lastOrNull()?.sortOrder)

        val entity = ShoppingItemEntity(
            id = UuidV7.generate(),
            text = text,
            isChecked = false,
            sortOrder = sortOrder,
            version = 0L,
            serverSeq = 0L,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            isDirty = true
        )

        persistUpsert(entity)
        return entity
    }

    suspend fun toggleChecked(id: String) {
        val existing = shoppingDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            isChecked = !existing.isChecked,
            updatedAt = now,
            isDirty = true
        )
        persistUpsert(updated)
    }

    suspend fun updateText(id: String, rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty()) return
        val existing = shoppingDao.getById(id) ?: return
        if (existing.text == text) return
        val updated = existing.copy(
            text = text,
            updatedAt = System.currentTimeMillis(),
            isDirty = true
        )
        persistUpsert(updated)
    }

    suspend fun moveItemBetween(id: String, beforeSortOrder: String?, afterSortOrder: String?) {
        val existing = shoppingDao.getById(id) ?: return
        val newOrder = FractionalOrder.between(beforeSortOrder, afterSortOrder)
        val updated = existing.copy(
            sortOrder = newOrder,
            updatedAt = System.currentTimeMillis(),
            isDirty = true
        )
        persistUpsert(updated)
    }

    suspend fun deleteItem(id: String): ShoppingItemEntity? {
        val existing = shoppingDao.getById(id) ?: return null
        deleteItems(listOf(existing))
        return existing
    }

    suspend fun clearChecked(): List<ShoppingItemEntity> {
        val checked = shoppingDao.getActiveItemsOnce().filter { it.isChecked }
        if (checked.isNotEmpty()) {
            deleteItems(checked)
        }
        return checked
    }

    suspend fun clearAll(): List<ShoppingItemEntity> {
        val all = shoppingDao.getActiveItemsOnce()
        if (all.isNotEmpty()) {
            deleteItems(all)
        }
        return all
    }

    suspend fun restoreItems(items: List<ShoppingItemEntity>) {
        if (items.isEmpty()) return
        val now = System.currentTimeMillis()
        db.withTransaction {
            for (item in items) {
                val restored = item.copy(deletedAt = null, updatedAt = now, isDirty = true)
                shoppingDao.upsert(restored)
                enqueueUpsertTx(restored, now)
            }
        }
        syncScheduler.enqueueImmediateSync(delayMs = 400L)
    }

    private suspend fun deleteItems(items: List<ShoppingItemEntity>) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            for (item in items) {
                shoppingDao.upsert(item.copy(deletedAt = now, updatedAt = now, isDirty = true))
                syncDao.enqueue(
                    PendingMutationEntity(
                        mutationId = UuidV7.generate(),
                        entityType = "shopping_item",
                        op = "delete",
                        entityId = item.id,
                        baseVersion = item.version,
                        payloadJson = "{}",
                        createdAt = now
                    )
                )
            }
        }
        syncScheduler.enqueueImmediateSync(delayMs = 400L)
    }

    private suspend fun persistUpsert(entity: ShoppingItemEntity) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            shoppingDao.upsert(entity)
            enqueueUpsertTx(entity, now)
        }
        syncScheduler.enqueueImmediateSync(delayMs = 400L)
    }

    private suspend fun enqueueUpsertTx(entity: ShoppingItemEntity, now: Long) {
        val payload = json.encodeToString(
            ShoppingUpsertPayloadDto(
                text = entity.text,
                isChecked = entity.isChecked,
                sortOrder = entity.sortOrder,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
        )
        syncDao.deleteSuperseded("shopping_item", entity.id, "upsert")
        syncDao.enqueue(
            PendingMutationEntity(
                mutationId = UuidV7.generate(),
                entityType = "shopping_item",
                op = "upsert",
                entityId = entity.id,
                baseVersion = entity.version,
                payloadJson = payload,
                createdAt = now
            )
        )
    }
}
