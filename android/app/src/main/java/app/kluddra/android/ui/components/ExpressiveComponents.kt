package app.kluddra.android.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kluddra.android.R
import app.kluddra.android.data.local.NoteEntity
import app.kluddra.android.sync.SyncStateKind
import app.kluddra.android.sync.SyncStatus
import app.kluddra.android.ui.notes.NoteBlockType
import app.kluddra.android.ui.notes.NoteFormatting
import app.kluddra.android.ui.theme.LocalKluddraDarkTheme
import app.kluddra.android.ui.theme.LocalKluddraSemantics
import app.kluddra.android.ui.theme.NoteColor
import kotlinx.coroutines.delay

/**
 * Unified expressive circular (+) floating action button shared by both Notes and Shopping tabs
 * so primary creation actions align pixel-for-pixel with a crisp background cutout halo and
 * spring press physics.
 */
@Composable
fun KluddraAddFab(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.91f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "kluddra_add_fab_scale"
    )
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        interactionSource = interactionSource,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        border = BorderStroke(2.5.dp, MaterialTheme.colorScheme.background),
        shadowElevation = 10.dp,
        modifier = modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = contentDescription,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

/**
 * Two-step pull-to-refresh container shared by both Notes and Shopping tabs:
 * If the user is scrolled down, dragging back to the top stops cleanly at offset 0 and will
 * NEVER accidentally trigger a sync in the same gesture. Once settled at the top, a deliberate
 * downward pull (112dp threshold) triggers [onRefresh].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KluddraPullToRefreshBox(
    isRefreshing: Boolean,
    isAtTop: Boolean,
    isScrollInProgress: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val pullState = rememberPullToRefreshState()
    var armedAtTop by remember { mutableStateOf(isAtTop) }

    LaunchedEffect(isAtTop, isScrollInProgress) {
        if (!isAtTop) {
            armedAtTop = false
        } else if (!isScrollInProgress) {
            armedAtTop = true
        }
    }

    Box(
        modifier = modifier.pullToRefresh(
            isRefreshing = isRefreshing,
            state = pullState,
            enabled = armedAtTop && isAtTop,
            threshold = 112.dp,
            onRefresh = onRefresh
        )
    ) {
        content()
        PullToRefreshDefaults.Indicator(
            state = pullState,
            isRefreshing = isRefreshing,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/**
 * Formats a persisted [lastSyncAt] epoch-millis timestamp as a live-updating relative string
 * ("Just now", "10 min ago", "2 h ago", etc.) across all 11 supported European locales.
 */
@Composable
fun rememberRelativeLastSyncedText(lastSyncAt: Long?): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastSyncAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    if (lastSyncAt == null || lastSyncAt <= 0L) {
        return stringResource(R.string.sync_never)
    }
    val diffMs = (now - lastSyncAt).coerceAtLeast(0L)
    val minutes = diffMs / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1L -> stringResource(R.string.sync_time_just_now)
        minutes < 60L -> stringResource(R.string.sync_time_minutes_ago, minutes.toInt())
        hours < 24L -> stringResource(R.string.sync_time_hours_ago, hours.toInt())
        else -> stringResource(R.string.sync_time_days_ago, days.toInt())
    }
}

/**
 * Fixed-size, zero-layout-shift sync micro-indicator placed discreetly next to the screen title.
 * Never shifts content when syncing starts/finishes; tapping opens a clean status dialog with
 * the relative last-synced time, any sync failure reason, and an immediate Sync button.
 */
