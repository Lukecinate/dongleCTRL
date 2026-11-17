package com.suprcharg3d.dongleCTRL.protocol

import com.suprcharg3d.dongleCTRL.dsp.FilterType

data class FiioPresetDef(
    val id: Int,
    val label: String,
    val custom: Boolean,
    val bypass: Boolean,
    val category: String = "general"
)

data class FiioSliderDef(
    val min: Int,
    val max: Int,
    val step: Int,
    val label: String
)

data class FiioControlDef(
    val section: String,
    val key: String,
    val label: String = "",
    val uiType: Int?,
    val getCmd: Int,
    val setCmd: Int,
    val min: Int?,
    val max: Int?,
    val step: Int?,
    val unit: String = "",
    val startText: String = "",
    val parseRulerType: Int = 0,
    val options: List<NamedOption> = emptyList(),
    val bitMaskOptions: List<NamedOption> = emptyList(),
    val supportCmd: Int = 0,
    val sliders: List<FiioSliderDef> = emptyList(),
    val allowLimit: Boolean = false,
    val limitCmd: Int = 0,
    val allowMaxValueChange: Boolean = false,
    val maxValueChangeCmd: Int = 0,
    val queryCmd: Int = 0
)

data class FiioDeviceSchema(
    val deviceType: Int,
    val name: String,
    val connectMode: Int?,
    val commandBuilderType: Int?,
    val parseRulerType: Int,
    val usbOnlyPeq: Boolean,
    val bandCount: Int,
    val filterList: List<Int>,
    val gainMin: Double,
    val gainMax: Double,
    val eqSwitchSupport: Boolean,
    val eqSwitchGetCmd: Int,
    val eqSwitchSetCmd: Int,
    val eqPresetSupport: Boolean,
    val eqPresetGetCmd: Int,
    val eqPresetSetCmd: Int,
    val eqBandSupport: Boolean,
    val eqBandGetCmd: Int,
    val eqBandSetCmd: Int,
    val eqBandSupportBatch: Boolean,
    val eqSaveSupport: Boolean,
    val eqSaveSetCmd: Int,
    val eqResetSetCmd: Int,
    val masterGainSupport: Boolean,
    val masterGainGetCmd: Int,
    val masterGainSetCmd: Int,
    val batteryGetCmd: Int,
    val codecGetCmd: Int,
    val codecOptions: List<NamedOption>,
    val presets: List<FiioPresetDef>,
    val controls: List<FiioControlDef>,
    val editNameGetCmd: Int = 0,
    val editNameSetCmd: Int = 0,
    val editNameMaxBytes: Int = 24,
    val editNameRuler: Int = 0,
    val clearPairingCmd: Int = 0,
    val resetDefaultCmd: Int = 0,
    val closeDeviceCmd: Int = 0,
    val closeDeviceRuler: Int = 0,
    val btResetDefaultCmd: Int = 0,
    val systemStandbyCmd: Int = 0,
    val eqSwitchRuler: Int = 0,
    val eqResetRuler: Int = 0,
    val eqBandBatchGetCmd: Int = 0,
    val eqBandBatchSetCmd: Int = 0,
    val masterGainSupportBatch: Boolean = false,
    val editEqNameSupport: Boolean = false,
    val editEqNameGetCmd: Int = 0,
    val editEqNameSetCmd: Int = 0,
    val editEqNameSupportBatch: Boolean = false,
    val deviceVersionCmd: Int = 0,
    val deviceCommandInterval: Int = 0,
    val batchQuerySupported: Boolean = false,
    val batchQueryCmd: Int = 0,
    val batchQueryMaxPayload: Int = 200,
    val batchSetSupported: Boolean = false,
    val batchSetCmd: Int = 0
) {
    val customPresetIds: Set<Int>
        get() = presets.filter { it.custom }.map { it.id }.toSet()

    val bypassPresetId: Int?
        get() = presets.firstOrNull { it.bypass }?.id

    val firstCustomPresetId: Int
        get() = presets.firstOrNull { it.custom }?.id ?: 160

    /** Returns presets adjusted for QX11 UAC 1.0 when parseRulerType == 12. */
    fun presetsForUac(isUac1: Boolean): List<FiioPresetDef> {
        if (!isUac1 || parseRulerType != 12) return presets
        return listOf(
            FiioPresetDef(0, "Jazz", custom = false, bypass = false),
            FiioPresetDef(1, "Pop", custom = false, bypass = false),
            FiioPresetDef(2, "Rock", custom = false, bypass = false),
            FiioPresetDef(3, "Dance", custom = false, bypass = false),
            FiioPresetDef(4, "R&B", custom = false, bypass = false),
            FiioPresetDef(5, "Classic", custom = false, bypass = false),
            FiioPresetDef(6, "Hip-Hop", custom = false, bypass = false),
            FiioPresetDef(12, "Cinema", custom = false, bypass = false, category = "movie"),
            FiioPresetDef(257, "FPS", custom = false, bypass = false, category = "game"),
            FiioPresetDef(258, "ACT", custom = false, bypass = false, category = "game"),
            FiioPresetDef(259, "MOBA", custom = false, bypass = false, category = "game"),
            FiioPresetDef(260, "MUG", custom = false, bypass = false, category = "game"),
            FiioPresetDef(160, "User 1", custom = true, bypass = false),
            FiioPresetDef(161, "User 2", custom = true, bypass = false),
            FiioPresetDef(162, "User 3", custom = true, bypass = false),
            FiioPresetDef(163, "User 4", custom = true, bypass = false),
            FiioPresetDef(164, "User 5", custom = true, bypass = false),
            FiioPresetDef(165, "User 6", custom = true, bypass = false),
            FiioPresetDef(166, "User 7", custom = true, bypass = false),
            FiioPresetDef(240, "Off / Bypass", custom = false, bypass = true)
        )
    }

    fun supportedFilterTypes(): List<FilterType> {
        val mapped = filterList.map { FilterType.fromFiioCode(it) }.distinct()
        return mapped.ifEmpty { listOf(FilterType.PEAK, FilterType.LOW_SHELF, FilterType.HIGH_SHELF) }
    }

    fun findControl(vararg keys: String): FiioControlDef? =
        controls.firstOrNull { ctrl -> keys.any { k -> ctrl.key.equals(k, ignoreCase = true) } }
}

