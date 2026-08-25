package com.junchen.jingdu

import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import java.io.Closeable
import java.util.LinkedHashMap
import kotlin.math.roundToInt

internal data class SourceDisplayMap(private val projection: TextProjection) {
    val sourceCodePoints: Long get() = projection.sourceCodePoints
    val displayCodePoints: Long get() = projection.displayCodePoints
    fun sourceForDisplay(displayed: Long): Long = projection.sourceForDisplay(displayed)
    fun displayForSource(source: Long): Long = projection.displayForSource(source)

    companion object {
        fun between(source: String, display: String): SourceDisplayMap = SourceDisplayMap(TextProjection.between(source, display))
        fun compose(first: TextProjection, second: TextProjection): SourceDisplayMap = SourceDisplayMap(first.compose(second))
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
 * Bounded read/presentation pipeline shared by paged and continuous modes. Presentation edits are
 * converted into an edit-aware source/display projection so unchanged regions retain exact Core
 * offsets. Conversion and mapping are cached outside Compose.
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
        if (length <= 0) return ReaderDisplayWindow(0, "", "", 0, SourceDisplayMap.between("", ""))
        val bounded = position.coerceIn(0, length - 1)
        val aligned = ((bounded - BACK_BUFFER_CHARS).coerceAtLeast(0) / ALIGN_CHARS) * ALIGN_CHARS
        val key = WindowKey(aligned, presentationKey(settings))
        cache[key]?.let { return it }
        val source = reader.readAt(aligned, ReaderController.WINDOW_CHARS)
        val presented = presentationNormalize(source, settings)
        val sourceToPresented = TextProjection.between(source, presented)
        val display = ChineseDisplayConverter.convert(presented, settings.chineseMode, settings.chineseOverrides)
        val presentedToDisplay = TextProjection.between(presented, display)
        val result = ReaderDisplayWindow(
            start = aligned,
            sourceText = source,
            displayText = display,
            documentLength = length,
            map = SourceDisplayMap.compose(sourceToPresented, presentedToDisplay),
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

    @Synchronized fun clear() = cache.clear()

    @Synchronized
    override fun close() {
        cache.clear()
        reader.close()
    }

    private fun presentationNormalize(source: String, settings: ReaderSettings): String {
        var value = source
        if (settings.compressBlankLines) value = value.replace(Regex("\\n[ \\t]*\\n(?:[ \\t]*\\n)+"), "\n\n")
        if (settings.paragraphSpacingEm > 0f) {
            value = value.replace("\n\n", "\n${ReaderTypographySpec.PARAGRAPH_SPACER}\n")
        }
        return value
    }

    private fun presentationKey(settings: ReaderSettings): Int = listOf(
        settings.chineseMode.name,
        settings.chineseOverrides.hashCode(),
        settings.compressBlankLines,
        settings.paragraphSpacingEm,
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
    val typographyFingerprint: Int,
    val columns: Int,
)

data class PageLayoutSnapshot(
    val displayedEndUtf16: Int,
    val displayedCodePoints: Long,
    val sourceCodePoints: Long,
    val firstColumnEndUtf16: Int,
)

/** Small LRU used to keep exact page measurement out of repeated Compose layout churn. */
internal object ReaderPageLayoutCache {
    private val cache = object : LinkedHashMap<PageLayoutKey, PageLayoutSnapshot>(20, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PageLayoutKey, PageLayoutSnapshot>?): Boolean = size > 16
    }

    @Synchronized fun get(key: PageLayoutKey): PageLayoutSnapshot? = cache[key]
    @Synchronized fun put(key: PageLayoutKey, value: PageLayoutSnapshot) { cache[key] = value }
    @Synchronized fun clear() = cache.clear()

    fun measure(
        sourceText: String,
        displayText: String,
        widthPx: Int,
        heightPx: Int,
        columns: Int,
        settings: ReaderSettings,
        density: Density,
        typeface: Typeface? = null,
        map: SourceDisplayMap? = null,
    ): PageLayoutSnapshot {
        val safeColumns = columns.coerceIn(1, 2)
        val columnWidth = ((widthPx - if (safeColumns == 2) (28f * density.density).roundToInt() else 0) / safeColumns).coerceAtLeast(1)
        val spec = ReaderTypographySpec.from(settings)
        val key = PageLayoutKey(
            textHash = 31 * sourceText.hashCode() + displayText.hashCode(),
            widthPx = widthPx,
            heightPx = heightPx,
            typographyFingerprint = spec.fingerprint,
            columns = safeColumns,
        )
        get(key)?.let { return it }

        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG or TextPaint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = with(density) { spec.fontSizeSp.sp.toPx() }
            letterSpacing = spec.letterSpacingEm
            this.typeface = typeface ?: Typeface.create(Typeface.SANS_SERIF, when (spec.weight) {
                ReaderFontWeight.NORMAL -> Typeface.NORMAL
                ReaderFontWeight.MEDIUM, ReaderFontWeight.SEMIBOLD -> Typeface.BOLD
            })
        }
        val layoutText = spec.androidLayoutText(displayText, density)
        fun endFor(text: CharSequence): Int {
            if (text.isEmpty()) return 0
            val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, columnWidth)
                .setIncludePad(false)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setMaxLines(Int.MAX_VALUE)
                .setLineSpacing(0f, spec.lineHeightMultiplier.coerceAtLeast(1f))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            if (spec.alignment == ReaderTextAlignment.JUSTIFY) builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
            val layout = builder.build()
            if (layout.lineCount <= 0) return 0
            val line = layout.getLineForVertical((heightPx - 1).coerceAtLeast(0))
            return layout.getLineEnd(line.coerceIn(0, layout.lineCount - 1)).coerceIn(0, text.length)
        }

        val firstEnd = endFor(layoutText)
        val secondEnd = if (safeColumns == 2 && firstEnd < layoutText.length) {
            firstEnd + endFor(layoutText.subSequence(firstEnd, layoutText.length))
        } else firstEnd
        val displayedEnd = secondEnd.coerceIn(0, displayText.length)
        val displayedPoints = displayText.codePointCount(0, displayedEnd).toLong()
        val projection = map ?: SourceDisplayMap.between(sourceText, displayText)
        val sourcePoints = projection.sourceForDisplay(displayedPoints)
        return PageLayoutSnapshot(displayedEnd, displayedPoints, sourcePoints, firstEnd).also { put(key, it) }
    }
}
