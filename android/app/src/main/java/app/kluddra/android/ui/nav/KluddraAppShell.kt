package app.kluddra.android.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kluddra.android.AppContainer
import app.kluddra.android.R
import app.kluddra.android.ui.components.KluddraEmptyState
import app.kluddra.android.ui.theme.LocalKluddraDarkTheme
import app.kluddra.android.ui.theme.LocalKluddraElevation
import app.kluddra.android.ui.notes.NoteEditorScreen
import app.kluddra.android.ui.notes.NoteEditorViewModel
import app.kluddra.android.ui.notes.NotesOverviewScreen
import app.kluddra.android.ui.notes.NotesViewModel
import app.kluddra.android.ui.notes.TrashScreen
import app.kluddra.android.ui.onboarding.OnboardingScreen
import app.kluddra.android.ui.onboarding.OnboardingStep
import app.kluddra.android.ui.onboarding.OnboardingViewModel
import app.kluddra.android.ui.settings.SettingsScreen
import app.kluddra.android.ui.settings.SettingsViewModel
import app.kluddra.android.ui.shopping.ShoppingListScreen
import app.kluddra.android.ui.shopping.ShoppingViewModel
import kotlinx.coroutines.launch

enum class PrimaryDestination {
    NOTES,
    SHOPPING
}

enum class SecondaryOverlay {
    NONE,
    SETTINGS,
    TRASH,
    CONNECT_SERVER
}

