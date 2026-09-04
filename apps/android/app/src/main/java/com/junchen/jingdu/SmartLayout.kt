package com.junchen.jingdu

/**
 * Precision-first, display-only paragraph repair for legacy TXT hard wraps.
 *
 * The source text remains authoritative. This helper only removes a newline when the bounded window
 * has strong evidence of fixed-width hard wrapping; ordinary paragraph-per-line files, headings,
 * indentation and dialogue boundaries stay untouched. ReaderPresentationPipeline builds the exact
 * monotonic source projection after the transformation.
 */
internal object SmartLayout {
    data class Result(
        val text: String,
        val hardWrapDetected: Boolean,
        val joinedBreaks: Int,
    )

    fun present(source: String): Result {
        if (source.isBlank() || ('\n' !in source && '\r' !in source)) return Result(source, false, 0)
        // Normal well-formed TXT is the hot path. Inspect only a bounded prefix of lines first and
        // avoid allocating/splitting the whole Reader window unless hard-wrap evidence is strong.
        val sample = source.lineSequence().take(MAX_ANALYSIS_LINES).toList()
        if (!looksHardWrapped(sample)) return Result(source, false, 0)

        val normalized = source.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')
        val output = StringBuilder(normalized.length)
        var joined = 0
        for (index in lines.indices) {
            val current = lines[index]
            output.append(current)
            if (index == lines.lastIndex) continue
            val next = lines[index + 1]
            if (canJoin(current, next)) {
                if (needsLatinSpace(current, next)) output.append(' ')
                joined++
            } else {
                output.append('\n')
            }
        }
        return Result(output.toString(), joined > 0, joined)
    }

    private fun looksHardWrapped(lines: List<String>): Boolean {
        val contentLengths = lines.asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !ReaderHeadingClassifier.isHeading(it) }
            .map { it.codePointCount(0, it.length) }
            .filter { it in MIN_LINE_CP..MAX_LINE_CP }
            .toList()
        if (contentLengths.size < MIN_CONTENT_LINES) return false

        var plausible = 0
        var joinable = 0
        for (index in 0 until lines.lastIndex) {
            val a = lines[index].trim()
            val b = lines[index + 1].trim()
            if (a.isEmpty() || b.isEmpty()) continue
            if (a.codePointCount(0, a.length) !in MIN_LINE_CP..MAX_LINE_CP ||
                b.codePointCount(0, b.length) !in MIN_NEXT_CP..MAX_LINE_CP) continue
            if (ReaderHeadingClassifier.isHeading(a) || ReaderHeadingClassifier.isHeading(b)) continue
            plausible++
            if (canJoin(lines[index], lines[index + 1])) joinable++
        }
        if (joinable < MIN_JOINABLE_BREAKS || plausible <= 0) return false

        val sorted = contentLengths.sorted()
        val median = sorted[sorted.size / 2]
        val nearMedian = contentLengths.count { kotlin.math.abs(it - median) <= MAX_WRAP_VARIANCE_CP }
        val consistentWidth = median in MIN_MEDIAN_CP..MAX_MEDIAN_CP && nearMedian * 2 >= contentLengths.size
        return consistentWidth && joinable.toDouble() / plausible.toDouble() >= MIN_JOINABLE_RATIO
    }

    private fun canJoin(rawCurrent: String, rawNext: String): Boolean {
        if (rawCurrent.isBlank() || rawNext.isBlank()) return false
        if (hasParagraphIndent(rawNext)) return false
        val current = rawCurrent.trimEnd()
        val next = rawNext.trimStart()
        if (current.isEmpty() || next.isEmpty()) return false
        if (ReaderHeadingClassifier.isHeading(current.trim()) || ReaderHeadingClassifier.isHeading(next.trim())) return false
        val currentCp = current.codePointCount(0, current.length)
        val nextCp = next.codePointCount(0, next.length)
        if (currentCp !in MIN_LINE_CP..MAX_LINE_CP || nextCp !in MIN_NEXT_CP..MAX_LINE_CP) return false
        if (endsParagraph(current) || startsFreshBlock(next)) return false
        return true
    }

    private fun hasParagraphIndent(value: String): Boolean =
        value.startsWith('\t') || value.startsWith("\u3000") || value.startsWith("  ")

    private fun endsParagraph(value: String): Boolean {
        val last = value.lastOrNull() ?: return true
        return last in TERMINAL_PUNCTUATION
    }

    private fun startsFreshBlock(value: String): Boolean {
        val first = value.firstOrNull() ?: return true
        return first in BLOCK_OPENERS || value.startsWith("——") || value.startsWith("***") || value.startsWith("###")
    }

    private fun needsLatinSpace(current: String, next: String): Boolean {
        val a = current.lastOrNull() ?: return false
        val b = next.firstOrNull() ?: return false
        return a.isLetterOrDigit() && b.isLetterOrDigit() && a.code < 128 && b.code < 128
    }

    private const val MAX_ANALYSIS_LINES = 80
    private const val MIN_CONTENT_LINES = 5
    private const val MIN_JOINABLE_BREAKS = 4
    private const val MIN_LINE_CP = 10
    private const val MIN_NEXT_CP = 6
    private const val MAX_LINE_CP = 120
    private const val MIN_MEDIAN_CP = 18
    private const val MAX_MEDIAN_CP = 90
    private const val MAX_WRAP_VARIANCE_CP = 12
    private const val MIN_JOINABLE_RATIO = 0.55
    private const val TERMINAL_PUNCTUATION = "。！？!?；;…：:。！？!?；;\"'”’」』》）)]】"
    private const val BLOCK_OPENERS = "“‘「『《（(【["
}
