package com.junchen.jingdu

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import java.text.BreakIterator
import java.util.Locale
import java.util.WeakHashMap

data class ReaderSelectionRange(
    val sourceStart: Long,
    val sourceEnd: Long,
    val excerpt: String,
) {
    init { require(sourceStart >= 0 && sourceEnd >= sourceStart) }
}

/** Pure bounded selection logic. UI handles only provide display offsets. */
internal object ReaderSelectionController {
    private const val SOURCE_RELATIVE_TAG = "jingdu.source.relative"
    private const val SOURCE_BASE_TAG = "jingdu.source.base"

    private data class CachedSelectionMetadata(
        val text: String,
        val annotated: AnnotatedString,
    )

    private val selectionMetadata = WeakHashMap<SourceDisplayMap, CachedSelectionMetadata>()

    /**
     * Builds the exact display->source annotations before the text reaches a Compose frame.
     * ReaderPresentationPipeline invokes this on its worker dispatcher for both paged and
     * continuous windows. The UI path then only copies already-built relative annotations and
     * adds one source-base tag for the current window.
     */
    fun prewarmSelection(displayText: String, map: SourceDisplayMap) {
        if (displayText.isNotEmpty()) relativeAnnotations(displayText, map)
    }

    fun annotatedForSelection(
        sourceBase: Long,
        displayText: AnnotatedString,
        map: SourceDisplayMap,
    ): AnnotatedString {
        if (displayText.isEmpty()) return displayText
        val relative = relativeAnnotations(displayText.text, map)
        return buildAnnotatedString {
            append(relative)
            displayText.spanStyles.forEach { addStyle(it.item, it.start, it.end) }
            displayText.paragraphStyles.forEach { addStyle(it.item, it.start, it.end) }
            addStringAnnotation(SOURCE_BASE_TAG, sourceBase.toString(), 0, length)
        }
    }

    private fun relativeAnnotations(displayText: String, map: SourceDisplayMap): AnnotatedString {
        synchronized(selectionMetadata) {
            selectionMetadata[map]?.takeIf { it.text == displayText }?.let { return it.annotated }
        }
        val built = buildAnnotatedString {
            append(displayText)
            var utfStart = 0
            var displayCp = 0L
            while (utfStart < displayText.length) {
                val cp = Character.codePointAt(displayText, utfStart)
                val utfEnd = utfStart + Character.charCount(cp)
                val sourceStart = map.sourceForDisplay(displayCp)
                val sourceEnd = map.sourceForDisplay(displayCp + 1).coerceAtLeast(sourceStart + 1)
                addStringAnnotation(SOURCE_RELATIVE_TAG, "$sourceStart:$sourceEnd", utfStart, utfEnd)
                utfStart = utfEnd
                displayCp++
            }
        }
        synchronized(selectionMetadata) {
            selectionMetadata[map] = CachedSelectionMetadata(displayText, built)
        }
        return built
    }

    fun fromSelectedTexts(selectedTexts: List<AnnotatedString>): ReaderSelectionRange? {
        if (selectedTexts.isEmpty()) return null
        var start = Long.MAX_VALUE
        var end = -1L
        val excerpt = StringBuilder()
        selectedTexts.forEach { selected ->
            if (selected.isEmpty()) return@forEach
            if (excerpt.isNotEmpty()) excerpt.append('\n')
            excerpt.append(selected.text.replace(ReaderTypographySpec.PARAGRAPH_SPACER.toString(), ""))
            val base = selected.getStringAnnotations(SOURCE_BASE_TAG, 0, selected.length)
                .firstOrNull()?.item?.toLongOrNull() ?: return@forEach
            selected.getStringAnnotations(SOURCE_RELATIVE_TAG, 0, selected.length).forEach { annotation ->
                val parts = annotation.item.split(':', limit = 2)
                val a = parts.getOrNull(0)?.toLongOrNull() ?: return@forEach
                val b = parts.getOrNull(1)?.toLongOrNull() ?: return@forEach
                start = minOf(start, base + a)
                end = maxOf(end, base + b)
            }
        }
        if (start == Long.MAX_VALUE || end <= start) return null
        return ReaderSelectionRange(start, end, excerpt.toString().trim().take(MAX_EXCERPT))
    }

