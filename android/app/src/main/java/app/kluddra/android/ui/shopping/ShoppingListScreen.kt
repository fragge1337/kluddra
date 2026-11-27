package app.kluddra.android.ui.shopping

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kluddra.android.R
import app.kluddra.android.data.local.ShoppingItemEntity
import app.kluddra.android.sync.SyncStateKind
import app.kluddra.android.ui.components.CircularHeaderButton
import app.kluddra.android.ui.components.DiscreetSyncIndicator
import app.kluddra.android.ui.components.KluddraEmptyState
import app.kluddra.android.ui.components.KluddraPullToRefreshBox
import app.kluddra.android.ui.components.collapsibleTopBar
import app.kluddra.android.ui.components.kluddraCardShadow
import app.kluddra.android.ui.components.kluddraRaisedBorder
import app.kluddra.android.ui.components.kluddraRecessedBorder
import app.kluddra.android.ui.components.kluddraRecessedInnerShadow
import app.kluddra.android.ui.components.rememberCollapsibleTopBarState
import app.kluddra.android.ui.theme.LocalKluddraDarkTheme
import app.kluddra.android.ui.theme.LocalKluddraElevation
import app.kluddra.android.ui.theme.LocalKluddraSemantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private val ShoppingRowShape = RoundedCornerShape(24.dp)
private val ShoppingCheckboxShape = RoundedCornerShape(10.dp)
private val ClearCheckedBtnShape = RoundedCornerShape(20.dp)

