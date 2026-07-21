package dev.digitalducktape.openride.ui.classes

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.digitalducktape.openride.core.content.ChannelSection
import dev.digitalducktape.openride.core.content.Video
import dev.digitalducktape.openride.ui.theme.MetricTextStyles
import kotlinx.coroutines.launch

/**
 * Classes tab: browse by creator, or filter the whole catalog at once.
 *
 * Two layouts, one screen. With the default sort and no length filter the tab shows one
 * horizontal row per configured creator, which is how someone browses when they don't have
 * something specific in mind. Choosing a sort or a length is a question about the entire
 * catalog ("a 30-minute class, any creator"), so those switch to a flat grid — per-creator
 * rows can't answer it. Category chips narrow both layouts.
 */
@Composable
fun ClassesScreen(
    viewModel: ClassesViewModel,
    onStartVideoRide: (videoId: String) -> Unit,
    onOpenCreator: (sourceId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val takenVideos by viewModel.takenVideos.collectAsState(initial = emptyMap())
    val filters by viewModel.filters.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val gridVideos by viewModel.gridVideos.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.refresh() }

    val startRide: (Video) -> Unit = { video ->
        if (viewModel.startRideForVideo(video)) onStartVideoRide(video.id)
    }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val state = uiState) {
            is ClassesUiState.Loading -> LoadingContent()
            is ClassesUiState.Loaded -> LoadedContent(
                anyRefreshFailed = state.anyRefreshFailed,
                filters = filters,
                rows = rows,
                gridVideos = gridVideos,
                takenVideos = takenVideos,
                onCategorySelected = viewModel::setCategory,
                onSortSelected = viewModel::setSort,
                onLengthSelected = viewModel::setLength,
                onHideTakenToggled = viewModel::setHideTaken,
                onVideoSelected = startRide,
                onOpenCreator = onOpenCreator,
                onRandomRide = { viewModel.randomVideo()?.let(startRide) },
                onRetry = { scope.launch { viewModel.refresh() } },
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LoadedContent(
    anyRefreshFailed: Boolean,
    filters: ClassFilters,
    rows: List<ChannelSection>,
    gridVideos: List<Video>?,
    takenVideos: Map<String, Long>,
    onCategorySelected: (CategoryFilter) -> Unit,
    onSortSelected: (ClassSort) -> Unit,
    onLengthSelected: (LengthFilter) -> Unit,
    onHideTakenToggled: (Boolean) -> Unit,
    onVideoSelected: (Video) -> Unit,
    onOpenCreator: (Long) -> Unit,
    onRandomRide: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasAnything = gridVideos?.isNotEmpty() ?: rows.any { it.videos.isNotEmpty() }

    Column(modifier = modifier.fillMaxSize().padding(vertical = 32.dp, horizontal = 48.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Classes",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            // No dice/shuffle glyph is available without adding material-icons-extended (this
            // app only depends on material-icons-core), so the button is text-only rather
            // than pulling in a new dependency for one icon.
            Button(onClick = onRandomRide, enabled = hasAnything) {
                Text(text = "Random Ride")
            }
        }

        FilterBar(
            filters = filters,
            onCategorySelected = onCategorySelected,
            onSortSelected = onSortSelected,
            onLengthSelected = onLengthSelected,
            onHideTakenToggled = onHideTakenToggled,
            modifier = Modifier.padding(top = 16.dp),
        )

        if (anyRefreshFailed) {
            RefreshFailedBanner(modifier = Modifier.padding(top = 16.dp))
        }

        if (!hasAnything) {
            EmptyContent(isFiltered = !filters.isDefaultBrowse, onRetry = onRetry)
        } else if (gridVideos != null) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = CardWidth),
                modifier = Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(gridVideos, key = { it.id }) { video ->
                    VideoCard(
                        video = video,
                        takenEpochMs = takenVideos[video.id],
                        onClick = { onVideoSelected(video) },
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(top = 4.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(rows, key = { it.sourceId }) { section ->
                    ChannelRow(
                        section = section,
                        takenVideos = takenVideos,
                        onVideoSelected = onVideoSelected,
                        onOpenCreator = { onOpenCreator(section.sourceId) },
                    )
                }
            }
        }
    }
}

/** Empty state: a filter that matched nothing is a different problem than no content at all. */
@Composable
private fun EmptyContent(isFiltered: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isFiltered) {
                    "No classes match these filters"
                } else {
                    "No classes available right now — check your connection and try again"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isFiltered) {
                Text(
                    text = "Tap to retry",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp).clickable(onClick = onRetry),
                )
            }
        }
    }
}

@Composable
private fun RefreshFailedBanner(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = "Couldn't refresh one or more channels — showing the last saved list",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterBar(
    filters: ClassFilters,
    onCategorySelected: (CategoryFilter) -> Unit,
    onSortSelected: (ClassSort) -> Unit,
    onLengthSelected: (LengthFilter) -> Unit,
    onHideTakenToggled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryFilter.entries.forEach { category ->
            FilterChip(
                selected = filters.category == category,
                onClick = { onCategorySelected(category) },
                label = { Text(category.label) },
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        DropdownFilter(
            label = filters.sort.label,
            options = ClassSort.entries.map { it to it.label },
            onSelected = onSortSelected,
        )
        DropdownFilter(
            label = filters.length.label,
            options = LengthFilter.entries.map { it to it.label },
            onSelected = onLengthSelected,
        )

        FilterChip(
            selected = filters.hideTaken,
            onClick = { onHideTakenToggled(!filters.hideTaken) },
            label = { Text("Hide completed") },
        )
    }
}

/** A compact dropdown — the bike's screen is wide but touch targets need to stay large. */
@Composable
private fun <T> DropdownFilter(
    label: String,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }) {
            Text(label)
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelected(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun ChannelRow(
    section: ChannelSection,
    takenVideos: Map<String, Long>,
    onVideoSelected: (Video) -> Unit,
    onOpenCreator: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 28.dp)) {
        // Tapping the shelf eyebrow opens the creator's own page — their full recent catalog
        // plus the playlists they've curated, which the single row here can't show.
        Row(
            // Sized to a real touch target for a screen operated mid-ride, without growing the
            // shelf's visual rhythm — the eyebrow text stays centered at its normal size.
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(onClick = onOpenCreator),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = section.channelName.uppercase(),
                style = MetricTextStyles.SectionEyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // KeyboardArrowRight stands in for a chevron glyph — ChevronRight itself lives in
            // material-icons-extended, which this app doesn't depend on (see the Random Ride
            // button above for the same constraint).
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open ${section.channelName}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        LazyRow(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(section.videos, key = { it.id }) { video ->
                VideoCard(
                    video = video,
                    takenEpochMs = takenVideos[video.id],
                    onClick = { onVideoSelected(video) },
                )
            }
        }
    }
}
