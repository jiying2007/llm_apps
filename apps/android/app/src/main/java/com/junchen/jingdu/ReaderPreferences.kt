package com.junchen.jingdu

import android.content.Context

enum class ReaderPalette { PAPER, LIGHT, NIGHT }
enum class ReaderTypeface { SYSTEM, SERIF }
enum class ChineseDisplayMode { ORIGINAL, SIMPLIFIED, TRADITIONAL, TAIWAN, TAIWAN_PHRASES, HONG_KONG }

data class ReaderSettings(
    val palette: ReaderPalette = ReaderPalette.PAPER,
    val typeface: ReaderTypeface = ReaderTypeface.SYSTEM,
    val fontSizeSp: Float = 20f,
    val lineHeightMultiplier: Float = 1.55f,
    val horizontalPaddingDp: Float = 24f,
    val chineseMode: ChineseDisplayMode = ChineseDisplayMode.ORIGINAL,
    val chineseOverrides: String = "",
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsVoiceName: String = "",
    val autoPageDelayMs: Long = 6500L,
)

class ReaderPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("jingdu.reader.settings.v1", Context.MODE_PRIVATE)

    fun load(): ReaderSettings {
        val value = ReaderSettings(
            palette = enumOrDefault(prefs.getString("palette", null), ReaderPalette.PAPER),
            typeface = enumOrDefault(prefs.getString("typeface", null), ReaderTypeface.SYSTEM),
            fontSizeSp = prefs.getFloat("fontSizeSp", 20f).coerceIn(16f, 34f),
            lineHeightMultiplier = prefs.getFloat("lineHeight", 1.55f).coerceIn(1.2f, 2.0f),
            horizontalPaddingDp = prefs.getFloat("horizontalPadding", 24f).coerceIn(12f, 48f),
            chineseMode = enumOrDefault(prefs.getString("chineseMode", null), ChineseDisplayMode.ORIGINAL),
            chineseOverrides = (prefs.getString("chineseOverrides", "") ?: "").take(MAX_OVERRIDE_TEXT_CHARS),
            ttsRate = prefs.getFloat("ttsRate", 1.0f).coerceIn(0.6f, 1.8f),
            ttsPitch = prefs.getFloat("ttsPitch", 1.0f).coerceIn(0.7f, 1.4f),
            ttsVoiceName = prefs.getString("ttsVoiceName", "") ?: "",
            autoPageDelayMs = prefs.getLong("autoPageDelayMs", 6500L).coerceIn(2500L, 15000L),
        )
        ChineseDisplayConverter.configure(value)
        return value
    }

    fun save(value: ReaderSettings) {
        val safe = value.copy(chineseOverrides = value.chineseOverrides.take(MAX_OVERRIDE_TEXT_CHARS))
        prefs.edit()
            .putString("palette", safe.palette.name)
            .putString("typeface", safe.typeface.name)
            .putFloat("fontSizeSp", safe.fontSizeSp)
            .putFloat("lineHeight", safe.lineHeightMultiplier)
            .putFloat("horizontalPadding", safe.horizontalPaddingDp)
            .putString("chineseMode", safe.chineseMode.name)
            .putString("chineseOverrides", safe.chineseOverrides)
            .putFloat("ttsRate", safe.ttsRate)
            .putFloat("ttsPitch", safe.ttsPitch)
            .putString("ttsVoiceName", safe.ttsVoiceName)
            .putLong("autoPageDelayMs", safe.autoPageDelayMs)
            .apply()
        ChineseDisplayConverter.configure(safe)
    }

    fun exportMap(): Map<String, Any> {
        val value = load()
        return mapOf(
            "palette" to value.palette.name,
            "typeface" to value.typeface.name,
            "fontSizeSp" to value.fontSizeSp,
            "lineHeightMultiplier" to value.lineHeightMultiplier,
            "horizontalPaddingDp" to value.horizontalPaddingDp,
            "chineseMode" to value.chineseMode.name,
            "chineseOverrides" to value.chineseOverrides,
            "ttsRate" to value.ttsRate,
            "ttsPitch" to value.ttsPitch,
            "ttsVoiceName" to value.ttsVoiceName,
            "autoPageDelayMs" to value.autoPageDelayMs,
        )
    }

    fun importMap(values: Map<String, Any?>): ReaderSettings {
        val fallback = load()
        val imported = ReaderSettings(
            palette = runCatching { ReaderPalette.valueOf(values["palette"] as? String ?: fallback.palette.name) }.getOrDefault(fallback.palette),
            typeface = runCatching { ReaderTypeface.valueOf(values["typeface"] as? String ?: fallback.typeface.name) }.getOrDefault(fallback.typeface),
            fontSizeSp = (values["fontSizeSp"] as? Number)?.toFloat()?.coerceIn(16f, 34f) ?: fallback.fontSizeSp,
            lineHeightMultiplier = (values["lineHeightMultiplier"] as? Number)?.toFloat()?.coerceIn(1.2f, 2.0f) ?: fallback.lineHeightMultiplier,
            horizontalPaddingDp = (values["horizontalPaddingDp"] as? Number)?.toFloat()?.coerceIn(12f, 48f) ?: fallback.horizontalPaddingDp,
            chineseMode = runCatching { ChineseDisplayMode.valueOf(values["chineseMode"] as? String ?: fallback.chineseMode.name) }.getOrDefault(fallback.chineseMode),
            chineseOverrides = (values["chineseOverrides"] as? String)?.take(MAX_OVERRIDE_TEXT_CHARS) ?: fallback.chineseOverrides,
            ttsRate = (values["ttsRate"] as? Number)?.toFloat()?.coerceIn(0.6f, 1.8f) ?: fallback.ttsRate,
            ttsPitch = (values["ttsPitch"] as? Number)?.toFloat()?.coerceIn(0.7f, 1.4f) ?: fallback.ttsPitch,
            ttsVoiceName = (values["ttsVoiceName"] as? String)?.take(256) ?: fallback.ttsVoiceName,
            autoPageDelayMs = (values["autoPageDelayMs"] as? Number)?.toLong()?.coerceIn(2500L, 15000L) ?: fallback.autoPageDelayMs,
        )
        save(imported)
        return imported
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T {
        if (raw == null) return fallback
        return enumValues<T>().firstOrNull { it.name == raw } ?: fallback
    }

    private companion object {
        const val MAX_OVERRIDE_TEXT_CHARS = 16 * 1024
    }
}
