package app.kluddra.android.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kluddra.android.R
import app.kluddra.android.data.local.NoteEntity
import app.kluddra.android.data.prefs.NotesLayoutMode
import app.kluddra.android.sync.SyncStateKind
import app.kluddra.android.ui.components.CircularHeaderButton
import app.kluddra.android.ui.components.DiscreetSyncIndicator
import app.kluddra.android.ui.components.KluddraAddFab
import app.kluddra.android.ui.components.KluddraEmptyState
import app.kluddra.android.ui.components.KluddraPullToRefreshBox
import app.kluddra.android.ui.components.NoteCard
import app.kluddra.android.ui.theme.LocalKluddraDarkTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotesOverviewScreen(
    state: NotesOverviewUiState,
    selectedNoteId: String?,
    forceSingleColumn: Boolean,
    onQueryChange: (String) -> Unit,
    onToggleLayout: () -> Unit,
    onNoteClick: (String) -> Unit,
    onCreateNote: () -> Unit,
    onTogglePin: (NoteEntity) -> Unit,
    onToggleLock: (NoteEntity) -> Unit,
    onTrashNote: (String) -> Unit,
    onUndoTrash: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val gridState = rememberLazyStaggeredGridState()
    val isAtTop by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
        }
    }

    var actionSheetNote by remember { mutableStateOf<NoteEntity?>(null) }
    // Search field is NEVER mounted until the user explicitly taps the Search button ->
    // switching to the Notes tab never focuses search or pops up the keyboard.
    var isSearchOpen by rememberSaveable { mutableStateOf(state.query.isNotEmpty()) }

    fun closeSearch() {
        isSearchOpen = false
        onQueryChange("")
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    if (isSearchOpen) {
        BackHandler { closeSearch() }
    }

    val effectiveColumns = when {
        forceSingleColumn -> 1
        density.fontScale >= 1.3f -> 1
        state.layoutMode == NotesLayoutMode.LIST -> 1
        else -> 2
    }

    val trashedMessage = stringResource(R.string.snackbar_note_trashed)
    val undoLabel = stringResource(R.string.action_undo)
    val syncFailedMessage = stringResource(R.string.sync_failed_snackbar)
    val retryLabel = stringResource(R.string.action_retry)

    // Notify the user discreetly via Snackbar (zero layout shift) if a sync fails
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isSearchOpen,
                    transitionSpec = {
                        if (targetState) {
                            (fadeIn(tween(200)) +
                                slideInHorizontally(tween(220)) { it / 10 } +
                                scaleIn(initialScale = 0.97f, animationSpec = tween(200)))
                                .togetherWith(fadeOut(tween(120)))
                        } else {
                            fadeIn(tween(150))
                                .togetherWith(
                                    fadeOut(tween(120)) +
                                        slideOutHorizontally(tween(140)) { it / 10 } +
                                        scaleOut(targetScale = 0.97f, animationSpec = tween(120))
                                )
                        }
                    },
                    label = "notes_header_search_transition"
                ) { searchOpen ->
                    if (searchOpen) {
                        ActiveSearchBar(
                            query = state.query,
                            onQueryChange = onQueryChange,
                            onClose = { closeSearch() }
                        )
                    } else {
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
                                    text = stringResource(R.string.nav_notes),
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
                                CircularHeaderButton(
                                    icon = Icons.Outlined.Search,
                                    contentDescription = stringResource(R.string.search_notes_placeholder),
                                    onClick = { isSearchOpen = true }
                                )
                                if (!forceSingleColumn) {
                                    val isGrid = state.layoutMode == NotesLayoutMode.GRID
                                    CircularHeaderButton(
                                        icon = if (isGrid) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                                        contentDescription = stringResource(
                                            if (isGrid) R.string.toggle_layout_list else R.string.toggle_layout_grid
                                        ),
                                        onClick = onToggleLayout
                                    )
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
            }
        },
        floatingActionButton = {
            KluddraAddFab(
                contentDescription = stringResource(R.string.new_note),
                onClick = onCreateNote
            )
        }
    ) { innerPadding ->
        KluddraPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            isAtTop = isAtTop,
            isScrollInProgress = gridState.isScrollInProgress,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isEmpty = state.pinnedNotes.isEmpty() && state.otherNotes.isEmpty()

            if (isEmpty) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.query.isNotBlank()) {
                        KluddraEmptyState(
                            icon = Icons.Outlined.Search,
                            title = stringResource(R.string.empty_search_title),
                            subtitle = stringResource(R.string.empty_search_subtitle)
                        )
                    } else {
                        KluddraEmptyState(
                            icon = Icons.Outlined.EditNote,
                            title = stringResource(R.string.empty_notes_title),
                            subtitle = stringResource(R.string.empty_notes_subtitle)
                        )
                    }
                }
            } else {
                LazyVerticalStaggeredGrid(
                    state = gridState,
                    columns = StaggeredGridCells.Fixed(effectiveColumns),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 110.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (state.pinnedNotes.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine, key = "pinned_container") {
                            PinnedNotesContainer(
                                notes = state.pinnedNotes,
                                columns = effectiveColumns,
                                selectedNoteId = selectedNoteId,
                                onNoteClick = onNoteClick,
                                onNoteLongClick = { note ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    actionSheetNote = note
                                }
                            )
                        }
                    }

                    if (state.otherNotes.isNotEmpty()) {
                        if (state.pinnedNotes.isNotEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine, key = "header_others") {
                                SectionHeader(
                                    text = stringResource(R.string.section_notes),
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                        items(
                            items = state.otherNotes,
                            key = { it.id }
                        ) { note ->
                            NoteCard(
                                note = note,
                                isSelected = note.id == selectedNoteId,
                                isCompactGrid = effectiveColumns > 1,
                                onClick = { onNoteClick(note.id) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    actionSheetNote = note
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }

    actionSheetNote?.let { note ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { actionSheetNote = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = note.title.ifBlank {
                        NoteFormatting.autoGenerateTitle(note.body).ifBlank { stringResource(R.string.untitled_note) }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Surface(
                    onClick = {
                        onTogglePin(note)
                        actionSheetNote = null
                    },
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(if (note.isPinned) R.string.action_unpin else R.string.action_pin))
                        },
                        leadingContent = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                }
                Surface(
                    onClick = {
                        onToggleLock(note)
                        actionSheetNote = null
                    },
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(if (note.isReadOnly) R.string.action_unlock else R.string.action_lock))
                        },
                        leadingContent = {
                            Icon(
                                if (note.isReadOnly) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                                contentDescription = null
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                }
                Surface(
                    onClick = {
                        val id = note.id
                        actionSheetNote = null
                        onTrashNote(id)
                        scope.launch {
                            val res = snackbarHostState.showSnackbar(
                                message = trashedMessage,
                                actionLabel = undoLabel,
                                duration = SnackbarDuration.Short
                            )
                            if (res == SnackbarResult.ActionPerformed) {
                                onUndoTrash(id)
                            }
                        }
                    },
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.action_delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun ActiveSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val darkTheme = LocalKluddraDarkTheme.current
    val currentQuery by rememberUpdatedState(query)
    val currentOnClose by rememberUpdatedState(onClose)
    var isFocused by remember { mutableStateOf(false) }
    var sawImeOpen by remember { mutableStateOf(false) }

    val imeVisible = WindowInsets.isImeVisible
    val imeCurrentBottom = WindowInsets.ime.getBottom(density)
    val imeTargetBottom = WindowInsets.imeAnimationTarget.getBottom(density)

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    // React at t=0ms when the IME starts hiding (Gesture Back, Predictive Back, 3-button Back,
    // or IME collapse button). On Android 14+ with Predictive Back enabled, Gesture Back bypasses
    // KeyEvent(KEYCODE_BACK), while WindowInsets.imeAnimationTarget drops to 0 immediately when
    // the hide animation begins (~380ms before isImeVisible flips to false).
    // Only arm sawImeOpen once imeCurrentBottom > 0 so the initial open frame (tgt>0, cur=0)
    // can never prematurely trigger dismissal.
    LaunchedEffect(imeTargetBottom, imeCurrentBottom, imeVisible) {
        if (imeCurrentBottom > 0) {
            sawImeOpen = true
        }
        if (sawImeOpen && (imeTargetBottom == 0 || (!imeVisible && imeCurrentBottom == 0))) {
            sawImeOpen = false
            focusManager.clearFocus(force = true)
            if (currentQuery.isEmpty()) {
                currentOnClose()
            }
        }
    }

    Surface(
        color = if (darkTheme) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            width = if (isFocused) 1.5.dp else 1.dp,
            color = if (isFocused) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        shadowElevation = if (darkTheme) 6.dp else 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_notes_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { isFocused = it.isFocused }
                        .onPreInterceptKeyBeforeSoftKeyboard { event ->
                            // Intercept Back BEFORE the IME consumes it so keyboard & search bar
                            // animate away together immediately (when empty) or unfocus immediately
                            // while keeping active search results visible (1st Back), 2nd Back closes!
                            if (event.key == Key.Back) {
                                if (event.type == KeyEventType.KeyUp) {
                                    sawImeOpen = false
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
                                    if (currentQuery.isEmpty()) {
                                        currentOnClose()
                                    }
                                }
                                true
                            } else {
                                false
                            }
                        }
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.clear_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PinnedNotesContainer(
    notes: List<NoteEntity>,
    columns: Int,
    selectedNoteId: String?,
    onNoteClick: (String) -> Unit,
    onNoteLongClick: (NoteEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val darkTheme = LocalKluddraDarkTheme.current
    val trayColor = (if (darkTheme) {
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.62f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.58f)
    }).compositeOver(MaterialTheme.colorScheme.background)

    Surface(
        color = trayColor,
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (darkTheme) 0.42f else 0.52f)
        ),
        shadowElevation = if (darkTheme) 2.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.section_pinned).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = CircleShape
                ) {
                    Text(
                        text = notes.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                    )
                }
            }

            if (columns > 1 && notes.size > 1) {
                val leftNotes = notes.filterIndexed { index, _ -> index % 2 == 0 }
                val rightNotes = notes.filterIndexed { index, _ -> index % 2 == 1 }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        leftNotes.forEach { note ->
                            NoteCard(
                                note = note,
                                isSelected = note.id == selectedNoteId,
                                isCompactGrid = true,
                                onClick = { onNoteClick(note.id) },
                                onLongClick = { onNoteLongClick(note) }
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rightNotes.forEach { note ->
                            NoteCard(
                                note = note,
                                isSelected = note.id == selectedNoteId,
                                isCompactGrid = true,
                                onClick = { onNoteClick(note.id) },
                                onLongClick = { onNoteLongClick(note) }
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    notes.forEach { note ->
                        NoteCard(
                            note = note,
                            isSelected = note.id == selectedNoteId,
                            isCompactGrid = columns > 1,
                            onClick = { onNoteClick(note.id) },
                            onLongClick = { onNoteLongClick(note) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
    )
}
