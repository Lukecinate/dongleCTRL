package com.suprcharg3d.dongleCTRL.dsp

import kotlin.math.roundToInt

data class ParsedPeqProfile(
    val preampDb: Double = 0.0,
    val bands: List<EqBand>
)

/**
 * Offline importer/exporter for AutoEQ / EqualizerAPO ParametricEQ.txt and FiiO XML presets.
 */
object PeqTextParser {

    private val preampRegex = Regex("""Preamp:\s*([+-]?\d+(?:\.\d+)?)\s*dB""", RegexOption.IGNORE_CASE)
    private val filterRegex = Regex(
        """Filter\s+\d+:\s*(ON|OFF)\s+([A-Z]+)\s+Fc\s+(\d+(?:\.\d+)?)\s*Hz(?:\s+Gain\s*([+-]?\d+(?:\.\d+)?)\s*dB)?(?:\s+Q\s*(\d+(?:\.\d+)?))?""",
        RegexOption.IGNORE_CASE
    )
    private val xmlFilterRegex = Regex(
        """<filter[^>]*freq="(\d+(?:\.\d+)?)"[^>]*gain="([+-]?\d+(?:\.\d+)?)"[^>]*q="(\d+(?:\.\d+)?)"[^>]*/?>""",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: String, maxBands: Int = 31): ParsedPeqProfile {
        val trimmed = text.trim()
        if (trimmed.startsWith("<")) {
            val xmlMatches = xmlFilterRegex.findAll(trimmed).toList()
            if (xmlMatches.isNotEmpty()) {
                val bands = xmlMatches.take(maxBands).mapIndexed { idx, m ->
                    EqBand(
                        index = idx,
                        frequencyHz = m.groupValues[1].toDouble().roundToInt().coerceIn(10, 24000),
                        gainDb = m.groupValues[2].toDouble().coerceIn(-24.0, 24.0),
                        q = m.groupValues[3].toDouble().coerceIn(0.1, 30.0),
                        filterType = FilterType.PEAK,
                        enabled = true
                    )
                }
                return ParsedPeqProfile(preampDb = 0.0, bands = bands)
            }
        }

        val preamp = preampRegex.find(trimmed)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val parsedBands = mutableListOf<EqBand>()
        for (line in trimmed.lineSequence()) {
            val m = filterRegex.find(line.trim()) ?: continue
            if (parsedBands.size >= maxBands) break
            val enabled = m.groupValues[1].equals("ON", ignoreCase = true)
            val code = m.groupValues[2].uppercase()
            val fc = m.groupValues[3].toDouble().roundToInt().coerceIn(10, 24000)
            val gain = m.groupValues[4].toDoubleOrNull() ?: 0.0
            val q = m.groupValues[5].toDoubleOrNull() ?: 1.41
            val type = when {
                code.startsWith("LS") -> FilterType.LOW_SHELF
                code.startsWith("HS") -> FilterType.HIGH_SHELF
                code.startsWith("LP") -> FilterType.LOW_PASS
                code.startsWith("HP") -> FilterType.HIGH_PASS
                code.startsWith("BP") -> FilterType.BAND_PASS
                code.startsWith("AP") -> FilterType.ALL_PASS
                else -> FilterType.PEAK
            }
            parsedBands += EqBand(
                index = parsedBands.size,
                frequencyHz = fc,
                gainDb = gain.coerceIn(-24.0, 24.0),
                q = q.coerceIn(0.1, 30.0),
                filterType = type,
                enabled = enabled
            )
        }
        return ParsedPeqProfile(preampDb = preamp, bands = parsedBands)
    }

    fun exportAutoEq(bands: List<EqBand>, preampDb: Double = 0.0): String {
        val sb = StringBuilder()
        sb.appendLine("Preamp: ${formatOneDecimal(preampDb)} dB")
        bands.forEachIndexed { i, band ->
            val state = if (band.enabled) "ON" else "OFF"
            val code = when (band.filterType) {
                FilterType.PEAK -> "PK"
                FilterType.LOW_SHELF -> "LSC"
                FilterType.HIGH_SHELF -> "HSC"
                FilterType.LOW_PASS -> "LPQ"
                FilterType.HIGH_PASS -> "HPQ"
                FilterType.BAND_PASS -> "BP"
                FilterType.ALL_PASS -> "AP"
            }
            sb.appendLine(
                "Filter ${i + 1}: $state $code Fc ${band.frequencyHz} Hz Gain ${formatOneDecimal(band.gainDb)} dB Q ${formatTwoDecimals(band.q)}"
            )
        }
        return sb.toString().trimEnd()
    }

    private fun formatOneDecimal(v: Double): String {
        val scaled = (v * 10.0).roundToInt()
        val absScaled = kotlin.math.abs(scaled)
        val sign = if (scaled < 0) "-" else ""
        return "$sign${absScaled / 10}.${absScaled % 10}"
    }

    private fun formatTwoDecimals(v: Double): String {
        val scaled = (v * 100.0).roundToInt()
        val absScaled = kotlin.math.abs(scaled)
        val frac = (absScaled % 100).toString().padStart(2, '0')
        return "${absScaled / 100}.$frac"
    }
}