private class ShoppingInputStateHolder {
    var text by mutableStateOf("")
    var isFocused by mutableStateOf(false)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ShoppingListScreen(
    state: ShoppingListUiState,
    onAddItem: (String) -> Unit,
    onToggleChecked: (String) -> Unit,
    onReorderActive: (List<ShoppingItemEntity>, String) -> Unit,
    onDeleteItem: (String, (ShoppingItemEntity) -> Unit) -> Unit,
    onClearChecked: ((List<ShoppingItemEntity>) -> Unit) -> Unit,
    onClearAll: ((List<ShoppingItemEntity>) -> Unit) -> Unit,
    onRestoreItems: (List<ShoppingItemEntity>) -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    isSyncEnabled: Boolean = true,
    collapseTopBarOnScroll: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val darkTheme = LocalKluddraDarkTheme.current
    val semantics = LocalKluddraSemantics.current
    val topBarState = rememberCollapsibleTopBarState(enabled = collapseTopBarOnScroll)

    // Input text & focus live in a holder never read by ShoppingListScreen itself so typing
    // causes ZERO recompositions of the list or any ShoppingItemRow!
    val inputHolder = remember { ShoppingInputStateHolder() }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var hasMeasuredBounds by remember { mutableStateOf(false) }
    var bottomOccupiedBelowPx by remember { mutableIntStateOf(0) }

    // Synchronous active list + deduplicated checked list so every item keeps ONE stable key (it.id)
    // across both sections — allowing LazyColumn's animateItem() to smoothly GLIDE rows up/down!
    var draggingOrder by remember { mutableStateOf<List<ShoppingItemEntity>?>(null) }
    var lastMovedId by remember { mutableStateOf<String?>(null) }
    val lastDragStoppedAtMs = remember { longArrayOf(0L) }
    val activeItems = draggingOrder ?: state.activeItems
    val activeIdSet = remember(activeItems) { activeItems.mapTo(HashSet()) { it.id } }
    val checkedItems = remember(state.checkedItems, activeIdSet) {
        state.checkedItems.filterNot { it.id in activeIdSet }
    }

    // Two-stage visual toggle: lets the user visibly see the checkbox tick/untick with a spring pop
    // for ~210ms BEFORE the card smoothly slides down into Checked (or back up into Active)!
    val pendingVisualCheck = remember { mutableStateMapOf<String, Boolean>() }

    fun triggerAnimatedToggle(item: ShoppingItemEntity) {
        if (item.id in pendingVisualCheck) return
        draggingOrder = null
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        pendingVisualCheck[item.id] = !item.isChecked
        scope.launch {
            delay(210L)
            onToggleChecked(item.id)
            delay(120L)
            pendingVisualCheck.remove(item.id)
        }
    }

    val lazyListState = rememberLazyListState()
    val isAtTop by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
        }
    }
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromId = from.key as? String ?: return@rememberReorderableLazyListState
        val toId = to.key as? String ?: return@rememberReorderableLazyListState
        val base = draggingOrder ?: state.activeItems
        val fromIdx = base.indexOfFirst { it.id == fromId }
        val toIdx = base.indexOfFirst { it.id == toId }
        if (fromIdx != -1 && toIdx != -1) {
            val mutable = base.toMutableList()
            val moved = mutable.removeAt(fromIdx)
            mutable.add(toIdx, moved)
            draggingOrder = mutable
            lastMovedId = moved.id
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val undoLabel = stringResource(R.string.action_undo)
    val removedItemMsg = stringResource(R.string.shopping_item_removed)
    val clearedListMsg = stringResource(R.string.shopping_cleared_snackbar)
    val syncFailedMessage = stringResource(R.string.sync_failed_snackbar)
    val retryLabel = stringResource(R.string.action_retry)
    val totalCount = state.activeItems.size + state.checkedItems.size

    LaunchedEffect(isSyncEnabled, state.syncStatus.kind) {
        if (isSyncEnabled && (state.syncStatus.kind == SyncStateKind.ERROR || state.syncStatus.kind == SyncStateKind.AUTH_EXPIRED)) {
            val res = snackbarHostState.showSnackbar(
                message = syncFailedMessage,
                actionLabel = retryLabel,
                duration = SnackbarDuration.Short
            )
            if (res == SnackbarResult.ActionPerformed) {
                onRefresh()
            }
        }
    }

    fun showUndoSnackbar(message: String, removed: List<ShoppingItemEntity>) {
        if (removed.isEmpty()) return
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                onRestoreItems(removed)
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                hasMeasuredBounds = true
                bottomOccupiedBelowPx = (view.height - coords.boundsInRoot().bottom.roundToInt())
                    .coerceAtLeast(0)
            },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Exact same 48dp header geometry as NotesOverviewScreen so title, cloud icon,
            // and circular header buttons align pixel-for-pixel across both tabs!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                    .collapsibleTopBar(topBarState)
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.End))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.shopping_title),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            autoSize = TextAutoSize.StepBased(minFontSize = 17.sp, maxFontSize = 24.sp),
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isSyncEnabled) {
                            DiscreetSyncIndicator(
                                syncStatus = state.syncStatus,
                                pendingCount = state.pendingCount,
                                onRetry = onRefresh
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (totalCount > 0) {
                            Box {
                                CircularHeaderButton(
                                    icon = Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(R.string.shopping_clear_all),
                                    onClick = { showOverflowMenu = true }
                                )
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    if (state.checkedItems.isNotEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.shopping_clear_checked)) },
                                            leadingIcon = {
                                                Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                                            },
                                            onClick = {
                                                showOverflowMenu = false
                                                onClearChecked { removed ->
                                                    showUndoSnackbar(removedItemMsg, removed)
                                                }
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = stringResource(R.string.shopping_clear_all),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Outlined.DeleteOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            showClearAllDialog = true
                                        }
                                    )
                                }
                            }
                        }
                        CircularHeaderButton(
                            icon = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.nav_settings),
                            showWarningBadge = isSyncEnabled && state.syncStatus.isServerOutdated,
                            onClick = onOpenSettings
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
            ) {
                KluddraPullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    isAtTop = isAtTop,
                    isScrollInProgress = lazyListState.isScrollInProgress,
                    onRefresh = onRefresh,
                    enabled = isSyncEnabled,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val isListCompletelyEmpty = activeItems.isEmpty() && checkedItems.isEmpty()
                    LazyColumn(
                        state = lazyListState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 2.dp,
                            bottom = 28.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.End))
                            .nestedScroll(topBarState.nestedScrollConnection)
                    ) {
                        if (!isListCompletelyEmpty) {
                            item(key = "shopping_status_summary", contentType = "status_summary") {
                                Text(
                                    text = if (state.activeItems.isEmpty()) {
                                        stringResource(R.string.shopping_all_done)
                                    } else {
                                        stringResource(R.string.shopping_remaining_count, state.activeItems.size)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (state.activeItems.isEmpty()) {
                                        semantics.success
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                                )
                            }
                        }

                        items(
                            items = activeItems,
                            key = { it.id },
                            contentType = { "active_item" }
                        ) { item ->
                            ReorderableItem(
                                state = reorderableState,
                                key = item.id,
                                animateItemModifier = Modifier.animateItem(
                                    fadeInSpec = null,
                                    placementSpec = spring(dampingRatio = 0.82f, stiffness = 340f),
                                    fadeOutSpec = tween(180)
                                )
                            ) { isDragging ->
                                val pendingOverride by remember(item.id) {
                                    derivedStateOf { pendingVisualCheck[item.id] }
                                }
                                ShoppingItemRow(
                                    item = item,
                                    displayChecked = pendingOverride ?: item.isChecked,
                                    darkTheme = darkTheme,
                                    isDragging = isDragging,
                                    onToggle = {
                                        val sinceDragMs = android.os.SystemClock.uptimeMillis() - lastDragStoppedAtMs[0]
                                        if (!isDragging && sinceDragMs > 200L) {
                                            triggerAnimatedToggle(item)
                                        }
                                    },
                                    dragHandleModifier = Modifier.longPressDraggableHandle(
                                        onDragStarted = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragStopped = {
                                            lastDragStoppedAtMs[0] = android.os.SystemClock.uptimeMillis()
                                            val finalOrder = draggingOrder
                                            val moved = lastMovedId ?: item.id
                                            draggingOrder = null
                                            lastMovedId = null
                                            if (finalOrder != null) {
                                                onReorderActive(finalOrder, moved)
                                            }
                                        }
                                    )
                                )
                            }
                        }

                        // Inline "+ Add item..." row positioned directly after the last active item
                        // (above the Checked section, Google Keep style) — zero floating button, zero bounce!
                        // Self-contained input state + IME dismissal + BringIntoViewRequester so typing
                        // causes ZERO recompositions of ShoppingListScreen or any ShoppingItemRow!
                        item(key = "inline_add_item", contentType = "inline_add") {
                            InlineAddShoppingItemRow(
                                holder = inputHolder,
                                activeCount = activeItems.size,
                                darkTheme = darkTheme,
                                onAddItem = onAddItem
                            )
                        }

                        if (isListCompletelyEmpty) {
                            item(key = "shopping_empty_state", contentType = "empty_state") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 56.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    KluddraEmptyState(
                                        icon = Icons.Outlined.ShoppingBag,
                                        title = stringResource(R.string.shopping_empty_title),
                                        subtitle = stringResource(R.string.shopping_empty_subtitle)
                                    )
                                }
                            }
                        }

                        if (checkedItems.isNotEmpty()) {
                            item(key = "checked_header", contentType = "checked_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, bottom = 4.dp, start = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.shopping_checked_section,
                                            checkedItems.size
                                        ).uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FilledTonalButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onClearChecked { removed ->
                                                showUndoSnackbar(removedItemMsg, removed)
                                            }
                                        },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        ),
                                        shape = ClearCheckedBtnShape,
                                        border = kluddraRaisedBorder(isColoredSurface = true),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier.kluddraCardShadow(
                                            elevation = if (darkTheme) 6.dp else 3.5.dp,
                                            shape = ClearCheckedBtnShape
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteSweep,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.shopping_clear_checked),
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            items(
                                items = checkedItems,
                                key = { it.id },
                                contentType = { "checked_item" }
                            ) { item ->
                                val pendingOverride by remember(item.id) {
                                    derivedStateOf { pendingVisualCheck[item.id] }
                                }
                                ShoppingItemRow(
                                    item = item,
                                    displayChecked = pendingOverride ?: item.isChecked,
                                    darkTheme = darkTheme,
                                    isDragging = false,
                                    onToggle = { triggerAnimatedToggle(item) },
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = null,
                                        placementSpec = spring(dampingRatio = 0.82f, stiffness = 340f),
                                        fadeOutSpec = tween(180)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Isolated IME-synced bottom spacer: only this lightweight Spacer recomposes during
            // keyboard open/close frames, so LazyColumn resizes in layout phase with ZERO row recompositions!
            ImeSyncedBottomSpacer(
                hasMeasuredBounds = hasMeasuredBounds,
                bottomOccupiedBelowPx = bottomOccupiedBelowPx
            )
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(stringResource(R.string.confirm_clear_all_title)) },
            text = { Text(stringResource(R.string.confirm_clear_all_body, totalCount)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllDialog = false
                        onClearAll { removed ->
                            showUndoSnackbar(clearedListMsg, removed)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.shopping_clear_all),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ImeSyncedBottomSpacer(
    hasMeasuredBounds: Boolean,
    bottomOccupiedBelowPx: Int
) {
    if (!hasMeasuredBounds) return
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val navBarBottomPx = WindowInsets.navigationBars.getBottom(density)
    val heightDp = with(density) {
        (maxOf(imeBottomPx, navBarBottomPx) - bottomOccupiedBelowPx).coerceAtLeast(0).toDp()
    }
    if (heightDp > 0.dp) {
        Spacer(modifier = Modifier.height(heightDp))
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun InlineAddShoppingItemRow(
    holder: ShoppingInputStateHolder,
    activeCount: Int,
    darkTheme: Boolean,
    onAddItem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val elev = LocalKluddraElevation.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var sawImeOpen by remember { mutableStateOf(false) }

    val value = holder.text
    val isFocused = holder.isFocused

    val imeVisible = WindowInsets.isImeVisible
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeTargetBottomPx = WindowInsets.imeAnimationTarget.getBottom(density)
    val imeSettledOpen = imeBottomPx > 0 && imeBottomPx == imeTargetBottomPx

    // Unfocus immediately at t=0ms when the user dismisses the keyboard (zero lag, zero bounce).
    LaunchedEffect(imeTargetBottomPx, imeBottomPx, imeVisible) {
        if (imeBottomPx > 0) {
            sawImeOpen = true
        }
        if (sawImeOpen && (imeTargetBottomPx == 0 || (!imeVisible && imeBottomPx == 0))) {
            sawImeOpen = false
            focusManager.clearFocus(force = true)
        }
    }

    // Native BringIntoViewRequester covering the entire card + 12dp bottom breathing room.
    LaunchedEffect(isFocused, activeCount, imeSettledOpen) {
        if (isFocused) {
            delay(32L)
            bringIntoViewRequester.bringIntoView()
        }
    }

    LaunchedEffect(value) {
        if (isFocused && value.isNotEmpty()) {
            delay(16L)
            bringIntoViewRequester.bringIntoView()
        }
    }

    fun submitNewItem() {
        val cleaned = holder.text.trim()
        if (cleaned.isNotEmpty()) {
            onAddItem(cleaned)
            holder.text = ""
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val containerColor by animateColorAsState(
        targetValue = when {
            isFocused && darkTheme -> MaterialTheme.colorScheme.surfaceContainerHigh
            isFocused -> MaterialTheme.colorScheme.surfaceContainerLowest
            else -> elev.recessedWell
        },
        animationSpec = tween(160),
        label = "inline_add_bg"
    )

    val rowSurfaceModifier = if (isFocused) {
        Modifier
            .fillMaxWidth()
            .kluddraCardShadow(
                elevation = if (darkTheme) 9.dp else 6.dp,
                shape = ShoppingRowShape
            )
    } else {
        Modifier
            .fillMaxWidth()
            .kluddraRecessedInnerShadow(ShoppingRowShape)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .padding(bottom = 12.dp)
    ) {
        Surface(
            color = containerColor,
            shape = ShoppingRowShape,
            border = if (isFocused) {
                BorderStroke(1.75.dp, MaterialTheme.colorScheme.primary)
            } else {
                kluddraRecessedBorder()
            },
            shadowElevation = 0.dp,
            modifier = rowSurfaceModifier
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShoppingRowShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = {
                            runCatching { focusRequester.requestFocus() }
                            keyboardController?.show()
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.shopping_add_button),
                        tint = if (isFocused) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.shopping_add_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = { updated ->
                            if (updated.contains('\n')) {
                                updated.lines().forEach { line ->
                                    if (line.isNotBlank()) onAddItem(line.trim())
                                }
                                holder.text = ""
                            } else {
                                holder.text = updated
                            }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { submitNewItem() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { holder.isFocused = it.isFocused }
                            .onPreInterceptKeyBeforeSoftKeyboard { event ->
                                if (event.key == Key.Back) {
                                    if (event.type == KeyEventType.KeyUp) {
                                        sawImeOpen = false
                                        focusManager.clearFocus(force = true)
                                        keyboardController?.hide()
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                    )
                }

                AnimatedVisibility(
                    visible = value.isNotBlank(),
                    enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.8f, animationSpec = tween(140)),
                    exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.8f, animationSpec = tween(100))
                ) {
                    FilledIconButton(
                        onClick = { submitNewItem() },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.shopping_add_button),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShoppingItemRow(
    item: ShoppingItemEntity,
    displayChecked: Boolean,
    darkTheme: Boolean,
    isDragging: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier
) {
    val semantics = LocalKluddraSemantics.current
    val rowInteractionSource = remember { MutableInteractionSource() }
    val isRowPressed by rowInteractionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isRowPressed && !isDragging) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "shopping_row_press_scale"
    )
    // Single unified spring drives checkbox fill, row tint, checkbox bounce, and check-icon pop
    // (cuts per-row Animatable coroutines from 5 down to 2 and eliminates extra GraphicsLayers on scroll!).
    val checkProgress by animateFloatAsState(
        targetValue = if (displayChecked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.50f, stiffness = 540f),
        label = "shopping_check_progress"
    )
    val clampedProgress = checkProgress.coerceIn(0f, 1f)

    val surfaceLow = MaterialTheme.colorScheme.surfaceContainerLow
    val checkedBg = remember(semantics.successContainer, surfaceLow, darkTheme) {
        semantics.successContainer
            .copy(alpha = if (darkTheme) 0.52f else 0.78f)
            .compositeOver(surfaceLow)
    }
    val containerColor = if (isDragging) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        lerp(surfaceLow, checkedBg, clampedProgress)
    }
    val checkboxBg = lerp(
        MaterialTheme.colorScheme.surfaceContainerHigh,
        semantics.success,
        clampedProgress
    )
    val rowElevation = when {
        isDragging -> 14.dp
        displayChecked -> if (darkTheme) 4.dp else 2.dp
        else -> if (darkTheme) 8.dp else 4.dp
    }
    val checkedBorder = remember(semantics.success, darkTheme) {
        BorderStroke(
            1.dp,
            semantics.success.copy(alpha = if (darkTheme) 0.4f else 0.3f)
        )
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
        shape = ShoppingRowShape,
        border = when {
            isDragging -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            displayChecked -> checkedBorder
            else -> kluddraRaisedBorder(isColoredSurface = false)
        },
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .then(scaleModifier)
            .kluddraCardShadow(
                elevation = rowElevation,
                shape = ShoppingRowShape
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ShoppingRowShape)
                .clickable(
                    interactionSource = rowInteractionSource,
                    indication = ripple(),
                    role = Role.Checkbox,
                    onClick = onToggle
                )
                .then(dragHandleModifier)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lightweight tactile rounded-square checkbox: zero inner Surface or idle GraphicsLayers!
            val isTransitioningCheck = checkProgress > 0.02f && checkProgress != 1f
            val boxBounceScale = if (isTransitioningCheck) {
                1f + ((checkProgress - 0.5f).coerceAtLeast(0f) * 0.14f)
            } else {
                1f
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(26.dp)
                        .then(
                            if (boxBounceScale != 1f) {
                                Modifier.graphicsLayer {
                                    scaleX = boxBounceScale
                                    scaleY = boxBounceScale
                                }
                            } else {
                                Modifier
                            }
                        )
                        .background(checkboxBg, ShoppingCheckboxShape)
                        .then(
                            if (clampedProgress < 0.5f) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = ShoppingCheckboxShape
                                )
                            } else {
                                Modifier
                            }
                        )
                ) {
                    if (checkProgress > 0.05f) {
                        val iconScale = checkProgress.coerceAtLeast(0f)
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = item.text,
                            tint = semantics.onSuccess,
                            modifier = Modifier
                                .size(17.dp)
                                .then(
                                    if (iconScale != 1f) {
                                        Modifier.graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
            ) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (displayChecked) FontWeight.Normal else FontWeight.Medium,
                        textDecoration = if (displayChecked) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (displayChecked) {
                        semantics.onSuccessContainer.copy(alpha = 0.82f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
