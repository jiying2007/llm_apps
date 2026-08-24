package com.junchen.jingdu

import android.content.Context

enum class ReaderPalette { PAPER, LIGHT, NIGHT, OLED }
enum class ReaderTypeface { SYSTEM, SERIF }
enum class ChineseDisplayMode { ORIGINAL, SIMPLIFIED, TRADITIONAL, TAIWAN, TAIWAN_PHRASES, HONG_KONG }
enum class ReaderMode { PAGED, CONTINUOUS }
enum class ReaderPageAnimation { NONE, SLIDE }
enum class ReaderOrientation { SYSTEM, PORTRAIT, LANDSCAPE }
enum class ReaderTextAlignment { START, JUSTIFY }
enum class ReaderFontWeight { NORMAL, MEDIUM, SEMIBOLD }
enum class ReaderPreset { STANDARD, COMFORT, LARGE, NIGHT, CUSTOM }
enum class ReaderVolumeKeyMode { PAGE_WHEN_NOT_TTS, ALWAYS_PAGE, SYSTEM_VOLUME }
enum class ReaderWideColumns { AUTO, SINGLE, DOUBLE }

data class ReaderSettings(
    val palette: ReaderPalette = ReaderPalette.PAPER,
    val typeface: ReaderTypeface = ReaderTypeface.SYSTEM,
    val fontSizeSp: Float = 20f,
    val lineHeightMultiplier: Float = 1.55f,
    val horizontalPaddingDp: Float = 24f,
    val verticalPaddingDp: Float = 18f,
    val firstLineIndentEm: Float = 0f,
    val textAlignment: ReaderTextAlignment = ReaderTextAlignment.JUSTIFY,
    val fontWeight: ReaderFontWeight = ReaderFontWeight.NORMAL,
    val preset: ReaderPreset = ReaderPreset.STANDARD,
    val readingMode: ReaderMode = ReaderMode.PAGED,
    val pageAnimation: ReaderPageAnimation = ReaderPageAnimation.SLIDE,
    val tapPagingEnabled: Boolean = true,
    val swipePagingEnabled: Boolean = true,
    val reversePagingGestures: Boolean = false,
    val tapZoneEdgeFraction: Float = 0.25f,
    val controlsAutoHideMs: Long = 3500L,
    val useSystemBrightness: Boolean = true,
    val readerBrightness: Float = 0.45f,
    val orientation: ReaderOrientation = ReaderOrientation.SYSTEM,
    val volumeKeyMode: ReaderVolumeKeyMode = ReaderVolumeKeyMode.PAGE_WHEN_NOT_TTS,
    val reverseVolumeKeys: Boolean = false,
    val wideColumns: ReaderWideColumns = ReaderWideColumns.AUTO,
    val showReadingStatus: Boolean = true,
    val focusRulerLines: Int = 0,
    val autoScrollEnabled: Boolean = false,
    val autoScrollSpeedDpPerSecond: Float = 55f,
    val chineseMode: ChineseDisplayMode = ChineseDisplayMode.ORIGINAL,
    val chineseOverrides: String = "",
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsVoiceName: String = "",
    val autoPageDelayMs: Long = 6500L,
)

internal fun ReaderSettings.applyPreset(value: ReaderPreset): ReaderSettings = when (value) {
    ReaderPreset.STANDARD -> copy(
        preset = value,
        palette = ReaderPalette.PAPER,
        typeface = ReaderTypeface.SYSTEM,
        fontSizeSp = 20f,
        lineHeightMultiplier = 1.55f,
        horizontalPaddingDp = 24f,
        verticalPaddingDp = 18f,
        firstLineIndentEm = 0f,
        textAlignment = ReaderTextAlignment.JUSTIFY,
        fontWeight = ReaderFontWeight.NORMAL,
    )
    ReaderPreset.COMFORT -> copy(
        preset = value,
        palette = ReaderPalette.PAPER,
        typeface = ReaderTypeface.SERIF,
        fontSizeSp = 21f,
        lineHeightMultiplier = 1.65f,
        horizontalPaddingDp = 28f,
        verticalPaddingDp = 22f,
        firstLineIndentEm = 2f,
        textAlignment = ReaderTextAlignment.JUSTIFY,
        fontWeight = ReaderFontWeight.NORMAL,
    )
    ReaderPreset.LARGE -> copy(
        preset = value,
        palette = ReaderPalette.LIGHT,
        typeface = ReaderTypeface.SYSTEM,
        fontSizeSp = 28f,
        lineHeightMultiplier = 1.72f,
        horizontalPaddingDp = 30f,
        verticalPaddingDp = 24f,
        firstLineIndentEm = 1f,
        textAlignment = ReaderTextAlignment.START,
        fontWeight = ReaderFontWeight.MEDIUM,
    )
    ReaderPreset.NIGHT -> copy(
        preset = value,
        palette = ReaderPalette.NIGHT,
        typeface = ReaderTypeface.SERIF,
        fontSizeSp = 21f,
        lineHeightMultiplier = 1.65f,
        horizontalPaddingDp = 26f,
        verticalPaddingDp = 20f,
        firstLineIndentEm = 2f,
        textAlignment = ReaderTextAlignment.JUSTIFY,
        fontWeight = ReaderFontWeight.NORMAL,
    )
    ReaderPreset.CUSTOM -> copy(preset = value)
}

class ReaderPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("jingdu.reader.settings.v1", Context.MODE_PRIVATE)

    fun load(): ReaderSettings {
        val value = ReaderSettings(
            palette = enumOrDefault(prefs.getString("palette", null), ReaderPalette.PAPER),
            typeface = enumOrDefault(prefs.getString("typeface", null), ReaderTypeface.SYSTEM),
            fontSizeSp = prefs.getFloat("fontSizeSp", 20f).coerceIn(16f, 34f),
            lineHeightMultiplier = prefs.getFloat("lineHeight", 1.55f).coerceIn(1.2f, 2.0f),
            horizontalPaddingDp = prefs.getFloat("horizontalPadding", 24f).coerceIn(12f, 48f),
            verticalPaddingDp = prefs.getFloat("verticalPadding", 18f).coerceIn(8f, 48f),
            firstLineIndentEm = prefs.getFloat("firstLineIndentEm", 0f).coerceIn(0f, 2f),
            textAlignment = enumOrDefault(prefs.getString("textAlignment", null), ReaderTextAlignment.JUSTIFY),
            fontWeight = enumOrDefault(prefs.getString("fontWeight", null), ReaderFontWeight.NORMAL),
            preset = enumOrDefault(prefs.getString("preset", null), ReaderPreset.STANDARD),
            readingMode = enumOrDefault(prefs.getString("readingMode", null), ReaderMode.PAGED),
            pageAnimation = enumOrDefault(prefs.getString("pageAnimation", null), ReaderPageAnimation.SLIDE),
            tapPagingEnabled = prefs.getBoolean("tapPagingEnabled", true),
            swipePagingEnabled = prefs.getBoolean("swipePagingEnabled", true),
            reversePagingGestures = prefs.getBoolean("reversePagingGestures", false),
            tapZoneEdgeFraction = prefs.getFloat("tapZoneEdgeFraction", 0.25f).coerceIn(0.20f, 0.35f),
            controlsAutoHideMs = prefs.getLong("controlsAutoHideMs", 3500L).coerceIn(2000L, 10000L),
            useSystemBrightness = prefs.getBoolean("useSystemBrightness", true),
            readerBrightness = prefs.getFloat("readerBrightness", 0.45f).coerceIn(0.05f, 1f),
            orientation = enumOrDefault(prefs.getString("orientation", null), ReaderOrientation.SYSTEM),
            volumeKeyMode = enumOrDefault(prefs.getString("volumeKeyMode", null), ReaderVolumeKeyMode.PAGE_WHEN_NOT_TTS),
            reverseVolumeKeys = prefs.getBoolean("reverseVolumeKeys", false),
            wideColumns = enumOrDefault(prefs.getString("wideColumns", null), ReaderWideColumns.AUTO),
            showReadingStatus = prefs.getBoolean("showReadingStatus", true),
            focusRulerLines = prefs.getInt("focusRulerLines", 0).let { if (it == 3 || it == 5) it else 0 },
            autoScrollEnabled = false,
            autoScrollSpeedDpPerSecond = prefs.getFloat("autoScrollSpeed", 55f).coerceIn(20f, 240f),
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
        val safe = sanitize(value)
        prefs.edit()
            .putString("palette", safe.palette.name)
            .putString("typeface", safe.typeface.name)
            .putFloat("fontSizeSp", safe.fontSizeSp)
            .putFloat("lineHeight", safe.lineHeightMultiplier)
            .putFloat("horizontalPadding", safe.horizontalPaddingDp)
            .putFloat("verticalPadding", safe.verticalPaddingDp)
            .putFloat("firstLineIndentEm", safe.firstLineIndentEm)
            .putString("textAlignment", safe.textAlignment.name)
            .putString("fontWeight", safe.fontWeight.name)
            .putString("preset", safe.preset.name)
            .putString("readingMode", safe.readingMode.name)
            .putString("pageAnimation", safe.pageAnimation.name)
            .putBoolean("tapPagingEnabled", safe.tapPagingEnabled)
            .putBoolean("swipePagingEnabled", safe.swipePagingEnabled)
            .putBoolean("reversePagingGestures", safe.reversePagingGestures)
            .putFloat("tapZoneEdgeFraction", safe.tapZoneEdgeFraction)
            .putLong("controlsAutoHideMs", safe.controlsAutoHideMs)
            .putBoolean("useSystemBrightness", safe.useSystemBrightness)
            .putFloat("readerBrightness", safe.readerBrightness)
            .putString("orientation", safe.orientation.name)
            .putString("volumeKeyMode", safe.volumeKeyMode.name)
            .putBoolean("reverseVolumeKeys", safe.reverseVolumeKeys)
            .putString("wideColumns", safe.wideColumns.name)
            .putBoolean("showReadingStatus", safe.showReadingStatus)
            .putInt("focusRulerLines", safe.focusRulerLines)
            .putFloat("autoScrollSpeed", safe.autoScrollSpeedDpPerSecond)
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
            "verticalPaddingDp" to value.verticalPaddingDp,
            "firstLineIndentEm" to value.firstLineIndentEm,
            "textAlignment" to value.textAlignment.name,
            "fontWeight" to value.fontWeight.name,
            "preset" to value.preset.name,
            "readingMode" to value.readingMode.name,
            "pageAnimation" to value.pageAnimation.name,
            "tapPagingEnabled" to value.tapPagingEnabled,
            "swipePagingEnabled" to value.swipePagingEnabled,
            "reversePagingGestures" to value.reversePagingGestures,
            "tapZoneEdgeFraction" to value.tapZoneEdgeFraction,
            "controlsAutoHideMs" to value.controlsAutoHideMs,
            "useSystemBrightness" to value.useSystemBrightness,
            "readerBrightness" to value.readerBrightness,
            "orientation" to value.orientation.name,
            "volumeKeyMode" to value.volumeKeyMode.name,
            "reverseVolumeKeys" to value.reverseVolumeKeys,
            "wideColumns" to value.wideColumns.name,
            "showReadingStatus" to value.showReadingStatus,
            "focusRulerLines" to value.focusRulerLines,
            "autoScrollSpeedDpPerSecond" to value.autoScrollSpeedDpPerSecond,
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
            palette = enumValue(values["palette"], fallback.palette),
            typeface = enumValue(values["typeface"], fallback.typeface),
            fontSizeSp = number(values["fontSizeSp"], fallback.fontSizeSp).coerceIn(16f, 34f),
            lineHeightMultiplier = number(values["lineHeightMultiplier"], fallback.lineHeightMultiplier).coerceIn(1.2f, 2.0f),
            horizontalPaddingDp = number(values["horizontalPaddingDp"], fallback.horizontalPaddingDp).coerceIn(12f, 48f),
            verticalPaddingDp = number(values["verticalPaddingDp"], fallback.verticalPaddingDp).coerceIn(8f, 48f),
            firstLineIndentEm = number(values["firstLineIndentEm"], fallback.firstLineIndentEm).coerceIn(0f, 2f),
            textAlignment = enumValue(values["textAlignment"], fallback.textAlignment),
            fontWeight = enumValue(values["fontWeight"], fallback.fontWeight),
            preset = enumValue(values["preset"], fallback.preset),
            readingMode = enumValue(values["readingMode"], fallback.readingMode),
            pageAnimation = enumValue(values["pageAnimation"], fallback.pageAnimation),
            tapPagingEnabled = values["tapPagingEnabled"] as? Boolean ?: fallback.tapPagingEnabled,
            swipePagingEnabled = values["swipePagingEnabled"] as? Boolean ?: fallback.swipePagingEnabled,
            reversePagingGestures = values["reversePagingGestures"] as? Boolean ?: fallback.reversePagingGestures,
            tapZoneEdgeFraction = number(values["tapZoneEdgeFraction"], fallback.tapZoneEdgeFraction).coerceIn(0.20f, 0.35f),
            controlsAutoHideMs = (values["controlsAutoHideMs"] as? Number)?.toLong()?.coerceIn(2000L, 10000L) ?: fallback.controlsAutoHideMs,
            useSystemBrightness = values["useSystemBrightness"] as? Boolean ?: fallback.useSystemBrightness,
            readerBrightness = number(values["readerBrightness"], fallback.readerBrightness).coerceIn(0.05f, 1f),
            orientation = enumValue(values["orientation"], fallback.orientation),
            volumeKeyMode = enumValue(values["volumeKeyMode"], fallback.volumeKeyMode),
            reverseVolumeKeys = values["reverseVolumeKeys"] as? Boolean ?: fallback.reverseVolumeKeys,
            wideColumns = enumValue(values["wideColumns"], fallback.wideColumns),
            showReadingStatus = values["showReadingStatus"] as? Boolean ?: fallback.showReadingStatus,
            focusRulerLines = (values["focusRulerLines"] as? Number)?.toInt()?.let { if (it == 3 || it == 5) it else 0 } ?: fallback.focusRulerLines,
            autoScrollEnabled = false,
            autoScrollSpeedDpPerSecond = number(values["autoScrollSpeedDpPerSecond"], fallback.autoScrollSpeedDpPerSecond).coerceIn(20f, 240f),
            chineseMode = enumValue(values["chineseMode"], fallback.chineseMode),
            chineseOverrides = (values["chineseOverrides"] as? String)?.take(MAX_OVERRIDE_TEXT_CHARS) ?: fallback.chineseOverrides,
            ttsRate = number(values["ttsRate"], fallback.ttsRate).coerceIn(0.6f, 1.8f),
            ttsPitch = number(values["ttsPitch"], fallback.ttsPitch).coerceIn(0.7f, 1.4f),
            ttsVoiceName = (values["ttsVoiceName"] as? String)?.take(256) ?: fallback.ttsVoiceName,
            autoPageDelayMs = (values["autoPageDelayMs"] as? Number)?.toLong()?.coerceIn(2500L, 15000L) ?: fallback.autoPageDelayMs,
        )
        save(imported)
        return imported
    }

    private fun sanitize(value: ReaderSettings): ReaderSettings = value.copy(
        fontSizeSp = value.fontSizeSp.coerceIn(16f, 34f),
        lineHeightMultiplier = value.lineHeightMultiplier.coerceIn(1.2f, 2.0f),
        horizontalPaddingDp = value.horizontalPaddingDp.coerceIn(12f, 48f),
        verticalPaddingDp = value.verticalPaddingDp.coerceIn(8f, 48f),
        firstLineIndentEm = value.firstLineIndentEm.coerceIn(0f, 2f),
        tapZoneEdgeFraction = value.tapZoneEdgeFraction.coerceIn(0.20f, 0.35f),
        controlsAutoHideMs = value.controlsAutoHideMs.coerceIn(2000L, 10000L),
        readerBrightness = value.readerBrightness.coerceIn(0.05f, 1f),
        focusRulerLines = if (value.focusRulerLines == 3 || value.focusRulerLines == 5) value.focusRulerLines else 0,
        autoScrollEnabled = false,
        autoScrollSpeedDpPerSecond = value.autoScrollSpeedDpPerSecond.coerceIn(20f, 240f),
        chineseOverrides = value.chineseOverrides.take(MAX_OVERRIDE_TEXT_CHARS),
        ttsRate = value.ttsRate.coerceIn(0.6f, 1.8f),
        ttsPitch = value.ttsPitch.coerceIn(0.7f, 1.4f),
        ttsVoiceName = value.ttsVoiceName.take(256),
        autoPageDelayMs = value.autoPageDelayMs.coerceIn(2500L, 15000L),
    )

    private fun number(value: Any?, fallback: Float): Float = (value as? Number)?.toFloat() ?: fallback

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T {
        if (raw == null) return fallback
        return enumValues<T>().firstOrNull { it.name == raw } ?: fallback
    }

    private inline fun <reified T : Enum<T>> enumValue(value: Any?, fallback: T): T =
        runCatching { enumValueOf<T>(value as? String ?: fallback.name) }.getOrDefault(fallback)

    private companion object {
        const val MAX_OVERRIDE_TEXT_CHARS = 16 * 1024
    }
}
