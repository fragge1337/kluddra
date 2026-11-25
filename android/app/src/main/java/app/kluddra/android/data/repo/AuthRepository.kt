package app.kluddra.android.data.repo

import androidx.room.withTransaction
import app.kluddra.android.data.local.KluddraDatabase
import app.kluddra.android.data.prefs.KluddraPreferences
import app.kluddra.android.data.remote.KluddraApiClient
import app.kluddra.android.data.remote.ServerInfoDto
import app.kluddra.android.sync.SyncEngine
import app.kluddra.android.sync.SyncScheduler

class AuthRepository(
    private val api: KluddraApiClient,
    private val preferences: KluddraPreferences,
    private val db: KluddraDatabase,
    private val syncEngine: SyncEngine,
    private val syncScheduler: SyncScheduler
) {
    suspend fun probeServer(serverUrl: String, allowLocalHttp: Boolean): ServerInfoDto =
        api.fetchServerInfo(serverUrl, allowLocalHttp)

    suspend fun completeSetup(
        serverUrl: String,
        allowLocalHttp: Boolean,
        setupCode: String,
        username: String,
        password: String
    ) {
        val tokens = api.completeSetup(serverUrl, allowLocalHttp, setupCode, username, password)
        preferences.saveSession(
            serverUrl = serverUrl,
            allowLocalHttp = allowLocalHttp,
            username = tokens.user.username,
            sessionId = tokens.sessionId,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken
        )
        syncScheduler.ensurePeriodicSyncScheduled()
        syncEngine.syncNow()
    }

    suspend fun login(
        serverUrl: String,
        allowLocalHttp: Boolean,
        username: String,
        password: String
    ) {
        val tokens = api.login(serverUrl, allowLocalHttp, username, password)
        preferences.saveSession(
            serverUrl = serverUrl,
            allowLocalHttp = allowLocalHttp,
            username = tokens.user.username,
            sessionId = tokens.sessionId,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken
        )
        syncScheduler.ensurePeriodicSyncScheduled()
        syncEngine.syncNow()
    }

    suspend fun signOut() {
        api.logout()
        syncScheduler.cancelAll()
        preferences.clearSession()
        db.withTransaction {
            db.noteDao().clearAll()
            db.shoppingDao().clearAll()
            db.syncDao().clearOutbox()
            db.syncDao().clearMetadata()
        }
    }
}
