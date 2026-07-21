package dev.digitalducktape.openride.ui.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.digitalducktape.openride.core.content.ContentCategory
import dev.digitalducktape.openride.core.content.ContentSource
import dev.digitalducktape.openride.core.content.ContentSourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Content Sources: add your own creators and playlists, and hide the built-in ones you don't
 * ride. Reached from the Profile tab.
 */
@Composable
fun ContentSourcesScreen(
    viewModel: ContentSourcesViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sources by viewModel.sources.collectAsStateList()
    val addState by viewModel.addState.collectAsState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(vertical = 32.dp, horizontal = 48.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = "Content sources",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Text(
                text = "Paste a YouTube channel link, @handle, or playlist link",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )

            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    placeholder = { Text("@creator or https://youtube.com/…") },
                    modifier = Modifier.width(520.dp),
                )
                Button(
                    onClick = { scope.launch { viewModel.resolve(input) } },
                    enabled = input.isNotBlank() && addState !is AddSourceState.Resolving,
                    // Material3's default Button height (40dp) falls short of the 48dp this
                    // bike's touchscreen needs; IconButton/Switch already meet it.
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Look up")
                }
            }

            when (val state = addState) {
                is AddSourceState.Resolving -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp),
                )

                is AddSourceState.Failed -> Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )

                is AddSourceState.Resolved -> Column(modifier = Modifier.padding(top = 16.dp)) {
                    // Local to this resolution: disables the row the instant a category is
                    // tapped so a double-tap can't fire confirmAdd twice while the first save
                    // is still in flight (the DB's unique index blocks the duplicate row, but
                    // which category wins would be nondeterministic and no error surfaces).
                    // Discarded and reset whenever a new Resolved state mounts.
                    var isSaving by remember { mutableStateOf(false) }
                    Text(
                        text = "Add \"${state.source.displayName}\" as:",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = {
                                isSaving = true
                                scope.launch {
                                    viewModel.confirmAdd(ContentCategory.Workout)
                                    input = ""
                                }
                            },
                            enabled = !isSaving,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Workout") }
                        Button(
                            onClick = {
                                isSaving = true
                                scope.launch {
                                    viewModel.confirmAdd(ContentCategory.Scenic)
                                    input = ""
                                }
                            },
                            enabled = !isSaving,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Scenic") }
                        OutlinedButton(
                            onClick = viewModel::cancelAdd,
                            enabled = !isSaving,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Cancel") }
                    }
                }

                is AddSourceState.Idle -> Unit
            }

            LazyColumn(
                modifier = Modifier.padding(top = 24.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(sources, key = { it.id }) { source ->
                    SourceRow(
                        source = source,
                        onToggleHidden = { scope.launch { viewModel.setHidden(source.id, !source.hidden) } },
                        onDelete = { scope.launch { viewModel.deleteCustom(source.id) } },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: ContentSource,
    onToggleHidden: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = buildString {
                    append(if (source.sourceType == ContentSourceType.PLAYLIST) "Playlist" else "Channel")
                    append("  •  ")
                    append(source.category.name)
                    if (source.builtIn) append("  •  Built in")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = !source.hidden, onCheckedChange = { onToggleHidden() })
        // Built-ins can only be hidden — keeping them means a rider can always get the
        // original catalog back without reinstalling.
        if (!source.builtIn) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Remove ${source.displayName}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun <T> Flow<List<T>>.collectAsStateList() = collectAsState(initial = emptyList())
