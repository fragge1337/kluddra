package app.kluddra.android.data.repo

import androidx.room.withTransaction
import app.kluddra.android.data.local.KluddraDatabase
import app.kluddra.android.data.prefs.KluddraPreferences
import app.kluddra.android.data.remote.KluddraApiClient
import app.kluddra.android.data.remote.ServerInfoDto
import app.kluddra.android.data.remote.SessionInfoDto
import app.kluddra.android.sync.SyncEngine
import app.kluddra.android.sync.SyncScheduler

class AuthRepository(
    private val api: KluddraApiClient,
    private val preferences: KluddraPreferences,
    private val db: KluddraDatabase,
    private val syncEngine: SyncEngine,
    private val syncScheduler: SyncScheduler,
    private val noteRepository: NoteRepository,
    private val shoppingRepository: ShoppingRepository
) {
    suspend fun startLocalOnly() {
        syncScheduler.cancelAll()
        syncEngine.resetStatus()
        preferences.enableLocalOnlyMode()
    }

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
        noteRepository.enqueueAllLocalForSync()
        shoppingRepository.enqueueAllLocalForSync()
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
        noteRepository.enqueueAllLocalForSync()
        shoppingRepository.enqueueAllLocalForSync()
        syncScheduler.ensurePeriodicSyncScheduled()
        syncEngine.syncNow()
    }

    suspend fun listSessions(): List<SessionInfoDto> = api.listSessions()

    suspend fun revokeSession(sessionId: String) = api.revokeSession(sessionId)

    suspend fun revokeOtherSessions(): Int = api.revokeOtherSessions()

    suspend fun signOut(keepLocalCopy: Boolean = false) {
        api.logout()
        syncScheduler.cancelAll()
        syncEngine.resetStatus()
        preferences.clearSession(keepLocalMode = keepLocalCopy)
        if (keepLocalCopy) {
            db.withTransaction {
                db.syncDao().clearOutbox()
                db.syncDao().clearMetadata()
            }
            noteRepository.markAllLocalDirty()
            shoppingRepository.markAllLocalDirty()
        } else {
            db.withTransaction {
                db.noteDao().clearAll()
                db.shoppingDao().clearAll()
                db.syncDao().clearOutbox()
                db.syncDao().clearMetadata()
            }
        }
    }
}
