package com.junchen.jingdu

import android.content.Context
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import java.io.Closeable
import java.util.LinkedHashMap
import kotlin.math.roundToInt

data class SourceDisplayMap(
    val sourceCodePoints: Long,
    val displayCodePoints: Long,
) {
    fun sourceForDisplay(displayed: Long): Long {
        if (sourceCodePoints <= 0 || displayCodePoints <= 0) return 0
        return (displayed.coerceIn(0, displayCodePoints).toDouble() / displayCodePoints.toDouble() * sourceCodePoints)
            .toLong().coerceIn(0, sourceCodePoints)
    }

    fun displayForSource(source: Long): Long {
        if (sourceCodePoints <= 0 || displayCodePoints <= 0) return 0
        return (source.coerceIn(0, sourceCodePoints).toDouble() / sourceCodePoints.toDouble() * displayCodePoints)
            .toLong().coerceIn(0, displayCodePoints)
    }
}

data class ReaderDisplayWindow(
    val start: Long,
    val sourceText: String,
    val displayText: String,
    val documentLength: Long,
    val map: SourceDisplayMap,
)

/**
 * Bounded read/presentation pipeline shared by paged and continuous modes. Conversion and
 * source/display mapping are cached outside Compose so scrolling only performs cheap lookups.
 */
internal class ReaderViewportEngine(context: Context, private val bookId: String) : Closeable {
    private val reader = ReaderController()
    private val cache = object : LinkedHashMap<WindowKey, ReaderDisplayWindow>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<WindowKey, ReaderDisplayWindow>?): Boolean = size > MAX_WINDOWS
    }

    init {
        val repository = BookRepository(context.applicationContext)
        val book = repository.list().firstOrNull { it.id == bookId } ?: error("book unavailable")
        reader.open(repository.normalizedFile(book), 0)
    }

    @Synchronized
    fun readAround(position: Long, settings: ReaderSettings): ReaderDisplayWindow {
        val length = reader.length()
        if (length <= 0) return ReaderDisplayWindow(0, "", "", 0, SourceDisplayMap(0, 0))
        val bounded = position.coerceIn(0, length - 1)
        val aligned = ((bounded - BACK_BUFFER_CHARS).coerceAtLeast(0) / ALIGN_CHARS) * ALIGN_CHARS
        val key = WindowKey(aligned, presentationKey(settings))
        cache[key]?.let { return it }
        val source = reader.readAt(aligned, ReaderController.WINDOW_CHARS)
        val presentedSource = presentationNormalize(source, settings)
        val display = ChineseDisplayConverter.convert(presentedSource, settings.chineseMode, settings.chineseOverrides)
        val result = ReaderDisplayWindow(
            start = aligned,
            sourceText = source,
            displayText = display,
            documentLength = length,
            map = SourceDisplayMap(
                sourceCodePoints = source.codePointCount(0, source.length).toLong(),
                displayCodePoints = display.codePointCount(0, display.length).toLong(),
            ),
        )
        cache[key] = result
        return result
    }

    @Synchronized
    fun prefetch(position: Long, settings: ReaderSettings) {
        if (reader.length() <= 0) return
        readAround(position, settings)
        readAround((position + ReaderController.WINDOW_CHARS / 2).coerceAtMost(reader.length() - 1), settings)
        readAround((position - ReaderController.WINDOW_CHARS / 2).coerceAtLeast(0), settings)
    }

    @Synchronized
    fun clear() = cache.clear()

    @Synchronized
    override fun close() {
        cache.clear()
        reader.close()
    }

    private fun presentationNormalize(source: String, settings: ReaderSettings): String {
        if (!settings.compressBlankLines) return source
        return source.replace(Regex("\\n[ \\t]*\\n(?:[ \\t]*\\n)+"), "\n\n")
    }

    private fun presentationKey(settings: ReaderSettings): Int = listOf(
        settings.chineseMode.name,
        settings.chineseOverrides.hashCode(),
        settings.compressBlankLines,
    ).hashCode()

    private data class WindowKey(val start: Long, val presentationKey: Int)

    private companion object {
        const val MAX_WINDOWS = 8
        const val ALIGN_CHARS = 2048L
        const val BACK_BUFFER_CHARS = 1536L
    }
}

