package app.kluddra.android

import android.app.Application
import androidx.work.Configuration
import app.kluddra.android.data.export.LocalExporter
import app.kluddra.android.data.local.KluddraDatabase
import app.kluddra.android.data.prefs.KeystoreCryptoManager
import app.kluddra.android.data.prefs.KluddraPreferences
import app.kluddra.android.data.remote.KluddraApiClient
import app.kluddra.android.data.repo.AuthRepository
import app.kluddra.android.data.repo.NoteRepository
import app.kluddra.android.data.repo.ShoppingRepository
import app.kluddra.android.sync.KluddraWorkerFactory
import app.kluddra.android.sync.SyncEngine
import app.kluddra.android.sync.SyncScheduler

class AppContainer(application: Application) {
    val database: KluddraDatabase = KluddraDatabase.create(application)
    val cryptoManager: KeystoreCryptoManager = KeystoreCryptoManager()
    val preferences: KluddraPreferences = KluddraPreferences(application, cryptoManager)
    val apiClient: KluddraApiClient = KluddraApiClient(preferences)
    val syncEngine: SyncEngine = SyncEngine(database, apiClient)
    val syncScheduler: SyncScheduler = SyncScheduler(application)
    val noteRepository: NoteRepository = NoteRepository(database, syncScheduler)
    val shoppingRepository: ShoppingRepository = ShoppingRepository(database, syncScheduler)
    val authRepository: AuthRepository = AuthRepository(
        api = apiClient,
        preferences = preferences,
        db = database,
        syncEngine = syncEngine,
        syncScheduler = syncScheduler
    )
    val localExporter: LocalExporter = LocalExporter(database.noteDao(), database.shoppingDao())
}

class KluddraApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(KluddraWorkerFactory(container.syncEngine))
            .build()
}
