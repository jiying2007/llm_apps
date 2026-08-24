package com.junchen.jingdu

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

enum class ReaderPalette { PAPER, LIGHT, SEPIA, NIGHT, OLED }
enum class ReaderTypeface { SYSTEM, SERIF, MONOSPACE, CUSTOM }
enum class ChineseDisplayMode { ORIGINAL, SIMPLIFIED, TRADITIONAL, TAIWAN, TAIWAN_PHRASES, HONG_KONG }
enum class ReaderMode { PAGED, CONTINUOUS }
enum class ReaderPageAnimation { NONE, SLIDE }
enum class ReaderOrientation { SYSTEM, PORTRAIT, LANDSCAPE }
enum class ReaderTextAlignment { START, JUSTIFY }
enum class ReaderFontWeight { NORMAL, MEDIUM, SEMIBOLD }
enum class ReaderPreset { STANDARD, COMFORT, LARGE, NIGHT, CUSTOM }
enum class ReaderVolumeKeyMode { PAGE_WHEN_NOT_TTS, ALWAYS_PAGE, SYSTEM_VOLUME }
enum class ReaderWideColumns { AUTO, SINGLE, DOUBLE }
enum class ReaderTapZonePreset { BALANCED, RIGHT_HANDED, LEFT_HANDED, CUSTOM }
enum class ReaderAutoPageMode { ADAPTIVE, FIXED }

data class ReaderSettings(
    val palette: ReaderPalette = ReaderPalette.PAPER,
    val typeface: ReaderTypeface = ReaderTypeface.SYSTEM,
    val customFontId: String = "",
    val fontSizeSp: Float = 20f,
    val lineHeightMultiplier: Float = 1.55f,
    val letterSpacingEm: Float = 0f,
    val paragraphSpacingEm: Float = 0.45f,
    val horizontalPaddingDp: Float = 24f,
    val verticalPaddingDp: Float = 18f,
    val firstLineIndentEm: Float = 0f,
    val textAlignment: ReaderTextAlignment = ReaderTextAlignment.JUSTIFY,
    val fontWeight: ReaderFontWeight = ReaderFontWeight.NORMAL,
    val compressBlankLines: Boolean = true,
    val emphasizeHeadings: Boolean = true,
    val preset: ReaderPreset = ReaderPreset.STANDARD,
    val readingMode: ReaderMode = ReaderMode.PAGED,
    val pageAnimation: ReaderPageAnimation = ReaderPageAnimation.SLIDE,
    val tapPagingEnabled: Boolean = true,
    val swipePagingEnabled: Boolean = true,
    val reversePagingGestures: Boolean = false,
    val tapZonePreset: ReaderTapZonePreset = ReaderTapZonePreset.BALANCED,
    val tapZoneEdgeFraction: Float = 0.25f,
    val brightnessGestureEnabled: Boolean = true,
    val pinchFontEnabled: Boolean = true,
    val doubleTapBookmarkEnabled: Boolean = false,
    val controlsAutoHideMs: Long = 3500L,
    val useSystemBrightness: Boolean = true,
    val readerBrightness: Float = 0.45f,
    val orientation: ReaderOrientation = ReaderOrientation.SYSTEM,
    val volumeKeyMode: ReaderVolumeKeyMode = ReaderVolumeKeyMode.PAGE_WHEN_NOT_TTS,
    val reverseVolumeKeys: Boolean = false,
    val wideColumns: ReaderWideColumns = ReaderWideColumns.AUTO,
    val showReadingStatus: Boolean = true,
    val showClock: Boolean = false,
    val showBattery: Boolean = false,
    val focusRulerLines: Int = 0,
    val hapticEnabled: Boolean = true,
    val gestureCoachDismissed: Boolean = false,
    val autoScrollEnabled: Boolean = false,
    val autoScrollSpeedDpPerSecond: Float = 55f,
    val autoPageMode: ReaderAutoPageMode = ReaderAutoPageMode.ADAPTIVE,
    val autoPagePaceMultiplier: Float = 1.0f,
    val autoPageDelayMs: Long = 6500L,
    val chineseMode: ChineseDisplayMode = ChineseDisplayMode.ORIGINAL,
    val chineseOverrides: String = "",
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsVoiceName: String = "",
)

