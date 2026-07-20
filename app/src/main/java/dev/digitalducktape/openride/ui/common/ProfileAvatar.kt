package dev.digitalducktape.openride.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import coil.compose.AsyncImage
import dev.digitalducktape.openride.core.data.Profile
import java.io.File

/**
 * The rider's circular avatar, used everywhere one appears (profile select, home header,
 * profile tab, the editor's preview): the camera photo when one is set (already
 * center-cropped square by AvatarPhotoStore, clipped to a circle here), otherwise the
 * emoji on its color disc. `null` [profile] renders the neutral "no rider" placeholder.
 */
@Composable
fun ProfileAvatar(
    profile: Profile?,
    size: Dp,
    emojiSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    ProfileAvatar(
        photoPath = profile?.avatarPhotoPath,
        color = profile?.let { Color(it.avatarColor) } ?: MaterialTheme.colorScheme.surfaceVariant,
        emoji = profile?.avatarEmoji ?: "👤",
        size = size,
        emojiSize = emojiSize,
        modifier = modifier,
    )
}

/** Field-level variant for the profile editor, where no [Profile] row exists yet. */
@Composable
fun ProfileAvatar(
    photoPath: String?,
    color: Color,
    emoji: String,
    size: Dp,
    emojiSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    // requiredSize: the avatar is the one element that must never be squeezed out of round
    // by a tight parent constraint.
    val circle = modifier.requiredSize(size).clip(CircleShape)
    if (photoPath != null) {
        AsyncImage(
            model = File(photoPath),
            contentDescription = "Profile photo",
            contentScale = ContentScale.Crop,
            modifier = circle.background(color),
        )
    } else {
        Box(
            modifier = circle.background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emoji, fontSize = emojiSize)
        }
    }
}
