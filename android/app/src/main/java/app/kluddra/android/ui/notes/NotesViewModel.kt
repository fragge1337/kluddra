package app.kluddra.android.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.kluddra.android.data.local.NoteEntity
import app.kluddra.android.data.prefs.KluddraPreferences
import app.kluddra.android.data.prefs.NotesLayoutMode
import app.kluddra.android.data.repo.NoteRepository
import app.kluddra.android.sync.SyncEngine
import app.kluddra.android.sync.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotesOverviewUiState(
    val query: String = "",
    val pinnedNotes: List<NoteEntity> = emptyList(),
    val otherNotes: List<NoteEntity> = emptyList(),
    val layoutMode: NotesLayoutMode = NotesLayoutMode.GRID,
    val syncStatus: SyncStatus = SyncStatus(),
    val pendingCount: Int = 0,
    val isRefreshing: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(
    private val noteRepository: NoteRepository,
    private val preferences: KluddraPreferences,
    private val syncEngine: SyncEngine,
    pendingCountFlow: kotlinx.coroutines.flow.Flow<Int>
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val isRefreshing = MutableStateFlow(false)

    private val notesFlow = searchQuery.flatMapLatest { q ->
        if (q.isBlank()) noteRepository.observeActiveNotes()
        else noteRepository.searchActiveNotes(q)
    }

    val uiState: StateFlow<NotesOverviewUiState> = combine(
        searchQuery,
        notesFlow,
        preferences.settingsFlow.map { it.notesLayoutMode },
        syncEngine.status,
        pendingCountFlow,
        isRefreshing
    ) { values ->
        val q = values[0] as String
        @Suppress("UNCHECKED_CAST")
        val notes = values[1] as List<NoteEntity>
        val layout = values[2] as NotesLayoutMode
        val sync = values[3] as SyncStatus
        val pending = values[4] as Int
        val refreshing = values[5] as Boolean

        val (pinned, others) = notes.partition { it.isPinned }
        NotesOverviewUiState(
            query = q,
            pinnedNotes = pinned,
            otherNotes = others,
            layoutMode = layout,
            syncStatus = sync,
            pendingCount = pending,
            isRefreshing = refreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotesOverviewUiState()
    )

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
