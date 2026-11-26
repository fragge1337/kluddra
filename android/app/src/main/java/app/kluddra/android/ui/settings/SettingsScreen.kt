package app.kluddra.android.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kluddra.android.BuildConfig
import app.kluddra.android.R
import app.kluddra.android.data.prefs.ThemeMode
import app.kluddra.android.sync.SyncStateKind
import app.kluddra.android.ui.components.rememberRelativeLastSyncedText
import app.kluddra.android.ui.theme.LocalKluddraDarkTheme
import app.kluddra.android.ui.theme.LocalKluddraSemantics
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onRefreshSessions: () -> Unit,
    onRevokeSession: (String, (Boolean) -> Unit) -> Unit,
    onRevokeOtherSessions: ((Int?) -> Unit) -> Unit,
    onOpenTrash: () -> Unit,
    onExportJson: (OutputStream, (Boolean) -> Unit) -> Unit,
    onExportMarkdownZip: (OutputStream, (Boolean) -> Unit) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptic = LocalHapticFeedback.current
    val semantics = LocalKluddraSemantics.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showSessionsSheet by remember { mutableStateOf(false) }
    var showRevokeOthersConfirm by remember { mutableStateOf(false) }

    val exportSuccessMsg = stringResource(R.string.settings_export_success)
    val exportFailedMsg = stringResource(R.string.settings_export_failed)
    val sessionRevokedMsg = stringResource(R.string.sessions_revoked_one)
    val sessionsActionFailedMsg = stringResource(R.string.sessions_action_failed)

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
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
                    containerColor = MaterialTheme.colorScheme.background
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
                val isSyncError = state.syncStatus.kind == SyncStateKind.ERROR ||
                    state.syncStatus.kind == SyncStateKind.AUTH_EXPIRED
                val isHealthySync = state.syncStatus.kind == SyncStateKind.IDLE &&
                    state.pendingCount == 0 &&
                    !state.syncStatus.isServerOutdated

                SettingsGroupCard(title = stringResource(R.string.settings_section_server)) {
                    ListItem(
                        headlineContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(stringResource(R.string.settings_server_address))
                                Icon(
                                    imageVector = when {
                                        isSyncError -> Icons.Outlined.ErrorOutline
                                        state.syncStatus.kind == SyncStateKind.OFFLINE -> Icons.Outlined.CloudOff
                                        else -> Icons.Outlined.CloudDone
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        isSyncError -> MaterialTheme.colorScheme.error
                                        state.syncStatus.isServerOutdated -> semantics.warning
                                        isHealthySync -> semantics.success
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(state.settings.serverUrl.ifBlank { "—" })
                                if (state.settings.username.isNotBlank()) {
                                    Text(stringResource(R.string.settings_signed_in_as, state.settings.username))
                                }
                                Text(
                                    text = stringResource(
                                        R.string.settings_server_version,
                                        state.syncStatus.serverVersion ?: "—"
                                    )
                                )
                            }
                        },
                        colors = settingsItemColors()
                    )

                    if (state.syncStatus.isServerOutdated) {
                        Surface(
                            color = semantics.warningContainer,
                            contentColor = semantics.onWarningContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    tint = semantics.warning,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = stringResource(R.string.server_outdated_title),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.server_outdated_body,
                                            state.syncStatus.serverVersion ?: "—",
                                            state.syncStatus.minRecommendedServerVersion
                                        ),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

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
                    val lastSyncedRelative = rememberRelativeLastSyncedText(state.syncStatus.lastSyncAt)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = syncLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    isSyncError -> MaterialTheme.colorScheme.error
                                    isHealthySync -> semantics.success
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.sync_last_synced, lastSyncedRelative),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isSyncError && !state.syncStatus.message.isNullOrBlank()) {
                                Text(
                                    text = state.syncStatus.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSyncNow()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Sync,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                text = stringResource(R.string.action_sync_now),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_active_sessions)) },
                        supportingContent = { Text(stringResource(R.string.settings_active_sessions_desc)) },
                        leadingContent = { Icon(Icons.Outlined.Devices, contentDescription = null) },
                        colors = settingsItemColors(),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showSessionsSheet = true
                            onRefreshSessions()
                        }
                    )
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
                                Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        colors = settingsItemColors(),
                        modifier = Modifier.clickable { showSignOutConfirm = true }
                    )
                }
            }

            // Section 2: Appearance (Language follows system automatically!)
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
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onThemeModeChange(mode)
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index, themeOptions.size)
                                ) {
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onDynamicColorChange(it)
                                    }
                                )
                            },
                            colors = settingsItemColors()
                        )
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

    if (showSessionsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val sessionsState = state.sessionsState
        val otherSessionsCount = sessionsState.sessions.count { !it.current }
        ModalBottomSheet(
            onDismissRequest = { showSessionsSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.settings_active_sessions),
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onRefreshSessions()
                        },
                        enabled = !sessionsState.isLoading && !sessionsState.isMutating
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.action_retry)
                        )
                    }
                }

                when {
                    sessionsState.isLoading && sessionsState.sessions.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    sessionsState.hasError && sessionsState.sessions.isEmpty() -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.sessions_load_failed),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onRefreshSessions) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }
                        }
                    }

                    else -> {
                        sessionsState.sessions.forEach { session ->
                            val lastActiveRel = rememberRelativeLastSyncedText(
                                session.lastUsedAt.takeIf { it > 0L }
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(
                                    width = if (session.current) 1.5.dp else 1.dp,
                                    color = if (session.current) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Smartphone,
                                        contentDescription = null,
                                        tint = if (session.current) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = session.deviceName.ifBlank {
                                                    stringResource(R.string.sessions_unknown_device)
                                                },
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (session.current) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.sessions_this_device),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = stringResource(R.string.sessions_last_active, lastActiveRel),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!session.current) {
                                        IconButton(
                                            enabled = !sessionsState.isMutating,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onRevokeSession(session.id) { ok ->
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(
                                                            if (ok) sessionRevokedMsg else sessionsActionFailedMsg
                                                        )
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.Logout,
                                                contentDescription = stringResource(R.string.sessions_revoke_device),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (otherSessionsCount == 0) {
                            Text(
                                text = stringResource(R.string.sessions_only_this_device),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                            FilledTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showRevokeOthersConfirm = true
                                },
                                enabled = !sessionsState.isMutating,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.sessions_revoke_others))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRevokeOthersConfirm) {
        AlertDialog(
            onDismissRequest = { showRevokeOthersConfirm = false },
            title = { Text(stringResource(R.string.sessions_revoke_others_confirm_title)) },
            text = { Text(stringResource(R.string.sessions_revoke_others_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRevokeOthersConfirm = false
                        onRevokeOtherSessions { count ->
                            scope.launch {
                                if (count != null) {
                                    snackbarHostState.showSnackbar(
                                        resources.getString(R.string.sessions_revoked_others, count)
                                    )
                                } else {
                                    snackbarHostState.showSnackbar(sessionsActionFailedMsg)
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.sessions_revoke_others),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeOthersConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
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
    val darkTheme = LocalKluddraDarkTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 14.dp, bottom = 8.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (darkTheme) 0.55f else 0.55f)
            ),
            shadowElevation = if (darkTheme) 6.dp else 2.dp,
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