    fun wordAt(
        sourceBase: Long,
        displayText: String,
        displayUtf16: Int,
        map: SourceDisplayMap,
        locale: Locale,
    ): ReaderSelectionRange? {
        if (displayText.isEmpty()) return null
        val safe = displayUtf16.coerceIn(0, displayText.length - 1)
        val breaker = BreakIterator.getWordInstance(locale).apply { setText(displayText) }
        var start = breaker.preceding((safe + 1).coerceAtMost(displayText.length))
        if (start == BreakIterator.DONE) start = 0
        var end = breaker.following(safe)
        if (end == BreakIterator.DONE) end = displayText.length
        while (start < end && displayText[start].isWhitespace()) start++
        while (end > start && displayText[end - 1].isWhitespace()) end--
        if (end <= start) return null
        return rangeForDisplay(sourceBase, displayText, start, end, map)
    }

    fun rangeForDisplay(
        sourceBase: Long,
        displayText: String,
        displayStartUtf16: Int,
        displayEndUtf16: Int,
        map: SourceDisplayMap,
    ): ReaderSelectionRange? {
        if (displayText.isEmpty()) return null
        val a = minOf(displayStartUtf16, displayEndUtf16).coerceIn(0, displayText.length)
        val b = maxOf(displayStartUtf16, displayEndUtf16).coerceIn(a, displayText.length)
        if (b <= a) return null
        val displayStartCp = displayText.codePointCount(0, a).toLong()
        val displayEndCp = displayText.codePointCount(0, b).toLong()
        val sourceStart = sourceBase + map.sourceForDisplay(displayStartCp)
        val sourceEnd = sourceBase + map.sourceForDisplay(displayEndCp)
        if (sourceEnd <= sourceStart) return null
        return ReaderSelectionRange(sourceStart, sourceEnd, displayText.substring(a, b).replace(ReaderTypographySpec.PARAGRAPH_SPACER.toString(), "").trim().take(MAX_EXCERPT))
    }

    fun updateBoundary(
        current: ReaderSelectionRange,
        moveStart: Boolean,
        sourceBase: Long,
        displayText: String,
        displayUtf16: Int,
        map: SourceDisplayMap,
    ): ReaderSelectionRange {
        val safeUtf = displayUtf16.coerceIn(0, displayText.length)
        val displayCp = displayText.codePointCount(0, safeUtf).toLong()
        val source = sourceBase + map.sourceForDisplay(displayCp)
        val nextStart = if (moveStart) minOf(source, current.sourceEnd - 1) else current.sourceStart
        val nextEnd = if (moveStart) current.sourceEnd else maxOf(source, current.sourceStart + 1)
        val localStart = (nextStart - sourceBase).coerceIn(0, map.sourceCodePoints)
        val localEnd = (nextEnd - sourceBase).coerceIn(localStart, map.sourceCodePoints)
        val displayStart = utf16ForCodePoints(displayText, map.displayForSource(localStart))
        val displayEnd = utf16ForCodePoints(displayText, map.displayForSource(localEnd))
        val excerpt = if (displayEnd > displayStart) displayText.substring(displayStart, displayEnd)
            .replace(ReaderTypographySpec.PARAGRAPH_SPACER.toString(), "").trim().take(MAX_EXCERPT) else current.excerpt
        return ReaderSelectionRange(nextStart.coerceAtLeast(0), nextEnd.coerceAtLeast(nextStart + 1), excerpt)
    }

    /** Two-stage selection keeps a stable anchor while another page/window is opened. */
    fun extendAcrossBoundary(current: ReaderSelectionRange, newBoundary: Long, towardPrevious: Boolean): ReaderSelectionRange =
        if (towardPrevious) current.copy(sourceStart = newBoundary.coerceIn(0, current.sourceStart))
        else current.copy(sourceEnd = newBoundary.coerceAtLeast(current.sourceEnd))

    private fun utf16ForCodePoints(text: String, count: Long): Int {
        if (text.isEmpty() || count <= 0) return 0
        val total = text.codePointCount(0, text.length)
        return text.offsetByCodePoints(0, count.coerceIn(0, total.toLong()).toInt())
    }

    private const val MAX_EXCERPT = 800
}
