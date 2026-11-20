package com.suprcharg3d.dongleCTRL.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.suprcharg3d.dongleCTRL.dsp.DefaultBands
import com.suprcharg3d.dongleCTRL.dsp.EqBand
import com.suprcharg3d.dongleCTRL.dsp.EqPreset
import com.suprcharg3d.dongleCTRL.dsp.FilterType
import com.suprcharg3d.dongleCTRL.dsp.PresetBandHelper
import com.suprcharg3d.dongleCTRL.ui.components.InteractivePeqGraph
import com.suprcharg3d.dongleCTRL.ui.components.MainTabHeader
import kotlin.math.roundToInt

private val STANDARD_EDITOR_FILTERS = listOf(
    FilterType.PEAK,
    FilterType.LOW_SHELF,
    FilterType.HIGH_SHELF,
    FilterType.LOW_PASS,
    FilterType.HIGH_PASS
)

@Composable
fun PresetsScreen(
    presets: List<EqPreset>,
    hasTargetDac: Boolean,
    onApplyPreset: (EqPreset) -> Unit,
    onSavePreset: (existingId: String?, name: String, preampDb: Double, bands: List<EqBand>) -> Unit,
    onDeletePreset: (String) -> Unit,
    onRequestImportFile: () -> Unit,
    onRequestExportPreset: (EqPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    var editorDraft by remember { mutableStateOf<PresetEditorDraft?>(null) }
    var deleteConfirmTarget by remember { mutableStateOf<EqPreset?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        MainTabHeader(title = "PRESETS")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    editorDraft = PresetEditorDraft(
                        existingId = null,
                        name = "Custom EQ",
                        preampDb = 0.0,
                        bands = DefaultBands.flatBands(PresetBandHelper.STANDARD_PRESET_BANDS)
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create Preset", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = onRequestImportFile,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import AutoEQ", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (presets.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No saved presets yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap Create Preset to build a 10-band curve, Import AutoEQ to load a .txt file, or save from your DAC's Edit PEQ page.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(presets, key = { it.id }) { preset ->
                    PresetCard(
                        preset = preset,
                        dacAvailable = hasTargetDac,
                        onApply = { onApplyPreset(preset) },
                        onExport = { onRequestExportPreset(preset) },
                        onEdit = {
                            val padded = PresetBandHelper.padToCount(
                                preset.bands,
                                PresetBandHelper.STANDARD_PRESET_BANDS
                            )
                            editorDraft = PresetEditorDraft(
                                existingId = preset.id,
                                name = preset.name,
                                preampDb = preset.preampDb,
                                bands = padded
                            )
                        },
                        onDelete = { deleteConfirmTarget = preset }
                    )
                }
            }
        }
    }

    editorDraft?.let { draft ->
        OfflinePresetEditorDialog(
            draft = draft,
            onSave = { existingId, name, preampDb, bands ->
                onSavePreset(existingId, name, preampDb, bands)
                editorDraft = null
            },
            onDismiss = { editorDraft = null }
        )
    }

    deleteConfirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            title = { Text("Delete Preset?") },
            text = { Text("Remove \"${target.name}\" from your saved presets?") },
            confirmButton = {
                Button(onClick = {
                    onDeletePreset(target.id)
                    deleteConfirmTarget = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTarget = null }) { Text("Cancel") }
            }
        )
    }
}

private data class PresetEditorDraft(
    val existingId: String?,
    val name: String,
    val preampDb: Double,
    val bands: List<EqBand>
)

