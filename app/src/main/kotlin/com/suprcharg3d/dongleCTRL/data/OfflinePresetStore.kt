package com.suprcharg3d.dongleCTRL.data

import android.content.Context
import com.suprcharg3d.dongleCTRL.dsp.DefaultBands
import com.suprcharg3d.dongleCTRL.dsp.EqBand
import com.suprcharg3d.dongleCTRL.dsp.EqPreset
import com.suprcharg3d.dongleCTRL.dsp.FilterType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 100% offline local preset library stored as JSON in internal app storage.
 */
class OfflinePresetStore(context: Context) {

    private val storageFile = File(context.filesDir, "user_peq_presets.json")

    fun loadAll(): List<EqPreset> {
        val userPresets = readUserPresets()
        return BUILTIN_PRESETS + userPresets
    }

    fun saveUserPreset(preset: EqPreset): List<EqPreset> {
        val current = readUserPresets().filterNot { it.id == preset.id }.toMutableList()
        current.add(0, preset.copy(builtIn = false))
        writeUserPresets(current)
        return BUILTIN_PRESETS + current
    }

    fun deleteUserPreset(id: String): List<EqPreset> {
        val current = readUserPresets().filterNot { it.id == id }
        writeUserPresets(current)
        return BUILTIN_PRESETS + current
    }

    private fun readUserPresets(): List<EqPreset> {
        if (!storageFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(storageFile.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val bandsArr = obj.getJSONArray("bands")
                    val bands = buildList {
                        for (j in 0 until bandsArr.length()) {
                            val b = bandsArr.getJSONObject(j)
                            add(
                                EqBand(
                                    index = b.getInt("index"),
                                    frequencyHz = b.getInt("frequencyHz"),
                                    gainDb = b.getDouble("gainDb"),
                                    q = b.getDouble("q"),
                                    filterType = FilterType.entries.firstOrNull {
                                        it.name == b.optString("filterType")
                                    } ?: FilterType.PEAK,
                                    enabled = b.optBoolean("enabled", true)
                                )
                            )
                        }
                    }
                    add(
                        EqPreset(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            preampDb = obj.optDouble("preampDb", 0.0),
                            bands = bands,
                            builtIn = false
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeUserPresets(list: List<EqPreset>) {
        val arr = JSONArray()
        for (p in list) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("preampDb", p.preampDb)
            val bandsArr = JSONArray()
            for (b in p.bands) {
                val bo = JSONObject()
                bo.put("index", b.index)
                bo.put("frequencyHz", b.frequencyHz)
                bo.put("gainDb", b.gainDb)
                bo.put("q", b.q)
                bo.put("filterType", b.filterType.name)
                bo.put("enabled", b.enabled)
                bandsArr.put(bo)
            }
            obj.put("bands", bandsArr)
            arr.put(obj)
        }
        storageFile.writeText(arr.toString(2))
    }

    companion object {
        val BUILTIN_PRESETS: List<EqPreset> = listOf(
            EqPreset(
                id = "builtin_flat",
                name = "Reference Flat",
                preampDb = 0.0,
                bands = DefaultBands.flatBands(10),
                builtIn = true
            ),
            EqPreset(
                id = "builtin_harman",
                name = "Harman Target Warmth",
                preampDb = -4.5,
                bands = listOf(
                    EqBand(0, 35, 3.0, 0.70, FilterType.LOW_SHELF),
                    EqBand(1, 105, 2.5, 0.71, FilterType.LOW_SHELF),
                    EqBand(2, 220, -1.5, 1.10, FilterType.PEAK),
                    EqBand(3, 500, 0.0, 1.41, FilterType.PEAK),
                    EqBand(4, 1000, 0.0, 1.41, FilterType.PEAK),
                    EqBand(5, 2200, 2.0, 1.50, FilterType.PEAK),
                    EqBand(6, 3500, 1.5, 2.20, FilterType.PEAK),
                    EqBand(7, 5800, -1.5, 2.50, FilterType.PEAK),
                    EqBand(8, 8500, 1.0, 1.80, FilterType.PEAK),
                    EqBand(9, 12000, 1.5, 0.71, FilterType.HIGH_SHELF)
                ),
                builtIn = true
            ),
            EqPreset(
                id = "builtin_vocal",
                name = "Vocal Clarity",
                preampDb = -3.0,
                bands = listOf(
                    EqBand(0, 60, -1.5, 0.80, FilterType.LOW_SHELF),
                    EqBand(1, 160, -1.0, 1.20, FilterType.PEAK),
                    EqBand(2, 400, 0.0, 1.41, FilterType.PEAK),
                    EqBand(3, 950, 1.5, 1.30, FilterType.PEAK),
                    EqBand(4, 1800, 2.5, 1.40, FilterType.PEAK),
                    EqBand(5, 3000, 3.0, 1.80, FilterType.PEAK),
                    EqBand(6, 5200, 1.0, 2.00, FilterType.PEAK),
                    EqBand(7, 7500, -1.0, 2.80, FilterType.PEAK),
                    EqBand(8, 10000, 1.0, 1.41, FilterType.PEAK),
                    EqBand(9, 14000, 1.5, 0.71, FilterType.HIGH_SHELF)
                ),
                builtIn = true
            ),
            EqPreset(
                id = "builtin_subbass",
                name = "Sub-Bass Punch",
                preampDb = -5.0,
                bands = listOf(
                    EqBand(0, 32, 4.5, 0.80, FilterType.PEAK),
                    EqBand(1, 65, 3.5, 0.90, FilterType.PEAK),
                    EqBand(2, 125, 1.5, 1.20, FilterType.PEAK),
                    EqBand(3, 250, -1.0, 1.41, FilterType.PEAK),
                    EqBand(4, 600, 0.0, 1.41, FilterType.PEAK),
                    EqBand(5, 1500, 0.5, 1.41, FilterType.PEAK),
                    EqBand(6, 3200, 1.5, 1.60, FilterType.PEAK),
                    EqBand(7, 6000, 0.5, 1.80, FilterType.PEAK),
                    EqBand(8, 10000, 1.5, 1.20, FilterType.PEAK),
                    EqBand(9, 14000, 0.5, 0.71, FilterType.HIGH_SHELF)
                ),
                builtIn = true
            )
        )
    }
}
