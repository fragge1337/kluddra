package app.kluddra.android.ui.notes

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.kluddra.android.data.repo.NoteRepository
import app.kluddra.android.data.util.UuidV7
import app.kluddra.android.ui.theme.NoteColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class NoteEditorUiState(
    val noteId: String = "",
    val isNewNote: Boolean = true,
    val title: String = "",
    val hasManualTitle: Boolean = false,
    val blocks: List<NoteBlock> = listOf(NoteBlock("b0", NoteBlockType.PARAGRAPH, "")),
    val focusedBlockIndex: Int = 0,
    val focusRequestCounter: Int = 0,
    val colorKey: String = NoteColor.LAVENDER.key,
    val isPinned: Boolean = false,
    val isReadOnly: Boolean = false,
    val isConflictCopy: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isLoaded: Boolean = false
) {
    val serializedBody: String
        get() = NoteFormatting.serializeBlocks(blocks)

    val effectiveTitleForSave: String
        get() = if (hasManualTitle && title.isNotBlank()) {
            title.trim()
        } else {
            NoteFormatting.autoGenerateTitle(serializedBody)
        }
}

class NoteEditorViewModel(
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    private var autoSaveJob: Job? = null
    private var observeJob: Job? = null
    private var nextBlockUid = 1

    private fun newUid(): String = "blk_${nextBlockUid++}"

    fun openNote(noteId: String?, initialTitle: String = "", initialBody: String = "") {
        autoSaveJob?.cancel()
        observeJob?.cancel()

        if (noteId.isNullOrBlank()) {
            val freshId = UuidV7.generate()
            val parsedBlocks = NoteFormatting.parseBlocks(initialBody)
            nextBlockUid = parsedBlocks.size + 1
            val now = System.currentTimeMillis()
            _uiState.value = NoteEditorUiState(
                noteId = freshId,
                isNewNote = true,
                title = initialTitle,
                hasManualTitle = initialTitle.isNotBlank(),
                blocks = parsedBlocks,
                focusedBlockIndex = 0,
                focusRequestCounter = 1,
                colorKey = NoteColor.LAVENDER.key,
                isPinned = false,
                isReadOnly = false,
                isConflictCopy = false,
                createdAt = now,
                updatedAt = now,
                isLoaded = true
            )
            // Assign smart color avoiding immediate neighboring notes in the overview
            viewModelScope.launch {
                val smartColor = noteRepository.pickSmartNewNoteColor()
                _uiState.update { current ->
                    if (current.noteId == freshId) current.copy(colorKey = smartColor) else current
                }
                if (initialTitle.isNotBlank() || initialBody.isNotBlank()) {
                    flushSaveNow()
                }
            }
            return
        }

        _uiState.value = NoteEditorUiState(noteId = noteId, isNewNote = false, isLoaded = false)
        observeJob = viewModelScope.launch {
            noteRepository.observeNote(noteId).collect { entity ->
                if (entity == null) return@collect
                _uiState.update { current ->
                    if (!current.isLoaded) {
                        val parsedBlocks = NoteFormatting.parseBlocks(entity.body)
                        nextBlockUid = parsedBlocks.size + 1
                        val derived = NoteFormatting.autoGenerateTitle(entity.body)
                        val manual = entity.title.isNotBlank() && entity.title != derived
                        current.copy(
                            title = entity.title,
                            hasManualTitle = manual,
                            blocks = parsedBlocks,
                            colorKey = NoteColor.resolveForNote(entity.color, entity.id).key,
                            isPinned = entity.isPinned,
                            isReadOnly = entity.isReadOnly,
                            isConflictCopy = entity.conflictOfNoteId != null,
                            createdAt = entity.createdAt,
                            updatedAt = entity.updatedAt,
                            isLoaded = true
                        )
                    } else {
                        current.copy(
                            colorKey = NoteColor.resolveForNote(entity.color, entity.id).key,
                            isPinned = entity.isPinned,
                            isReadOnly = entity.isReadOnly,
                            isConflictCopy = entity.conflictOfNoteId != null,
                            createdAt = entity.createdAt,
                            updatedAt = entity.updatedAt,
                            title = if (entity.isReadOnly) entity.title else current.title,
                            blocks = if (entity.isReadOnly) NoteFormatting.parseBlocks(entity.body) else current.blocks
                        )
                    }
                }
            }
        }
    }

    fun onTitleChange(newTitle: String) {
        val state = _uiState.value
        if (state.isReadOnly) return
        _uiState.update {
            it.copy(
                title = newTitle,
                hasManualTitle = newTitle.isNotBlank()
            )
        }
        scheduleAutoSave()
    }

    fun onBlockFocused(index: Int) {
        _uiState.update { it.copy(focusedBlockIndex = index.coerceIn(0, (it.blocks.lastIndex).coerceAtLeast(0))) }
    }

    fun onBlockTextChange(index: Int, newText: String) {
        val state = _uiState.value
        if (state.isReadOnly || index !in state.blocks.indices) return

        // Handle Enter key (newline inserted) -> split into next block, auto-continuing lists!
        if (newText.contains('\n')) {
            val parts = newText.split('\n')
            val current = state.blocks[index]
            val mutable = state.blocks.toMutableList()

            // Pressing Enter on an empty list item exits list mode cleanly back to paragraph!
            if (parts.size == 2 && parts[0].isEmpty() && parts[1].isEmpty() && current.type != NoteBlockType.PARAGRAPH) {
                mutable[index] = current.copy(type = NoteBlockType.PARAGRAPH, isChecked = false, text = "")
                _uiState.update {
                    it.copy(blocks = mutable, focusedBlockIndex = index, focusRequestCounter = it.focusRequestCounter + 1)
                }
                scheduleAutoSave()
                return
            }

            mutable[index] = current.copy(text = parts.first())
            val nextType = when (current.type) {
                NoteBlockType.CHECKBOX -> NoteBlockType.CHECKBOX
                NoteBlockType.NUMBERED -> NoteBlockType.NUMBERED
                NoteBlockType.PARAGRAPH -> NoteBlockType.PARAGRAPH
            }
            var insertIdx = index + 1
            for (i in 1 until parts.size) {
                val parsedLine = if (nextType == NoteBlockType.PARAGRAPH) {
                    NoteFormatting.parseBlocks(parts[i]).first().copy(uid = newUid())
                } else {
                    NoteBlock(uid = newUid(), type = nextType, text = parts[i], isChecked = false)
                }
                mutable.add(insertIdx, parsedLine)
                insertIdx++
            }
            val newFocus = (insertIdx - 1).coerceIn(0, mutable.lastIndex)
            _uiState.update {
                it.copy(
                    blocks = mutable,
                    focusedBlockIndex = newFocus,
                    focusRequestCounter = it.focusRequestCounter + 1
                )
            }
            scheduleAutoSave()
            return
        }

        val mutable = state.blocks.toMutableList()
        mutable[index] = mutable[index].copy(text = newText)
        _uiState.update { it.copy(blocks = mutable, focusedBlockIndex = index) }
        scheduleAutoSave()
    }

    fun onBlockBackspaceWhenEmpty(index: Int) {
        val state = _uiState.value
        if (state.isReadOnly || index !in state.blocks.indices) return
        val current = state.blocks[index]
        val mutable = state.blocks.toMutableList()

        // First convert empty checkbox/numbered item back to plain paragraph
        if (current.type != NoteBlockType.PARAGRAPH) {
            mutable[index] = current.copy(type = NoteBlockType.PARAGRAPH, isChecked = false)
            _uiState.update { it.copy(blocks = mutable, focusedBlockIndex = index) }
            scheduleAutoSave()
            return
        }

        // Otherwise remove empty paragraph line and move cursor up to previous line
        if (index > 0) {
            mutable.removeAt(index)
            _uiState.update {
                it.copy(
                    blocks = mutable,
                    focusedBlockIndex = index - 1,
                    focusRequestCounter = it.focusRequestCounter + 1
                )
            }
            scheduleAutoSave()
        }
    }

    fun toggleBlockCheckbox(index: Int) {
        val state = _uiState.value
        if (state.isReadOnly || index !in state.blocks.indices) return
        val target = state.blocks[index]
        if (target.type != NoteBlockType.CHECKBOX) return
        val mutable = state.blocks.toMutableList()
        mutable[index] = target.copy(isChecked = !target.isChecked)
        _uiState.update { it.copy(blocks = mutable) }
        scheduleAutoSave()
    }

    fun toggleListTypeOnFocusedBlock(targetType: NoteBlockType) {
        val state = _uiState.value
        if (state.isReadOnly) return
        val idx = state.focusedBlockIndex.coerceIn(0, state.blocks.lastIndex)
        val current = state.blocks[idx]
        val nextType = if (current.type == targetType) NoteBlockType.PARAGRAPH else targetType
        val mutable = state.blocks.toMutableList()
        mutable[idx] = current.copy(
            type = nextType,
            isChecked = if (nextType == NoteBlockType.CHECKBOX) current.isChecked else false
        )
        _uiState.update {
            it.copy(
                blocks = mutable,
                focusedBlockIndex = idx,
                focusRequestCounter = it.focusRequestCounter + 1
            )
        }
        scheduleAutoSave()
    }

    fun setColor(color: NoteColor) {
        val state = _uiState.value
        if (state.noteId.isBlank()) return
        _uiState.update { it.copy(colorKey = color.key) }
        viewModelScope.launch {
            if (state.title.isNotBlank() || state.serializedBody.isNotBlank()) {
                noteRepository.saveNote(
                    id = state.noteId,
                    title = state.effectiveTitleForSave,
                    body = state.serializedBody,
                    color = color.key,
                    isPinned = state.isPinned
                )
                noteRepository.setColor(state.noteId, color.key)
            }
        }
    }

    fun togglePin() {
        val state = _uiState.value
        if (state.noteId.isBlank()) return
        val nextPinned = !state.isPinned
        _uiState.update { it.copy(isPinned = nextPinned) }
        viewModelScope.launch {
            if (state.title.isNotBlank() || state.serializedBody.isNotBlank()) {
                noteRepository.saveNote(
                    id = state.noteId,
                    title = state.effectiveTitleForSave,
                    body = state.serializedBody,
                    color = state.colorKey,
                    isPinned = nextPinned
                )
                noteRepository.setPinned(state.noteId, nextPinned)
            }
        }
    }

    fun toggleReadOnly() {
        val state = _uiState.value
        if (state.noteId.isBlank()) return
        val nextLocked = !state.isReadOnly
        viewModelScope.launch {
            if (nextLocked && (state.title.isNotBlank() || state.serializedBody.isNotBlank())) {
                noteRepository.saveNote(
                    id = state.noteId,
                    title = state.effectiveTitleForSave,
                    body = state.serializedBody,
                    color = state.colorKey,
                    isPinned = state.isPinned
                )
            }
            _uiState.update { it.copy(isReadOnly = nextLocked) }
            noteRepository.setReadOnly(state.noteId, nextLocked)
        }
    }

    fun moveToTrash(): String? {
        autoSaveJob?.cancel()
        val id = _uiState.value.noteId
        if (id.isBlank()) return null
        viewModelScope.launch { noteRepository.moveToTrash(id) }
        return id
    }

    fun flushSaveNow() {
        autoSaveJob?.cancel()
        val state = _uiState.value
        if (state.noteId.isBlank() || state.isReadOnly) return
        val body = state.serializedBody
        if (state.title.isBlank() && body.isBlank()) return
        val finalTitle = state.effectiveTitleForSave
        viewModelScope.launch {
            noteRepository.saveNote(
                id = state.noteId,
                title = finalTitle,
                body = body,
                color = state.colorKey,
                isPinned = state.isPinned
            )
        }
    }

    fun shareText(): String {
        val state = _uiState.value
        return NoteFormatting.toSharePlainText(state.effectiveTitleForSave, state.serializedBody)
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(450L)
            val state = _uiState.value
            val body = state.serializedBody
            if (!state.isReadOnly && (state.title.isNotBlank() || body.isNotBlank())) {
                val saved = noteRepository.saveNote(
                    id = state.noteId,
                    title = state.effectiveTitleForSave,
                    body = body,
                    color = state.colorKey,
                    isPinned = state.isPinned
                )
                if (saved != null) {
                    _uiState.update { current ->
                        if (current.noteId == saved.id) {
                            current.copy(createdAt = saved.createdAt, updatedAt = saved.updatedAt)
                        } else {
                            current
                        }
                    }
                }
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
