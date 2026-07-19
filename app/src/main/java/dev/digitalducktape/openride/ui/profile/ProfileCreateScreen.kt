package dev.digitalducktape.openride.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Create-profile flow (PRD P0-3): required name, avatar color/emoji picker, optional weight
 * (kg) and FTP fields. Saving sets the new profile active and moves on to Home.
 */
@Composable
fun ProfileCreateScreen(
    viewModel: ProfileCreateViewModel,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onSaved()
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Add rider",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Box(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .size(96.dp)
                    .background(color = Color(uiState.avatarColor), shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = uiState.avatarEmoji, style = MaterialTheme.typography.displaySmall)
            }

            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Name") },
                isError = uiState.nameError != null,
                supportingText = { uiState.nameError?.let { Text(it) } },
                singleLine = true,
                modifier = Modifier.width(360.dp).padding(top = 24.dp),
            )

            Text(
                text = "Avatar color",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(360.dp).padding(top = 24.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.width(360.dp).padding(top = 8.dp),
            ) {
                AvatarOptions.colors.forEach { colorInt ->
                    val selected = uiState.avatarColor == colorInt
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(color = Color(colorInt), shape = CircleShape)
                            .border(
                                width = if (selected) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onBackground,
                                shape = CircleShape,
                            )
                            .clickable { viewModel.onAvatarColorChange(colorInt) },
                    )
                }
            }

            Text(
                text = "Avatar icon",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(360.dp).padding(top = 24.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.width(360.dp).padding(top = 8.dp),
            ) {
                AvatarOptions.emojis.forEach { emoji ->
                    val selected = uiState.avatarEmoji == emoji
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape,
                            )
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            )
                            .clickable { viewModel.onAvatarEmojiChange(emoji) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = emoji, fontSize = 18.sp)
                    }
                }
            }

            OutlinedTextField(
                value = uiState.weightKgInput,
                onValueChange = viewModel::onWeightChange,
                label = { Text("Weight (kg) — optional") },
                isError = uiState.weightError != null,
                supportingText = { uiState.weightError?.let { Text(it) } },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(360.dp).padding(top = 24.dp),
            )

            OutlinedTextField(
                value = uiState.ftpInput,
                onValueChange = viewModel::onFtpChange,
                label = { Text("FTP (watts) — optional") },
                isError = uiState.ftpError != null,
                supportingText = { uiState.ftpError?.let { Text(it) } },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(360.dp).padding(top = 16.dp),
            )

            Button(
                onClick = { scope.launch { viewModel.save() } },
                modifier = Modifier.width(360.dp).padding(top = 32.dp),
            ) {
                Text("Save")
            }
            TextButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) {
                Text("Cancel")
            }
        }
    }
}
