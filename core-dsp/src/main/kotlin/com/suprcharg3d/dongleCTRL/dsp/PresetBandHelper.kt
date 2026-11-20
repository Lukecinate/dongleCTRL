package com.suprcharg3d.dongleCTRL.dsp

import kotlin.math.abs

/**
 * Resolves active PEQ bands, standard 10-band preset normalization,
 * and DAC band-count adaptation (auto-padding vs user band selection).
 */
object PresetBandHelper {

    const val STANDARD_PRESET_BANDS = 10

    private val PASS_OR_ALLPASS_TYPES = setOf(
        FilterType.LOW_PASS,
        FilterType.HIGH_PASS,
        FilterType.BAND_PASS,
        FilterType.ALL_PASS
    )

    sealed interface BandResolution {
        data class Ready(val bands: List<EqBand>) : BandResolution
        data class NeedsSelection(
            val candidateBands: List<EqBand>,
            val maxSelectable: Int
        ) : BandResolution
    }

    /**
     * A band is considered "active" if it is enabled and actually alters the curve:
     * non-zero gain (> 0.0001 dB) OR a pass/all-pass filter where cutoff Q/slope acts even at 0 dB.
     */
    fun isBandActive(band: EqBand): Boolean =
        band.enabled && (abs(band.gainDb) > 1e-4 || band.filterType in PASS_OR_ALLPASS_TYPES)

    fun activeBands(bands: List<EqBand>): List<EqBand> =
        bands.filter(::isBandActive)

    /**
     * Normalizes an imported or DAC-captured band list into the standard 10-band preset format.
     * - If active bands <= 10: auto-fits without prompting, padding unused slots with flat 0 dB bands.
     * - If active bands > 10 and [selectedCandidateIndices] is null: returns [BandResolution.NeedsSelection].
     */
    fun resolveStandardTenBands(
        sourceBands: List<EqBand>,
        selectedCandidateIndices: List<Int>? = null
    ): BandResolution {
        val active = activeBands(sourceBands)

        if (selectedCandidateIndices == null) {
            if (active.size > STANDARD_PRESET_BANDS) {
                return BandResolution.NeedsSelection(
                    candidateBands = active,
                    maxSelectable = STANDARD_PRESET_BANDS
                )
            }
            val chosen = if (sourceBands.size <= STANDARD_PRESET_BANDS) {
                sourceBands
            } else {
                active
            }
            return BandResolution.Ready(padToCount(chosen, STANDARD_PRESET_BANDS))
        }

        val picked = selectedCandidateIndices
            .distinct()
            .sorted()
            .mapNotNull { active.getOrNull(it) }
            .take(STANDARD_PRESET_BANDS)
        return BandResolution.Ready(padToCount(picked, STANDARD_PRESET_BANDS))
    }

    /**
     * Resolves how a preset fits a target DAC's band capacity ([targetMaxBands]).
     * - Preserves original slot indices when [presetBands].size <= [targetMaxBands].
     * - Packs only active bands when [presetBands].size > [targetMaxBands] and active count <= [targetMaxBands].
     * - Returns [BandResolution.NeedsSelection] when active count > [targetMaxBands] and [selectedCandidateIndices] is null.
     */
    fun resolveBandsForTarget(
        presetBands: List<EqBand>,
        targetMaxBands: Int,
        selectedCandidateIndices: List<Int>? = null
    ): BandResolution {
        require(targetMaxBands > 0) { "targetMaxBands must be positive" }
        val active = activeBands(presetBands)

        if (selectedCandidateIndices == null) {
            if (active.size > targetMaxBands) {
                return BandResolution.NeedsSelection(
                    candidateBands = active,
                    maxSelectable = targetMaxBands
                )
            }
            val chosen = if (presetBands.size <= targetMaxBands) {
                presetBands
            } else {
                active
            }
            return BandResolution.Ready(padToCount(chosen, targetMaxBands))
        }

        val picked = selectedCandidateIndices
            .distinct()
            .sorted()
            .mapNotNull { active.getOrNull(it) }
            .take(targetMaxBands)
        return BandResolution.Ready(padToCount(picked, targetMaxBands))
    }

    fun padToCount(bands: List<EqBand>, targetCount: Int): List<EqBand> {
        val defaults = DefaultBands.flatBands(targetCount)
        return List(targetCount) { idx ->
            bands.getOrNull(idx)?.copy(index = idx) ?: defaults[idx]
        }
    }
}
