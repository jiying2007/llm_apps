package com.junchen.jingdu

import java.text.BreakIterator
import java.util.Locale

data class ReaderSelectionRange(
    val sourceStart: Long,
    val sourceEnd: Long,
    val excerpt: String,
) {
    init { require(sourceStart >= 0 && sourceEnd >= sourceStart) }
}

/** Pure bounded selection logic. UI handles only provide display offsets. */
internal object ReaderSelectionController {
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
