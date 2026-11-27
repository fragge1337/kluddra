package app.kluddra.android.ui.notes

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.kluddra.android.data.local.NoteEntity
import app.kluddra.android.data.prefs.KluddraPreferences
import app.kluddra.android.data.prefs.NotesLayoutMode
import app.kluddra.android.data.prefs.NotesSortOrder
import app.kluddra.android.data.repo.NoteRepository
import app.kluddra.android.sync.SyncEngine
import app.kluddra.android.sync.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class NotesOverviewUiState(
    val query: String = "",
    val pinnedNotes: List<NoteEntity> = emptyList(),
    val otherNotes: List<NoteEntity> = emptyList(),
    val layoutMode: NotesLayoutMode = NotesLayoutMode.GRID,
    val sortOrder: NotesSortOrder = NotesSortOrder.UPDATED_DESC,
    val syncStatus: SyncStatus = SyncStatus(),
    val pendingCount: Int = 0,
    val isRefreshing: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class NotesViewModel(
    private val noteRepository: NoteRepository,
    private val preferences: KluddraPreferences,
    private val syncEngine: SyncEngine,
    pendingCountFlow: kotlinx.coroutines.flow.Flow<Int>
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val isRefreshing = MutableStateFlow(false)

    private val sortOrderFlow = preferences.settingsFlow
        .map { it.notesSortOrder }
        .distinctUntilChanged()

    private val layoutModeFlow = preferences.settingsFlow
        .map { it.notesLayoutMode }
        .distinctUntilChanged()

    // Partition & sort pinned vs other notes off the main thread and debounce non-empty FTS queries
    // so rapid typing and background sync ticks never stall UI frames or re-allocate note lists.
    private val partitionedNotes = combine(
        searchQuery
            .debounce { q -> if (q.isBlank()) 0L else 120L }
            .distinctUntilChanged()
            .flatMapLatest { q ->
                if (q.isBlank()) noteRepository.observeActiveNotes()
                else noteRepository.searchActiveNotes(q)
            }
            .distinctUntilChanged(),
        sortOrderFlow
    ) { notes, sortOrder ->
        val (pinned, others) = notes.partition { it.isPinned }
        sortNotes(pinned, sortOrder) to sortNotes(others, sortOrder)
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<NotesOverviewUiState> = combine(
        searchQuery,
        partitionedNotes,
        layoutModeFlow,
        sortOrderFlow,
        syncEngine.observedStatus,
        pendingCountFlow.distinctUntilChanged(),
        isRefreshing
    ) { values ->
        val q = values[0] as String
        @Suppress("UNCHECKED_CAST")
        val partitioned = values[1] as Pair<List<NoteEntity>, List<NoteEntity>>
        val pinned = partitioned.first
        val others = partitioned.second
        val layout = values[2] as NotesLayoutMode
        val sort = values[3] as NotesSortOrder
        val sync = values[4] as SyncStatus
        val pending = values[5] as Int
        val refreshing = values[6] as Boolean

        NotesOverviewUiState(
            query = q,
            pinnedNotes = pinned,
            otherNotes = others,
            layoutMode = layout,
            sortOrder = sort,
            syncStatus = sync,
            pendingCount = pending,
            isRefreshing = refreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotesOverviewUiState()
    )

    private fun sortNotes(notes: List<NoteEntity>, order: NotesSortOrder): List<NoteEntity> {
        if (notes.size <= 1) return notes
        return when (order) {
            NotesSortOrder.UPDATED_DESC -> notes.sortedByDescending { it.updatedAt }
            NotesSortOrder.CREATED_DESC -> notes.sortedByDescending { it.createdAt }
            NotesSortOrder.CREATED_ASC -> notes.sortedBy { it.createdAt }
            NotesSortOrder.TITLE_ASC -> notes.sortedBy { note ->
                note.title.ifBlank { NoteFormatting.autoGenerateTitle(note.body) }.lowercase()
            }
        }
    }

    fun setSortOrder(order: NotesSortOrder) {
        viewModelScope.launch {
            preferences.setNotesSortOrder(order)
        }
    }

    val trashedNotes: StateFlow<List<NoteEntity>> = noteRepository.observeTrashedNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(newQuery: String) {
        searchQuery.value = newQuery
    }

    fun toggleLayoutMode() {
        viewModelScope.launch {
            val next = if (uiState.value.layoutMode == NotesLayoutMode.GRID) {
                NotesLayoutMode.LIST
            } else {
                NotesLayoutMode.GRID
            }
            preferences.setNotesLayoutMode(next)
        }
    }

    fun togglePin(note: NoteEntity) {
        viewModelScope.launch {
            noteRepository.setPinned(note.id, !note.isPinned)
        }
    }

    fun toggleReadOnly(note: NoteEntity) {
        viewModelScope.launch {
            noteRepository.setReadOnly(note.id, !note.isReadOnly)
        }
    }

    fun moveToTrash(noteId: String) {
        viewModelScope.launch {
            noteRepository.moveToTrash(noteId)
        }
    }

    fun restoreFromTrash(noteId: String) {
        viewModelScope.launch {
            noteRepository.restoreFromTrash(noteId)
        }
    }

    fun purgeNote(noteId: String) {
        viewModelScope.launch {
            noteRepository.purgeNote(noteId)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            noteRepository.emptyTrash(trashedNotes.value)
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
            noteRepository: NoteRepository,
            preferences: KluddraPreferences,
            syncEngine: SyncEngine,
            pendingCountFlow: kotlinx.coroutines.flow.Flow<Int>
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NotesViewModel(noteRepository, preferences, syncEngine, pendingCountFlow) as T
        }
    }
}
