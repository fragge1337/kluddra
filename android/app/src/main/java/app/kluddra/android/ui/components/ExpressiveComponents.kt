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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import app.kluddra.android.R
import app.kluddra.android.data.local.NoteEntity
import app.kluddra.android.sync.SyncStateKind
import app.kluddra.android.sync.SyncStatus
import app.kluddra.android.ui.notes.NoteBlockType
import app.kluddra.android.ui.notes.NoteFormatting
import app.kluddra.android.ui.theme.LocalKluddraDarkTheme
import app.kluddra.android.ui.theme.LocalKluddraElevation
import app.kluddra.android.ui.theme.LocalKluddraSemantics
import app.kluddra.android.ui.theme.NoteColor
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Reusable calibrated elevation shadow tuned to Kluddra's 5-layer Light and dimmed Dark palette.
 */
@Composable
fun Modifier.kluddraCardShadow(
    elevation: Dp,
    shape: Shape
): Modifier {
    val elev = LocalKluddraElevation.current
    return this.shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = elev.cardAmbientShadow,
        spotColor = elev.cardSpotShadow,
        clip = false
    )
}

private val NoteCardCornerRadius = 28.dp
private val NoteCardShape = RoundedCornerShape(NoteCardCornerRadius)

/**
 * Browser-style scroll-collapsible top bar controller used in compact-height phone landscape
 * so scrolling down smoothly collapses the header out of the way and scrolling up reveals it
 * immediately, reclaiming maximum vertical viewport height.
 */
@Stable
class CollapsibleTopBarState(
    enabled: Boolean
) {
    var enabled by mutableStateOf(enabled)
    var heightPx by mutableFloatStateOf(0f)
    var offsetPx by mutableFloatStateOf(0f)

    fun expand() {
        offsetPx = 0f
    }

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!enabled || heightPx <= 0f) return Offset.Zero
            val delta = available.y
            val newOffset = (offsetPx + delta).coerceIn(-heightPx, 0f)
            val consumed = newOffset - offsetPx
            offsetPx = newOffset
            return Offset(0f, consumed)
        }
    }
}

@Composable
fun rememberCollapsibleTopBarState(enabled: Boolean): CollapsibleTopBarState {
    val state = remember { CollapsibleTopBarState(enabled) }
    LaunchedEffect(enabled) {
        state.enabled = enabled
        if (!enabled) {
            state.expand()
        }
    }
    return state
}

fun Modifier.collapsibleTopBar(state: CollapsibleTopBarState): Modifier =
    this
        .clipToBounds()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val fullHeight = placeable.height
            if (state.heightPx != fullHeight.toFloat()) {
                state.heightPx = fullHeight.toFloat()
                state.offsetPx = if (state.enabled) {
                    state.offsetPx.coerceIn(-fullHeight.toFloat(), 0f)
                } else {
                    0f
                }
            }
            val effectiveOffset = if (state.enabled) state.offsetPx else 0f
            val visibleHeight = (fullHeight + effectiveOffset.roundToInt()).coerceAtLeast(0)
            layout(placeable.width, visibleHeight) {
                placeable.placeRelative(0, effectiveOffset.roundToInt())
            }
        }

/**
 * Tactile vertical-bevel border for raised surfaces (Z+1 .. Z+3): soft light catch along the top
 * rim transitioning smoothly into a grounded bottom edge. Memoized per theme/surface parameters
 * to avoid per-recomposition Brush allocations during fast scrolling.
 */
@Composable
fun kluddraRaisedBorder(
    isColoredSurface: Boolean = false
): BorderStroke {
    val darkTheme = LocalKluddraDarkTheme.current
    val elev = LocalKluddraElevation.current
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    return remember(darkTheme, isColoredSurface, elev, outlineVariant) {
        if (isColoredSurface) {
            BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (darkTheme) 0.34f else 0.62f),
                    0.30f to Color.Transparent,
                    1f to Color.Black.copy(alpha = if (darkTheme) 0.24f else 0.08f)
                )
            )
        } else {
            BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    0f to elev.cardTopHighlight,
                    0.28f to outlineVariant.copy(alpha = if (darkTheme) 0.48f else 0.44f),
                    1f to elev.cardBottomEdge
                )
            )
        }
    }
}

/**
 * Inverted bevel border for recessed Z-1 wells (Pinned tray, resting inline add row):
 * darker upper rim where light is blocked + soft lower edge catching ambient light.
 */
@Composable
fun kluddraRecessedBorder(): BorderStroke {
    val darkTheme = LocalKluddraDarkTheme.current
    val elev = LocalKluddraElevation.current
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    return remember(darkTheme, elev, outlineVariant) {
        BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                0f to elev.recessedInnerShadow.copy(alpha = if (darkTheme) 0.55f else 0.24f),
                0.35f to outlineVariant.copy(alpha = if (darkTheme) 0.32f else 0.45f),
                1f to elev.recessedBottomCatch
            )
        )
    }
}