data class SharedIncomingNote(
    val title: String,
    val body: String
)

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun KluddraAppShell(
    container: AppContainer,
    incomingSharedNote: SharedIncomingNote?,
    onConsumeIncomingShare: () -> Unit
) {
    val settings by container.preferences.settingsFlow.collectAsStateWithLifecycle(
        initialValue = null
    )

    // Avoid flashing onboarding before DataStore emits initial preference state
    val resolvedSettings = settings ?: return

    if (!resolvedSettings.hasCompletedOnboarding) {
        val onboardingVm: OnboardingViewModel = viewModel(
            key = "onboarding_welcome",
            factory = OnboardingViewModel.factory(container.authRepository)
        )
        val onboardingState by onboardingVm.uiState.collectAsStateWithLifecycle()
        OnboardingScreen(
            state = onboardingState,
            onUseLocalOnly = onboardingVm::chooseLocalOnly,
            onChooseServer = onboardingVm::startServerConnect,
            onBackFromServer = onboardingVm::goBackToWelcome,
            onServerUrlChange = onboardingVm::onServerUrlChange,
            onProbeServer = { onboardingVm.probeServer() },
            onConfirmLocalHttp = onboardingVm::confirmLocalHttpConsent,
            onDismissLocalHttp = onboardingVm::dismissLocalHttpConsent,
            onSetupCodeChange = onboardingVm::onSetupCodeChange,
            onUsernameChange = onboardingVm::onUsernameChange,
            onPasswordChange = onboardingVm::onPasswordChange,
            onSubmitCredentials = onboardingVm::submitCredentials,
            onChangeServer = onboardingVm::goBackToServerStep
        )
        return
    }

    val pendingCountFlow = remember(container) { container.database.syncDao().observePendingCount() }

    val notesVm: NotesViewModel = viewModel(
        factory = NotesViewModel.factory(
            container.noteRepository,
            container.preferences,
            container.syncEngine,
            pendingCountFlow
        )
    )
    val editorVm: NoteEditorViewModel = viewModel(
        factory = NoteEditorViewModel.factory(container.noteRepository)
    )
    val shoppingVm: ShoppingViewModel = viewModel(
        factory = ShoppingViewModel.factory(
            container.shoppingRepository,
            container.syncEngine,
            pendingCountFlow
        )
    )
    val settingsVm: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            container.preferences,
            container.authRepository,
            container.syncEngine,
            container.localExporter,
            container.noteRepository,
            pendingCountFlow
        )
    )

    var primaryDestination by rememberSaveable { mutableStateOf(PrimaryDestination.NOTES) }
    var overlay by rememberSaveable { mutableStateOf(SecondaryOverlay.NONE) }
    var activeNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingShareNavigateNoteId by remember { mutableStateOf<String?>(null) }
    var isInSinglePaneEditor by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(primaryDestination) {
        if (primaryDestination != PrimaryDestination.NOTES) {
            isInSinglePaneEditor = false
        }
    }

    // Handle incoming ACTION_SEND text from other Android apps
    LaunchedEffect(incomingSharedNote) {
        if (incomingSharedNote != null) {
            overlay = SecondaryOverlay.NONE
            primaryDestination = PrimaryDestination.NOTES
            editorVm.openNote(
                noteId = null,
                initialTitle = incomingSharedNote.title,
                initialBody = incomingSharedNote.body
            )
            val freshId = editorVm.uiState.value.noteId
            activeNoteId = freshId
            pendingShareNavigateNoteId = freshId
            onConsumeIncomingShare()
        }
    }

    when (overlay) {
        SecondaryOverlay.SETTINGS -> {
            BackHandler { overlay = SecondaryOverlay.NONE }
            val settingsState by settingsVm.uiState.collectAsStateWithLifecycle()
            SettingsScreen(
                state = settingsState,
                onThemeModeChange = settingsVm::setThemeMode,
                onDynamicColorChange = settingsVm::setDynamicColor,
                onSyncNow = settingsVm::syncNow,
                onConnectServer = { overlay = SecondaryOverlay.CONNECT_SERVER },
                onRefreshSessions = settingsVm::refreshSessions,
                onRevokeSession = settingsVm::revokeSession,
                onRevokeOtherSessions = settingsVm::revokeOtherSessions,
                onOpenTrash = { overlay = SecondaryOverlay.TRASH },
                onExportJson = settingsVm::exportJson,
                onExportMarkdownZip = settingsVm::exportMarkdownZip,
                onSignOut = { keepLocalCopy ->
                    if (!keepLocalCopy) {
                        overlay = SecondaryOverlay.NONE
                    }
                    settingsVm.signOut(keepLocalCopy = keepLocalCopy)
                },
                onBack = { overlay = SecondaryOverlay.NONE }
            )
            return
        }

        SecondaryOverlay.CONNECT_SERVER -> {
            BackHandler { overlay = SecondaryOverlay.SETTINGS }
            val connectVm: OnboardingViewModel = viewModel(
                key = "settings_connect_server",
                factory = OnboardingViewModel.factory(container.authRepository)
            )
            LaunchedEffect(Unit) {
                connectVm.resetForStep(OnboardingStep.ENTER_SERVER)
            }
            LaunchedEffect(resolvedSettings.isSignedIn) {
                if (resolvedSettings.isSignedIn) {
                    overlay = SecondaryOverlay.SETTINGS
                }
            }
            val connectState by connectVm.uiState.collectAsStateWithLifecycle()
            OnboardingScreen(
                state = connectState,
                onUseLocalOnly = { overlay = SecondaryOverlay.SETTINGS },
                onChooseServer = connectVm::startServerConnect,
                onBackFromServer = { overlay = SecondaryOverlay.SETTINGS },
                onServerUrlChange = connectVm::onServerUrlChange,
                onProbeServer = { connectVm.probeServer() },
                onConfirmLocalHttp = connectVm::confirmLocalHttpConsent,
                onDismissLocalHttp = connectVm::dismissLocalHttpConsent,
                onSetupCodeChange = connectVm::onSetupCodeChange,
                onUsernameChange = connectVm::onUsernameChange,
                onPasswordChange = connectVm::onPasswordChange,
                onSubmitCredentials = connectVm::submitCredentials,
                onChangeServer = connectVm::goBackToServerStep
            )
            return
        }

        SecondaryOverlay.TRASH -> {
            BackHandler { overlay = SecondaryOverlay.SETTINGS }
            val trashed by notesVm.trashedNotes.collectAsStateWithLifecycle()
            TrashScreen(
                trashedNotes = trashed,
                onRestore = notesVm::restoreFromTrash,
                onDeleteForever = notesVm::purgeNote,
                onEmptyTrash = notesVm::emptyTrash,
                onBack = { overlay = SecondaryOverlay.SETTINGS }
            )
            return
        }

        SecondaryOverlay.NONE -> Unit
    }

    val config = LocalConfiguration.current
    val isCompactHeight = config.screenHeightDp < 480
    val isTabletLayout = !isCompactHeight && config.screenWidthDp >= 600
    val useSideRail = !isInSinglePaneEditor && (isTabletLayout || isCompactHeight)
    val useBottomBar = !isInSinglePaneEditor && !useSideRail

    val elevation = LocalKluddraElevation.current
    val darkTheme = LocalKluddraDarkTheme.current

    fun selectDestination(target: PrimaryDestination) {
        if (primaryDestination != target) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        primaryDestination = target
    }

    val mainContent: @Composable () -> Unit = {
        when (primaryDestination) {
            PrimaryDestination.NOTES -> {
                val notesState by notesVm.uiState.collectAsStateWithLifecycle()
                val editorState by editorVm.uiState.collectAsStateWithLifecycle()
                // Scoped strictly to PrimaryDestination.NOTES so the navigator's internal
                // SeekableTransitionState shares the exact lifecycle of NavigableListDetailPaneScaffold
                // (never reused across tab switches, Settings overlays, or layout changes).
                val listDetailNavigator = rememberListDetailPaneScaffoldNavigator<String>()
                val isDualPane =
                    listDetailNavigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded &&
                        listDetailNavigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
                val inSinglePaneDetail =
                    !isDualPane &&
                        listDetailNavigator.currentDestination?.pane == ListDetailPaneScaffoldRole.Detail

                LaunchedEffect(inSinglePaneDetail) {
                    isInSinglePaneEditor = inSinglePaneDetail
                }

                LaunchedEffect(pendingShareNavigateNoteId) {
                    val noteId = pendingShareNavigateNoteId
                    if (noteId != null) {
                        pendingShareNavigateNoteId = null
                        listDetailNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail, noteId)
                    }
                }

                NavigableListDetailPaneScaffold(
                    navigator = listDetailNavigator,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    listPane = {
                        AnimatedPane {
                            NotesOverviewScreen(
                                state = notesState,
                                isSyncEnabled = resolvedSettings.isSignedIn,
                                collapseTopBarOnScroll = isCompactHeight,
                                selectedNoteId = if (isDualPane) activeNoteId else null,
                                forceSingleColumn = isDualPane,
                                onQueryChange = notesVm::onQueryChange,
                                onToggleLayout = notesVm::toggleLayoutMode,
                                onSortOrderChange = notesVm::setSortOrder,
                                onNoteClick = { noteId ->
                                    activeNoteId = noteId
                                    editorVm.openNote(noteId)
                                    scope.launch {
                                        listDetailNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail, noteId)
                                    }
                                },
                                onCreateNote = {
                                    editorVm.openNote(null)
                                    val freshId = editorVm.uiState.value.noteId
                                    activeNoteId = freshId
                                    scope.launch {
                                        listDetailNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail, freshId)
                                    }
                                },
                                onTogglePin = notesVm::togglePin,
                                onToggleLock = notesVm::toggleReadOnly,
                                onTrashNote = { id ->
                                    notesVm.moveToTrash(id)
                                    if (activeNoteId == id) activeNoteId = null
                                },
                                onUndoTrash = notesVm::restoreFromTrash,
                                onOpenSettings = { overlay = SecondaryOverlay.SETTINGS },
                                onRefresh = notesVm::refreshSync
                            )
                        }
                    },
                    detailPane = {
                        AnimatedPane {
                            if (activeNoteId == null && isDualPane) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    KluddraEmptyState(
                                        icon = Icons.Outlined.EditNote,
                                        title = stringResource(R.string.select_note_prompt),
                                        subtitle = stringResource(R.string.empty_notes_subtitle)
                                    )
                                }
                            } else {
                                NoteEditorScreen(
                                    state = editorState,
                                    showBackButton = !isDualPane,
                                    collapseTopBarOnScroll = isCompactHeight,
                                    onTitleChange = editorVm::onTitleChange,
                                    onBlockFocused = editorVm::onBlockFocused,
                                    onBlockTextChange = editorVm::onBlockTextChange,
                                    onBlockBackspaceWhenEmpty = editorVm::onBlockBackspaceWhenEmpty,
                                    onToggleBlockCheckbox = editorVm::toggleBlockCheckbox,
                                    onToggleListType = editorVm::toggleListTypeOnFocusedBlock,
                                    onSelectColor = editorVm::setColor,
                                    onTogglePin = editorVm::togglePin,
                                    onToggleReadOnly = editorVm::toggleReadOnly,
                                    onDelete = {
                                        editorVm.moveToTrash()
                                        activeNoteId = null
                                        if (!isDualPane) {
                                            scope.launch { listDetailNavigator.navigateBack() }
                                        }
                                    },
                                    onFlushSave = editorVm::flushSaveNow,
                                    onBack = {
                                        scope.launch { listDetailNavigator.navigateBack() }
                                    },
                                    shareTextProvider = editorVm::shareText
                                )
                            }
                        }
                    }
                )
            }

            PrimaryDestination.SHOPPING -> {
                val shoppingState by shoppingVm.uiState.collectAsStateWithLifecycle()
                ShoppingListScreen(
                    state = shoppingState,
                    isSyncEnabled = resolvedSettings.isSignedIn,
                    collapseTopBarOnScroll = isCompactHeight,
                    onAddItem = shoppingVm::addItem,
                    onToggleChecked = shoppingVm::toggleChecked,
                    onReorderActive = shoppingVm::reorderActive,
                    onDeleteItem = shoppingVm::deleteItem,
                    onClearChecked = shoppingVm::clearChecked,
                    onClearAll = shoppingVm::clearAll,
                    onRestoreItems = shoppingVm::restoreItems,
                    onOpenSettings = { overlay = SecondaryOverlay.SETTINGS },
                    onRefresh = shoppingVm::refreshSync
                )
            }
        }
    }

    // Single unified layout tree so mainContent() (and NavigableListDetailPaneScaffold's
    // SeekableTransitionState) ALWAYS remains in the exact same composition slot when switching
    // between side rail, bottom bar, single-pane editor, or rotating portrait <-> landscape!
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (useSideRail) {
                KluddraNavigationRail(
                    selected = primaryDestination,
                    isTablet = isTabletLayout,
                    onSelect = ::selectDestination
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (useSideRail) {
                            Modifier.consumeWindowInsets(
                                WindowInsets.displayCutout.only(WindowInsetsSides.Start)
                                    .union(WindowInsets.navigationBars.only(WindowInsetsSides.Start))
                            )
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (useBottomBar) {
                            Modifier.consumeWindowInsets(
                                WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                            )
                        } else {
                            Modifier
                        }
                    )
            ) {
                mainContent()

                // Seamless 6-stop eased shadow along the right edge of the side rail
                if (useSideRail) {
                    val shadowBase = elevation.navBarTopShadow
                    val maxAlpha = shadowBase.alpha
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(18.dp)
                            .background(
                                Brush.horizontalGradient(
                                    0.00f to shadowBase,
                                    0.12f to shadowBase.copy(alpha = maxAlpha * 0.68f),
                                    0.28f to shadowBase.copy(alpha = maxAlpha * 0.36f),
                                    0.50f to shadowBase.copy(alpha = maxAlpha * 0.14f),
                                    0.75f to shadowBase.copy(alpha = maxAlpha * 0.04f),
                                    1.00f to Color.Transparent
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(0.75.dp)
                            .background(
                                if (darkTheme) {
                                    elevation.cardTopHighlight.copy(alpha = 0.08f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)
                                }
                            )
                    )
                }

                // Seamless exponentially-eased upward shadow above the anchored bottom
                // NavigationBar (zero Mach-banding, zero solid seam, zero IME height impact).
                if (useBottomBar) {
                    val shadowBase = elevation.navBarTopShadow
                    val maxAlpha = shadowBase.alpha
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(22.dp)
                            .background(
                                Brush.verticalGradient(
                                    0.00f to Color.Transparent,
                                    0.25f to shadowBase.copy(alpha = maxAlpha * 0.04f),
                                    0.50f to shadowBase.copy(alpha = maxAlpha * 0.14f),
                                    0.72f to shadowBase.copy(alpha = maxAlpha * 0.36f),
                                    0.88f to shadowBase.copy(alpha = maxAlpha * 0.68f),
                                    1.00f to shadowBase
                                )
                            )
                    )
                }
            }
        }

        if (useBottomBar) {
            NavigationBar(
                containerColor = elevation.navBarContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = primaryDestination == PrimaryDestination.NOTES,
                    onClick = { selectDestination(PrimaryDestination.NOTES) },
                    icon = {
                        Icon(
                            imageVector = if (primaryDestination == PrimaryDestination.NOTES) {
                                Icons.Rounded.Description
                            } else {
                                Icons.Outlined.Description
                            },
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.nav_notes)) }
                )
                NavigationBarItem(
                    selected = primaryDestination == PrimaryDestination.SHOPPING,
                    onClick = { selectDestination(PrimaryDestination.SHOPPING) },
                    icon = {
                        Icon(
                            imageVector = if (primaryDestination == PrimaryDestination.SHOPPING) {
                                Icons.Rounded.ShoppingBag
                            } else {
                                Icons.Outlined.ShoppingBag
                            },
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.nav_shopping)) }
                )
            }
        }
    }
}

