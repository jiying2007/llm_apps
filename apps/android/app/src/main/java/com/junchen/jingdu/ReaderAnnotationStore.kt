package com.junchen.jingdu

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

enum class ReaderAnnotationKind { BOOKMARK, HIGHLIGHT, NOTE }
enum class ReaderHighlightStyle { YELLOW, GREEN, BLUE, PINK }

data class ReaderAnnotation(
    val id: String,
    val bookId: String,
    val sourceStart: Long,
    val sourceEnd: Long,
    val kind: ReaderAnnotationKind,
    val style: ReaderHighlightStyle = ReaderHighlightStyle.YELLOW,
    val note: String = "",
    val excerpt: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

/**
 * Local-only source-range annotations. Offsets always refer to normalized source/Core coordinates;
 * presentation conversion, typography and pagination can change without invalidating annotations.
 */
internal class ReaderAnnotationStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "reader-v2-annotations.json")
    private val lock = Any()

    fun list(bookId: String): List<ReaderAnnotation> = synchronized(lock) {
        readAll().filter { it.bookId == bookId }.sortedWith(compareBy({ it.sourceStart }, { it.createdAt }))
    }

    fun bookmarks(bookId: String): List<ReaderAnnotation> = list(bookId).filter { it.kind == ReaderAnnotationKind.BOOKMARK }

    fun addBookmark(bookId: String, offset: Long): ReaderAnnotation = synchronized(lock) {
        val all = readAll().toMutableList()
        val existing = all.firstOrNull {
            it.bookId == bookId && it.kind == ReaderAnnotationKind.BOOKMARK && kotlin.math.abs(it.sourceStart - offset) <= 1
        }
        if (existing != null) return@synchronized existing
        val value = ReaderAnnotation(
            id = UUID.randomUUID().toString(), bookId = bookId,
            sourceStart = offset.coerceAtLeast(0), sourceEnd = offset.coerceAtLeast(0),
            kind = ReaderAnnotationKind.BOOKMARK,
        )
        all += value
        writeAll(all)
        value
    }

    fun upsertRange(
        bookId: String,
        start: Long,
        end: Long,
        kind: ReaderAnnotationKind,
        style: ReaderHighlightStyle = ReaderHighlightStyle.YELLOW,
        note: String = "",
        excerpt: String = "",
        id: String? = null,
    ): ReaderAnnotation = synchronized(lock) {
        require(kind != ReaderAnnotationKind.BOOKMARK) { "range annotation required" }
        val from = minOf(start, end).coerceAtLeast(0)
        val to = maxOf(start, end).coerceAtLeast(from + 1)
        val all = readAll().toMutableList()
        val now = System.currentTimeMillis()
        val previous = id?.let { value -> all.firstOrNull { it.id == value && it.bookId == bookId } }
        val next = ReaderAnnotation(
            id = previous?.id ?: UUID.randomUUID().toString(),
            bookId = bookId,
            sourceStart = from,
            sourceEnd = to,
            kind = kind,
            style = style,
            note = note.take(MAX_NOTE_CHARS),
            excerpt = excerpt.take(MAX_EXCERPT_CHARS),
            createdAt = previous?.createdAt ?: now,
            updatedAt = now,
        )
        if (previous != null) all.remove(previous)
        all += next
        writeAll(all)
        next
    }

    fun delete(bookId: String, id: String) = synchronized(lock) {
        val all = readAll().toMutableList()
        if (all.removeAll { it.bookId == bookId && it.id == id }) writeAll(all)
    }

    fun deleteBookmark(bookId: String, offset: Long) = synchronized(lock) {
        val all = readAll().toMutableList()
        if (all.removeAll { it.bookId == bookId && it.kind == ReaderAnnotationKind.BOOKMARK && kotlin.math.abs(it.sourceStart - offset) <= 1 }) writeAll(all)
    }

    fun clearBook(bookId: String) = synchronized(lock) {
        val all = readAll().toMutableList()
        if (all.removeAll { it.bookId == bookId }) writeAll(all)
    }

    fun remapBook(bookId: String, oldLength: Long, newLength: Long) = synchronized(lock) {
        if (oldLength <= 0 || newLength <= 0) return@synchronized
        val all = readAll().map { item ->
            if (item.bookId != bookId) item else item.copy(
                sourceStart = mapOffset(item.sourceStart, oldLength, newLength),
                sourceEnd = mapOffset(item.sourceEnd, oldLength, newLength).coerceAtLeast(mapOffset(item.sourceStart, oldLength, newLength)),
                updatedAt = System.currentTimeMillis(),
            )
        }
        writeAll(all)
    }

    fun exportJson(): JSONArray = synchronized(lock) {
        JSONArray().also { array -> readAll().forEach { array.put(toJson(it)) } }
    }

    fun importJson(array: JSONArray) = synchronized(lock) {
        val parsed = ArrayList<ReaderAnnotation>()
        for (index in 0 until minOf(array.length(), MAX_ANNOTATIONS)) {
            parsed += fromJson(array.getJSONObject(index))
        }
        writeAll(parsed.distinctBy { it.id })
    }

    private fun readAll(): List<ReaderAnnotation> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val root = JSONObject(file.readText(StandardCharsets.UTF_8))
            if (root.optInt("schema") != SCHEMA) return@runCatching emptyList()
            val array = root.optJSONArray("annotations") ?: JSONArray()
            buildList {
                for (index in 0 until minOf(array.length(), MAX_ANNOTATIONS)) {
                    runCatching { add(fromJson(array.getJSONObject(index))) }
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun writeAll(values: List<ReaderAnnotation>) {
        val root = JSONObject().put("schema", SCHEMA).put("annotations", JSONArray().also { array ->
            values.take(MAX_ANNOTATIONS).forEach { array.put(toJson(it)) }
        })
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temp).use { output ->
            output.write(root.toString().toByteArray(StandardCharsets.UTF_8)); output.fd.sync()
        }
        runCatching { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            .getOrElse { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    }

    private fun toJson(value: ReaderAnnotation) = JSONObject()
        .put("id", value.id).put("bookId", value.bookId)
        .put("sourceStart", value.sourceStart).put("sourceEnd", value.sourceEnd)
        .put("kind", value.kind.name).put("style", value.style.name)
        .put("note", value.note).put("excerpt", value.excerpt)
        .put("createdAt", value.createdAt).put("updatedAt", value.updatedAt)

    private fun fromJson(value: JSONObject): ReaderAnnotation {
        val id = value.getString("id").take(80)
        val bookId = value.getString("bookId").take(128)
        val start = value.getLong("sourceStart").coerceAtLeast(0)
        val end = value.getLong("sourceEnd").coerceAtLeast(start)
        return ReaderAnnotation(
            id = id, bookId = bookId, sourceStart = start, sourceEnd = end,
            kind = enumOr(value.optString("kind"), ReaderAnnotationKind.HIGHLIGHT),
            style = enumOr(value.optString("style"), ReaderHighlightStyle.YELLOW),
            note = value.optString("note").take(MAX_NOTE_CHARS),
            excerpt = value.optString("excerpt").take(MAX_EXCERPT_CHARS),
            createdAt = value.optLong("createdAt", 0).coerceAtLeast(0),
            updatedAt = value.optLong("updatedAt", 0).coerceAtLeast(0),
        )
    }

    private fun mapOffset(value: Long, oldLength: Long, newLength: Long): Long {
        if (newLength <= 1) return 0
        if (oldLength <= 1) return value.coerceIn(0, newLength - 1)
        return (value.coerceIn(0, oldLength - 1).toDouble() / (oldLength - 1).toDouble() * (newLength - 1)).toLong()
            .coerceIn(0, newLength - 1)
    }

    private inline fun <reified T : Enum<T>> enumOr(raw: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback

    private companion object {
        const val SCHEMA = 2
        const val MAX_ANNOTATIONS = 20_000
        const val MAX_NOTE_CHARS = 8 * 1024
        const val MAX_EXCERPT_CHARS = 512
    }
}