internal fun ReaderSettings.applyPreset(value: ReaderPreset): ReaderSettings = when (value) {
    ReaderPreset.STANDARD -> copy(
        preset = value,
        palette = ReaderPalette.PAPER,
        typeface = ReaderTypeface.SYSTEM,
        fontSizeSp = 20f,
        lineHeightMultiplier = 1.55f,
        letterSpacingEm = 0f,
        paragraphSpacingEm = 0.45f,
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
        letterSpacingEm = 0.01f,
        paragraphSpacingEm = 0.55f,
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
        letterSpacingEm = 0.01f,
        paragraphSpacingEm = 0.65f,
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
        letterSpacingEm = 0.01f,
        paragraphSpacingEm = 0.55f,
        horizontalPaddingDp = 26f,
        verticalPaddingDp = 20f,
        firstLineIndentEm = 2f,
        textAlignment = ReaderTextAlignment.JUSTIFY,
        fontWeight = ReaderFontWeight.NORMAL,
    )
    ReaderPreset.CUSTOM -> copy(preset = value)
}

private val Context.readerV2DataStore: DataStore<Preferences> by preferencesDataStore(name = "jingdu_reader_v2")

/**
 * Reader V2 settings store. There is deliberately no SharedPreferences migration path: the first
 * store version has not launched, so the old reader-settings schema is discarded instead of
 * becoming permanent compatibility debt.
 */
class ReaderPreferences(context: Context) {
    private val dataStore = context.applicationContext.readerV2DataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun load(): ReaderSettings = runBlocking(Dispatchers.IO) { read(dataStore.data.first()) }

    fun save(value: ReaderSettings) {
        val safe = sanitize(value)
        ChineseDisplayConverter.configure(safe)
        scope.launch { write(safe) }
    }

    fun exportMap(): Map<String, Any> = toMap(load())

