package com.suprcharg3d.dongleCTRL.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.suprcharg3d.dongleCTRL.dsp.BiquadCalculator
import com.suprcharg3d.dongleCTRL.dsp.EqBand
import com.suprcharg3d.dongleCTRL.protocol.DacControlValue
import com.suprcharg3d.dongleCTRL.protocol.DacProfile
import com.suprcharg3d.dongleCTRL.protocol.DacSliderField
import com.suprcharg3d.dongleCTRL.protocol.NamedOption
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Renders the bundled offline product photo for EPZ / FiiO / TRN Black Pearl DACs when available,
 * falling back cleanly to the minimalist vector DAC illustration otherwise.
 */
@Composable
fun DacProductImage(
    profile: DacProfile?,
    isConnected: Boolean,
    modifier: Modifier = Modifier,
    fallbackModifier: Modifier = modifier
) {
    val context = LocalContext.current
    val bitmap = remember(profile?.id, profile?.vendorId, profile?.productId, profile?.fiioDeviceType, profile?.model) {
        profile?.let { DacImageResolver.loadBitmap(context, it) }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = profile?.model,
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    } else {
        DacDongleIllustration(isConnected = isConnected, modifier = fallbackModifier)
    }
}

/**
 * Minimalist custom vector illustration of an audiophile USB-C dongle DAC + cable.
 */
@Composable
fun DacDongleIllustration(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val bodySurface = MaterialTheme.colorScheme.surfaceContainerHighest
    val bodyOutline = MaterialTheme.colorScheme.outline
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cy = h * 0.5f

        // Braided cable arc
        val cablePath = Path().apply {
            moveTo(w * 0.08f, cy)
            cubicTo(w * 0.18f, cy - h * 0.25f, w * 0.25f, cy + h * 0.2f, w * 0.34f, cy)
        }
        drawPath(
            path = cablePath,
            color = bodyOutline.copy(alpha = 0.85f),
            style = Stroke(width = 6f, cap = StrokeCap.Round)
        )

        // USB-C plug collar
        drawRoundRect(
            color = Color(0xFF434A57),
            topLeft = Offset(w * 0.31f, cy - h * 0.13f),
            size = Size(w * 0.10f, h * 0.26f),
            cornerRadius = CornerRadius(8f, 8f)
        )

        // Main CNC anodized DAC chassis
        val chassisLeft = w * 0.39f
        val chassisWidth = w * 0.48f
        val chassisTop = cy - h * 0.24f
        val chassisHeight = h * 0.48f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF333945), bodySurface, Color(0xFF191D24)),
                startY = chassisTop,
                endY = chassisTop + chassisHeight
            ),
            topLeft = Offset(chassisLeft, chassisTop),
            size = Size(chassisWidth, chassisHeight),
            cornerRadius = CornerRadius(18f, 18f)
        )
        drawRoundRect(
            color = bodyOutline.copy(alpha = 0.7f),
            topLeft = Offset(chassisLeft, chassisTop),
            size = Size(chassisWidth, chassisHeight),
            cornerRadius = CornerRadius(18f, 18f),
            style = Stroke(width = 2.2f)
        )

        // Subtle recessed audio glass window + status LED
        drawRoundRect(
            color = Color(0xFF111419),
            topLeft = Offset(chassisLeft + chassisWidth * 0.16f, cy - chassisHeight * 0.22f),
            size = Size(chassisWidth * 0.52f, chassisHeight * 0.44f),
            cornerRadius = CornerRadius(10f, 10f)
        )
        drawCircle(
            color = if (isConnected) accent else Color(0xFF5D6575),
            radius = 5.5f,
            center = Offset(chassisLeft + chassisWidth * 0.26f, cy)
        )

        // 4.4mm balanced output jack ring
        drawCircle(
            color = Color(0xFFD6A85C),
            radius = chassisHeight * 0.22f,
            center = Offset(chassisLeft + chassisWidth * 0.88f, cy),
            style = Stroke(width = 4f)
        )
        drawCircle(
            color = Color(0xFF0A0C0E),
            radius = chassisHeight * 0.14f,
            center = Offset(chassisLeft + chassisWidth * 0.88f, cy)
        )
    }
}

/**
 * Interactive logarithmic 20 Hz - 20 kHz Parametric EQ canvas with draggable nodes.
 */