/**
 * Pure-Kotlin universal registry of all FiiO / JadeAudio / Snowsky device schemas
 * extracted from official sc_config_default.db.
 */
class FiioSchemaCatalog(val schemas: List<FiioDeviceSchema>) {

    private val byDeviceType = schemas.associateBy { it.deviceType }

    fun findByDeviceType(deviceType: Int): FiioDeviceSchema? = byDeviceType[deviceType]

    fun matchName(rawName: String?): FiioDeviceSchema? {
        val clean = normalizeModelName(rawName) ?: return null
        // Exact match first, then longest matching substring (so "FIIO BTR17" doesn't match "FIIO BTR1")
        byExactNormalized(clean)?.let { return it }
        return schemas
            .sortedByDescending { it.name.length }
            .firstOrNull { schema ->
                val target = normalizeModelName(schema.name) ?: return@firstOrNull false
                clean.contains(target) || target.contains(clean)
            }
    }

    private fun byExactNormalized(clean: String): FiioDeviceSchema? =
        schemas.firstOrNull { normalizeModelName(it.name) == clean }

    companion object {
        private fun normalizeModelName(name: String?): String? {
            val raw = name?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            val aliased = if (raw == "fiio dsp audio") "fiio ls-tc2" else raw
            return aliased
                .replace("jadeaudio", " ")
                .replace("snowsky", " ")
                .replace("fiio", " ")
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()
                .ifEmpty { raw }
        }

        @Suppress("UNCHECKED_CAST")
        fun fromJson(jsonText: String): FiioSchemaCatalog {
            val root = SimpleJson.parseObject(jsonText)
            val rawSchemas = root["schemas"] as? List<Map<String, Any?>> ?: emptyList()
            val list = rawSchemas.mapNotNull { obj ->
                val dt = (obj["deviceType"] as? Number)?.toInt() ?: return@mapNotNull null
                val name = obj["name"] as? String ?: return@mapNotNull null
                val rawPresets = obj["presets"] as? List<Map<String, Any?>> ?: emptyList()
                val presets = rawPresets.mapNotNull { p ->
                    val pid = (p["id"] as? Number)?.toInt() ?: return@mapNotNull null
                    FiioPresetDef(
                        id = pid,
                        label = p["label"] as? String ?: "Slot $pid",
                        custom = p["custom"] as? Boolean ?: false,
                        bypass = p["bypass"] as? Boolean ?: false,
                        category = p["category"] as? String ?: "general"
                    )
                }
                val rawCtrls = obj["controls"] as? List<Map<String, Any?>> ?: emptyList()
                val ctrls = rawCtrls.map { c ->
                    val rawOpts = c["options"] as? List<Map<String, Any?>> ?: emptyList()
                    val opts = rawOpts.mapNotNull { o ->
                        val oid = (o["id"] as? Number)?.toInt() ?: return@mapNotNull null
                        NamedOption(oid, o["label"] as? String ?: oid.toString())
                    }
                    val rawBitMasks = c["bitMaskOptions"] as? List<Map<String, Any?>> ?: emptyList()
                    val bitMaskOpts = rawBitMasks.mapNotNull { o ->
                        val oid = (o["id"] as? Number)?.toInt() ?: return@mapNotNull null
                        NamedOption(oid, o["label"] as? String ?: oid.toString())
                    }
                    val rawSliders = c["sliders"] as? List<Map<String, Any?>> ?: emptyList()
                    val sliders = rawSliders.map { sl ->
                        FiioSliderDef(
                            min = (sl["min"] as? Number)?.toInt() ?: 0,
                            max = (sl["max"] as? Number)?.toInt() ?: 5,
                            step = (sl["step"] as? Number)?.toInt() ?: 1,
                            label = sl["label"] as? String ?: ""
                        )
                    }
                    FiioControlDef(
                        section = c["section"] as? String ?: "state",
                        key = c["key"] as? String ?: "",
                        label = c["label"] as? String ?: "",
                        uiType = (c["uiType"] as? Number)?.toInt(),
                        getCmd = (c["getCmd"] as? Number)?.toInt() ?: 0,
                        setCmd = (c["setCmd"] as? Number)?.toInt() ?: 0,
                        min = (c["min"] as? Number)?.toInt(),
                        max = (c["max"] as? Number)?.toInt(),
                        step = (c["step"] as? Number)?.toInt(),
                        unit = c["unit"] as? String ?: "",
                        startText = c["startText"] as? String ?: "",
                        parseRulerType = (c["parseRulerType"] as? Number)?.toInt() ?: 0,
                        options = opts,
                        bitMaskOptions = bitMaskOpts,
                        supportCmd = (c["supportCmd"] as? Number)?.toInt() ?: 0,
                        sliders = sliders,
                        allowLimit = c["allowLimit"] as? Boolean ?: false,
                        limitCmd = (c["limitCmd"] as? Number)?.toInt() ?: 0,
                        allowMaxValueChange = c["allowMaxValueChange"] as? Boolean ?: false,
                        maxValueChangeCmd = (c["maxValueChangeCmd"] as? Number)?.toInt() ?: 0,
                        queryCmd = (c["queryCmd"] as? Number)?.toInt() ?: 0
                    )
                }
                val rawCodecOpts = obj["codecOptions"] as? List<Map<String, Any?>> ?: emptyList()
                val codecOpts = rawCodecOpts.mapNotNull { o ->
                    val oid = (o["id"] as? Number)?.toInt() ?: return@mapNotNull null
                    NamedOption(oid, o["label"] as? String ?: oid.toString())
                }
                val rawFilters = obj["filterList"] as? List<Number> ?: emptyList()
                FiioDeviceSchema(
                    deviceType = dt,
                    name = name,
                    connectMode = (obj["connectMode"] as? Number)?.toInt(),
                    commandBuilderType = (obj["commandBuilderType"] as? Number)?.toInt(),
                    parseRulerType = (obj["parseRulerType"] as? Number)?.toInt() ?: 0,
                    usbOnlyPeq = obj["usbOnlyPeq"] as? Boolean ?: false,
                    bandCount = (obj["bandCount"] as? Number)?.toInt() ?: 10,
                    filterList = rawFilters.map { it.toInt() },
                    gainMin = (obj["gainMin"] as? Number)?.toDouble() ?: -12.0,
                    gainMax = (obj["gainMax"] as? Number)?.toDouble() ?: 12.0,
                    eqSwitchSupport = obj["eqSwitchSupport"] as? Boolean ?: true,
                    eqSwitchGetCmd = (obj["eqSwitchGetCmd"] as? Number)?.toInt() ?: 26,
                    eqSwitchSetCmd = (obj["eqSwitchSetCmd"] as? Number)?.toInt() ?: 26,
                    eqPresetSupport = obj["eqPresetSupport"] as? Boolean ?: true,
                    eqPresetGetCmd = (obj["eqPresetGetCmd"] as? Number)?.toInt() ?: 22,
                    eqPresetSetCmd = (obj["eqPresetSetCmd"] as? Number)?.toInt() ?: 22,
                    eqBandSupport = obj["eqBandSupport"] as? Boolean ?: true,
                    eqBandGetCmd = (obj["eqBandGetCmd"] as? Number)?.toInt() ?: 21,
                    eqBandSetCmd = (obj["eqBandSetCmd"] as? Number)?.toInt() ?: 21,
                    eqBandSupportBatch = obj["eqBandSupportBatch"] as? Boolean ?: false,
                    eqSaveSupport = obj["eqSaveSupport"] as? Boolean ?: false,
                    eqSaveSetCmd = (obj["eqSaveSetCmd"] as? Number)?.toInt() ?: 0,
                    eqResetSetCmd = (obj["eqResetSetCmd"] as? Number)?.toInt() ?: 0,
                    masterGainSupport = obj["masterGainSupport"] as? Boolean ?: true,
                    masterGainGetCmd = (obj["masterGainGetCmd"] as? Number)?.toInt() ?: 23,
                    masterGainSetCmd = (obj["masterGainSetCmd"] as? Number)?.toInt() ?: 23,
                    batteryGetCmd = (obj["batteryGetCmd"] as? Number)?.toInt() ?: 0,
                    codecGetCmd = (obj["codecGetCmd"] as? Number)?.toInt() ?: 0,
                    codecOptions = codecOpts,
                    presets = presets,
                    controls = ctrls,
                    editNameGetCmd = (obj["editNameGetCmd"] as? Number)?.toInt() ?: 0,
                    editNameSetCmd = (obj["editNameSetCmd"] as? Number)?.toInt() ?: 0,
                    editNameMaxBytes = (obj["editNameMaxBytes"] as? Number)?.toInt() ?: 24,
                    editNameRuler = (obj["editNameRuler"] as? Number)?.toInt() ?: 0,
                    clearPairingCmd = (obj["clearPairingCmd"] as? Number)?.toInt() ?: 0,
                    resetDefaultCmd = (obj["resetDefaultCmd"] as? Number)?.toInt() ?: 0,
                    closeDeviceCmd = (obj["closeDeviceCmd"] as? Number)?.toInt() ?: 0,
                    closeDeviceRuler = (obj["closeDeviceRuler"] as? Number)?.toInt() ?: 0,
                    btResetDefaultCmd = (obj["btResetDefaultCmd"] as? Number)?.toInt() ?: 0,
                    systemStandbyCmd = (obj["systemStandbyCmd"] as? Number)?.toInt() ?: 0,
                    eqSwitchRuler = (obj["eqSwitchRuler"] as? Number)?.toInt() ?: 0,
                    eqResetRuler = (obj["eqResetRuler"] as? Number)?.toInt() ?: 0,
                    eqBandBatchGetCmd = (obj["eqBandBatchGetCmd"] as? Number)?.toInt() ?: 0,
                    eqBandBatchSetCmd = (obj["eqBandBatchSetCmd"] as? Number)?.toInt() ?: 0,
                    masterGainSupportBatch = obj["masterGainSupportBatch"] as? Boolean ?: false,
                    editEqNameSupport = obj["editEqNameSupport"] as? Boolean ?: false,
                    editEqNameGetCmd = (obj["editEqNameGetCmd"] as? Number)?.toInt() ?: 0,
                    editEqNameSetCmd = (obj["editEqNameSetCmd"] as? Number)?.toInt() ?: 0,
                    editEqNameSupportBatch = obj["editEqNameSupportBatch"] as? Boolean ?: false,
                    deviceVersionCmd = (obj["deviceVersionCmd"] as? Number)?.toInt() ?: 0,
                    deviceCommandInterval = (obj["deviceCommandInterval"] as? Number)?.toInt() ?: 0,
                    batchQuerySupported = obj["batchQuerySupported"] as? Boolean ?: false,
                    batchQueryCmd = (obj["batchQueryCmd"] as? Number)?.toInt() ?: 0,
                    batchQueryMaxPayload = (obj["batchQueryMaxPayload"] as? Number)?.toInt() ?: 200,
                    batchSetSupported = obj["batchSetSupported"] as? Boolean ?: false,
                    batchSetCmd = (obj["batchSetCmd"] as? Number)?.toInt() ?: 0
                )
            }
            return FiioSchemaCatalog(list)
        }
    }
}
