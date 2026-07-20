package dev.digitalducktape.openride.ui.classes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.digitalducktape.openride.core.content.ChannelSection
import dev.digitalducktape.openride.core.content.Video
import dev.digitalducktape.openride.ui.common.TimeFormat
import dev.digitalducktape.openride.ui.theme.MetricTextStyles
import dev.digitalducktape.openride.ui.theme.OpenRideColors
import kotlinx.coroutines.launch

private val CardWidth = 280.dp
private val CardThumbnailHeight = 158.dp

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

@Composable
private fun VideoCard(
    video: Video,
    takenEpochMs: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(CardWidth)
            .clickable(onClick = onClick),
    ) {
        Box {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CardThumbnailHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            // "Already ridden" badge (v2): when this class was last taken by the active
            // rider — the reminder that keeps someone from unknowingly repeating a class.
            takenEpochMs?.let { epochMs ->
                TakenBadge(
                    epochMs = epochMs,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                )
            }
            // Duration is only shown when known — the RSS feed never includes it and
            // oEmbed doesn't reliably either (see YouTubeContentRepository), so most
            // videos simply won't have a badge here rather than showing a fake "0:00".
            video.durationSec?.let { seconds ->
                DurationBadge(
                    seconds = seconds,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                )
            }
        }
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Check + "TAKEN JUL 19" pill over the thumbnail of an already-ridden class. */
@Composable
private fun TakenBadge(epochMs: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = OpenRideColors.Success,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = TakenLabel.format(epochMs),
            style = MetricTextStyles.UnitLabel,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun DurationBadge(seconds: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = TimeFormat.elapsed(seconds),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