    fun importMap(values: Map<String, Any?>): ReaderSettings {
        val fallback = load()
        val imported = sanitize(
            ReaderSettings(
                palette = enumValue(values["palette"], fallback.palette),
                typeface = enumValue(values["typeface"], fallback.typeface),
                customFontId = string(values["customFontId"], fallback.customFontId, 128),
                fontSizeSp = number(values["fontSizeSp"], fallback.fontSizeSp),
                lineHeightMultiplier = number(values["lineHeightMultiplier"], fallback.lineHeightMultiplier),
                letterSpacingEm = number(values["letterSpacingEm"], fallback.letterSpacingEm),
                paragraphSpacingEm = number(values["paragraphSpacingEm"], fallback.paragraphSpacingEm),
                horizontalPaddingDp = number(values["horizontalPaddingDp"], fallback.horizontalPaddingDp),
                verticalPaddingDp = number(values["verticalPaddingDp"], fallback.verticalPaddingDp),
                firstLineIndentEm = number(values["firstLineIndentEm"], fallback.firstLineIndentEm),
                textAlignment = enumValue(values["textAlignment"], fallback.textAlignment),
                fontWeight = enumValue(values["fontWeight"], fallback.fontWeight),
                compressBlankLines = boolean(values["compressBlankLines"], fallback.compressBlankLines),
                emphasizeHeadings = boolean(values["emphasizeHeadings"], fallback.emphasizeHeadings),
                preset = enumValue(values["preset"], fallback.preset),
                readingMode = enumValue(values["readingMode"], fallback.readingMode),
                pageAnimation = enumValue(values["pageAnimation"], fallback.pageAnimation),
                tapPagingEnabled = boolean(values["tapPagingEnabled"], fallback.tapPagingEnabled),
                swipePagingEnabled = boolean(values["swipePagingEnabled"], fallback.swipePagingEnabled),
                reversePagingGestures = boolean(values["reversePagingGestures"], fallback.reversePagingGestures),
                tapZonePreset = enumValue(values["tapZonePreset"], fallback.tapZonePreset),
                tapZoneEdgeFraction = number(values["tapZoneEdgeFraction"], fallback.tapZoneEdgeFraction),
                brightnessGestureEnabled = boolean(values["brightnessGestureEnabled"], fallback.brightnessGestureEnabled),
                pinchFontEnabled = boolean(values["pinchFontEnabled"], fallback.pinchFontEnabled),
                doubleTapBookmarkEnabled = boolean(values["doubleTapBookmarkEnabled"], fallback.doubleTapBookmarkEnabled),
                controlsAutoHideMs = long(values["controlsAutoHideMs"], fallback.controlsAutoHideMs),
                useSystemBrightness = boolean(values["useSystemBrightness"], fallback.useSystemBrightness),
                readerBrightness = number(values["readerBrightness"], fallback.readerBrightness),
                orientation = enumValue(values["orientation"], fallback.orientation),
                volumeKeyMode = enumValue(values["volumeKeyMode"], fallback.volumeKeyMode),
                reverseVolumeKeys = boolean(values["reverseVolumeKeys"], fallback.reverseVolumeKeys),
                wideColumns = enumValue(values["wideColumns"], fallback.wideColumns),
                showReadingStatus = boolean(values["showReadingStatus"], fallback.showReadingStatus),
                showClock = boolean(values["showClock"], fallback.showClock),
                showBattery = boolean(values["showBattery"], fallback.showBattery),
                focusRulerLines = int(values["focusRulerLines"], fallback.focusRulerLines),
                hapticEnabled = boolean(values["hapticEnabled"], fallback.hapticEnabled),
                gestureCoachDismissed = boolean(values["gestureCoachDismissed"], fallback.gestureCoachDismissed),
                autoScrollEnabled = false,
                autoScrollSpeedDpPerSecond = number(values["autoScrollSpeedDpPerSecond"], fallback.autoScrollSpeedDpPerSecond),
                autoPageMode = enumValue(values["autoPageMode"], fallback.autoPageMode),
                autoPagePaceMultiplier = number(values["autoPagePaceMultiplier"], fallback.autoPagePaceMultiplier),
                autoPageDelayMs = long(values["autoPageDelayMs"], fallback.autoPageDelayMs),
                chineseMode = enumValue(values["chineseMode"], fallback.chineseMode),
                chineseOverrides = string(values["chineseOverrides"], fallback.chineseOverrides, MAX_OVERRIDE_TEXT_CHARS),
                ttsRate = number(values["ttsRate"], fallback.ttsRate),
                ttsPitch = number(values["ttsPitch"], fallback.ttsPitch),
                ttsVoiceName = string(values["ttsVoiceName"], fallback.ttsVoiceName, 256),
            ),
        )
        save(imported)
        return imported
    }

