package com.junchen.jingdu

import java.text.BreakIterator
import java.util.Locale

/** Bounded source-offset navigation for TTS. Never scans the whole book. */
internal object TtsSemanticNavigator {
    fun previousSentence(reader: ReaderController, position: Long, locale: Locale = Locale.getDefault()): Long =
        previousBoundary(reader, position, locale, sentence = true)

    fun nextSentence(reader: ReaderController, position: Long, locale: Locale = Locale.getDefault()): Long =
        nextBoundary(reader, position, locale, sentence = true)

    fun previousParagraph(reader: ReaderController, position: Long): Long =
        previousBoundary(reader, position, Locale.getDefault(), sentence = false)

    fun nextParagraph(reader: ReaderController, position: Long): Long =
        nextBoundary(reader, position, Locale.getDefault(), sentence = false)

    private fun previousBoundary(reader: ReaderController, position: Long, locale: Locale, sentence: Boolean): Long {
        if (position <= 0 || reader.length() <= 0) return 0
        val start = (position - WINDOW_CP).coerceAtLeast(0)
        val text = reader.readAt(start, position - start)
        if (text.isEmpty()) return start
        if (!sentence) {
            val normalized = text.replace("\r\n", "\n")
            val cursor = normalized.trimEnd().lastIndexOf("\n\n")
            if (cursor < 0) return start
            var next = cursor + 2
            while (next < normalized.length && normalized[next].isWhitespace()) next++
            return (start + normalized.codePointCount(0, next)).coerceIn(0, position)
        }
        val breaker = BreakIterator.getSentenceInstance(locale).apply { setText(text) }
        val endUtf = text.offsetByCodePoints(0, text.codePointCount(0, text.length))
        var boundary = breaker.preceding(endUtf)
        if (boundary == BreakIterator.DONE) return start
        // If we're exactly at a sentence start, move once more so Previous means previous sentence.
        if (boundary >= text.length - 2) boundary = breaker.preceding(boundary)
        if (boundary == BreakIterator.DONE) return start
        return (start + text.codePointCount(0, boundary)).coerceIn(0, position)
    }

    private fun nextBoundary(reader: ReaderController, position: Long, locale: Locale, sentence: Boolean): Long {
        val length = reader.length()
        if (length <= 0 || position >= length - 1) return (length - 1).coerceAtLeast(0)
        val text = reader.readAt(position, minOf(WINDOW_CP, length - position))
        if (text.isEmpty()) return position
        if (!sentence) {
            val normalized = text.replace("\r\n", "\n")
            val cursor = normalized.indexOf("\n\n", startIndex = 1)
            if (cursor < 0) return (position + text.codePointCount(0, text.length)).coerceAtMost(length - 1)
            var next = cursor + 2
            while (next < normalized.length && normalized[next].isWhitespace()) next++
            return (position + normalized.codePointCount(0, next)).coerceAtMost(length - 1)
        }
        val breaker = BreakIterator.getSentenceInstance(locale).apply { setText(text) }
        var boundary = breaker.following(0)
        if (boundary == BreakIterator.DONE) return (position + text.codePointCount(0, text.length)).coerceAtMost(length - 1)
        while (boundary < text.length && text.substring(0, boundary).isBlank()) {
            boundary = breaker.following(boundary)
            if (boundary == BreakIterator.DONE) return position
        }
        return (position + text.codePointCount(0, boundary)).coerceAtMost(length - 1)
    }

    private const val WINDOW_CP = 4096L
}
