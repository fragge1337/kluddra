package app.kluddra.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.kluddra.android.data.prefs.ThemeMode
import app.kluddra.android.data.prefs.UserSettings
import app.kluddra.android.ui.nav.KluddraAppShell
import app.kluddra.android.ui.nav.SharedIncomingNote
import app.kluddra.android.ui.theme.KluddraTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val incomingShare = mutableStateOf<SharedIncomingNote?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleShareIntent(intent)

        val container = (application as KluddraApp).container

        setContent {
            val settings by container.preferences.settingsFlow.collectAsStateWithLifecycle(
                initialValue = UserSettings()
            )
            val isDark = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        androidx.activity.SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (isDark) {
                        androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        androidx.activity.SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
            }

            KluddraTheme(
                themeMode = settings.themeMode,
                useDynamicColor = settings.useDynamicColor
            ) {
                KluddraAppShell(
                    container = container,
                    incomingSharedNote = incomingShare.value,
                    onConsumeIncomingShare = { incomingShare.value = null }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val container = (application as KluddraApp).container
        lifecycleScope.launch {
            if (container.preferences.getCredentials() != null) {
                container.syncScheduler.ensurePeriodicSyncScheduled()
                container.syncEngine.syncNow()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
            if (text.isNotEmpty() || subject.isNotEmpty()) {
                incomingShare.value = SharedIncomingNote(title = subject, body = text)
            }
        }
    }
}
