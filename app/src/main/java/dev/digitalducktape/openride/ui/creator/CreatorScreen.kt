package dev.digitalducktape.openride.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.digitalducktape.openride.core.content.Video
import dev.digitalducktape.openride.ui.classes.VideoCard
import dev.digitalducktape.openride.ui.theme.MetricTextStyles

/**
 * A creator's page, reached by tapping their shelf name on the Classes tab: a LATEST shelf of
 * their recent classes, then one shelf per playlist they've curated.
 */
@Composable
fun CreatorScreen(
    viewModel: CreatorViewModel,
    onStartVideoRide: (videoId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val takenVideos by viewModel.takenVideos.collectAsState(initial = emptyMap())

    LaunchedEffect(Unit) { viewModel.load() }

    val startRide: (Video) -> Unit = { video ->
        if (viewModel.startRideForVideo(video.id)) onStartVideoRide(video.id)
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val state = uiState) {
            is CreatorUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            is CreatorUiState.NotFound -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "That creator is no longer in your catalog",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is CreatorUiState.Loaded -> LoadedCreator(
                state = state,
                takenVideos = takenVideos,
                onVideoSelected = startRide,
                onPlaylistVisible = { playlistId -> viewModel.loadPlaylist(playlistId) },
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun LoadedCreator(
    state: CreatorUiState.Loaded,
    takenVideos: Map<String, Long>,
    onVideoSelected: (Video) -> Unit,
    onPlaylistVisible: suspend (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(vertical = 32.dp, horizontal = 48.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    // Icons.AutoMirrored.Filled.ArrowBack is in material-icons-core (verified in
                    // the bundled jar) and is already used this way elsewhere in the app.
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to classes",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = state.displayName,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (state.refreshFailed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Couldn't refresh this creator — showing the last saved list",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)) {
            item {
                Shelf(
                    title = "LATEST",
                    videos = state.latest,
                    takenVideos = takenVideos,
                    onVideoSelected = onVideoSelected,
                )
            }
            items(state.playlistRows, key = { it.playlist.id }) { row ->
                // Each playlist is its own page fetch, so it's requested when its shelf is
                // first composed rather than all at once when the page opens.
                LaunchedEffect(row.playlist.id) {
                    if (row.videos.isEmpty() && !row.isLoading && !row.loadFailed) {
                        onPlaylistVisible(row.playlist.id)
                    }
                }
                Shelf(
                    title = row.playlist.title.uppercase(),
                    videos = row.videos,
                    takenVideos = takenVideos,
                    onVideoSelected = onVideoSelected,
                    isLoading = row.isLoading,
                    emptyMessage = if (row.loadFailed) "Couldn't load this playlist" else null,
                )
            }
        }
    }
}

@Composable
private fun Shelf(
    title: String,
    videos: List<Video>,
    takenVideos: Map<String, Long>,
    onVideoSelected: (Video) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    emptyMessage: String? = null,
) {
    Column(modifier = modifier.padding(top = 28.dp)) {
        Text(
            text = title,
            style = MetricTextStyles.SectionEyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            isLoading -> CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp).size(28.dp),
            )
            videos.isEmpty() && emptyMessage != null -> Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            else -> LazyRow(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(videos, key = { it.id }) { video ->
                    VideoCard(
                        video = video,
                        takenEpochMs = takenVideos[video.id],
                        onClick = { onVideoSelected(video) },
                    )
                }
            }
        }
    }
}
