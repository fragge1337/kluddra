package app.kluddra.android.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kluddra.android.R
import app.kluddra.android.data.local.NoteEntity
import app.kluddra.android.data.prefs.NotesLayoutMode
import app.kluddra.android.ui.components.KluddraEmptyState
import app.kluddra.android.ui.components.NoteCard
import app.kluddra.android.ui.components.SyncStatusBanner
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

    var actionSheetNote by remember { mutableStateOf<NoteEntity?>(null) }

    // Respect accessibility font scale: switch automatically to 1 column when text is large
    val effectiveColumns = when {
        forceSingleColumn -> 1
        density.fontScale >= 1.3f -> 1
        state.layoutMode == NotesLayoutMode.LIST -> 1
        else -> 2
    }

    val trashedMessage = stringResource(R.string.snackbar_note_trashed)
    val undoLabel = stringResource(R.string.action_undo)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 10.dp, bottom = 4.dp)
            ) {
                ExpressiveSearchHeader(
                    query = state.query,
                    layoutMode = state.layoutMode,
                    showLayoutToggle = !forceSingleColumn,
                    onQueryChange = onQueryChange,
                    onToggleLayout = onToggleLayout,
                    onOpenSettings = onOpenSettings
                )
                SyncStatusBanner(
                    syncStatus = state.syncStatus,
                    pendingCount = state.pendingCount,
                    onRetry = onRefresh
                )
            }
        },
        floatingActionButton = {
            MediumFloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCreateNote()
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.new_note),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
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
                    columns = StaggeredGridCells.Fixed(effectiveColumns),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 104.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (state.pinnedNotes.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine, key = "header_pinned") {
                            SectionHeader(text = stringResource(R.string.section_pinned))
                        }
                        items(
                            items = state.pinnedNotes,
                            key = { it.id }
                        ) { note ->
                            NoteCard(
                                note = note,
                                isSelected = note.id == selectedNoteId,
                                onClick = { onNoteClick(note.id) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    actionSheetNote = note
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    if (state.otherNotes.isNotEmpty()) {
                        if (state.pinnedNotes.isNotEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine, key = "header_others") {
                                SectionHeader(
                                    text = stringResource(R.string.section_notes),
                                    modifier = Modifier.padding(top = 8.dp)
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = note.title.ifBlank { note.body.lines().firstOrNull().orEmpty().ifBlank { stringResource(R.string.untitled_note) } },
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
                        colors = androidx.compose.material3.ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
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
                        colors = androidx.compose.material3.ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
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
                        colors = androidx.compose.material3.ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressiveSearchHeader(
    query: String,
    layoutMode: NotesLayoutMode,
    showLayoutToggle: Boolean,
    onQueryChange: (String) -> Unit,
    onToggleLayout: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(56.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
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
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                if (showLayoutToggle) {
                    IconButton(onClick = onToggleLayout) {
                        val isGrid = layoutMode == NotesLayoutMode.GRID
                        Icon(
                            imageVector = if (isGrid) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                            contentDescription = stringResource(
                                if (isGrid) R.string.toggle_layout_list else R.string.toggle_layout_grid
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.nav_settings),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
        modifier = modifier.padding(start = 6.dp, top = 4.dp, bottom = 2.dp)
    )
}
