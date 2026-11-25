package app.kluddra.android.ui.shopping

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kluddra.android.R
import app.kluddra.android.data.local.ShoppingItemEntity
import app.kluddra.android.ui.components.KluddraEmptyState
import app.kluddra.android.ui.components.SyncStatusBanner
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    state: ShoppingListUiState,
    onAddItem: (String) -> Unit,
    onToggleChecked: (String) -> Unit,
    onUpdateText: (String, String) -> Unit,
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

    var inputText by remember { mutableStateOf("") }
    var editingItem by remember { mutableStateOf<ShoppingItemEntity?>(null) }
    var editingText by remember { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    // Local mutable mirror of active items during drag gesture for 60/120fps reordering
    var localActive by remember { mutableStateOf(state.activeItems) }
    var lastMovedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.activeItems) {
        localActive = state.activeItems
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = localActive.indexOfFirst { it.id == from.key }
        val toIdx = localActive.indexOfFirst { it.id == to.key }
        if (fromIdx != -1 && toIdx != -1) {
            val mutable = localActive.toMutableList()
            val moved = mutable.removeAt(fromIdx)
            mutable.add(toIdx, moved)
            localActive = mutable
            lastMovedId = moved.id
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val undoLabel = stringResource(R.string.action_undo)
    val removedItemMsg = stringResource(R.string.shopping_item_removed)
    val clearedListMsg = stringResource(R.string.shopping_cleared_snackbar)

    val totalCount = state.activeItems.size + state.checkedItems.size

    fun showUndoSnackbar(message: String, removed: List<ShoppingItemEntity>) {
        if (removed.isEmpty()) return
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
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
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.shopping_title),
                                style = MaterialTheme.typography.headlineSmall
                            )
                            if (totalCount > 0) {
                                Text(
                                    text = if (state.activeItems.isEmpty()) {
                                        stringResource(R.string.shopping_all_done)
                                    } else {
                                        stringResource(R.string.shopping_remaining_count, state.activeItems.size)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        if (totalCount > 0) {
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Outlined.MoreVert,
                                        contentDescription = stringResource(R.string.shopping_clear_all)
                                    )
                                }
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
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.nav_settings)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                SyncStatusBanner(
                    syncStatus = state.syncStatus,
                    pendingCount = state.pendingCount,
                    onRetry = onRefresh
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
            ) {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (localActive.isEmpty() && state.checkedItems.isEmpty()) {
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
                                top = 8.dp,
                                bottom = 24.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = localActive,
                                key = { it.id }
                            ) { item ->
                                ReorderableItem(reorderableState, key = item.id) { isDragging ->
                                    ShoppingItemRow(
                                        item = item,
                                        isDragging = isDragging,
                                        onToggle = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onToggleChecked(item.id)
                                        },
                                        onEditClick = {
                                            editingItem = item
                                            editingText = item.text
                                        },
                                        onDelete = {
                                            onDeleteItem(item.id) { removed ->
                                                showUndoSnackbar(removedItemMsg, listOf(removed))
                                            }
                                        },
                                        dragHandleModifier = Modifier.draggableHandle(
                                            onDragStarted = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragStopped = {
                                                val moved = lastMovedId ?: item.id
                                                onReorderActive(localActive, moved)
                                                lastMovedId = null
                                            }
                                        ),
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }

                            if (state.checkedItems.isNotEmpty()) {
                                item(key = "checked_header") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 14.dp, bottom = 4.dp, start = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = stringResource(
                                                R.string.shopping_checked_section,
                                                state.checkedItems.size
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
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.shopping_clear_checked),
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                }

                                items(
                                    items = state.checkedItems,
                                    key = { it.id }
                                ) { item ->
                                    ShoppingItemRow(
                                        item = item,
                                        isDragging = false,
                                        onToggle = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onToggleChecked(item.id)
                                        },
                                        onEditClick = {
                                            editingItem = item
                                            editingText = item.text
                                        },
                                        onDelete = {
                                            onDeleteItem(item.id) { removed ->
                                                showUndoSnackbar(removedItemMsg, listOf(removed))
                                            }
                                        },
                                        dragHandleModifier = null,
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // One-handed rapid multi-item entry bar docked right above keyboard / bottom nav
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
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
                                // Support pasting multi-line lists cleanly!
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
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { submitNewItem() },
                        enabled = inputText.isNotBlank(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.shopping_add_button)
                        )
                    }
                }
            }
        }
    }

    // Inline edit dialog for a shopping item
    editingItem?.let { target ->
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text(stringResource(R.string.shopping_edit_item)) },
            text = {
                OutlinedTextField(
                    value = editingText,
                    onValueChange = { editingText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editingText.isNotBlank()) {
                            onUpdateText(target.id, editingText)
                        }
                        editingItem = null
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val id = target.id
                            editingItem = null
                            onDeleteItem(id) { removed ->
                                showUndoSnackbar(removedItemMsg, listOf(removed))
                            }
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.shopping_delete_item),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    TextButton(onClick = { editingItem = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingItemRow(
    item: ShoppingItemEntity,
    isDragging: Boolean,
    onToggle: () -> Unit,
    onEditClick: () -> Unit,
    onDelete: () -> Unit,
    dragHandleModifier: Modifier?,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            isDragging -> MaterialTheme.colorScheme.primaryContainer
            item.isChecked -> MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.65f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "shopping_row_bg"
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.shopping_delete_item),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        modifier = modifier
    ) {
        Surface(
            color = containerColor,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = if (isDragging) 6.dp else 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large 48dp one-handed checkbox tap target
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (item.isChecked) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Outlined.RadioButtonUnchecked
                        },
                        contentDescription = item.text,
                        tint = if (item.isChecked) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Tapping text opens quick edit while keeping row comfortable for one hand
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = onEditClick)
                        .padding(horizontal = 6.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (item.isChecked) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    }
                }
            }
        }
    }
}
