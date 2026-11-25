package app.kluddra.android.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.kluddra.android.data.local.ShoppingItemEntity
import app.kluddra.android.data.repo.ShoppingRepository
import app.kluddra.android.sync.SyncEngine
import app.kluddra.android.sync.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShoppingListUiState(
    val activeItems: List<ShoppingItemEntity> = emptyList(),
    val checkedItems: List<ShoppingItemEntity> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus(),
    val pendingCount: Int = 0,
    val isRefreshing: Boolean = false
)

class ShoppingViewModel(
    private val repository: ShoppingRepository,
    private val syncEngine: SyncEngine,
    pendingCountFlow: Flow<Int>
) : ViewModel() {

    private val isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<ShoppingListUiState> = combine(
        repository.observeItems(),
        syncEngine.status,
        pendingCountFlow,
        isRefreshing
    ) { items, sync, pending, refreshing ->
        val (checked, active) = items.partition { it.isChecked }
        ShoppingListUiState(
            activeItems = active,
            checkedItems = checked,
            syncStatus = sync,
            pendingCount = pending,
            isRefreshing = refreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ShoppingListUiState()
    )

    fun addItem(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.addItem(text)
        }
    }

    fun toggleChecked(id: String) {
        viewModelScope.launch {
            repository.toggleChecked(id)
        }
    }

    fun updateItemText(id: String, newText: String) {
        if (newText.isBlank()) return
        viewModelScope.launch {
            repository.updateText(id, newText)
        }
    }

    fun reorderActive(orderedActive: List<ShoppingItemEntity>, movedItemId: String) {
        val index = orderedActive.indexOfFirst { it.id == movedItemId }
        if (index == -1) return
        val before = orderedActive.getOrNull(index - 1)?.sortOrder
        val after = orderedActive.getOrNull(index + 1)?.sortOrder
        viewModelScope.launch {
            repository.moveItemBetween(movedItemId, before, after)
        }
    }

    fun deleteItem(id: String, onDeleted: (ShoppingItemEntity) -> Unit) {
        viewModelScope.launch {
            val removed = repository.deleteItem(id)
            if (removed != null) onDeleted(removed)
        }
    }

    fun clearChecked(onCleared: (List<ShoppingItemEntity>) -> Unit) {
        viewModelScope.launch {
            val removed = repository.clearChecked()
            if (removed.isNotEmpty()) onCleared(removed)
        }
    }

    fun clearAll(onCleared: (List<ShoppingItemEntity>) -> Unit) {
        viewModelScope.launch {
            val removed = repository.clearAll()
            if (removed.isNotEmpty()) onCleared(removed)
        }
    }

    fun restoreItems(items: List<ShoppingItemEntity>) {
        viewModelScope.launch {
            repository.restoreItems(items)
        }
    }

    fun refreshSync() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                syncEngine.syncNow()
            } finally {
                isRefreshing.value = false
            }
        }
    }

    companion object {
        fun factory(
            repository: ShoppingRepository,
            syncEngine: SyncEngine,
            pendingCountFlow: Flow<Int>
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShoppingViewModel(repository, syncEngine, pendingCountFlow) as T
        }
    }
}
