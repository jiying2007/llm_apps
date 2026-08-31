package com.junchen.jingdu

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ReaderSkimController(context: Context, private val bookId: String) : AutoCloseable {
    private val engine = ReaderViewportEngine(context.applicationContext, bookId)

    suspend fun preview(
        fraction: Float,
        originOffset: Long,
        settings: ReaderSettings,
        chapters: List<ChapterModel>,
        charsPerMinute: Double,
    ): ReaderSkimPreview = withContext(Dispatchers.IO) {
        val initial = engine.readAround(originOffset, settings)
        val length = initial.documentLength
        if (length <= 0) return@withContext ReaderSkimPreview(0f, 0, null, "", 0, 0, null, null, originOffset)
        val offset = (fraction.coerceIn(0f, 1f) * (length - 1).toFloat()).toLong().coerceIn(0, length - 1)
        val window = engine.readAround(offset, settings)
        val localSource = (offset - window.start).coerceIn(0, window.map.sourceCodePoints)
        val displayCp = window.map.displayForSource(localSource)
        val utf = utf16Index(window.displayText, displayCp)
        val preview = previewAround(window.displayText, utf)
        val chapterIndex = chapters.indexOfLast { it.offset <= offset }
        val chapter = chapters.getOrNull(chapterIndex)
        val chapterEnd = chapters.getOrNull(chapterIndex + 1)?.offset ?: length
        val chapterSpan = (chapterEnd - (chapter?.offset ?: 0L)).coerceAtLeast(1)
        val chapterProgress = (((offset - (chapter?.offset ?: 0L)).coerceAtLeast(0).toDouble() / chapterSpan.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        val bookProgress = ((offset.toDouble() / length.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        ReaderSkimPreview(
            fraction = fraction.coerceIn(0f, 1f),
            offset = offset,
            chapter = chapter?.title?.let { ReaderTextPresentation.chapterTitle(it, settings) },
            preview = preview,
            chapterProgressPercent = chapterProgress,
            bookProgressPercent = bookProgress,
            chapterRemainingMinutes = remaining(offset, chapterEnd, charsPerMinute),
            bookRemainingMinutes = remaining(offset, length, charsPerMinute),
            originOffset = originOffset,
        )
    }

    override fun close() = engine.close()

    private fun remaining(position: Long, end: Long, cpm: Double): Int? {
        if (end <= position || cpm <= 0) return null
        return kotlin.math.ceil((end - position).toDouble() / cpm.coerceAtLeast(1.0)).toInt().coerceAtLeast(1)
    }

    private fun previewAround(text: String, utf: Int): String {
        if (text.isBlank()) return ""
        val clean = text.replace(ReaderTypographySpec.PARAGRAPH_SPACER.toString(), "")
        val safe = utf.coerceIn(0, clean.length)
        val start = (safe - 90).coerceAtLeast(0)
        val end = (safe + 180).coerceAtMost(clean.length)
        return clean.substring(start, end).replace(Regex("\\s+"), " ").trim()
    }

    private fun utf16Index(text: String, codePoints: Long): Int {
        val total = text.codePointCount(0, text.length)
        return text.offsetByCodePoints(0, codePoints.coerceIn(0, total.toLong()).toInt())
    }
}
