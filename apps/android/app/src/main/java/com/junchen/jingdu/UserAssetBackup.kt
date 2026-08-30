package com.junchen.jingdu

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * Portable local user assets that contain no book text. Book progress is revision-bound and may be
 * staged before the matching TXT is re-imported. Reading sessions contain identifiers/counts only.
 */
internal class UserAssetBackup(context: Context) {
    private val appContext = context.applicationContext
    private val repository = BookRepository(appContext)
    private val libraryMetadata = LibraryMetadataStore(appContext)
    private val statsDao = ReaderDatabaseProvider.get(appContext).statsDao()

    fun exportLibrary(): JSONArray {
        val books = repository.list().associateBy { it.id }
        val metadata = libraryMetadata.all()
        val ids = (books.keys + metadata.keys).sorted().take(MAX_LIBRARY_ASSETS)
        return JSONArray().also { array ->
            ids.forEach { id ->
                if (!validSha256(id)) return@forEach
                val book = books[id]
                val meta = metadata[id] ?: LibraryMetadata()
                val normalizedSha = book?.normalizedSha256 ?: meta.pendingNormalizedSha256
                val progress = book?.progress ?: meta.pendingProgress
                val item = JSONObject()
                    .put("bookId", id)
                    .put("favorite", meta.favorite)
                    .put("tags", JSONArray(meta.tags))
                if (normalizedSha != null && validSha256(normalizedSha) && progress != null) {
                    item.put("normalizedSha256", normalizedSha)
                    item.put("progress", progress.coerceAtLeast(0))
                }
                array.put(item)
            }
        }
    }

    fun importLibrary(array: JSONArray): Int {
        if (array.length() > MAX_LIBRARY_ASSETS) throw IllegalArgumentException("too many library assets")
        val parsed = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val bookId = item.optString("bookId")
                if (!validSha256(bookId)) continue
                val tags = item.optJSONArray("tags") ?: JSONArray()
                val normalizedSha = item.optString("normalizedSha256").takeIf(::validSha256)
                val progress = if (normalizedSha != null && item.has("progress")) item.optLong("progress").coerceAtLeast(0) else null
                add(
                    PortableLibraryAsset(
                        bookId = bookId,
                        favorite = item.optBoolean("favorite", false),
                        tags = buildList {
                            for (tagIndex in 0 until minOf(tags.length(), 12)) {
                                val tag = tags.optString(tagIndex).trim().take(24)
                                if (tag.isNotEmpty() && tag !in this) add(tag)
                            }
                        },
                        normalizedSha256 = normalizedSha,
                        progress = progress,
                    ),
                )
            }
        }.distinctBy { it.bookId }

        parsed.forEach { item ->
            libraryMetadata.restorePortable(item.bookId, item.favorite, item.tags, item.normalizedSha256, item.progress)
        }

        val existing = repository.list().associateBy { it.id }
        parsed.forEach { item ->
            val book = existing[item.bookId] ?: return@forEach
            val progress = libraryMetadata.consumeRestoredProgress(book.id, book.normalizedSha256) ?: return@forEach
            repository.saveProgress(book, progress)
        }
        return parsed.size
    }

    fun exportReadingStats(): JSONObject = runBlocking(Dispatchers.IO) {
        val pace = statsDao.pace()
        val sessions = statsDao.listSessions().takeLast(MAX_READING_SESSIONS)
        JSONObject()
            .put("schema", 1)
            .put("type", "jingdu-reading-stats")
            .put("containsBookText", false)
            .put("pace", pace?.let {
                JSONObject()
                    .put("charsPerMinute", it.charsPerMinute)
                    .put("samples", it.samples)
            })
            .put("sessions", JSONArray().also { array -> sessions.forEach { array.put(sessionJson(it)) } })
    }

    fun importReadingStats(root: JSONObject): Int {
        if (root.optInt("schema") != 1 || root.optString("type") != "jingdu-reading-stats" || root.optBoolean("containsBookText", true)) {
            throw IllegalArgumentException("unsupported reading stats backup")
        }
        val sessionsArray = root.optJSONArray("sessions") ?: JSONArray()
        if (sessionsArray.length() > MAX_READING_SESSIONS) throw IllegalArgumentException("too many reading sessions")
        val sessions = buildList {
            for (index in 0 until sessionsArray.length()) {
                val item = sessionsArray.optJSONObject(index) ?: continue
                val id = item.optString("id").take(80)
                val bookId = item.optString("bookId")
                if (id.isBlank() || !validSha256(bookId)) continue
                val startedAt = item.optLong("startedAt").coerceAtLeast(0)
                val durationMs = item.optLong("durationMs").coerceIn(0, MAX_SESSION_DURATION_MS)
                val startPosition = item.optLong("startPosition").coerceAtLeast(0)
                val endPosition = item.optLong("endPosition").coerceAtLeast(0)
                val charsRead = item.optLong("charsRead").coerceIn(0, MAX_SESSION_CHARS)
                if (startedAt == 0L || durationMs == 0L) continue
                add(
                    ReaderSessionEntity(
                        id = id,
                        bookId = bookId,
                        dayEpoch = item.optLong("dayEpoch"),
                        startedAt = startedAt,
                        durationMs = durationMs,
                        startPosition = startPosition,
                        endPosition = endPosition,
                        charsRead = charsRead,
                    ),
                )
            }
        }.distinctBy { it.id }

        val paceObject = root.optJSONObject("pace")
        val pace = paceObject?.let {
            ReaderPaceEntity(
                charsPerMinute = it.optDouble("charsPerMinute", 500.0).coerceIn(120.0, 1800.0),
                samples = it.optInt("samples", 0).coerceIn(0, 1_000_000),
            )
        }

        runBlocking(Dispatchers.IO) {
            statsDao.clearSessions()
            statsDao.clearPace()
            if (sessions.isNotEmpty()) statsDao.upsertSessions(sessions)
            if (pace != null) statsDao.upsertPace(pace)
        }
        return sessions.size
    }

    private fun sessionJson(value: ReaderSessionEntity) = JSONObject()
        .put("id", value.id)
        .put("bookId", value.bookId)
        .put("dayEpoch", value.dayEpoch)
        .put("startedAt", value.startedAt)
        .put("durationMs", value.durationMs)
        .put("startPosition", value.startPosition)
        .put("endPosition", value.endPosition)
        .put("charsRead", value.charsRead)

    private fun validSha256(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

    private data class PortableLibraryAsset(
        val bookId: String,
        val favorite: Boolean,
        val tags: List<String>,
        val normalizedSha256: String?,
        val progress: Long?,
    )

    private companion object {
        const val MAX_LIBRARY_ASSETS = 5_000
        const val MAX_READING_SESSIONS = 5_000
        const val MAX_SESSION_DURATION_MS = 24L * 60L * 60L * 1000L
        const val MAX_SESSION_CHARS = 100_000_000L
    }
}
