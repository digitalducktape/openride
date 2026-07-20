package dev.digitalducktape.openride.ui.classes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.digitalducktape.openride.core.content.ChannelSection
import dev.digitalducktape.openride.core.content.Video
import dev.digitalducktape.openride.ui.theme.MetricTextStyles
import kotlinx.coroutines.launch

/**
 * Classes tab (PRD P0-6, T10/v2): one horizontal row per configured YouTube channel, each
 * video a card with thumbnail/title/duration-when-known. Tapping a card starts a ride for
 * the active profile and opens the in-app player with metrics overlaid
 * ([dev.digitalducktape.openride.ui.ride.VideoRideScreen]) — superseding v1's handoff to
 * the YouTube app, see `docs/DECISIONS.md`.
 */
@Composable
fun ClassesScreen(
    viewModel: ClassesViewModel,
    onStartVideoRide: (videoId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val takenVideos by viewModel.takenVideos.collectAsState(initial = emptyMap())
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val state = uiState) {
            is ClassesUiState.Loading -> LoadingContent()
            is ClassesUiState.Loaded -> LoadedContent(
                state = state,
                takenVideos = takenVideos,
                onVideoSelected = { video ->
                    if (viewModel.startRideForVideo(video.id)) onStartVideoRide(video.id)
                },
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
    state: ClassesUiState.Loaded,
    takenVideos: Map<String, Long>,
    onVideoSelected: (Video) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nonEmptySections = state.sections.filter { it.videos.isNotEmpty() }

    Column(modifier = modifier.fillMaxSize().padding(vertical = 32.dp, horizontal = 48.dp)) {
        Text(
            text = "Classes",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        if (state.anyRefreshFailed) {
            RefreshFailedBanner(modifier = Modifier.padding(top = 16.dp))
        }

        if (nonEmptySections.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No classes available right now — check your connection and try again",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Tap to retry",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clickable(onClick = onRetry),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(top = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(nonEmptySections, key = { it.channelId }) { section ->
                    ChannelRow(
                        section = section,
                        takenVideos = takenVideos,
                        onVideoSelected = onVideoSelected,
                    )
                }
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
private fun ChannelRow(
    section: ChannelSection,
    takenVideos: Map<String, Long>,
    onVideoSelected: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 28.dp)) {
        // Tracked-caps shelf eyebrow (v2 redesign spec) — the bike app labels its content
        // shelves this way rather than with title-case headings.
        Text(
            text = section.channelName.uppercase(),
            style = MetricTextStyles.SectionEyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
