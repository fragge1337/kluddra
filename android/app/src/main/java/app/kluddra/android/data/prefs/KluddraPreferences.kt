package app.kluddra.android.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.kluddraDataStore: DataStore<Preferences> by preferencesDataStore(name = "kluddra_prefs")

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

enum class NotesLayoutMode {
    GRID, LIST
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val notesLayoutMode: NotesLayoutMode = NotesLayoutMode.GRID,
    val serverUrl: String = "",
    val allowLocalHttp: Boolean = false,
    val username: String = "",
    val sessionId: String = "",
    val isSignedIn: Boolean = false
)

data class AuthCredentials(
    val serverUrl: String,
    val allowLocalHttp: Boolean,
    val username: String,
    val sessionId: String,
    val accessToken: String,
    val refreshToken: String
)

class KluddraPreferences(
    context: Context,
    private val crypto: KeystoreCryptoManager
) {
    private val dataStore = context.applicationContext.kluddraDataStore

    val settingsFlow: Flow<UserSettings> = dataStore.data.map { prefs ->
        val theme = prefs[KEY_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
        val layout = prefs[KEY_NOTES_LAYOUT]?.let { runCatching { NotesLayoutMode.valueOf(it) }.getOrNull() }
            ?: NotesLayoutMode.GRID
        val serverUrl = prefs[KEY_SERVER_URL].orEmpty()
        val hasRefresh = !prefs[KEY_ENC_REFRESH_TOKEN].isNullOrBlank()

        UserSettings(
            themeMode = theme,
            useDynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false,
            notesLayoutMode = layout,
            serverUrl = serverUrl,
            allowLocalHttp = prefs[KEY_ALLOW_LOCAL_HTTP] ?: false,
            username = prefs[KEY_USERNAME].orEmpty(),
            sessionId = prefs[KEY_SESSION_ID].orEmpty(),
            isSignedIn = serverUrl.isNotBlank() && hasRefresh
        )
    }

    suspend fun getCredentials(): AuthCredentials? {
        val prefs = dataStore.data.first()
        val serverUrl = prefs[KEY_SERVER_URL].orEmpty()
        val encAccess = prefs[KEY_ENC_ACCESS_TOKEN].orEmpty()
        val encRefresh = prefs[KEY_ENC_REFRESH_TOKEN].orEmpty()
        if (serverUrl.isBlank() || encRefresh.isBlank()) return null

        val refresh = crypto.decrypt(encRefresh) ?: return null
        val access = crypto.decrypt(encAccess).orEmpty()
        return AuthCredentials(
            serverUrl = serverUrl,
            allowLocalHttp = prefs[KEY_ALLOW_LOCAL_HTTP] ?: false,
            username = prefs[KEY_USERNAME].orEmpty(),
            sessionId = prefs[KEY_SESSION_ID].orEmpty(),
            accessToken = access,
            refreshToken = refresh
        )
    }

    suspend fun saveSession(
        serverUrl: String,
        allowLocalHttp: Boolean,
        username: String,
        sessionId: String,
        accessToken: String,
        refreshToken: String
    ) {
        val encAccess = crypto.encrypt(accessToken)
        val encRefresh = crypto.encrypt(refreshToken)
        dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = serverUrl
            prefs[KEY_ALLOW_LOCAL_HTTP] = allowLocalHttp
            prefs[KEY_USERNAME] = username
            prefs[KEY_SESSION_ID] = sessionId
            prefs[KEY_ENC_ACCESS_TOKEN] = encAccess
            prefs[KEY_ENC_REFRESH_TOKEN] = encRefresh
        }
    }

    suspend fun updateTokens(accessToken: String, refreshToken: String, sessionId: String) {
        val encAccess = crypto.encrypt(accessToken)
        val encRefresh = crypto.encrypt(refreshToken)
        dataStore.edit { prefs ->
            prefs[KEY_SESSION_ID] = sessionId
            prefs[KEY_ENC_ACCESS_TOKEN] = encAccess
            prefs[KEY_ENC_REFRESH_TOKEN] = encRefresh
        }
    }

    suspend fun clearSession() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_ENC_ACCESS_TOKEN)
            prefs.remove(KEY_ENC_REFRESH_TOKEN)
            prefs.remove(KEY_SESSION_ID)
            prefs.remove(KEY_USERNAME)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setNotesLayoutMode(mode: NotesLayoutMode) {
        dataStore.edit { it[KEY_NOTES_LAYOUT] = mode.name }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_NOTES_LAYOUT = stringPreferencesKey("notes_layout")
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_ALLOW_LOCAL_HTTP = booleanPreferencesKey("allow_local_http")
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_SESSION_ID = stringPreferencesKey("session_id")
        val KEY_ENC_ACCESS_TOKEN = stringPreferencesKey("enc_access_token")
        val KEY_ENC_REFRESH_TOKEN = stringPreferencesKey("enc_refresh_token")
    }
}
