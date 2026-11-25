package app.kluddra.android.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kluddra.android.AppContainer
import app.kluddra.android.R
import app.kluddra.android.ui.components.KluddraEmptyState
import app.kluddra.android.ui.notes.NoteEditorScreen
import app.kluddra.android.ui.notes.NoteEditorViewModel
import app.kluddra.android.ui.notes.NotesOverviewScreen
import app.kluddra.android.ui.notes.NotesViewModel
import app.kluddra.android.ui.notes.TrashScreen
import app.kluddra.android.ui.onboarding.OnboardingScreen
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
    TRASH
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

    if (!resolvedSettings.isSignedIn) {
        val onboardingVm: OnboardingViewModel = viewModel(
            factory = OnboardingViewModel.factory(container.authRepository)
        )
        val onboardingState by onboardingVm.uiState.collectAsStateWithLifecycle()
        OnboardingScreen(
            state = onboardingState,
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

    val listDetailNavigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()

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
            activeNoteId = editorVm.uiState.value.noteId
            listDetailNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail, activeNoteId)
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
                onLanguageChange = settingsVm::setLanguageTag,
                onSyncNow = settingsVm::syncNow,
                onOpenTrash = { overlay = SecondaryOverlay.TRASH },
                onExportJson = settingsVm::exportJson,
                onExportMarkdownZip = settingsVm::exportMarkdownZip,
                onSignOut = {
                    overlay = SecondaryOverlay.NONE
                    settingsVm.signOut()
                },
                onBack = { overlay = SecondaryOverlay.NONE }
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

    val isDualPane =
        listDetailNavigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded &&
            listDetailNavigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded

    val isInSinglePaneEditor =
        primaryDestination == PrimaryDestination.NOTES &&
            !isDualPane &&
            listDetailNavigator.currentDestination?.pane == ListDetailPaneScaffoldRole.Detail

    val adaptiveInfo = currentWindowAdaptiveInfo()
    val layoutType = if (isInSinglePaneEditor) {
        NavigationSuiteType.None
    } else {
        androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
            .calculateFromAdaptiveInfo(adaptiveInfo)
    }

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            item(
                selected = primaryDestination == PrimaryDestination.NOTES,
                onClick = { primaryDestination = PrimaryDestination.NOTES },
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
            item(
                selected = primaryDestination == PrimaryDestination.SHOPPING,
                onClick = { primaryDestination = PrimaryDestination.SHOPPING },
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
    ) {
        when (primaryDestination) {
            PrimaryDestination.NOTES -> {
                val notesState by notesVm.uiState.collectAsStateWithLifecycle()
                val editorState by editorVm.uiState.collectAsStateWithLifecycle()

                NavigableListDetailPaneScaffold(
                    navigator = listDetailNavigator,
                    listPane = {
                        AnimatedPane {
                            NotesOverviewScreen(
                                state = notesState,
                                selectedNoteId = if (isDualPane) activeNoteId else null,
                                forceSingleColumn = isDualPane,
                                onQueryChange = notesVm::onQueryChange,
                                onToggleLayout = notesVm::toggleLayoutMode,
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
                                    onTitleChange = editorVm::onTitleChange,
                                    onBodyChange = editorVm::onBodyChange,
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
                    onAddItem = shoppingVm::addItem,
                    onToggleChecked = shoppingVm::toggleChecked,
                    onUpdateText = shoppingVm::updateItemText,
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
}
