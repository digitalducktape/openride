package dev.digitalducktape.openride.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.digitalducktape.openride.core.data.Profile
import dev.digitalducktape.openride.ui.common.ProfileAvatar
import dev.digitalducktape.openride.ui.theme.OpenRideColors

/**
 * Launch destination (PRD P0-3, P0-8), in the bike's welcome-screen arrangement (v2
 * redesign spec): wordmark on the left, a horizontal row of large rider avatars
 * center-stage, and the quiet "Add rider" action bottom-center — all over a soft
 * dark gradient rather than the flat tab background. Selecting a profile scopes the
 * whole session to it and moves on to Home.
 */
@Composable
fun ProfileSelectScreen(
    viewModel: ProfileSelectViewModel,
    onProfileSelected: () -> Unit,
    onAddRider: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profiles by viewModel.profiles.collectAsState(initial = emptyList())

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF23283B), Color(0xFF12131A), OpenRideColors.Background),
                ),
            ),
    ) {
        Text(
            text = "OPENRIDE",
            fontSize = 40.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 14.sp,
            color = OpenRideColors.OnBackground,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 64.dp),
        )

        LazyRow(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = 420.dp),
            horizontalArrangement = Arrangement.spacedBy(56.dp),
        ) {
            items(profiles, key = { it.id }) { profile ->
                RiderAvatar(
                    profile = profile,
                    onClick = {
                        viewModel.selectProfile(profile.id)
                        onProfileSelected()
                    },
                )
            }
        }

        AddRiderAction(
            onClick = onAddRider,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
        )
    }
}

@Composable
private fun RiderAvatar(profile: Profile, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(180.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProfileAvatar(profile = profile, size = 180.dp, emojiSize = 72.sp)
        Text(
            text = profile.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = OpenRideColors.OnBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

@Composable
private fun AddRiderAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(color = Color(0x33FFFFFF), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "+", fontSize = 28.sp, color = OpenRideColors.OnBackground)
        }
        Text(
            text = "Add rider",
            style = MaterialTheme.typography.bodyMedium,
            color = OpenRideColors.OnBackground,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
