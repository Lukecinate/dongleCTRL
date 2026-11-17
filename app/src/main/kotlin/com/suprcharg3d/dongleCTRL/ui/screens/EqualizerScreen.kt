package com.suprcharg3d.dongleCTRL.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.suprcharg3d.dongleCTRL.ui.ActiveDacSession
import com.suprcharg3d.dongleCTRL.ui.MainViewModel
import com.suprcharg3d.dongleCTRL.ui.components.InteractivePeqGraph
import com.suprcharg3d.dongleCTRL.dsp.BiquadCalculator
import com.suprcharg3d.dongleCTRL.dsp.EqBand
import com.suprcharg3d.dongleCTRL.dsp.EqPreset
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(
    session: ActiveDacSession,
    selectedBandIndex: Int,
    presets: List<EqPreset>,
    autoApplyEnabled: Boolean,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onRequestImportFile: () -> Unit,
    onRequestExportFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val caps = session.capabilities
    val bands = session.state.bands
    val selectedBand = bands.getOrNull(selectedBandIndex) ?: bands.firstOrNull()

    var showPresetDialog by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompact = maxHeight < 760.dp
        val isVeryCompact = maxHeight < 640.dp
        val needsFallbackScroll = maxHeight < 520.dp
        val sectionGap = when {
            isVeryCompact -> 6.dp
            isCompact -> 8.dp
            else -> 12.dp
        }
        val buttonHeight = when {
            isVeryCompact -> 34.dp
            isCompact -> 38.dp
            else -> 42.dp
        }
        val columnModifier = Modifier
            .fillMaxSize()
            .let { base ->
                if (needsFallbackScroll) base.verticalScroll(rememberScrollState()) else base
            }
            .padding(horizontal = if (isCompact) 14.dp else 16.dp)
            .padding(bottom = if (isVeryCompact) 8.dp else if (isCompact) 12.dp else 16.dp)

        Column(modifier = columnModifier) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = if (isVeryCompact) 2.dp else if (isCompact) 6.dp else 10.dp,
                        bottom = if (isCompact) 4.dp else 6.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(if (isCompact) 38.dp else 44.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Parametric EQ",
                        style = if (isVeryCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${session.profile.model} · ${bands.size} Bands",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { showPresetDialog = true },
                    modifier = Modifier.size(if (isCompact) 38.dp else 44.dp)
                ) {
                    Icon(Icons.Rounded.Bookmarks, contentDescription = "Presets")
                }
                IconButton(
                    onClick = { viewModel.resetEqFlat() },
                    modifier = Modifier.size(if (isCompact) 38.dp else 44.dp)
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset Flat")
                }
            }

            // Interactive Curve Graph (flexibly scales to remaining height without requiring scroll)
            val graphSurfaceModifier = if (needsFallbackScroll) {
                Modifier
                    .fillMaxWidth()
                    .height(175.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 100.dp)
            }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(if (isCompact) 16.dp else 20.dp),
                modifier = graphSurfaceModifier
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isVeryCompact) 8.dp else if (isCompact) 10.dp else 12.dp)
                ) {
                    InteractivePeqGraph(
                        bands = bands,
                        selectedBandIndex = selectedBandIndex,
                        globalGainDb = session.state.globalGainDb,
                        sampleRate = caps.sampleRate,
                        minGainDb = caps.minGainDb,
                        maxGainDb = caps.maxGainDb,
                        interactive = true,
                        onSelectBand = viewModel::selectBand,
                        onDragBand = viewModel::updateBand,
                        fillAvailableHeight = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(sectionGap))

            // Horizontal Band Selector Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 8.dp)
            ) {
                bands.forEach { band ->
                    val isSelected = band.index == (selectedBand?.index ?: 0)
                    val freqLabel = if (band.frequencyHz >= 1000) {
                        "${((band.frequencyHz / 100.0).roundToInt() / 10.0)}k"
                    } else {
                        "${band.frequencyHz}"
                    }
                    val gainFormatted = "${if (band.gainDb > 0) "+" else ""}${(band.gainDb * 10.0).roundToInt() / 10.0}"

                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(if (isCompact) 12.dp else 14.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(if (isCompact) 12.dp else 14.dp))
                            .clickable { viewModel.selectBand(band.index) }
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = if (isCompact) 10.dp else 12.dp,
                                vertical = if (isVeryCompact) 4.dp else if (isCompact) 5.dp else 7.dp
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "B${band.index + 1} · $freqLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${gainFormatted}dB",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(sectionGap))

            // Selected Band Controls Card
            if (selectedBand != null) {
                BandEditorCard(
                    band = selectedBand,
                    supportedFilters = caps.supportedFilters,
                    minGain = caps.minGainDb,
                    maxGain = caps.maxGainDb,
                    minQ = caps.minQ,
                    maxQ = caps.maxQ,
                    compact = isCompact,
                    veryCompact = isVeryCompact,
                    onUpdate = viewModel::updateBand
                )
            }

            Spacer(modifier = Modifier.height(sectionGap))

            // Action Bar: Apply + Save to Flash + Import/Export
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                val buttonPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                val buttonTextStyle = if (isVeryCompact) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.labelLarge
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 10.dp)
                ) {
                    if (!autoApplyEnabled) {
                        OutlinedButton(
                            onClick = { viewModel.applyAllEqNow() },
                            contentPadding = buttonPadding,
                            modifier = Modifier
                                .weight(1f)
                                .height(buttonHeight)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Apply RAM",
                                style = buttonTextStyle,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Button(
                        onClick = { viewModel.saveToHardwareFlash() },
                        contentPadding = buttonPadding,
                        modifier = Modifier
                            .weight(1f)
                            .height(buttonHeight)
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Save to Flash",
                            style = buttonTextStyle,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (isVeryCompact) 6.dp else 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRequestImportFile,
                        contentPadding = buttonPadding,
                        modifier = Modifier
                            .weight(1f)
                            .height(buttonHeight)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Import AutoEQ",
                            style = buttonTextStyle,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = onRequestExportFile,
                        contentPadding = buttonPadding,
                        modifier = Modifier
                            .weight(1f)
                            .height(buttonHeight)
                    ) {
                        Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Export .txt",
                            style = buttonTextStyle,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    if (showPresetDialog) {
        AlertDialog(
            onDismissRequest = { showPresetDialog = false },
            title = { Text("Local Offline Presets") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.applyPreset(preset)
                                    showPresetDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(preset.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = "${preset.bands.size} bands · Preamp ${preset.preampDb} dB" +
                                            if (preset.builtIn) " · Built-in" else " · Saved",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!preset.builtIn) {
                                    IconButton(onClick = { viewModel.deleteUserPreset(preset.id) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete Preset")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPresetDialog = false
                    newPresetName = ""
                    showSavePresetDialog = true
                }) {
                    Text("Save Current")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPresetDialog = false }) { Text("Close") }
            }
        )
    }

    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Save Preset Locally") },
            text = {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    label = { Text("Preset Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveCurrentAsUserPreset(newPresetName)
                    showSavePresetDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BandEditorCard(
    band: EqBand,
    supportedFilters: List<com.suprcharg3d.dongleCTRL.dsp.FilterType>,
    minGain: Double,
    maxGain: Double,
    minQ: Double,
    maxQ: Double,
    compact: Boolean,
    veryCompact: Boolean,
    onUpdate: (EqBand) -> Unit
) {
    val sliderHeight = when {
        veryCompact -> 24.dp
        compact -> 28.dp
        else -> 34.dp
    }
    val interactiveMinSize = when {
        veryCompact -> 26.dp
        compact -> 32.dp
        else -> 38.dp
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(if (compact) 16.dp else 20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides interactiveMinSize) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (compact) 14.dp else 16.dp,
                    vertical = when {
                        veryCompact -> 8.dp
                        compact -> 10.dp
                        else -> 14.dp
                    }
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Band ${band.index + 1}",
                        style = if (veryCompact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (band.enabled) "Active" else "Bypassed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Switch(
                        checked = band.enabled,
                        onCheckedChange = { onUpdate(band.copy(enabled = it)) },
                        modifier = Modifier
                            .height(if (veryCompact) 26.dp else if (compact) 30.dp else 34.dp)
                            .scale(if (veryCompact) 0.78f else if (compact) 0.86f else 0.94f)
                    )
                }

                Spacer(modifier = Modifier.height(if (veryCompact) 2.dp else 4.dp))

                // Filter type selector pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
                ) {
                    supportedFilters.forEach { ft ->
                        FilterChip(
                            selected = band.filterType == ft,
                            onClick = { onUpdate(band.copy(filterType = ft)) },
                            label = {
                                Text(
                                    ft.displayName,
                                    style = if (veryCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
                                )
                            },
                            modifier = Modifier.height(if (veryCompact) 26.dp else if (compact) 28.dp else 32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (veryCompact) 4.dp else if (compact) 6.dp else 8.dp))

                // Gain slider + steppers
                val gainDisplay = "${if (band.gainDb > 0) "+" else ""}${(band.gainDb * 10.0).roundToInt() / 10.0} dB"
                ParameterHeaderRow(
                    label = "Gain",
                    valueText = gainDisplay,
                    compact = compact,
                    veryCompact = veryCompact,
                    onMinus = { onUpdate(band.copy(gainDb = ((band.gainDb - 0.5) * 10).roundToInt() / 10.0)) },
                    onPlus = { onUpdate(band.copy(gainDb = ((band.gainDb + 0.5) * 10).roundToInt() / 10.0)) }
                )
                Slider(
                    value = band.gainDb.toFloat(),
                    onValueChange = { onUpdate(band.copy(gainDb = (it * 10f).roundToInt() / 10.0)) },
                    valueRange = minGain.toFloat()..maxGain.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sliderHeight)
                )

                // Frequency slider (log-scaled for musical precision)
                val normFreq = BiquadCalculator.normalizedXForFrequency(band.frequencyHz.toDouble()).toFloat()
                ParameterHeaderRow(
                    label = "Frequency",
                    valueText = "${band.frequencyHz} Hz",
                    compact = compact,
                    veryCompact = veryCompact,
                    onMinus = { onUpdate(band.copy(frequencyHz = (band.frequencyHz * 0.95).roundToInt().coerceAtLeast(10))) },
                    onPlus = { onUpdate(band.copy(frequencyHz = (band.frequencyHz * 1.05).roundToInt().coerceAtMost(22000))) }
                )
                Slider(
                    value = normFreq,
                    onValueChange = { x ->
                        onUpdate(band.copy(frequencyHz = BiquadCalculator.frequencyForNormalizedX(x.toDouble())))
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sliderHeight)
                )

                // Q-factor slider
                val qDisplay = "${(band.q * 100.0).roundToInt() / 100.0}"
                ParameterHeaderRow(
                    label = "Q Factor",
                    valueText = qDisplay,
                    compact = compact,
                    veryCompact = veryCompact,
                    onMinus = { onUpdate(band.copy(q = ((band.q - 0.1) * 100).roundToInt() / 100.0)) },
                    onPlus = { onUpdate(band.copy(q = ((band.q + 0.1) * 100).roundToInt() / 100.0)) }
                )
                Slider(
                    value = band.q.toFloat().coerceIn(minQ.toFloat(), maxQ.toFloat()),
                    onValueChange = { onUpdate(band.copy(q = (it * 20f).roundToInt() / 20.0)) },
                    valueRange = minQ.toFloat()..maxQ.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sliderHeight)
                )
            }
        }
    }
}

@Composable
private fun ParameterHeaderRow(
    label: String,
    valueText: String,
    compact: Boolean,
    veryCompact: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    val labelStyle = if (veryCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
    val btnSize = if (veryCompact) 24.dp else if (compact) 26.dp else 30.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = labelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onMinus, modifier = Modifier.size(btnSize)) {
            Icon(Icons.Rounded.Remove, contentDescription = "Decrease $label", modifier = Modifier.size(16.dp))
        }
        Text(
            text = valueText,
            style = labelStyle,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = if (compact) 6.dp else 8.dp)
        )
        IconButton(onClick = onPlus, modifier = Modifier.size(btnSize)) {
            Icon(Icons.Rounded.Add, contentDescription = "Increase $label", modifier = Modifier.size(16.dp))
        }
    }
}
