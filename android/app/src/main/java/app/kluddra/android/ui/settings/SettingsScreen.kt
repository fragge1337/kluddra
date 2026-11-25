package app.kluddra.android.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kluddra.android.BuildConfig
import app.kluddra.android.R
import app.kluddra.android.data.prefs.ThemeMode
import app.kluddra.android.sync.SyncStateKind
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onSyncNow: () -> Unit,
    onOpenTrash: () -> Unit,
    onExportJson: (OutputStream, (Boolean) -> Unit) -> Unit,
    onExportMarkdownZip: (OutputStream, (Boolean) -> Unit) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showSignOutConfirm by remember { mutableStateOf(false) }

    val exportSuccessMsg = stringResource(R.string.settings_export_success)
    val exportFailedMsg = stringResource(R.string.settings_export_failed)

    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val stream = context.contentResolver.openOutputStream(uri)
            if (stream != null) {
                onExportJson(stream) { ok ->
                    runCatching { stream.close() }
                    scope.launch {
                        snackbarHostState.showSnackbar(if (ok) exportSuccessMsg else exportFailedMsg)
                    }
                }
            }
        }
    }

    val zipExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            val stream = context.contentResolver.openOutputStream(uri)
            if (stream != null) {
                onExportMarkdownZip(stream) { ok ->
                    runCatching { stream.close() }
                    scope.launch {
                        snackbarHostState.showSnackbar(if (ok) exportSuccessMsg else exportFailedMsg)
                    }
                }
            }
        }
    }

    val currentLocaleTag = remember {
        AppCompatDelegate.getApplicationLocales().toLanguageTags()
    }
    var selectedLang by remember {
        mutableStateOf(
            when {
                currentLocaleTag.startsWith("sv") -> "sv"
                currentLocaleTag.startsWith("en") -> "en"
                else -> ""
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Section 1: Server & Sync
            item(key = "section_server") {
                SettingsGroupCard(title = stringResource(R.string.settings_section_server)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_server_address)) },
                        supportingContent = {
                            Column {
                                Text(state.settings.serverUrl.ifBlank { "—" })
                                if (state.settings.username.isNotBlank()) {
                                    Text(stringResource(R.string.settings_signed_in_as, state.settings.username))
                                }
                            }
                        },
                        leadingContent = { Icon(Icons.Outlined.CloudDone, contentDescription = null) },
                        colors = settingsItemColors()
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    val syncLabel = when (state.syncStatus.kind) {
                        SyncStateKind.SYNCING -> stringResource(R.string.sync_status_syncing)
                        SyncStateKind.OFFLINE -> stringResource(R.string.sync_status_offline)
                        SyncStateKind.AUTH_EXPIRED -> stringResource(R.string.sync_status_auth_expired)
                        SyncStateKind.ERROR -> stringResource(R.string.sync_status_error)
                        SyncStateKind.IDLE -> if (state.pendingCount > 0) {
                            stringResource(R.string.sync_status_pending, state.pendingCount)
                        } else {
                            stringResource(R.string.sync_status_synced)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = syncLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(onClick = onSyncNow) {
                            Icon(
                                imageVector = Icons.Outlined.Sync,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(stringResource(R.string.action_sync_now))
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.settings_sign_out),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Logout,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        colors = settingsItemColors(),
                        modifier = Modifier.clickable { showSignOutConfirm = true }
                    )
                }
            }

            // Section 2: Appearance & Language
            item(key = "section_appearance") {
                SettingsGroupCard(title = stringResource(R.string.settings_section_appearance)) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            text = stringResource(R.string.settings_theme),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        val themeOptions = listOf(
                            ThemeMode.LIGHT to stringResource(R.string.theme_light),
                            ThemeMode.DARK to stringResource(R.string.theme_dark),
                            ThemeMode.SYSTEM to stringResource(R.string.theme_system)
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            themeOptions.forEachIndexed { index, (mode, label) ->
                                SegmentedButton(
                                    selected = state.settings.themeMode == mode,
                                    onClick = { onThemeModeChange(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(index, themeOptions.size)
                                ) {
                                    Text(label, maxLines = 1)
                                }
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                            supportingContent = { Text(stringResource(R.string.settings_dynamic_color_desc)) },
                            trailingContent = {
                                Switch(
                                    checked = state.settings.useDynamicColor,
                                    onCheckedChange = onDynamicColorChange
                                )
                            },
                            colors = settingsItemColors()
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            text = stringResource(R.string.settings_language),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        val langOptions = listOf(
                            "" to stringResource(R.string.language_system),
                            "sv" to stringResource(R.string.language_sv),
                            "en" to stringResource(R.string.language_en)
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            langOptions.forEachIndexed { index, (tag, label) ->
                                SegmentedButton(
                                    selected = selectedLang == tag,
                                    onClick = {
                                        selectedLang = tag
                                        onLanguageChange(tag)
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index, langOptions.size)
                                ) {
                                    Text(label, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Your Data (Trash & Open Export)
            item(key = "section_data") {
                SettingsGroupCard(title = stringResource(R.string.settings_section_data)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.nav_trash)) },
                        supportingContent = {
                            Text(stringResource(R.string.settings_trash_count, state.trashedCount))
                        },
                        leadingContent = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                        colors = settingsItemColors(),
                        modifier = Modifier.clickable(onClick = onOpenTrash)
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_export_json)) },
                        supportingContent = { Text(stringResource(R.string.settings_export_json_desc)) },
                        leadingContent = { Icon(Icons.Outlined.Download, contentDescription = null) },
                        colors = settingsItemColors(),
                        modifier = Modifier.clickable {
                            val stamp = LocalDate.now().toString()
                            jsonExportLauncher.launch("kluddra-export-$stamp.json")
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_export_markdown)) },
                        supportingContent = { Text(stringResource(R.string.settings_export_markdown_desc)) },
                        leadingContent = { Icon(Icons.Outlined.FolderZip, contentDescription = null) },
                        colors = settingsItemColors(),
                        modifier = Modifier.clickable {
                            val stamp = LocalDate.now().toString()
                            zipExportLauncher.launch("kluddra-notes-$stamp.zip")
                        }
                    )
                }
            }

            // Section 4: About
            item(key = "section_about") {
                SettingsGroupCard(title = stringResource(R.string.settings_section_about)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.app_name)) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME))
                                Text(stringResource(R.string.settings_privacy_note))
                            }
                        },
                        colors = settingsItemColors()
                    )
                }
            }
        }
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text(stringResource(R.string.confirm_sign_out_title)) },
            text = { Text(stringResource(R.string.confirm_sign_out_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirm = false
                        onSignOut()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.settings_sign_out),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun settingsItemColors() = ListItemDefaults.colors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
)