    private suspend fun write(value: ReaderSettings) {
        dataStore.edit { p ->
            p[Keys.palette] = value.palette.name
            p[Keys.typeface] = value.typeface.name
            p[Keys.customFontId] = value.customFontId
            p[Keys.fontSize] = value.fontSizeSp
            p[Keys.lineHeight] = value.lineHeightMultiplier
            p[Keys.letterSpacing] = value.letterSpacingEm
            p[Keys.paragraphSpacing] = value.paragraphSpacingEm
            p[Keys.horizontalPadding] = value.horizontalPaddingDp
            p[Keys.verticalPadding] = value.verticalPaddingDp
            p[Keys.firstLineIndent] = value.firstLineIndentEm
            p[Keys.textAlignment] = value.textAlignment.name
            p[Keys.fontWeight] = value.fontWeight.name
            p[Keys.compressBlankLines] = value.compressBlankLines
            p[Keys.emphasizeHeadings] = value.emphasizeHeadings
            p[Keys.preset] = value.preset.name
            p[Keys.readingMode] = value.readingMode.name
            p[Keys.pageAnimation] = value.pageAnimation.name
            p[Keys.tapPaging] = value.tapPagingEnabled
            p[Keys.swipePaging] = value.swipePagingEnabled
            p[Keys.reverseGestures] = value.reversePagingGestures
            p[Keys.tapZonePreset] = value.tapZonePreset.name
            p[Keys.tapZoneFraction] = value.tapZoneEdgeFraction
            p[Keys.brightnessGesture] = value.brightnessGestureEnabled
            p[Keys.pinchFont] = value.pinchFontEnabled
            p[Keys.doubleTapBookmark] = value.doubleTapBookmarkEnabled
            p[Keys.controlsAutoHide] = value.controlsAutoHideMs
            p[Keys.systemBrightness] = value.useSystemBrightness
            p[Keys.readerBrightness] = value.readerBrightness
            p[Keys.orientation] = value.orientation.name
            p[Keys.volumeKeyMode] = value.volumeKeyMode.name
            p[Keys.reverseVolume] = value.reverseVolumeKeys
            p[Keys.wideColumns] = value.wideColumns.name
            p[Keys.showReadingStatus] = value.showReadingStatus
            p[Keys.showClock] = value.showClock
            p[Keys.showBattery] = value.showBattery
            p[Keys.focusRuler] = value.focusRulerLines
            p[Keys.haptic] = value.hapticEnabled
            p[Keys.gestureCoachDismissed] = value.gestureCoachDismissed
            p[Keys.autoScrollSpeed] = value.autoScrollSpeedDpPerSecond
            p[Keys.autoPageMode] = value.autoPageMode.name
            p[Keys.autoPagePaceMultiplier] = value.autoPagePaceMultiplier
            p[Keys.autoPageDelay] = value.autoPageDelayMs
            p[Keys.chineseMode] = value.chineseMode.name
            p[Keys.chineseOverrides] = value.chineseOverrides
            p[Keys.ttsRate] = value.ttsRate
            p[Keys.ttsPitch] = value.ttsPitch
            p[Keys.ttsVoice] = value.ttsVoiceName
        }
    }