/**
 * Soft inner top-shadow lip inside recessed Z-1 trays so cards inside visibly sit above them.
 */
@Composable
fun Modifier.kluddraRecessedInnerShadow(shape: Shape): Modifier {
    val elev = LocalKluddraElevation.current
    return this
        .clip(shape)
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    0f to elev.recessedInnerShadow,
                    1f to Color.Transparent,
                    startY = 0f,
                    endY = 9.dp.toPx()
                ),
                size = Size(size.width, 9.dp.toPx())
            )
        }
}

private class MorphPolygonShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotationDegrees: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val w = size.width
        val h = size.height
        val rad = Math.toRadians(rotationDegrees.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()

        fun rx(nx: Float, ny: Float): Float {
            val dx = nx - 0.5f
            val dy = ny - 0.5f
            return (0.5f + dx * cosA - dy * sinA) * w
        }

        fun ry(nx: Float, ny: Float): Float {
            val dx = nx - 0.5f
            val dy = ny - 0.5f
            return (0.5f + dx * sinA + dy * cosA) * h
        }

        var first = true
        morph.forEachCubic(progress.coerceIn(0f, 1f)) { cubic ->
            if (first) {
                path.moveTo(rx(cubic.anchor0X, cubic.anchor0Y), ry(cubic.anchor0X, cubic.anchor0Y))
                first = false
            }
            path.cubicTo(
                rx(cubic.control0X, cubic.control0Y), ry(cubic.control0X, cubic.control0Y),
                rx(cubic.control1X, cubic.control1Y), ry(cubic.control1X, cubic.control1Y),
                rx(cubic.anchor1X, cubic.anchor1Y), ry(cubic.anchor1X, cubic.anchor1Y)
            )
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * Material 3 Expressive scalloped Cookie-to-Circle morphing (+) floating action button.
 * Rests in a tactile 9-petal MaterialShapes.Cookie9Sided silhouette, morphs smoothly into a circle
 * with a playful spring press + 90-degree (+) spin on tap, and casts a true contour-following
 * multi-layer drop shadow with a subtle top light catch.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KluddraAddFab(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val darkTheme = LocalKluddraDarkTheme.current
    val elev = LocalKluddraElevation.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    var entered by remember { mutableStateOf(false) }
    var spinClicks by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { entered = true }

    val entryScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.78f,
        animationSpec = spring(dampingRatio = 0.56f, stiffness = 360f),
        label = "kluddra_fab_entry_scale"
    )
    val entryRotation by animateFloatAsState(
        targetValue = if (entered) 0f else -20f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 320f),
        label = "kluddra_fab_entry_rot"
    )
    val morphProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.50f, stiffness = 420f),
        label = "kluddra_fab_morph"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.91f else 1f,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = 560f),
        label = "kluddra_fab_press_scale"
    )
    val petalRotation by animateFloatAsState(
        targetValue = if (pressed) 22f else 0f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 380f),
        label = "kluddra_fab_petal_rot"
    )
    val iconRotation by animateFloatAsState(
        targetValue = (spinClicks * 90f) + if (pressed) 45f else 0f,
        animationSpec = spring(dampingRatio = 0.44f, stiffness = 380f),
        label = "kluddra_fab_icon_rot"
    )

    val cookieToCircleMorph = remember {
        Morph(
            start = MaterialShapes.Cookie9Sided.normalized(),
            end = MaterialShapes.Circle.normalized()
        )
    }
    val combinedRotation = entryRotation + petalRotation
    val fabShape = remember(cookieToCircleMorph, morphProgress, combinedRotation) {
        MorphPolygonShape(
            morph = cookieToCircleMorph,
            progress = morphProgress,
            rotationDegrees = combinedRotation
        )
    }

    val containerColor = MaterialTheme.colorScheme.primary
    val ambientPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val keyPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val combinedScale = entryScale * pressScale

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(66.dp)
            .graphicsLayer {
                scaleX = combinedScale
                scaleY = combinedScale
            }
            .drawBehind {
                val outline = fabShape.createOutline(size, layoutDirection, this)
                if (outline is Outline.Generic) {
                    val androidPath = outline.path.asAndroidPath()
                    drawIntoCanvas { canvas ->
                        ambientPaint.color = containerColor.toArgb()
                        ambientPaint.setShadowLayer(
                            if (pressed) 10.dp.toPx() else 16.dp.toPx(),
                            0f,
                            if (pressed) 3.dp.toPx() else 6.dp.toPx(),
                            elev.fabAmbientShadow.toArgb()
                        )
                        canvas.nativeCanvas.drawPath(androidPath, ambientPaint)

                        keyPaint.color = containerColor.toArgb()
                        keyPaint.setShadowLayer(
                            if (pressed) 4.dp.toPx() else 6.dp.toPx(),
                            0f,
                            if (pressed) 1.5.dp.toPx() else 3.dp.toPx(),
                            elev.fabKeyShadow.toArgb()
                        )
                        canvas.nativeCanvas.drawPath(androidPath, keyPaint)
                    }
                }
            }
    ) {
        Surface(
            onClick = {
                spinClicks++
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            interactionSource = interactionSource,
            shape = fabShape,
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            border = BorderStroke(
                width = 1.25.dp,
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (darkTheme) 0.42f else 0.34f),
                    0.45f to Color.Transparent,
                    1f to Color.Black.copy(alpha = if (darkTheme) 0.24f else 0.16f)
                )
            ),
            shadowElevation = 0.dp,
            modifier = Modifier.size(66.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = if (darkTheme) 0.16f else 0.18f),
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = if (darkTheme) 0.08f else 0.06f)
                    )
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .size(30.dp)
                        .graphicsLayer {
                            rotationZ = iconRotation
                        }
                )
            }
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
    enabled: Boolean = true,
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
            isRefreshing = enabled && isRefreshing,
            state = pullState,
            enabled = enabled && armedAtTop && isAtTop,
            threshold = 112.dp,
            onRefresh = onRefresh
        )
    ) {
        content()
        if (enabled) {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
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
            border = kluddraRaisedBorder(isColoredSurface = highlighted),
            shadowElevation = 0.dp,
            modifier = Modifier
                .size(40.dp)
                .kluddraCardShadow(
                    elevation = if (darkTheme) 7.dp else 4.dp,
                    shape = CircleShape
                )
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
    dateLabel: String = "",
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

    val shape = NoteCardShape
    val selectionAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(160),
        label = "note_card_selection_ring"
    )
    val ringColor = MaterialTheme.colorScheme.primary
    val border = if (palette == NoteColor.NEUTRAL) {
        kluddraRaisedBorder(isColoredSurface = false)
    } else {
        kluddraRaisedBorder(isColoredSurface = true)
    }

    // Dynamic height driven by actual note length: short notes stay compact, longer notes grow naturally tall!
    val maxPreviewBlocks = if (isCompactGrid) 12 else 6
    val maxParagraphLines = if (isCompactGrid) 10 else 5
    val minCardHeight = 72.dp

    // Fast-path memoized preview parsing: stops scanning after maxPreviewBlocks and pre-renders
    // inline bold once per (body, maxPreviewBlocks) instead of running regexes on every scroll frame.
    val previewBlocks = remember(note.body, maxPreviewBlocks) {
        NoteFormatting.parsePreviewBlocks(note.body, maxPreviewBlocks)
    }

    val cardDescription = remember(note.title, note.body, note.isPinned, note.isReadOnly) {
        buildString {
            if (note.title.isNotBlank()) append(note.title) else append(NoteFormatting.autoGenerateTitle(note.body))
            if (note.isPinned) append(", pinned")
            if (note.isReadOnly) append(", write-protected")
        }
    }

    val scaleModifier = if (pressScale != 1f) {
        Modifier.graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        }
    } else {
        Modifier
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
        border = border,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .then(scaleModifier)
            .drawBehind {
                if (selectionAlpha > 0.001f) {
                    val gapPx = 3.dp.toPx()
                    val strokePx = 2.5.dp.toPx()
                    val outset = gapPx + strokePx / 2f
                    val cornerPx = NoteCardCornerRadius.toPx() + outset
                    drawRoundRect(
                        color = ringColor.copy(alpha = selectionAlpha),
                        topLeft = Offset(-outset, -outset),
                        size = Size(size.width + outset * 2f, size.height + outset * 2f),
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        style = Stroke(width = strokePx)
                    )
                }
            }
            .kluddraCardShadow(
                elevation = if (darkTheme) 9.dp else 5.dp,
                shape = shape
            )
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
                    previewBlocks.forEach { block ->
                        when (block.type) {
                            NoteBlockType.CHECKBOX -> {
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
                                        text = block.renderedText,
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
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(
                                        text = "${block.ordinal}.",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = contentColor
                                    )
                                    Text(
                                        text = block.renderedText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = secondaryColor,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            NoteBlockType.PARAGRAPH -> {
                                Text(
                                    text = block.renderedText,
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

            if (dateLabel.isNotBlank()) {
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryColor.copy(alpha = 0.76f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
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
