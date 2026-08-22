package com.junchen.jingdu

import android.content.Context

enum class ReaderPalette { PAPER, LIGHT, NIGHT }
enum class ReaderTypeface { SYSTEM, SERIF }

data class ReaderSettings(
    val palette: ReaderPalette = ReaderPalette.PAPER,
    val typeface: ReaderTypeface = ReaderTypeface.SYSTEM,
    val fontSizeSp: Float = 20f,
    val lineHeightMultiplier: Float = 1.55f,
    val horizontalPaddingDp: Float = 24f,
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val autoPageDelayMs: Long = 6500L,
)

class ReaderPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("jingdu.reader.settings.v1", Context.MODE_PRIVATE)

    fun load(): ReaderSettings = ReaderSettings(
        palette = enumOrDefault(prefs.getString("palette", null), ReaderPalette.PAPER),
        typeface = enumOrDefault(prefs.getString("typeface", null), ReaderTypeface.SYSTEM),
        fontSizeSp = prefs.getFloat("fontSizeSp", 20f).coerceIn(16f, 34f),
        lineHeightMultiplier = prefs.getFloat("lineHeight", 1.55f).coerceIn(1.2f, 2.0f),
        horizontalPaddingDp = prefs.getFloat("horizontalPadding", 24f).coerceIn(12f, 48f),
        ttsRate = prefs.getFloat("ttsRate", 1.0f).coerceIn(0.6f, 1.8f),
        ttsPitch = prefs.getFloat("ttsPitch", 1.0f).coerceIn(0.7f, 1.4f),
        autoPageDelayMs = prefs.getLong("autoPageDelayMs", 6500L).coerceIn(2500L, 15000L),
    )

    fun save(value: ReaderSettings) {
        prefs.edit()
            .putString("palette", value.palette.name)
            .putString("typeface", value.typeface.name)
            .putFloat("fontSizeSp", value.fontSizeSp)
            .putFloat("lineHeight", value.lineHeightMultiplier)
            .putFloat("horizontalPadding", value.horizontalPaddingDp)
            .putFloat("ttsRate", value.ttsRate)
            .putFloat("ttsPitch", value.ttsPitch)
            .putLong("autoPageDelayMs", value.autoPageDelayMs)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T {
        if (raw == null) return fallback
        return enumValues<T>().firstOrNull { it.name == raw } ?: fallback
    }
}