    private fun read(p: Preferences): ReaderSettings {
        val value = sanitize(
            ReaderSettings(
                palette = enumOrDefault(p[Keys.palette], ReaderPalette.PAPER),
                typeface = enumOrDefault(p[Keys.typeface], ReaderTypeface.SYSTEM),
                customFontId = p[Keys.customFontId].orEmpty(),
                fontSizeSp = p[Keys.fontSize] ?: 20f,
                lineHeightMultiplier = p[Keys.lineHeight] ?: 1.55f,
                letterSpacingEm = p[Keys.letterSpacing] ?: 0f,
                paragraphSpacingEm = p[Keys.paragraphSpacing] ?: 0.45f,
                horizontalPaddingDp = p[Keys.horizontalPadding] ?: 24f,
                verticalPaddingDp = p[Keys.verticalPadding] ?: 18f,
                firstLineIndentEm = p[Keys.firstLineIndent] ?: 0f,
                textAlignment = enumOrDefault(p[Keys.textAlignment], ReaderTextAlignment.JUSTIFY),
                fontWeight = enumOrDefault(p[Keys.fontWeight], ReaderFontWeight.NORMAL),
                compressBlankLines = p[Keys.compressBlankLines] ?: true,
                emphasizeHeadings = p[Keys.emphasizeHeadings] ?: true,
                preset = enumOrDefault(p[Keys.preset], ReaderPreset.STANDARD),
                readingMode = enumOrDefault(p[Keys.readingMode], ReaderMode.PAGED),
                pageAnimation = enumOrDefault(p[Keys.pageAnimation], ReaderPageAnimation.SLIDE),
                tapPagingEnabled = p[Keys.tapPaging] ?: true,
                swipePagingEnabled = p[Keys.swipePaging] ?: true,
                reversePagingGestures = p[Keys.reverseGestures] ?: false,
                tapZonePreset = enumOrDefault(p[Keys.tapZonePreset], ReaderTapZonePreset.BALANCED),
                tapZoneEdgeFraction = p[Keys.tapZoneFraction] ?: 0.25f,
                brightnessGestureEnabled = p[Keys.brightnessGesture] ?: true,
                pinchFontEnabled = p[Keys.pinchFont] ?: true,
                doubleTapBookmarkEnabled = p[Keys.doubleTapBookmark] ?: false,
                controlsAutoHideMs = p[Keys.controlsAutoHide] ?: 3500L,
                useSystemBrightness = p[Keys.systemBrightness] ?: true,
                readerBrightness = p[Keys.readerBrightness] ?: 0.45f,
                orientation = enumOrDefault(p[Keys.orientation], ReaderOrientation.SYSTEM),
                volumeKeyMode = enumOrDefault(p[Keys.volumeKeyMode], ReaderVolumeKeyMode.PAGE_WHEN_NOT_TTS),
                reverseVolumeKeys = p[Keys.reverseVolume] ?: false,
                wideColumns = enumOrDefault(p[Keys.wideColumns], ReaderWideColumns.AUTO),
                showReadingStatus = p[Keys.showReadingStatus] ?: true,
                showClock = p[Keys.showClock] ?: false,
                showBattery = p[Keys.showBattery] ?: false,
                focusRulerLines = p[Keys.focusRuler] ?: 0,
                hapticEnabled = p[Keys.haptic] ?: true,
                gestureCoachDismissed = p[Keys.gestureCoachDismissed] ?: false,
                autoScrollEnabled = false,
                autoScrollSpeedDpPerSecond = p[Keys.autoScrollSpeed] ?: 55f,
                autoPageMode = enumOrDefault(p[Keys.autoPageMode], ReaderAutoPageMode.ADAPTIVE),
                autoPagePaceMultiplier = p[Keys.autoPagePaceMultiplier] ?: 1f,
                autoPageDelayMs = p[Keys.autoPageDelay] ?: 6500L,
                chineseMode = enumOrDefault(p[Keys.chineseMode], ChineseDisplayMode.ORIGINAL),
                chineseOverrides = p[Keys.chineseOverrides].orEmpty(),
                ttsRate = p[Keys.ttsRate] ?: 1f,
                ttsPitch = p[Keys.ttsPitch] ?: 1f,
                ttsVoiceName = p[Keys.ttsVoice].orEmpty(),
            ),
        )
        ChineseDisplayConverter.configure(value)
        return value
    }

    private fun sanitize(v: ReaderSettings): ReaderSettings = v.copy(
        customFontId = v.customFontId.take(128),
        fontSizeSp = v.fontSizeSp.coerceIn(14f, 40f),
        lineHeightMultiplier = v.lineHeightMultiplier.coerceIn(1.15f, 2.2f),
        letterSpacingEm = v.letterSpacingEm.coerceIn(-0.02f, 0.12f),
        paragraphSpacingEm = v.paragraphSpacingEm.coerceIn(0f, 1.5f),
        horizontalPaddingDp = v.horizontalPaddingDp.coerceIn(8f, 56f),
        verticalPaddingDp = v.verticalPaddingDp.coerceIn(4f, 56f),
        firstLineIndentEm = v.firstLineIndentEm.coerceIn(0f, 3f),
        tapZoneEdgeFraction = v.tapZoneEdgeFraction.coerceIn(0.15f, 0.40f),
        controlsAutoHideMs = v.controlsAutoHideMs.coerceIn(1500L, 15000L),
        readerBrightness = v.readerBrightness.coerceIn(0.03f, 1f),
        focusRulerLines = if (v.focusRulerLines in listOf(0, 3, 5)) v.focusRulerLines else 0,
        autoScrollEnabled = false,
        autoScrollSpeedDpPerSecond = v.autoScrollSpeedDpPerSecond.coerceIn(12f, 320f),
        autoPagePaceMultiplier = v.autoPagePaceMultiplier.coerceIn(0.5f, 2.0f),
        autoPageDelayMs = v.autoPageDelayMs.coerceIn(2000L, 120000L),
        chineseOverrides = v.chineseOverrides.take(MAX_OVERRIDE_TEXT_CHARS),
        ttsRate = v.ttsRate.coerceIn(0.5f, 2.0f),
        ttsPitch = v.ttsPitch.coerceIn(0.6f, 1.6f),
        ttsVoiceName = v.ttsVoiceName.take(256),
    )

