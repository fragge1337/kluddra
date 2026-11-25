package app.kluddra.android.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.kluddra.android.data.repo.NoteRepository
import app.kluddra.android.data.util.UuidV7
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteEditorUiState(
    val noteId: String = "",
    val isNewNote: Boolean = true,
    val title: String = "",
    val body: String = "",
    val isPinned: Boolean = false,
    val isReadOnly: Boolean = false,
    val isConflictCopy: Boolean = false,
    val isLoaded: Boolean = false
)

class NoteEditorViewModel(
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    private var autoSaveJob: Job? = null
    private var observeJob: Job? = null

    fun openNote(noteId: String?, initialTitle: String = "", initialBody: String = "") {
        autoSaveJob?.cancel()
        observeJob?.cancel()

        if (noteId.isNullOrBlank()) {
            val freshId = UuidV7.generate()
            _uiState.value = NoteEditorUiState(
                noteId = freshId,
                isNewNote = true,
                title = initialTitle,
                body = initialBody,
                isPinned = false,
                isReadOnly = false,
                isConflictCopy = false,
                isLoaded = true
            )
            if (initialTitle.isNotBlank() || initialBody.isNotBlank()) {
                flushSaveNow()
            }
            return
        }

        _uiState.value = NoteEditorUiState(noteId = noteId, isNewNote = false, isLoaded = false)
        observeJob = viewModelScope.launch {
            noteRepository.observeNote(noteId).collect { entity ->
                if (entity == null) return@collect
                _uiState.update { current ->
                    if (!current.isLoaded) {
                        current.copy(
                            title = entity.title,
                            body = entity.body,
                            isPinned = entity.isPinned,
                            isReadOnly = entity.isReadOnly,
                            isConflictCopy = entity.conflictOfNoteId != null,
                            isLoaded = true
                        )
                    } else {
                        // Sync metadata updates (lock/pin) without clobbering active typing
                        current.copy(
                            isPinned = entity.isPinned,
                            isReadOnly = entity.isReadOnly,
                            isConflictCopy = entity.conflictOfNoteId != null,
                            title = if (entity.isReadOnly) entity.title else current.title,
                            body = if (entity.isReadOnly) entity.body else current.body
                        )
                    }
                }
            }
        }
    }

    fun onTitleChange(newTitle: String) {
        val state = _uiState.value
        if (state.isReadOnly) return
        _uiState.update { it.copy(title = newTitle) }
        scheduleAutoSave()
    }

    fun onBodyChange(newBody: String) {
        val state = _uiState.value
        if (state.isReadOnly) return
        _uiState.update { it.copy(body = newBody) }
        scheduleAutoSave()
    }

    fun togglePin() {
        val state = _uiState.value
        if (state.noteId.isBlank()) return
        val nextPinned = !state.isPinned
        _uiState.update { it.copy(isPinned = nextPinned) }
        viewModelScope.launch {
            if (state.title.isNotBlank() || state.body.isNotBlank()) {
                noteRepository.saveNote(state.noteId, state.title, state.body, nextPinned)
                noteRepository.setPinned(state.noteId, nextPinned)
            }
        }
    }

    fun toggleReadOnly() {
        val state = _uiState.value
        if (state.noteId.isBlank()) return
        val nextLocked = !state.isReadOnly
        viewModelScope.launch {
            // Ensure current text is persisted before locking
            if (nextLocked && (state.title.isNotBlank() || state.body.isNotBlank())) {
                noteRepository.saveNote(state.noteId, state.title, state.body, state.isPinned)
            }
            _uiState.update { it.copy(isReadOnly = nextLocked) }
            noteRepository.setReadOnly(state.noteId, nextLocked)
        }
    }

    fun moveToTrash(): String? {
        autoSaveJob?.cancel()
        val id = _uiState.value.noteId
        if (id.isBlank()) return null
        viewModelScope.launch {
            noteRepository.moveToTrash(id)
        }
        return id
    }

    fun flushSaveNow() {
        autoSaveJob?.cancel()
        val state = _uiState.value
        if (state.noteId.isBlank() || state.isReadOnly) return
        if (state.title.isBlank() && state.body.isBlank()) return
        viewModelScope.launch {
            noteRepository.saveNote(
                id = state.noteId,
                title = state.title,
                body = state.body,
                isPinned = state.isPinned
            )
        }
    }

    fun shareText(): String {
        val state = _uiState.value
        return buildString {
            if (state.title.isNotBlank()) {
                append(state.title.trim())
                if (state.body.isNotBlank()) append("\n\n")
            }
            append(state.body.trim())
        }
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(450L)
            val state = _uiState.value
            if (!state.isReadOnly && (state.title.isNotBlank() || state.body.isNotBlank())) {
                noteRepository.saveNote(
                    id = state.noteId,
                    title = state.title,
                    body = state.body,
                    isPinned = state.isPinned
                )
            }
        }
    }

    override fun onCleared() {
        flushSaveNow()
        super.onCleared()
    }

    companion object {
        fun factory(noteRepository: NoteRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    NoteEditorViewModel(noteRepository) as T
            }
    }
}