@Composable
fun InteractivePeqGraph(
    bands: List<EqBand>,
    selectedBandIndex: Int,
    globalGainDb: Double,
    sampleRate: Int,
    minGainDb: Double,
    maxGainDb: Double,
    interactive: Boolean = true,
    onSelectBand: (Int) -> Unit = {},
    onDragBand: (EqBand) -> Unit = {},
    fillAvailableHeight: Boolean = false,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val zeroLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val latestBands by rememberUpdatedState(bands)
    val latestSelected by rememberUpdatedState(selectedBandIndex)

    val dbSpanMin = minGainDb.coerceAtMost(-12.0)
    val dbSpanMax = maxGainDb.coerceAtLeast(12.0)

    Column(modifier = modifier) {
        val boxModifier = if (fillAvailableHeight) {
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 90.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .height(if (interactive) 210.dp else 96.dp)
        }
        Box(
            modifier = boxModifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .let { base ->
                        if (!interactive) base else {
                            base
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        val w = size.width.toFloat().coerceAtLeast(1f)
                                        val h = size.height.toFloat().coerceAtLeast(1f)
                                        val hit = latestBands.minByOrNull { b ->
                                            val bx = BiquadCalculator.normalizedXForFrequency(b.frequencyHz.toDouble()).toFloat() * w
                                            val by = gainToY(b.gainDb, dbSpanMin, dbSpanMax, h)
                                            abs(offset.x - bx) + abs(offset.y - by)
                                        }
                                        if (hit != null) onSelectBand(hit.index)
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            val w = size.width.toFloat().coerceAtLeast(1f)
                                            val h = size.height.toFloat().coerceAtLeast(1f)
                                            val hit = latestBands.minByOrNull { b ->
                                                val bx = BiquadCalculator.normalizedXForFrequency(b.frequencyHz.toDouble()).toFloat() * w
                                                val by = gainToY(b.gainDb, dbSpanMin, dbSpanMax, h)
                                                abs(start.x - bx) + abs(start.y - by)
                                            }
                                            if (hit != null) onSelectBand(hit.index)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val cur = latestBands.getOrNull(latestSelected) ?: return@detectDragGestures
                                            val w = size.width.toFloat().coerceAtLeast(1f)
                                            val h = size.height.toFloat().coerceAtLeast(1f)
                                            val normX = (change.position.x / w).toDouble().coerceIn(0.0, 1.0)
                                            val freq = BiquadCalculator.frequencyForNormalizedX(normX)
                                            val gain = yToGain(change.position.y, dbSpanMin, dbSpanMax, h)
                                                .coerceIn(minGainDb, maxGainDb)
                                            val roundedGain = (gain * 10.0).roundToInt() / 10.0
                                            onDragBand(cur.copy(frequencyHz = freq, gainDb = roundedGain))
                                        }
                                    )
                                }
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val padV = 12f
                val drawH = (h - padV * 2f).coerceAtLeast(1f)

                // Logarithmic frequency gridlines
                val gridFreqs = listOf(50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0)
                for (gf in gridFreqs) {
                    val x = BiquadCalculator.normalizedXForFrequency(gf).toFloat() * w
                    drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                }

                // dB horizontal lines
                val zeroY = padV + gainToY(0.0, dbSpanMin, dbSpanMax, drawH)
                drawLine(
                    color = zeroLineColor,
                    start = Offset(0f, zeroY),
                    end = Offset(w, zeroY),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                )
                for (dbLine in listOf(-6.0, 6.0, -12.0, 12.0)) {
                    if (dbLine in dbSpanMin..dbSpanMax) {
                        val y = padV + gainToY(dbLine, dbSpanMin, dbSpanMax, drawH)
                        drawLine(gridColor.copy(alpha = 0.35f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                    }
                }

                // Selected band isolated bell curve
                val activeBand = bands.getOrNull(selectedBandIndex)
                val steps = 96
                if (interactive && activeBand != null && activeBand.enabled) {
                    val bandPath = Path()
                    for (i in 0..steps) {
                        val normX = i.toDouble() / steps
                        val f = BiquadCalculator.frequencyForNormalizedX(normX).toDouble()
                        val db = BiquadCalculator.evaluateBandDb(activeBand, f, sampleRate)
                        val x = normX.toFloat() * w
                        val y = padV + gainToY(db, dbSpanMin, dbSpanMax, drawH)
                        if (i == 0) bandPath.moveTo(x, y) else bandPath.lineTo(x, y)
                    }
                    drawPath(
                        path = bandPath,
                        color = accent.copy(alpha = 0.38f),
                        style = Stroke(width = 2.5f)
                    )
                }

                // Combined PEQ curve
                val curvePath = Path()
                val fillPath = Path()
                for (i in 0..steps) {
                    val normX = i.toDouble() / steps
                    val f = BiquadCalculator.frequencyForNormalizedX(normX).toDouble()
                    val db = BiquadCalculator.evaluateMagnitudeDb(bands, f, globalGainDb, sampleRate)
                    val x = normX.toFloat() * w
                    val y = padV + gainToY(db, dbSpanMin, dbSpanMax, drawH)
                    if (i == 0) {
                        curvePath.moveTo(x, y)
                        fillPath.moveTo(x, zeroY)
                        fillPath.lineTo(x, y)
                    } else {
                        curvePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(w, zeroY)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = 0.26f), accent.copy(alpha = 0.02f))
                    )
                )
                drawPath(
                    path = curvePath,
                    color = accent,
                    style = Stroke(
                        width = if (interactive) 4.2f else 3.0f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Band handles
                if (interactive) {
                    for (band in bands) {
                        val bx = BiquadCalculator.normalizedXForFrequency(band.frequencyHz.toDouble()).toFloat() * w
                        val by = padV + gainToY(band.gainDb, dbSpanMin, dbSpanMax, drawH)
                        val isSel = band.index == selectedBandIndex
                        if (isSel) {
                            drawCircle(accent.copy(alpha = 0.25f), radius = 20f, center = Offset(bx, by))
                        }
                        drawCircle(
                            color = if (!band.enabled) gridColor else if (isSel) onSurface else accent,
                            radius = if (isSel) 9.5f else 6.5f,
                            center = Offset(bx, by)
                        )
                    }
                }
            }
        }

        if (interactive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = if (fillAvailableHeight) 2.dp else 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("20Hz", "100", "500", "1k", "5k", "20k").forEach { lbl ->
                    Text(
                        text = lbl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun gainToY(db: Double, minDb: Double, maxDb: Double, height: Float): Float {
    val norm = ((maxDb - db) / (maxDb - minDb)).coerceIn(0.0, 1.0)
    return (norm * height).toFloat()
}

private fun yToGain(y: Float, minDb: Double, maxDb: Double, height: Float): Double {
    val norm = (y / height).toDouble().coerceIn(0.0, 1.0)
    return maxDb - norm * (maxDb - minDb)
}

@Composable
fun SettingsSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
fun SettingActionRow(
    icon: ImageVector,
    title: String,
    valueText: String? = null,
    subtitle: String? = null,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (valueText != null) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .widthIn(max = 148.dp)
                        .padding(start = 10.dp, end = 4.dp)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 50.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    showDivider: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 50.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun SettingSliderRow(
    icon: ImageVector,
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    showDivider: Boolean = true,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 50.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun OptionPickerDialog(
    title: String,
    options: List<NamedOption>,
    selectedId: Int,
    onSelect: (NamedOption) -> Unit,
    onDismiss: () -> Unit,
    categoryById: Map<Int, String> = emptyMap(),
    editableIds: Set<Int> = emptySet(),
    nameMaxChars: Int = 8,
    onRenameOption: ((NamedOption, String) -> Unit)? = null
) {
    var editingOption by remember { mutableStateOf<NamedOption?>(null) }
    var renameDraft by remember { mutableStateOf("") }

    val distinctCategories = remember(options, categoryById) {
        options.mapNotNull { categoryById[it.id]?.takeIf(String::isNotBlank) }.distinct()
    }
    val showCategoryHeaders = distinctCategories.size > 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                var lastCategory: String? = null
                options.forEach { option ->
                    val cat = categoryById[option.id]?.takeIf(String::isNotBlank)
                    if (showCategoryHeaders && cat != null && cat != lastCategory) {
                        lastCategory = cat
                        val pretty = when (cat.lowercase()) {
                            "game" -> "Game Presets"
                            "movie", "cinema" -> "Cinema Presets"
                            "general", "normal" -> "General Presets"
                            else -> cat.replaceFirstChar { it.uppercase() }
                        }
                        Text(
                            text = pretty,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 2.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onSelect(option)
                                onDismiss()
                            }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option.id == selectedId,
                            onClick = {
                                onSelect(option)
                                onDismiss()
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        if (onRenameOption != null && option.id in editableIds) {
                            IconButton(
                                onClick = {
                                    editingOption = option
                                    renameDraft = option.label
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Rename ${option.label}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )

    editingOption?.let { target ->
        AlertDialog(
            onDismissRequest = { editingOption = null },
            title = { Text("Rename Preset Slot", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = renameDraft,
                        onValueChange = { if (it.length <= nameMaxChars) renameDraft = it },
                        singleLine = true,
                        label = { Text("Custom Name (max $nameMaxChars chars)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = renameDraft.trim().isNotEmpty(),
                    onClick = {
                        onRenameOption?.invoke(target, renameDraft.trim())
                        editingOption = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingOption = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun MultiSelectPickerDialog(
    title: String,
    options: List<NamedOption>,
    initialMask: Int,
    supportMask: Int = -1,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val visibleOptions = remember(options, supportMask) {
        if (supportMask >= 0) options.filter { (supportMask and it.id) != 0 }.ifEmpty { options }
        else options
    }
    var mask by remember(initialMask) { mutableStateOf(initialMask) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                visibleOptions.forEach { option ->
                    val checked = (mask and option.id) != 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                mask = if (checked) (mask and option.id.inv()) else (mask or option.id)
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                mask = if (isChecked) (mask or option.id) else (mask and option.id.inv())
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(mask)
                onDismiss()
            }) { Text("Apply") }
        }
    )
}

@Composable
fun DualSliderSwitchRow(
    icon: ImageVector,
    title: String,
    value: DacControlValue,
    sliders: List<DacSliderField>,
    showDivider: Boolean = true,
    onValueChange: (DacControlValue) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingSwitchRow(
            icon = icon,
            title = title,
            subtitle = if (value.boolValue) "Harmonic compensation enabled" else "Disabled",
            checked = value.boolValue,
            showDivider = false,
            onCheckedChange = { enabled ->
                onValueChange(value.copy(boolValue = enabled))
            }
        )
        if (value.boolValue) {
            val s1 = sliders.getOrNull(0) ?: DacSliderField(0, 5, 1, "2nd Harmonic")
            val s2 = sliders.getOrNull(1) ?: DacSliderField(0, 5, 1, "3rd Harmonic")
            Column(modifier = Modifier.padding(start = 50.dp, end = 16.dp, bottom = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(s1.label.ifBlank { "2nd Harmonic" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${value.intValue}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = value.intValue.coerceIn(s1.min, s1.max).toFloat(),
                    onValueChange = { onValueChange(value.copy(intValue = it.roundToInt())) },
                    valueRange = s1.min.toFloat()..s1.max.toFloat().coerceAtLeast(1f),
                    steps = (s1.max - s1.min - 1).coerceAtLeast(0),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(s2.label.ifBlank { "3rd Harmonic" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${value.secondaryValue}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = value.secondaryValue.coerceIn(s2.min, s2.max).toFloat(),
                    onValueChange = { onValueChange(value.copy(secondaryValue = it.roundToInt())) },
                    valueRange = s2.min.toFloat()..s2.max.toFloat().coerceAtLeast(1f),
                    steps = (s2.max - s2.min - 1).coerceAtLeast(0),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 50.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun RenameDeviceDialog(
    currentName: String,
    maxBytes: Int,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(currentName) { mutableStateOf(currentName) }
    val byteCount = draft.trim().toByteArray(Charsets.UTF_8).size
    val valid = draft.trim().isNotEmpty() && byteCount <= maxBytes

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Device", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    label = { Text("Bluetooth Name") },
                    supportingText = { Text("$byteCount / $maxBytes UTF-8 bytes") },
                    isError = !valid && draft.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(draft.trim())
                    onDismiss()
                }
            ) { Text("Save") }
        }
    )
}

@Composable
fun ActionConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) }
        }
    )
}