data class PageLayoutKey(
    val textHash: Int,
    val widthPx: Int,
    val heightPx: Int,
    val fontSizeMilliSp: Int,
    val lineHeightMilliSp: Int,
    val letterSpacingMilliEm: Int,
    val columns: Int,
)

data class PageLayoutSnapshot(
    val displayedEndUtf16: Int,
    val displayedCodePoints: Long,
    val sourceCodePoints: Long,
    val firstColumnEndUtf16: Int,
)

/** Small LRU used to keep page measurement out of repeated Compose layout churn. */
internal object ReaderPageLayoutCache {
    private val cache = object : LinkedHashMap<PageLayoutKey, PageLayoutSnapshot>(20, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PageLayoutKey, PageLayoutSnapshot>?): Boolean = size > 16
    }

    @Synchronized
    fun get(key: PageLayoutKey): PageLayoutSnapshot? = cache[key]

    @Synchronized
    fun put(key: PageLayoutKey, value: PageLayoutSnapshot) { cache[key] = value }

    @Synchronized
    fun clear() = cache.clear()

    fun measure(
        sourceText: String,
        displayText: String,
        widthPx: Int,
        heightPx: Int,
        columns: Int,
        settings: ReaderSettings,
        density: Density,
    ): PageLayoutSnapshot {
        val safeColumns = columns.coerceIn(1, 2)
        val columnWidth = ((widthPx - if (safeColumns == 2) (28f * density.density).roundToInt() else 0) / safeColumns).coerceAtLeast(1)
        val key = PageLayoutKey(
            textHash = 31 * sourceText.hashCode() + displayText.hashCode(),
            widthPx = widthPx,
            heightPx = heightPx,
            fontSizeMilliSp = (settings.fontSizeSp * 1000).roundToInt(),
            lineHeightMilliSp = (settings.fontSizeSp * settings.lineHeightMultiplier * 1000).roundToInt(),
            letterSpacingMilliEm = (settings.letterSpacingEm * 1000).roundToInt(),
            columns = safeColumns,
        )
        get(key)?.let { return it }

        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = with(density) { settings.fontSizeSp.sp.toPx() }
            letterSpacing = settings.letterSpacingEm
        }
        fun endFor(text: CharSequence): Int {
            if (text.isEmpty()) return 0
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, columnWidth)
                .setIncludePad(false)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setMaxLines(Int.MAX_VALUE)
                .setLineSpacing(0f, settings.lineHeightMultiplier.coerceAtLeast(1f))
                .setAlignment(if (settings.textAlignment == ReaderTextAlignment.JUSTIFY) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_NORMAL)
                .build()
            if (layout.lineCount <= 0) return 0
            val line = layout.getLineForVertical((heightPx - 1).coerceAtLeast(0))
            return layout.getLineEnd(line.coerceIn(0, layout.lineCount - 1)).coerceIn(0, text.length)
        }

        val firstEnd = endFor(displayText)
        val secondEnd = if (safeColumns == 2 && firstEnd < displayText.length) {
            firstEnd + endFor(displayText.substring(firstEnd))
        } else firstEnd
        val displayedEnd = secondEnd.coerceIn(0, displayText.length)
        val displayedPoints = displayText.codePointCount(0, displayedEnd).toLong()
        val sourcePoints = ChineseDisplayConverter.sourceCharsForDisplayed(sourceText, displayText, displayedPoints)
        return PageLayoutSnapshot(displayedEnd, displayedPoints, sourcePoints, firstEnd).also { put(key, it) }
    }
}