@Composable
fun DiscreetSyncIndicator(
    syncStatus: SyncStatus,
    onRetry: () -> Unit,
    pendingCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showStatusDialog by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "sync_btn_scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(26.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        when (syncStatus.kind) {
            SyncStateKind.SYNCING -> {
                val pulse = rememberInfiniteTransition(label = "sync_pulse")
                val dotAlpha by pulse.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 0.95f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 650),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "sync_dot_alpha"
                )
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showStatusDialog = true
                    },
                    interactionSource = interactionSource,
                    color = Color.Transparent,
                    shape = CircleShape,
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .alpha(dotAlpha)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
            SyncStateKind.OFFLINE -> {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showStatusDialog = true
                    },
                    interactionSource = interactionSource,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.CloudOff,
                            contentDescription = stringResource(R.string.sync_status_offline),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            SyncStateKind.ERROR, SyncStateKind.AUTH_EXPIRED -> {
                val pulse = rememberInfiniteTransition(label = "sync_err_pulse")
                val badgeScale by pulse.animateFloat(
                    initialValue = 0.94f,
                    targetValue = 1.06f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 900),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "sync_err_scale"
                )
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showStatusDialog = true
                    },
                    interactionSource = interactionSource,
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = badgeScale
                            scaleY = badgeScale
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = stringResource(R.string.sync_status_error),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
            SyncStateKind.IDLE -> {
                val semantics = LocalKluddraSemantics.current
                val healthy = pendingCount == 0 && !syncStatus.isServerOutdated
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showStatusDialog = true
                    },
                    interactionSource = interactionSource,
                    color = Color.Transparent,
                    shape = CircleShape,
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.CloudDone,
                            contentDescription = stringResource(R.string.sync_dialog_title),
                            tint = when {
                                syncStatus.isServerOutdated -> semantics.warning
                                healthy -> semantics.success
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (showStatusDialog) {
        val semantics = LocalKluddraSemantics.current
        val relativeTime = rememberRelativeLastSyncedText(syncStatus.lastSyncAt)
        val statusHeadline = when (syncStatus.kind) {
            SyncStateKind.SYNCING -> stringResource(R.string.sync_status_syncing)
            SyncStateKind.OFFLINE -> stringResource(R.string.sync_status_offline)
            SyncStateKind.AUTH_EXPIRED -> stringResource(R.string.sync_status_auth_expired)
            SyncStateKind.ERROR -> stringResource(R.string.sync_status_error)
            SyncStateKind.IDLE -> if (pendingCount > 0) {
                stringResource(R.string.sync_status_pending, pendingCount)
            } else {
                stringResource(R.string.sync_status_synced)
            }
        }
        val isError = syncStatus.kind == SyncStateKind.ERROR || syncStatus.kind == SyncStateKind.AUTH_EXPIRED

        AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            icon = {
                Icon(
                    imageVector = when {
                        isError -> Icons.Outlined.ErrorOutline
                        syncStatus.isServerOutdated -> Icons.Outlined.WarningAmber
                        syncStatus.kind == SyncStateKind.OFFLINE -> Icons.Outlined.CloudOff
                        else -> Icons.Outlined.CloudDone
                    },
                    contentDescription = null,
                    tint = when {
                        isError -> MaterialTheme.colorScheme.error
                        syncStatus.isServerOutdated -> semantics.warning
                        syncStatus.kind == SyncStateKind.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> semantics.success
                    }
                )
            },
            title = { Text(stringResource(R.string.sync_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = statusHeadline,
                        style = MaterialTheme.typography.titleSmall,
                        color = when {
                            isError -> MaterialTheme.colorScheme.error
                            syncStatus.kind == SyncStateKind.IDLE && pendingCount == 0 -> semantics.success
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = stringResource(R.string.sync_last_synced, relativeTime),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!syncStatus.serverVersion.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.settings_server_version, syncStatus.serverVersion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (syncStatus.isServerOutdated) {
                        Surface(
                            color = semantics.warningContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.server_outdated_body,
                                    syncStatus.serverVersion ?: "—",
                                    syncStatus.minRecommendedServerVersion
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = semantics.onWarningContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                    if (isError && !syncStatus.message.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = syncStatus.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showStatusDialog = false
                        onRetry()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(stringResource(R.string.action_sync_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStatusDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * Circular 40dp tonal icon button with crisp tactile press scale, haptic click feedback,
 * and an optional warning indicator badge (e.g. outdated server on the Settings button).
 */
@Composable
fun CircularHeaderButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    showWarningBadge: Boolean = false,
    modifier: Modifier = Modifier
) {
    val darkTheme = LocalKluddraDarkTheme.current
    val semantics = LocalKluddraSemantics.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 750f),
        label = "header_btn_scale"
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            interactionSource = interactionSource,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else if (darkTheme) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
            contentColor = if (highlighted) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            shape = CircleShape,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (darkTheme) 0.55f else 0.45f)
            ),
            shadowElevation = if (darkTheme) 5.dp else 2.dp,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (showWarningBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(semantics.warning)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteEntity,
    isSelected: Boolean,
    isCompactGrid: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val darkTheme = LocalKluddraDarkTheme.current
    val haptic = LocalHapticFeedback.current
    val palette = NoteColor.resolveForNote(note.color, note.id)

    val containerColor = palette.container(darkTheme)
    val contentColor = palette.onContainer(darkTheme)
    val secondaryColor = palette.secondaryText(darkTheme)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 650f),
        label = "note_card_press_scale"
    )

    val shape = RoundedCornerShape(28.dp)
    val border = when {
        isSelected -> BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
        palette == NoteColor.NEUTRAL && darkTheme -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
        palette == NoteColor.NEUTRAL && !darkTheme -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        darkTheme -> BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
        else -> BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.06f))
    }

    // Dynamic height driven by actual note length: short notes stay compact, longer notes grow naturally tall!
    val maxPreviewBlocks = if (isCompactGrid) 12 else 6
    val maxParagraphLines = if (isCompactGrid) 10 else 5
    val minCardHeight = 72.dp

    val previewBlocks = NoteFormatting.parseBlocks(note.body)
        .filter { it.text.isNotBlank() || it.type != NoteBlockType.PARAGRAPH }
        .take(maxPreviewBlocks)

    val cardDescription = buildString {
        if (note.title.isNotBlank()) append(note.title) else append(NoteFormatting.autoGenerateTitle(note.body))
        if (note.isPinned) append(", pinned")
        if (note.isReadOnly) append(", write-protected")
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
        border = border,
        tonalElevation = 0.dp,
        shadowElevation = if (darkTheme) 7.dp else 2.5.dp,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(shape)
            .semantics { contentDescription = cardDescription }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .defaultMinSize(minHeight = minCardHeight)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (note.title.isNotBlank() || note.isPinned || note.isReadOnly) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (note.title.isNotBlank()) {
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = contentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    if (note.isPinned || note.isReadOnly) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            if (note.isReadOnly) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(contentColor.copy(alpha = 0.12f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Lock,
                                        contentDescription = stringResource(R.string.badge_read_only),
                                        tint = contentColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            if (note.isPinned) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(contentColor.copy(alpha = 0.12f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PushPin,
                                        contentDescription = stringResource(R.string.section_pinned),
                                        tint = contentColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (previewBlocks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    var num = 0
                    previewBlocks.forEach { block ->
                        when (block.type) {
                            NoteBlockType.CHECKBOX -> {
                                num = 0
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (block.isChecked) {
                                            Icons.Outlined.CheckBox
                                        } else {
                                            Icons.Outlined.CheckBoxOutlineBlank
                                        },
                                        contentDescription = null,
                                        tint = if (block.isChecked) secondaryColor.copy(alpha = 0.65f) else contentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = NoteFormatting.renderInlineBold(block.text),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            textDecoration = if (block.isChecked) TextDecoration.LineThrough else TextDecoration.None
                                        ),
                                        color = if (block.isChecked) secondaryColor.copy(alpha = 0.7f) else secondaryColor,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            NoteBlockType.NUMBERED -> {
                                num++
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(
                                        text = "$num.",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = contentColor
                                    )
                                    Text(
                                        text = NoteFormatting.renderInlineBold(block.text),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = secondaryColor,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            NoteBlockType.PARAGRAPH -> {
                                num = 0
                                Text(
                                    text = NoteFormatting.renderInlineBold(block.text),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (note.title.isBlank()) contentColor else secondaryColor,
                                    maxLines = maxParagraphLines,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            if (note.conflictOfNoteId != null) {
                Surface(
                    color = contentColor.copy(alpha = 0.14f),
                    contentColor = contentColor,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.badge_conflict),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KluddraEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(92.dp)
                .clip(MaterialShapes.Clover4Leaf.toShape())
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