/**
 * Vertically-centered Material 3 Expressive navigation rail for tablets and landscape phones.
 * Keeps the two primary destinations generously sized and thumb-reachable in the middle of the
 * left rail instead of clustering tiny icons at the top corner.
 */
@Composable
private fun KluddraNavigationRail(
    selected: PrimaryDestination,
    isTablet: Boolean,
    onSelect: (PrimaryDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val elevation = LocalKluddraElevation.current
    val railWidth = if (isTablet) 96.dp else 80.dp
    // On landscape phones, 28dp vertical spacing lets center punch-hole cameras sit cleanly
    // in the open gap between the two destinations without wasting a ~34dp blank strip on the left.
    val itemSpacing = if (isTablet) 24.dp else 28.dp

    Surface(
        color = elevation.navBarContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Start))
                .width(railWidth)
                .padding(horizontal = 6.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(itemSpacing, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            KluddraRailDestinationItem(
                label = stringResource(R.string.nav_notes),
                icon = if (selected == PrimaryDestination.NOTES) {
                    Icons.Rounded.Description
                } else {
                    Icons.Outlined.Description
                },
                selected = selected == PrimaryDestination.NOTES,
                isTablet = isTablet,
                onClick = { onSelect(PrimaryDestination.NOTES) }
            )
            KluddraRailDestinationItem(
                label = stringResource(R.string.nav_shopping),
                icon = if (selected == PrimaryDestination.SHOPPING) {
                    Icons.Rounded.ShoppingBag
                } else {
                    Icons.Outlined.ShoppingBag
                },
                selected = selected == PrimaryDestination.SHOPPING,
                isTablet = isTablet,
                onClick = { onSelect(PrimaryDestination.SHOPPING) }
            )
        }
    }
}

@Composable
private fun KluddraRailDestinationItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    isTablet: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "rail_item_scale"
    )
    val pillColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = tween(180),
        label = "rail_pill_bg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(160),
        label = "rail_icon_tint"
    )

    val itemShape = RoundedCornerShape(20.dp)
    val pillWidth = if (isTablet) 68.dp else 56.dp
    val pillHeight = if (isTablet) 40.dp else 34.dp
    val iconSize = if (isTablet) 26.dp else 23.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (isTablet) 6.dp else 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(itemShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Tab,
                onClick = onClick
            )
            .padding(vertical = if (isTablet) 8.dp else 6.dp, horizontal = 2.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = pillWidth, height = pillHeight)
                .clip(RoundedCornerShape(pillHeight / 2))
                .background(pillColor)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
        }
        Text(
            text = label,
            style = if (isTablet) {
                MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
            } else {
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
            },
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
