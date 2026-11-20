package com.suprcharg3d.dongleCTRL.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suprcharg3d.dongleCTRL.dsp.EqBand
import com.suprcharg3d.dongleCTRL.ui.PendingBandSelection
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Prompts the user to pick up to [PendingBandSelection.maxSelectable] active PEQ bands
 * when either importing/capturing >10 bands into the standard 10-band preset store
 * or applying a preset with more active bands than the target DAC supports.
 */
@Composable
fun BandSelectionDialog(
    pending: PendingBandSelection,
    onConfirm: (selectedCandidateIndices: List<Int>, customPresetName: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val maxSelect = pending.maxSelectable
    val candidates = pending.candidateBands

    var selectedIndices by remember(pending) {
        mutableStateOf(
            candidates.indices.take(maxSelect).toSet()
        )
    }

    val initialName = when (pending) {
        is PendingBandSelection.SaveImportedPreset -> pending.name
        is PendingBandSelection.SaveDacToPreset -> pending.name
        is PendingBandSelection.ApplyPresetToDac -> ""
    }
    var presetName by remember(pending) { mutableStateOf(initialName) }
    val showNameField = pending !is PendingBandSelection.ApplyPresetToDac

    val title = when (pending) {
        is PendingBandSelection.ApplyPresetToDac -> "Choose $maxSelect Bands for DAC"
        is PendingBandSelection.SaveImportedPreset -> "Choose Up to $maxSelect Bands"
        is PendingBandSelection.SaveDacToPreset -> "Choose Up to $maxSelect Bands"
    }

    val subtitle = when (pending) {
        is PendingBandSelection.ApplyPresetToDac ->
            "\"${pending.preset.name}\" has ${candidates.size} active bands, while ${pending.dacName} supports $maxSelect bands."
        is PendingBandSelection.SaveImportedPreset ->
            "Imported file has ${candidates.size} active bands. Presets store up to $maxSelect bands."
        is PendingBandSelection.SaveDacToPreset ->
            "This DAC has ${candidates.size} active bands. Select up to $maxSelect bands to save."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (showNameField) {
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("Preset Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Quick selection presets + counter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            selectedIndices = candidates.indices.take(maxSelect).toSet()
                        },
                        label = { Text("First $maxSelect") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = {
                            selectedIndices = candidates.indices
                                .sortedByDescending { abs(candidates[it].gainDb) }
                                .take(maxSelect)
                                .toSet()
                        },
                        label = { Text("Strongest") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { selectedIndices = emptySet() },
                        label = { Text("Clear") }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${selectedIndices.size}/$maxSelect",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selectedIndices.isNotEmpty()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    candidates.forEachIndexed { idx, band ->
                        val isChecked = idx in selectedIndices
                        val canSelectMore = selectedIndices.size < maxSelect
                        BandSelectionRow(
                            band = band,
                            checked = isChecked,
                            enabled = isChecked || canSelectMore,
                            onToggle = {
                                selectedIndices = if (isChecked) {
                                    selectedIndices - idx
                                } else if (canSelectMore) {
                                    selectedIndices + idx
                                } else {
                                    selectedIndices
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        selectedIndices.sorted(),
                        if (showNameField) presetName else null
                    )
                },
                enabled = selectedIndices.isNotEmpty()
            ) {
                Text(
                    if (pending is PendingBandSelection.ApplyPresetToDac) {
                        "Apply (${selectedIndices.size})"
                    } else {
                        "Save (${selectedIndices.size})"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun BandSelectionRow(
    band: EqBand,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val gainRounded = (band.gainDb * 10.0).roundToInt() / 10.0
    val gainStr = "${if (gainRounded > 0) "+" else ""}$gainRounded dB"
    val qRounded = (band.q * 100.0).roundToInt() / 100.0
    val freqLabel = if (band.frequencyHz >= 1000) {
        val khz = (band.frequencyHz / 100.0).roundToInt() / 10.0
        "$khz kHz"
    } else {
        "${band.frequencyHz} Hz"
    }
    Surface(
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.7f else 0.35f)
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onToggle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { if (enabled) onToggle() },
                enabled = enabled
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Band ${band.index + 1} · $freqLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${band.filterType.displayName} · Q $qRounded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = gainStr,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    gainRounded > 0.0 -> MaterialTheme.colorScheme.primary
                    gainRounded < 0.0 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(end = 6.dp)
            )
        }
    }
}