    private fun toMap(v: ReaderSettings): Map<String, Any> = linkedMapOf(
        "palette" to v.palette.name,
        "typeface" to v.typeface.name,
        "customFontId" to v.customFontId,
        "fontSizeSp" to v.fontSizeSp,
        "lineHeightMultiplier" to v.lineHeightMultiplier,
        "letterSpacingEm" to v.letterSpacingEm,
        "paragraphSpacingEm" to v.paragraphSpacingEm,
        "horizontalPaddingDp" to v.horizontalPaddingDp,
        "verticalPaddingDp" to v.verticalPaddingDp,
        "firstLineIndentEm" to v.firstLineIndentEm,
        "textAlignment" to v.textAlignment.name,
        "fontWeight" to v.fontWeight.name,
        "compressBlankLines" to v.compressBlankLines,
        "emphasizeHeadings" to v.emphasizeHeadings,
        "preset" to v.preset.name,
        "readingMode" to v.readingMode.name,
        "pageAnimation" to v.pageAnimation.name,
        "tapPagingEnabled" to v.tapPagingEnabled,
        "swipePagingEnabled" to v.swipePagingEnabled,
        "reversePagingGestures" to v.reversePagingGestures,
        "tapZonePreset" to v.tapZonePreset.name,
        "tapZoneEdgeFraction" to v.tapZoneEdgeFraction,
        "brightnessGestureEnabled" to v.brightnessGestureEnabled,
        "pinchFontEnabled" to v.pinchFontEnabled,
        "doubleTapBookmarkEnabled" to v.doubleTapBookmarkEnabled,
        "controlsAutoHideMs" to v.controlsAutoHideMs,
        "useSystemBrightness" to v.useSystemBrightness,
        "readerBrightness" to v.readerBrightness,
        "orientation" to v.orientation.name,
        "volumeKeyMode" to v.volumeKeyMode.name,
        "reverseVolumeKeys" to v.reverseVolumeKeys,
        "wideColumns" to v.wideColumns.name,
        "showReadingStatus" to v.showReadingStatus,
        "showClock" to v.showClock,
        "showBattery" to v.showBattery,
        "focusRulerLines" to v.focusRulerLines,
        "hapticEnabled" to v.hapticEnabled,
        "gestureCoachDismissed" to v.gestureCoachDismissed,
        "autoScrollSpeedDpPerSecond" to v.autoScrollSpeedDpPerSecond,
        "autoPageMode" to v.autoPageMode.name,
        "autoPagePaceMultiplier" to v.autoPagePaceMultiplier,
        "autoPageDelayMs" to v.autoPageDelayMs,
        "chineseMode" to v.chineseMode.name,
        "chineseOverrides" to v.chineseOverrides,
        "ttsRate" to v.ttsRate,
        "ttsPitch" to v.ttsPitch,
        "ttsVoiceName" to v.ttsVoiceName,
    )

    private fun number(value: Any?, fallback: Float) = (value as? Number)?.toFloat() ?: fallback
    private fun long(value: Any?, fallback: Long) = (value as? Number)?.toLong() ?: fallback
    private fun int(value: Any?, fallback: Int) = (value as? Number)?.toInt() ?: fallback
    private fun boolean(value: Any?, fallback: Boolean) = value as? Boolean ?: fallback
    private fun string(value: Any?, fallback: String, max: Int) = (value as? String ?: fallback).take(max)

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T =
        raw?.let { candidate -> enumValues<T>().firstOrNull { it.name == candidate } } ?: fallback

