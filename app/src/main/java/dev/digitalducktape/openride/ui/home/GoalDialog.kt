package dev.digitalducktape.openride.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.digitalducktape.openride.core.ride.RideGoal

private enum class GoalType { NONE, DURATION, OUTPUT }

private fun goalTypeOf(goal: RideGoal): GoalType = when (goal) {
    RideGoal.None -> GoalType.NONE
    is RideGoal.Duration -> GoalType.DURATION
    is RideGoal.Output -> GoalType.OUTPUT
}

/**
 * Pre-ride goal picker (PRD P1-3): no goal, a time target (minutes), or an output target
 * (kJ). Invalid/blank input on the selected type falls back to [RideGoal.None] on save
 * rather than blocking the dialog with a validation error — a wrong/missing goal number is
 * low-stakes (it only affects an on-screen progress bar), so keeping the picker simple beats
 * a full form-validation treatment here.
 */
@Composable
fun GoalDialog(
    currentGoal: RideGoal,
    onDismiss: () -> Unit,
    onSave: (RideGoal) -> Unit,
    modifier: Modifier = Modifier,
) {
    var goalType by remember { mutableStateOf(goalTypeOf(currentGoal)) }
    var minutesInput by remember {
        mutableStateOf(if (currentGoal is RideGoal.Duration) (currentGoal.targetSec / 60).toString() else "")
    }
    var outputInput by remember {
        mutableStateOf(if (currentGoal is RideGoal.Output) currentGoal.targetKj.toString() else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ride goal") },
        text = {
            Column {
                GoalTypeOption("No goal", goalType == GoalType.NONE) { goalType = GoalType.NONE }
                GoalTypeOption("Time target", goalType == GoalType.DURATION) { goalType = GoalType.DURATION }
                if (goalType == GoalType.DURATION) {
                    OutlinedTextField(
                        value = minutesInput,
                        onValueChange = { minutesInput = it },
                        label = { Text("Minutes") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.padding(start = 40.dp, top = 4.dp),
                    )
                }
                GoalTypeOption("Output target", goalType == GoalType.OUTPUT) { goalType = GoalType.OUTPUT }
                if (goalType == GoalType.OUTPUT) {
                    OutlinedTextField(
                        value = outputInput,
                        onValueChange = { outputInput = it },
                        label = { Text("Target kJ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.padding(start = 40.dp, top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val goal = when (goalType) {
                    GoalType.NONE -> RideGoal.None
                    GoalType.DURATION ->
                        minutesInput.toIntOrNull()?.takeIf { it > 0 }?.let { RideGoal.Duration(it * 60) }
                            ?: RideGoal.None
                    GoalType.OUTPUT ->
                        outputInput.toDoubleOrNull()?.takeIf { it > 0.0 }?.let { RideGoal.Output(it) }
                            ?: RideGoal.None
                }
                onSave(goal)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        modifier = modifier,
    )
}

@Composable
private fun GoalTypeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

/** Compact label for the current goal, used on the Home screen (PRD P1-3). */
fun goalSummaryLabel(goal: RideGoal): String = when (goal) {
    RideGoal.None -> "No goal set"
    is RideGoal.Duration -> "Goal: ${goal.targetSec / 60} min"
    is RideGoal.Output -> "Goal: %.0f kJ".format(goal.targetKj)
}
