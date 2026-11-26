package app.kluddra.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.kluddra.android.data.export.LocalExporter
import app.kluddra.android.data.prefs.KluddraPreferences
import app.kluddra.android.data.prefs.ThemeMode
import app.kluddra.android.data.prefs.UserSettings
import app.kluddra.android.data.remote.SessionInfoDto
import app.kluddra.android.data.repo.AuthRepository
import app.kluddra.android.data.repo.NoteRepository
import app.kluddra.android.sync.SyncEngine
import app.kluddra.android.sync.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.OutputStream

data class SessionsUiState(
    val sessions: List<SessionInfoDto> = emptyList(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val hasError: Boolean = false
)

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val syncStatus: SyncStatus = SyncStatus(),
    val pendingCount: Int = 0,
    val trashedCount: Int = 0,
    val sessionsState: SessionsUiState = SessionsUiState()
)

class SettingsViewModel(
    private val preferences: KluddraPreferences,
    private val authRepository: AuthRepository,
    private val syncEngine: SyncEngine,
    private val localExporter: LocalExporter,
    noteRepository: NoteRepository,
    pendingCountFlow: Flow<Int>
) : ViewModel() {

    private val sessionsStateFlow = MutableStateFlow(SessionsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.settingsFlow,
        syncEngine.observedStatus,
        pendingCountFlow,
        noteRepository.observeTrashedCount(),
        sessionsStateFlow
    ) { settings, sync, pending, trashed, sessionsState ->
        SettingsUiState(
            settings = settings,
            syncStatus = sync,
            pendingCount = pending,
            trashedCount = trashed,
            sessionsState = sessionsState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun refreshSessions() {
        viewModelScope.launch {
            sessionsStateFlow.update { it.copy(isLoading = true, hasError = false) }
            runCatching { authRepository.listSessions() }
                .onSuccess { list ->
                    sessionsStateFlow.update {
                        it.copy(
                            sessions = list.sortedWith(
                                compareByDescending<SessionInfoDto> { s -> s.current }
                                    .thenByDescending { s -> s.lastUsedAt }
                            ),
                            isLoading = false,
                            hasError = false
                        )
                    }
                }
                .onFailure {
                    sessionsStateFlow.update { it.copy(isLoading = false, hasError = true) }
                }
        }
    }

    fun revokeSession(sessionId: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            sessionsStateFlow.update { it.copy(isMutating = true) }
            val ok = runCatching { authRepository.revokeSession(sessionId) }.isSuccess
            if (ok) {
                sessionsStateFlow.update { state ->
                    state.copy(
                        sessions = state.sessions.filterNot { it.id == sessionId },
                        isMutating = false
                    )
                }
            } else {
                sessionsStateFlow.update { it.copy(isMutating = false) }
            }
            onDone(ok)
        }
    }

    fun revokeOtherSessions(onDone: (Int?) -> Unit) {
        viewModelScope.launch {
            sessionsStateFlow.update { it.copy(isMutating = true) }
            val count = runCatching { authRepository.revokeOtherSessions() }.getOrNull()
            if (count != null) {
                sessionsStateFlow.update { state ->
                    state.copy(
                        sessions = state.sessions.filter { it.current },
                        isMutating = false
                    )
                }
            } else {
                sessionsStateFlow.update { it.copy(isMutating = false) }
            }
            onDone(count)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferences.setDynamicColor(enabled) }
    }

    fun syncNow() {
        viewModelScope.launch { syncEngine.syncNow() }
    }

    fun exportJson(output: OutputStream, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching { localExporter.writeJson(output) }.isSuccess
            onDone(ok)
        }
    }

    fun exportMarkdownZip(output: OutputStream, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching { localExporter.writeMarkdownZip(output) }.isSuccess
            onDone(ok)
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    companion object {
        fun factory(
            preferences: KluddraPreferences,
            authRepository: AuthRepository,
            syncEngine: SyncEngine,
            localExporter: LocalExporter,
            noteRepository: NoteRepository,
            pendingCountFlow: Flow<Int>
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(
                    preferences,
                    authRepository,
                    syncEngine,
                    localExporter,
                    noteRepository,
                    pendingCountFlow
                ) as T
        }
    }
}
