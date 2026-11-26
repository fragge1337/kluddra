package app.kluddra.android.ui.notes

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.kluddra.android.R
import app.kluddra.android.ui.theme.LocalKluddraDarkTheme
import app.kluddra.android.ui.theme.LocalKluddraSemantics
import app.kluddra.android.ui.theme.NoteColor
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoteEditorScreen(
    state: NoteEditorUiState,
    showBackButton: Boolean,
    onTitleChange: (String) -> Unit,
    onBlockFocused: (Int) -> Unit,
    onBlockTextChange: (Int, String) -> Unit,
    onBlockBackspaceWhenEmpty: (Int) -> Unit,
    onToggleBlockCheckbox: (Int) -> Unit,
    onToggleListType: (NoteBlockType) -> Unit,
    onSelectColor: (NoteColor) -> Unit,
    onTogglePin: () -> Unit,
    onToggleReadOnly: () -> Unit,
    onDelete: () -> Unit,
    onFlushSave: () -> Unit,
    onBack: () -> Unit,
    shareTextProvider: () -> String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val shareTitle = stringResource(R.string.share_chooser_title)
    val darkTheme = LocalKluddraDarkTheme.current
    val semantics = LocalKluddraSemantics.current
    val noteColor = NoteColor.fromKey(state.colorKey)
    val editorBackground by animateColorAsState(
        targetValue = noteColor.editorBackground(darkTheme, MaterialTheme.colorScheme.background),
        animationSpec = tween(260),
        label = "note_editor_background"
    )
    val boldVisualTransformation = remember { BoldMarkdownVisualTransformation() }

    var showColorPicker by remember(state.noteId) { mutableStateOf(false) }
    var isBoldTypingMode by remember(state.noteId) { mutableStateOf(false) }
    // Lock ALL text field focus when opening an existing note so Android/Compose can NEVER
    // auto-focus a field or pop up the keyboard while reading. Unlocks on the user's first touch.
    var allowTitleFocus by remember(state.noteId) { mutableStateOf(false) }
    var allowBodyFocus by remember(state.noteId) { mutableStateOf(state.isNewNote) }
    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    var colorPickerBounds by remember { mutableStateOf<Rect?>(null) }
    var paletteButtonBounds by remember { mutableStateOf<Rect?>(null) }

    BackHandler(enabled = showColorPicker) {
        showColorPicker = false
    }

    // Per-block FocusRequesters and TextFieldValues (preserves exact selection for Bold button!)
    val focusRequesters = remember(state.noteId) { mutableStateMapOf<String, FocusRequester>() }
    val fieldValues = remember(state.noteId) { mutableStateMapOf<String, TextFieldValue>() }

    // Opening an existing note must NEVER automatically pop up the keyboard.
    LaunchedEffect(state.noteId, state.isLoaded) {
        if (!state.isNewNote && state.focusRequestCounter == 0) {
            allowTitleFocus = false
            allowBodyFocus = false
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    // Focus straight into the active content block ONLY when creating a new note (focusRequestCounter = 1)
    // or when explicitly splitting/toggling blocks while editing (focusRequestCounter > 0).
    LaunchedEffect(state.noteId, state.focusRequestCounter, state.isLoaded) {
        if (state.isLoaded && !state.isReadOnly && state.focusRequestCounter > 0 && state.blocks.isNotEmpty()) {
            allowBodyFocus = true
            val targetIdx = state.focusedBlockIndex.coerceIn(0, state.blocks.lastIndex)
            val targetUid = state.blocks[targetIdx].uid
            if (state.isNewNote && state.focusRequestCounter == 1) {
                delay(120L)
            }
            focusRequesters[targetUid]?.let { req ->
                runCatching {
                    req.requestFocus()
                    keyboardController?.show()
                }
            }
            allowTitleFocus = true
        }
    }

    DisposableEffect(state.noteId) {
        onDispose { onFlushSave() }
    }

    fun applyBoldToFocusedBlock() {
        if (state.isReadOnly || state.blocks.isEmpty()) return
        val idx = state.focusedBlockIndex.coerceIn(0, state.blocks.lastIndex)
        val block = state.blocks[idx]
        val currentValue = fieldValues[block.uid] ?: TextFieldValue(block.text, TextRange(block.text.length))

        if (!currentValue.selection.collapsed) {
            val toggled = NoteFormatting.toggleBold(currentValue)
            fieldValues[block.uid] = toggled
            onBlockTextChange(idx, toggled.text)
            isBoldTypingMode = NoteFormatting.isSelectionFullyBold(toggled.text, toggled.selection)
        } else {
            val currentlyInBold = NoteFormatting.isCursorInBold(currentValue.text, currentValue.selection.start)
            if (isBoldTypingMode || currentlyInBold) {
                isBoldTypingMode = false
                if (currentlyInBold) {
                    fieldValues[block.uid] = NoteFormatting.exitBoldAtCursor(currentValue)
                }
            } else {
                isBoldTypingMode = true
            }
        }
    }

    val focusedBlock = state.blocks.getOrNull(state.focusedBlockIndex)
    val focusedFieldVal = focusedBlock?.let { fieldValues[it.uid] }
    val isBoldButtonActive = if (focusedFieldVal != null && !focusedFieldVal.selection.collapsed) {
        NoteFormatting.isSelectionFullyBold(focusedFieldVal.text, focusedFieldVal.selection)
    } else {
        isBoldTypingMode || (
            focusedFieldVal != null &&
                NoteFormatting.isCursorInBold(focusedFieldVal.text, focusedFieldVal.selection.start)
            )
    }
    val autoTitlePreview = if (!state.hasManualTitle && state.title.isBlank()) {
        NoteFormatting.autoGenerateTitle(state.serializedBody)
    } else {
        ""
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOffset = it.positionInRoot() }
            .pointerInput(state.noteId) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.firstOrNull { it.pressed && !it.previousPressed }
                        if (down != null) {
                            // Unlock text focus on deliberate user touch (scrolling still won't open IME,
                            // while tapping any word/title focuses immediately).
                            if (!allowBodyFocus) allowBodyFocus = true
                            if (!allowTitleFocus) allowTitleFocus = true
                            if (showColorPicker) {
                                val rootPos = down.position + rootOffset
                                val insidePicker = colorPickerBounds?.contains(rootPos) == true
                                val insidePaletteBtn = paletteButtonBounds?.contains(rootPos) == true
                                if (!insidePicker && !insidePaletteBtn) {
                                    showColorPicker = false
                                }
                            }
                        }
                    }
                }
            }
    ) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = editorBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Subtle color dot reflecting this note's card color
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(noteColor.swatch(darkTheme))
                        )
                        if (state.isReadOnly) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.badge_read_only),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(
                            onClick = {
                                onFlushSave()
                                onBack()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    }
                },
                actions = {
                    if (!state.isReadOnly) {
                        IconToggleButton(
                            checked = state.isPinned,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTogglePin()
                            }
                        ) {
                            Icon(
                                imageVector = if (state.isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                                contentDescription = stringResource(
                                    if (state.isPinned) R.string.action_unpin else R.string.action_pin
                                ),
                                tint = if (state.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleReadOnly()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = stringResource(R.string.action_lock),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            val content = shareTextProvider()
                            if (content.isNotBlank()) {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, content)
                                    val subj = state.effectiveTitleForSave
                                    if (subj.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subj)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, shareTitle))
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = stringResource(R.string.action_share),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDelete()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = editorBackground
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 22.dp)
                    .widthIn(max = 720.dp)
                    .align(Alignment.TopCenter)
            ) {
                AnimatedVisibility(
                    visible = state.isConflictCopy,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        color = semantics.warningContainer,
                        contentColor = semantics.onWarningContainer,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.conflict_banner),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (state.isReadOnly) {
                    // Read-only mode: renders rich bold, numbered lists, and locked checkboxes
                    // inside SelectionContainer -> keyboard NEVER opens, text can be selected/copied.
                    SelectionContainer {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val displayTitle = state.effectiveTitleForSave
                            if (displayTitle.isNotBlank()) {
                                Text(
                                    text = displayTitle,
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            state.blocks.forEachIndexed { idx, block ->
                                when (block.type) {
                                    NoteBlockType.CHECKBOX -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (block.isChecked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Text(
                                                text = NoteFormatting.renderInlineBold(block.text),
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    textDecoration = if (block.isChecked) TextDecoration.LineThrough else TextDecoration.None
                                                ),
                                                color = if (block.isChecked) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                } else {
                                                    MaterialTheme.colorScheme.onBackground
                                                }
                                            )
                                        }
                                    }
                                    NoteBlockType.NUMBERED -> {
                                        val num = NoteFormatting.numberForIndex(state.blocks, idx)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = "$num.",
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = NoteFormatting.renderInlineBold(block.text),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                    }
                                    NoteBlockType.PARAGRAPH -> {
                                        Text(
                                            text = NoteFormatting.renderInlineBold(block.text),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Optional Title field (auto-derived if left blank, never steals initial new-note focus!)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (state.title.isEmpty()) {
                            Text(
                                text = autoTitlePreview.ifBlank { stringResource(R.string.editor_title_hint) },
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        BasicTextField(
                            value = state.title,
                            onValueChange = onTitleChange,
                            textStyle = MaterialTheme.typography.headlineSmall.copy(
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusProperties { canFocus = allowTitleFocus }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Interactive structured content blocks (Paragraphs, Checkboxes, Numbered Lists, Bold)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 320.dp)
                            .clickable(
                                interactionSource = null,
                                indication = null
                            ) {
                                // Tapping empty canvas below lines unlocks focus and focuses the last line
                                allowBodyFocus = true
                                allowTitleFocus = true
                                val last = state.blocks.lastOrNull() ?: return@clickable
                                focusRequesters[last.uid]?.let { runCatching { it.requestFocus() } }
                            }
                            .padding(bottom = 140.dp)
                    ) {
                        state.blocks.forEachIndexed { idx, block ->
                            key(block.uid) {
                            val req = remember(block.uid) {
                                focusRequesters.getOrPut(block.uid) { FocusRequester() }
                            }
                            val storedVal = fieldValues[block.uid]
                            val tfValue = if (storedVal != null && storedVal.text == block.text) {
                                storedVal
                            } else {
                                TextFieldValue(block.text, TextRange(block.text.length)).also {
                                    fieldValues[block.uid] = it
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                when (block.type) {
                                    NoteBlockType.CHECKBOX -> {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onToggleBlockCheckbox(idx)
                                                }
                                        ) {
                                            Icon(
                                                imageVector = if (block.isChecked) {
                                                    Icons.Outlined.CheckBox
                                                } else {
                                                    Icons.Outlined.CheckBoxOutlineBlank
                                                },
                                                contentDescription = null,
                                                tint = if (block.isChecked) {
                                                    semantics.success
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                },
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    NoteBlockType.NUMBERED -> {
                                        val num = NoteFormatting.numberForIndex(state.blocks, idx)
                                        Text(
                                            text = "$num.",
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 2.dp, end = 4.dp)
                                        )
                                    }
                                    NoteBlockType.PARAGRAPH -> Unit
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(top = if (block.type == NoteBlockType.CHECKBOX) 3.dp else 0.dp)
                                ) {
                                    if (idx == 0 && state.blocks.size == 1 && block.text.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.editor_body_hint),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                        )
                                    }
                                    BasicTextField(
                                        value = tfValue,
                                        onValueChange = { rawUpdated ->
                                            val updated = NoteFormatting.applyBoldTypingDelta(
                                                previous = tfValue,
                                                updated = rawUpdated,
                                                boldTypingActive = isBoldTypingMode
                                            )
                                            fieldValues[block.uid] = updated
                                            if (updated.text != block.text) {
                                                if (updated.text.contains('\n')) {
                                                    isBoldTypingMode = false
                                                }
                                                onBlockTextChange(idx, updated.text)
                                            } else {
                                                // Cursor moved without typing -> sync bold button state
                                                isBoldTypingMode = NoteFormatting.isCursorInBold(
                                                    updated.text,
                                                    updated.selection.start
                                                )
                                            }
                                        },
                                        visualTransformation = boldVisualTransformation,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            color = if (block.type == NoteBlockType.CHECKBOX && block.isChecked) {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            } else {
                                                MaterialTheme.colorScheme.onBackground
                                            },
                                            textDecoration = if (block.type == NoteBlockType.CHECKBOX && block.isChecked) {
                                                TextDecoration.LineThrough
                                            } else {
                                                TextDecoration.None
                                            }
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.Sentences
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusProperties { canFocus = allowBodyFocus }
                                            .focusRequester(req)
                                            .onFocusChanged { fs ->
                                                if (fs.isFocused) onBlockFocused(idx)
                                            }
                                            .onPreviewKeyEvent { event ->
                                                if (event.type == KeyEventType.KeyDown &&
                                                    event.key == Key.Backspace &&
                                                    block.text.isEmpty()
                                                ) {
                                                    onBlockBackspaceWhenEmpty(idx)
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                    )
                                }
                            }
                            }
                        }
                    }
                }
            }

            // Bottom floating controls: Color swatch tray + Material 3 Expressive HorizontalFloatingToolbar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp)
            ) {
                AnimatedVisibility(
                    visible = showColorPicker && !state.isReadOnly,
                    enter = fadeIn(animationSpec = tween(150)) + scaleIn(
                        initialScale = 0.92f,
                        transformOrigin = TransformOrigin(0.5f, 1f),
                        animationSpec = tween(160)
                    ),
                    exit = fadeOut(animationSpec = tween(120)) + scaleOut(
                        targetScale = 0.92f,
                        transformOrigin = TransformOrigin(0.5f, 1f),
                        animationSpec = tween(120)
                    )
                ) {
                    // Transparent outer padding prevents clipping of the drop shadow during animation.
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Surface(
                            color = if (darkTheme) {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            },
                            shape = RoundedCornerShape(28.dp),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (darkTheme) 0.75f else 0.55f)
                            ),
                            shadowElevation = 8.dp,
                            modifier = Modifier.onGloballyPositioned { colorPickerBounds = it.boundsInRoot() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NoteColor.entries.forEach { option ->
                                    val selected = option.key == noteColor.key
                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSelectColor(option)
                                            showColorPicker = false
                                        },
                                        color = option.swatch(darkTheme),
                                        shape = CircleShape,
                                        border = if (selected) {
                                            BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
                                        } else {
                                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        if (selected) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = null,
                                                    tint = option.onContainer(darkTheme),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalFloatingToolbar(
                    expanded = true,
                    colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                        toolbarContainerColor = if (darkTheme) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        },
                        toolbarContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .shadow(
                            elevation = if (darkTheme) 8.dp else 4.dp,
                            shape = CircleShape,
                            clip = false
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (darkTheme) 0.65f else 0.45f),
                            shape = CircleShape
                        )
                ) {
                    if (state.isReadOnly) {
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleReadOnly()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LockOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(stringResource(R.string.action_unlock))
                        }
                    } else {
                        IconToggleButton(
                            checked = isBoldButtonActive,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                applyBoldToFocusedBlock()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FormatBold,
                                contentDescription = stringResource(R.string.format_bold),
                                tint = if (isBoldButtonActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }

                        IconToggleButton(
                            checked = focusedBlock?.type == NoteBlockType.CHECKBOX,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleListType(NoteBlockType.CHECKBOX)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckBox,
                                contentDescription = stringResource(R.string.format_checklist),
                                tint = if (focusedBlock?.type == NoteBlockType.CHECKBOX) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }

                        IconToggleButton(
                            checked = focusedBlock?.type == NoteBlockType.NUMBERED,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleListType(NoteBlockType.NUMBERED)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FormatListNumbered,
                                contentDescription = stringResource(R.string.format_numbered_list),
                                tint = if (focusedBlock?.type == NoteBlockType.NUMBERED) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }

                        IconToggleButton(
                            checked = showColorPicker,
                            onCheckedChange = { showColorPicker = !showColorPicker },
                            modifier = Modifier.onGloballyPositioned { paletteButtonBounds = it.boundsInRoot() }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Palette,
                                contentDescription = stringResource(R.string.action_note_color),
                                tint = if (showColorPicker) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    }
}
