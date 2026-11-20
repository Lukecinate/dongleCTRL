package com.suprcharg3d.dongleCTRL.data

import android.content.Context
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

    fun loadAll(): List<EqPreset> = readUserPresets()

    fun saveUserPreset(preset: EqPreset): List<EqPreset> {
        val current = readUserPresets().filterNot { it.id == preset.id }.toMutableList()
        current.add(0, preset.copy(builtIn = false))
        writeUserPresets(current)
        return current
    }

    fun deleteUserPreset(id: String): List<EqPreset> {
        val current = readUserPresets().filterNot { it.id == id }
        writeUserPresets(current)
        return current
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
}