@Composable
private fun PresetCard(
    preset: EqPreset,
    dacAvailable: Boolean,
    onApply: () -> Unit,
    onExport: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val activeCount = remember(preset.bands) { PresetBandHelper.activeBands(preset.bands).size }
    val preampRounded = (preset.preampDb * 10.0).roundToInt() / 10.0
    val preampStr = "${if (preampRounded > 0) "+" else ""}$preampRounded dB"

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$activeCount active / ${preset.bands.size} bands · Preamp $preampStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = if (preset.builtIn) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (preset.builtIn) "Built-in" else "Saved",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (preset.builtIn) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
            ) {
                InteractivePeqGraph(
                    bands = preset.bands,
                    selectedBandIndex = -1,
                    globalGainDb = preset.preampDb,
                    sampleRate = 96_000,
                    minGainDb = -12.0,
                    maxGainDb = 12.0,
                    interactive = false,
                    onSelectBand = {},
                    onDragBand = {},
                    fillAvailableHeight = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onApply,
                    enabled = dacAvailable,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Apply", style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = onExport,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export .txt", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (preset.builtIn) Icons.Rounded.ContentCopy else Icons.Rounded.Edit,
                        contentDescription = if (preset.builtIn) "Duplicate & Edit" else "Edit Preset",
                        modifier = Modifier.size(19.dp)
                    )
                }

                if (!preset.builtIn) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete Preset",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflinePresetEditorDialog(
    draft: PresetEditorDraft,
    onSave: (existingId: String?, name: String, preampDb: Double, bands: List<EqBand>) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(draft) { mutableStateOf(draft.name) }
    var preampDb by remember(draft) { mutableDoubleStateOf(draft.preampDb) }
    var bands by remember(draft) { mutableStateOf(draft.bands) }
    var selectedBandIndex by remember(draft) { mutableIntStateOf(0) }
    var numericEditTarget by remember { mutableStateOf<PeqNumericTarget?>(null) }
    var numericField by remember { mutableStateOf(TextFieldValue("")) }

    val selectedBand = bands.getOrNull(selectedBandIndex) ?: bands.firstOrNull()

    fun updateSingleBand(updated: EqBand) {
        bands = bands.mapIndexed { idx, existing ->
            if (idx == updated.index) updated else existing
        }
    }

    fun openNumericEditor(target: PeqNumericTarget, initialText: String) {
        numericField = TextFieldValue(initialText, selection = TextRange(0, initialText.length))
        numericEditTarget = target
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val isCompact = maxHeight < 760.dp
                val isVeryCompact = maxHeight < 640.dp
                val needsFallbackScroll = maxHeight < 560.dp
                val sectionGap = when {
                    isVeryCompact -> 6.dp
                    isCompact -> 8.dp
                    else -> 10.dp
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .let { base ->
                            if (needsFallbackScroll) base.verticalScroll(rememberScrollState()) else base
                        }
                        .padding(horizontal = if (isCompact) 14.dp else 16.dp, vertical = 8.dp)
                ) {
                    // Editor Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close Editor")
                        }
                        Text(
                            text = if (draft.existingId == null) "New 10-Band Preset" else "Edit Preset",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                bands = DefaultBands.flatBands(PresetBandHelper.STANDARD_PRESET_BANDS)
                                preampDb = 0.0
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset Flat")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = { onSave(draft.existingId, name, preampDb, bands) },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save")
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Preset Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(sectionGap))

                    val graphModifier = if (needsFallbackScroll) {
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .heightIn(min = 95.dp)
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(if (isCompact) 16.dp else 20.dp),
                        modifier = graphModifier
                    ) {
                        Box(modifier = Modifier.padding(if (isVeryCompact) 6.dp else 10.dp)) {
                            InteractivePeqGraph(
                                bands = bands,
                                selectedBandIndex = selectedBandIndex,
                                globalGainDb = preampDb,
                                sampleRate = 96_000,
                                minGainDb = -12.0,
                                maxGainDb = 12.0,
                                interactive = true,
                                onSelectBand = { selectedBandIndex = it },
                                onDragBand = ::updateSingleBand,
                                fillAvailableHeight = true,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(sectionGap))

                    BandChipsRow(
                        bands = bands,
                        selectedBandIndex = selectedBand?.index ?: 0,
                        compact = isCompact,
                        veryCompact = isVeryCompact,
                        onSelectBand = { selectedBandIndex = it }
                    )

                    Spacer(modifier = Modifier.height(sectionGap))

                    InlinePreampBar(
                        globalGainDb = preampDb,
                        gainRange = -12.0..12.0,
                        compact = isCompact,
                        veryCompact = isVeryCompact,
                        onChangeGain = { preampDb = it },
                        onTapValue = {
                            val cur = (preampDb * 10.0).roundToInt() / 10.0
                            openNumericEditor(PeqNumericTarget.PREAMP, "$cur")
                        }
                    )

                    Spacer(modifier = Modifier.height(sectionGap))

                    if (selectedBand != null) {
                        BandEditorCard(
                            band = selectedBand,
                            supportedFilters = STANDARD_EDITOR_FILTERS,
                            minGain = -12.0,
                            maxGain = 12.0,
                            minQ = 0.25,
                            maxQ = 12.0,
                            compact = isCompact,
                            veryCompact = isVeryCompact,
                            onUpdate = ::updateSingleBand,
                            onTapGainValue = {
                                val cur = (selectedBand.gainDb * 10.0).roundToInt() / 10.0
                                openNumericEditor(PeqNumericTarget.GAIN, "$cur")
                            },
                            onTapFreqValue = {
                                openNumericEditor(PeqNumericTarget.FREQUENCY, "${selectedBand.frequencyHz}")
                            },
                            onTapQValue = {
                                val cur = (selectedBand.q * 100.0).roundToInt() / 100.0
                                openNumericEditor(PeqNumericTarget.Q_FACTOR, "$cur")
                            }
                        )
                    }
                }
            }
        }
    }

    numericEditTarget?.let { target ->
        PeqNumericValueDialog(
            target = target,
            band = selectedBand,
            numericField = numericField,
            onNumericFieldChange = { numericField = it },
            minGainDb = -12.0,
            maxGainDb = 12.0,
            minQ = 0.25,
            maxQ = 12.0,
            globalGainRange = -12.0..12.0,
            onUpdateBand = ::updateSingleBand,
            onSetGlobalGain = { preampDb = it },
            onDismiss = { numericEditTarget = null }
        )
    }
}