    private inline fun <reified T : Enum<T>> enumValue(value: Any?, fallback: T): T =
        runCatching { enumValueOf<T>(value as? String ?: fallback.name) }.getOrDefault(fallback)

    private object Keys {
        val palette = stringPreferencesKey("palette")
        val typeface = stringPreferencesKey("typeface")
        val customFontId = stringPreferencesKey("customFontId")
        val fontSize = floatPreferencesKey("fontSizeSp")
        val lineHeight = floatPreferencesKey("lineHeight")
        val letterSpacing = floatPreferencesKey("letterSpacingEm")
        val paragraphSpacing = floatPreferencesKey("paragraphSpacingEm")
        val horizontalPadding = floatPreferencesKey("horizontalPadding")
        val verticalPadding = floatPreferencesKey("verticalPadding")
        val firstLineIndent = floatPreferencesKey("firstLineIndentEm")
        val textAlignment = stringPreferencesKey("textAlignment")
        val fontWeight = stringPreferencesKey("fontWeight")
        val compressBlankLines = booleanPreferencesKey("compressBlankLines")
        val emphasizeHeadings = booleanPreferencesKey("emphasizeHeadings")
        val preset = stringPreferencesKey("preset")
        val readingMode = stringPreferencesKey("readingMode")
        val pageAnimation = stringPreferencesKey("pageAnimation")
        val tapPaging = booleanPreferencesKey("tapPagingEnabled")
        val swipePaging = booleanPreferencesKey("swipePagingEnabled")
        val reverseGestures = booleanPreferencesKey("reversePagingGestures")
        val tapZonePreset = stringPreferencesKey("tapZonePreset")
        val tapZoneFraction = floatPreferencesKey("tapZoneEdgeFraction")
        val brightnessGesture = booleanPreferencesKey("brightnessGestureEnabled")
        val pinchFont = booleanPreferencesKey("pinchFontEnabled")
        val doubleTapBookmark = booleanPreferencesKey("doubleTapBookmarkEnabled")
        val controlsAutoHide = longPreferencesKey("controlsAutoHideMs")
        val systemBrightness = booleanPreferencesKey("useSystemBrightness")
        val readerBrightness = floatPreferencesKey("readerBrightness")
        val orientation = stringPreferencesKey("orientation")
        val volumeKeyMode = stringPreferencesKey("volumeKeyMode")
        val reverseVolume = booleanPreferencesKey("reverseVolumeKeys")
        val wideColumns = stringPreferencesKey("wideColumns")
        val showReadingStatus = booleanPreferencesKey("showReadingStatus")
        val showClock = booleanPreferencesKey("showClock")
        val showBattery = booleanPreferencesKey("showBattery")
        val focusRuler = intPreferencesKey("focusRulerLines")
        val haptic = booleanPreferencesKey("hapticEnabled")
        val gestureCoachDismissed = booleanPreferencesKey("gestureCoachDismissed")
        val autoScrollSpeed = floatPreferencesKey("autoScrollSpeed")
        val autoPageMode = stringPreferencesKey("autoPageMode")
        val autoPagePaceMultiplier = floatPreferencesKey("autoPagePaceMultiplier")
        val autoPageDelay = longPreferencesKey("autoPageDelayMs")
        val chineseMode = stringPreferencesKey("chineseMode")
        val chineseOverrides = stringPreferencesKey("chineseOverrides")
        val ttsRate = floatPreferencesKey("ttsRate")
        val ttsPitch = floatPreferencesKey("ttsPitch")
        val ttsVoice = stringPreferencesKey("ttsVoiceName")
    }

    private companion object { const val MAX_OVERRIDE_TEXT_CHARS = 16 * 1024 }
}
