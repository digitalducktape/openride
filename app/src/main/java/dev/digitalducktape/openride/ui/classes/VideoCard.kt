package dev.digitalducktape.openride.ui.classes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.digitalducktape.openride.core.content.Video
import dev.digitalducktape.openride.ui.common.TimeFormat
import dev.digitalducktape.openride.ui.theme.MetricTextStyles
import dev.digitalducktape.openride.ui.theme.OpenRideColors

val CardWidth = 280.dp
private val CardThumbnailHeight = 158.dp

@Composable
fun VideoCard(
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
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    // A feed-fallback class can't be verified as public, so it can't be started
                    // (ClassesViewModel.startRideForVideo refuses it). Dim it and badge it so it
                    // reads as unavailable rather than looking like a normal, tappable class.
                    .alpha(if (video.startable) 1f else 0.4f),
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
            if (!video.startable) {
                UnavailableBadge(
                    modifier = Modifier
                        .align(Alignment.Center),
                )
            }
        }
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (video.startable) 1f else 0.5f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Overlay marking a feed-fallback class that can't be started until a page fetch verifies it. */
@Composable
private fun UnavailableBadge(modifier: Modifier = Modifier) {
    Text(
        text = "Unavailable — refresh",
        style = MetricTextStyles.UnitLabel,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
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
