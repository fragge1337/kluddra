package app.kluddra.android.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.kluddra.android.data.export.LocalExporter
import app.kluddra.android.data.prefs.KluddraPreferences
import app.kluddra.android.data.prefs.ThemeMode
import app.kluddra.android.data.prefs.UserSettings
import app.kluddra.android.data.repo.AuthRepository
import app.kluddra.android.data.repo.NoteRepository
import app.kluddra.android.sync.SyncEngine
import app.kluddra.android.sync.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.OutputStream

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val syncStatus: SyncStatus = SyncStatus(),
    val pendingCount: Int = 0,
    val trashedCount: Int = 0
)

class SettingsViewModel(
    private val preferences: KluddraPreferences,
    private val authRepository: AuthRepository,
    private val syncEngine: SyncEngine,
    private val localExporter: LocalExporter,
    noteRepository: NoteRepository,
    pendingCountFlow: Flow<Int>
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.settingsFlow,
        syncEngine.status,
        pendingCountFlow,
        noteRepository.observeTrashedCount()
    ) { settings, sync, pending, trashed ->
        SettingsUiState(
            settings = settings,
            syncStatus = sync,
            pendingCount = pending,
            trashedCount = trashed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferences.setDynamicColor(enabled) }
    }

    fun setLanguageTag(tag: String) {
        val locales = if (tag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
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
