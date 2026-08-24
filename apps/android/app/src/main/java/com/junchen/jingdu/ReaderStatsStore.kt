package com.junchen.jingdu

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import kotlin.math.ceil

/** Reader-facing local statistics only. Nothing is uploaded and no analytics SDK is involved. */
internal class ReaderStatsStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "reader-v2-stats.json")
    private var sessionBook: String? = null
    private var sessionStartedAt = 0L
    private var sessionStartPosition = 0L
    private var lastPosition = 0L
    private var lastAt = 0L

    fun begin(bookId: String, position: Long) {
        val now = SystemClock.elapsedRealtime()
        if (sessionBook == bookId) return
        finish()
        sessionBook = bookId
        sessionStartedAt = now
        sessionStartPosition = position
        lastPosition = position
        lastAt = now
    }

    fun mark(bookId: String, position: Long) {
        begin(bookId, position)
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastAt
        val chars = position - lastPosition
        if (elapsed in 2_000L..180_000L && chars in 64L..20_000L) {
            val sample = chars.toDouble() * 60_000.0 / elapsed.toDouble()
            updatePace(sample.coerceIn(120.0, 1800.0))
        }
        lastPosition = position
        lastAt = now
    }

    fun finish() {
        val book = sessionBook ?: return
        val elapsed = (SystemClock.elapsedRealtime() - sessionStartedAt).coerceAtLeast(0)
        val root = read()
        val bookKey = "book.$book"
        val previous = root.optLong(bookKey, 0L)
        root.put(bookKey, previous + elapsed)
        root.put("sessions", root.optLong("sessions", 0L) + 1L)
        write(root)
        sessionBook = null
    }

    fun charsPerMinute(): Double = read().optDouble("cpm", DEFAULT_CPM).coerceIn(120.0, 1800.0)

    fun sessionMinutes(): Int = if (sessionBook == null) 0 else
        ceil((SystemClock.elapsedRealtime() - sessionStartedAt).coerceAtLeast(0) / 60_000.0).toInt()

    fun remainingMinutes(position: Long, length: Long): Int? {
        if (length <= 0 || position >= length) return null
        return ceil((length - position).toDouble() / charsPerMinute()).toInt().coerceAtLeast(1)
    }

    private fun updatePace(sample: Double) {
        val root = read()
        val previous = root.optDouble("cpm", DEFAULT_CPM)
        val samples = root.optInt("paceSamples", 0)
        val weight = if (samples < 5) 0.35 else 0.15
        root.put("cpm", previous * (1.0 - weight) + sample * weight)
        root.put("paceSamples", samples + 1)
        write(root)
    }

    private fun read(): JSONObject = runCatching {
        if (file.isFile) JSONObject(file.readText()) else JSONObject().put("schema", 1)
    }.getOrElse { JSONObject().put("schema", 1) }

    private fun write(root: JSONObject) {
        runCatching {
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(root.toString())
            if (!temp.renameTo(file)) { file.delete(); temp.renameTo(file) }
        }
    }

    private companion object { const val DEFAULT_CPM = 500.0 }
}
