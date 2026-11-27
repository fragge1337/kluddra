package app.kluddra.android.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kluddra.android.R
import app.kluddra.android.data.local.NoteEntity
import app.kluddra.android.ui.components.KluddraEmptyState
import app.kluddra.android.ui.components.kluddraCardShadow
import app.kluddra.android.ui.components.kluddraRaisedBorder
import app.kluddra.android.ui.theme.LocalKluddraDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    trashedNotes: List<NoteEntity>,
    onRestore: (String) -> Unit,
    onDeleteForever: (String) -> Unit,
    onEmptyTrash: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val darkTheme = LocalKluddraDarkTheme.current
    var confirmEmpty by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars.only(WindowInsetsSides.Top)
                    .union(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)),
                title = { Text(stringResource(R.string.trash_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (trashedNotes.isNotEmpty()) {
                        TextButton(onClick = { confirmEmpty = true }) {
                            Text(
                                text = stringResource(R.string.action_empty_trash),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (trashedNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                KluddraEmptyState(
                    icon = Icons.Outlined.DeleteOutline,
                    title = stringResource(R.string.trash_empty_title),
                    subtitle = stringResource(R.string.trash_empty_subtitle)
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                item(key = "trash_hint", contentType = "trash_hint") {
                    Text(
                        text = stringResource(R.string.trash_retention_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
                items(trashedNotes, key = { it.id }, contentType = { "trash_note" }) { note ->
                    val cardShape = RoundedCornerShape(24.dp)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = cardShape,
                        border = kluddraRaisedBorder(isColoredSurface = false),
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .kluddraCardShadow(
                                elevation = if (darkTheme) 8.dp else 4.dp,
                                shape = cardShape
                            )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (note.title.isNotBlank()) {
                                Text(
                                    text = note.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            if (note.body.isNotBlank()) {
                                Text(
                                    text = note.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                            ) {
                                TextButton(onClick = { onDeleteForever(note.id) }) {
                                    Text(
                                        text = stringResource(R.string.action_delete_forever),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                FilledTonalButton(onClick = { onRestore(note.id) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.RestoreFromTrash,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                    Text(stringResource(R.string.action_restore))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.confirm_empty_trash_title)) },
            text = { Text(stringResource(R.string.confirm_empty_trash_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmEmpty = false
                        onEmptyTrash()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_empty_trash),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
