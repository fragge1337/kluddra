package app.kluddra.android.ui.shopping

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DragHandle
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.type
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
import app.kluddra.android.ui.components.KluddraAddFab
import app.kluddra.android.ui.components.KluddraEmptyState
import app.kluddra.android.ui.components.KluddraPullToRefreshBox
import app.kluddra.android.ui.theme.LocalKluddraDarkTheme
import app.kluddra.android.ui.theme.LocalKluddraSemantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val addFocusRequester = remember { FocusRequester() }
    val darkTheme = LocalKluddraDarkTheme.current
    val semantics = LocalKluddraSemantics.current

    var inputText by remember { mutableStateOf("") }
    var isAddBarOpen by rememberSaveable { mutableStateOf(false) }
    var isInputFocused by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var hasMeasuredBounds by remember { mutableStateOf(false) }
    var bottomOccupiedBelowPx by remember { mutableIntStateOf(0) }
    var sawImeOpen by remember { mutableStateOf(false) }

    val imeVisible = WindowInsets.isImeVisible
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeTargetBottomPx = WindowInsets.imeAnimationTarget.getBottom(density)
    val navBarBottomPx = WindowInsets.navigationBars.getBottom(density)

    fun closeAddBar() {
        isAddBarOpen = false
        sawImeOpen = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    if (isAddBarOpen) {
        BackHandler { closeAddBar() }
    }

    // Close the quick-add bar at t=0ms when the user dismisses the keyboard (Gesture Back,
    // Predictive Back, 3-button Back, or IME collapse arrow) — zero lag and zero bounce!
    LaunchedEffect(isAddBarOpen, imeTargetBottomPx, imeBottomPx, imeVisible) {
        if (!isAddBarOpen) return@LaunchedEffect
        if (imeBottomPx > 0) {
            sawImeOpen = true
        }
        if (sawImeOpen && (imeTargetBottomPx == 0 || (!imeVisible && imeBottomPx == 0))) {
            closeAddBar()
        }
    }

    // Exact frame-synced bottom padding: rides flush above the keyboard whenever the keyboard
    // rises above the bottom nav bar, and stops cleanly on top of the nav bar (zero bounce!).
    val dockBottomPaddingDp = if (!hasMeasuredBounds) {
        0.dp
    } else {
        with(density) {
            val requiredFromWindowBottomPx = maxOf(imeBottomPx, navBarBottomPx)
            (requiredFromWindowBottomPx - bottomOccupiedBelowPx).coerceAtLeast(0).toDp()
        }
    }

    // Synchronous active list + deduplicated checked list so every item keeps ONE stable key (it.id)
    // across both sections — allowing LazyColumn's animateItem() to smoothly GLIDE rows up/down!
    var draggingOrder by remember { mutableStateOf<List<ShoppingItemEntity>?>(null) }
    var lastMovedId by remember { mutableStateOf<String?>(null) }
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

    LaunchedEffect(state.syncStatus.kind) {
        if (state.syncStatus.kind == SyncStateKind.ERROR || state.syncStatus.kind == SyncStateKind.AUTH_EXPIRED) {
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

    fun submitNewItem() {
        val cleaned = inputText.trim()
        if (cleaned.isNotEmpty()) {
            onAddItem(cleaned)
            inputText = ""
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
        floatingActionButton = {
            AnimatedVisibility(
                visible = !isAddBarOpen,
                enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.85f, animationSpec = tween(160)),
                exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.85f, animationSpec = tween(100))
            ) {
                KluddraAddFab(
                    contentDescription = stringResource(R.string.shopping_add_button),
                    onClick = { isAddBarOpen = true }
                )
            }
        },
        topBar = {
            // Exact same 48dp header geometry as NotesOverviewScreen so title, cloud icon,
            // and circular header buttons align pixel-for-pixel across both tabs!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
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
                        DiscreetSyncIndicator(
                            syncStatus = state.syncStatus,
                            pendingCount = state.pendingCount,
                            onRetry = onRefresh
                        )
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
                            showWarningBadge = state.syncStatus.isServerOutdated,
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
                .padding(innerPadding)
                .padding(bottom = dockBottomPaddingDp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
            ) {
                KluddraPullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    isAtTop = isAtTop,
                    isScrollInProgress = lazyListState.isScrollInProgress,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (activeItems.isEmpty() && state.checkedItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            KluddraEmptyState(
                                icon = Icons.Outlined.ShoppingBag,
                                title = stringResource(R.string.shopping_empty_title),
                                subtitle = stringResource(R.string.shopping_empty_subtitle)
                            )
                        }
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 2.dp,
                                bottom = if (isAddBarOpen) 24.dp else 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item(key = "shopping_status_summary") {
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
                            items(
                                items = activeItems,
                                key = { it.id }
                            ) { item ->
                                ReorderableItem(reorderableState, key = item.id) { isDragging ->
                                    ShoppingItemRow(
                                        item = item,
                                        displayChecked = pendingVisualCheck[item.id] ?: item.isChecked,
                                        darkTheme = darkTheme,
                                        isDragging = isDragging,
                                        onToggle = { triggerAnimatedToggle(item) },
                                        onDelete = {
                                            draggingOrder = null
                                            onDeleteItem(item.id) { removed ->
                                                showUndoSnackbar(removedItemMsg, listOf(removed))
                                            }
                                        },
                                        dragHandleModifier = Modifier.draggableHandle(
                                            onDragStarted = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragStopped = {
                                                val finalOrder = draggingOrder
                                                val moved = lastMovedId ?: item.id
                                                draggingOrder = null
                                                lastMovedId = null
                                                if (finalOrder != null) {
                                                    onReorderActive(finalOrder, moved)
                                                }
                                            }
                                        ),
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = tween(180),
                                            placementSpec = spring(dampingRatio = 0.82f, stiffness = 340f),
                                            fadeOutSpec = tween(180)
                                        )
                                    )
                                }
                            }

                            if (checkedItems.isNotEmpty()) {
                                item(key = "checked_header") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp, bottom = 4.dp, start = 6.dp),
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
                                            shape = RoundedCornerShape(20.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
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
                                    key = { it.id }
                                ) { item ->
                                    ShoppingItemRow(
                                        item = item,
                                        displayChecked = pendingVisualCheck[item.id] ?: item.isChecked,
                                        darkTheme = darkTheme,
                                        isDragging = false,
                                        onToggle = { triggerAnimatedToggle(item) },
                                        onDelete = {
                                            onDeleteItem(item.id) { removed ->
                                                showUndoSnackbar(removedItemMsg, listOf(removed))
                                            }
                                        },
                                        dragHandleModifier = null,
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = tween(180),
                                            placementSpec = spring(dampingRatio = 0.82f, stiffness = 340f),
                                            fadeOutSpec = tween(180)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // On-demand quick-add input dock opened by the (+) FAB: sits flush directly above the
            // keyboard for rapid multi-item entry and dismisses at t=0ms in sync with the keyboard.
            AnimatedVisibility(
                visible = isAddBarOpen,
                enter = fadeIn(tween(140)) + slideInVertically(tween(160)) { it / 2 },
                exit = fadeOut(tween(110)) + slideOutVertically(tween(120)) { it / 2 }
            ) {
                LaunchedEffect(Unit) {
                    sawImeOpen = false
                    // Wait one frame so AnimatedVisibility mounts BasicTextField's FocusNode
                    // before requesting focus and showing the IME.
                    withFrameNanos { }
                    runCatching { addFocusRequester.requestFocus() }
                    keyboardController?.show()
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (darkTheme) 0.55f else 0.7f)
                    ),
                    shadowElevation = if (darkTheme) 12.dp else 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Surface(
                            color = if (darkTheme) {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            },
                            shape = RoundedCornerShape(26.dp),
                            border = BorderStroke(
                                width = if (isInputFocused) 1.75.dp else 1.dp,
                                color = if (isInputFocused) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 680.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 18.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (inputText.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.shopping_add_placeholder),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    BasicTextField(
                                        value = inputText,
                                        onValueChange = { value ->
                                            if (value.contains('\n')) {
                                                value.lines().forEach { line ->
                                                    if (line.isNotBlank()) onAddItem(line.trim())
                                                }
                                                inputText = ""
                                            } else {
                                                inputText = value
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
                                            .focusRequester(addFocusRequester)
                                            .onFocusChanged { focusState ->
                                                isInputFocused = focusState.isFocused
                                                if (focusState.isFocused && isAddBarOpen) {
                                                    keyboardController?.show()
                                                }
                                            }
                                            .onPreInterceptKeyBeforeSoftKeyboard { event ->
                                                if (event.key == Key.Back) {
                                                    if (event.type == KeyEventType.KeyUp) {
                                                        closeAddBar()
                                                    }
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                FilledIconButton(
                                    onClick = { submitNewItem() },
                                    enabled = inputText.isNotBlank(),
                                    shape = CircleShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = stringResource(R.string.shopping_add_button)
                                    )
                                }
                                androidx.compose.material3.IconButton(
                                    onClick = { closeAddBar() },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.action_cancel),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShoppingItemRow(
    item: ShoppingItemEntity,
    displayChecked: Boolean,
    darkTheme: Boolean,
    isDragging: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    dragHandleModifier: Modifier?,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val semantics = LocalKluddraSemantics.current
    val rowInteractionSource = remember { MutableInteractionSource() }
    val isRowPressed by rowInteractionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isRowPressed && !isDragging) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "shopping_row_press_scale"
    )
    val checkPopScale by animateFloatAsState(
        targetValue = if (displayChecked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = 550f),
        label = "shopping_check_pop"
    )
    val boxScale by animateFloatAsState(
        targetValue = if (displayChecked) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 600f),
        label = "shopping_box_bounce"
    )

    val checkedBg = semantics.successContainer
        .copy(alpha = if (darkTheme) 0.52f else 0.78f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)

    val containerColor by animateColorAsState(
        targetValue = when {
            isDragging -> MaterialTheme.colorScheme.primaryContainer
            displayChecked -> checkedBg
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(durationMillis = 180),
        label = "shopping_row_bg"
    )
    val checkboxBg by animateColorAsState(
        targetValue = if (displayChecked) {
            semantics.success
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(durationMillis = 150),
        label = "shopping_checkbox_bg"
    )
    val rowShape = RoundedCornerShape(24.dp)

    Surface(
        color = containerColor,
        shape = rowShape,
        border = when {
            isDragging -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            displayChecked -> BorderStroke(
                1.dp,
                semantics.success.copy(alpha = if (darkTheme) 0.4f else 0.3f)
            )
            else -> BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (darkTheme) 0.55f else 0.55f)
            )
        },
        shadowElevation = if (isDragging) 10.dp else if (darkTheme) 5.dp else 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .combinedClickable(
                    interactionSource = rowInteractionSource,
                    indication = ripple(),
                    role = Role.Checkbox,
                    onClick = onToggle,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDelete()
                    }
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tactile rounded-square checkbox with spring pop animation
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp)
            ) {
                Surface(
                    color = checkboxBg,
                    shape = RoundedCornerShape(10.dp),
                    border = if (displayChecked) null else BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .size(26.dp)
                        .graphicsLayer {
                            scaleX = boxScale
                            scaleY = boxScale
                        }
                ) {
                    if (checkPopScale > 0.02f) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = item.text,
                                tint = semantics.onSuccess,
                                modifier = Modifier
                                    .size(17.dp)
                                    .graphicsLayer {
                                        scaleX = checkPopScale
                                        scaleY = checkPopScale
                                    }
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 12.dp)
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

            if (dragHandleModifier != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = dragHandleModifier
                        .size(48.dp)
                        .clip(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DragHandle,
                        contentDescription = stringResource(R.string.shopping_drag_handle),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
