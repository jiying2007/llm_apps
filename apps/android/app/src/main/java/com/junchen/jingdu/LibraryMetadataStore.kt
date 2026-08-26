package com.junchen.jingdu

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LibraryMetadata(
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
)

internal class LibraryMetadataStore(context: Context) {
    private val prefs = context.getSharedPreferences("jingdu.library.metadata.v1", Context.MODE_PRIVATE)

    fun load(bookId: String): LibraryMetadata {
        val raw = prefs.getString(bookId, null) ?: return LibraryMetadata()
        return runCatching {
            val json = JSONObject(raw)
            val tags = json.optJSONArray("tags") ?: JSONArray()
            LibraryMetadata(
                favorite = json.optBoolean("favorite", false),
                tags = buildList {
                    for (index in 0 until tags.length()) {
                        val tag = tags.optString(index).trim().take(MAX_TAG_CHARS)
                        if (tag.isNotEmpty() && tag !in this) add(tag)
                        if (size >= MAX_TAGS) break
                    }
                },
            )
        }.getOrDefault(LibraryMetadata())
    }

    fun toggleFavorite(bookId: String): LibraryMetadata {
        val current = load(bookId)
        return save(bookId, current.copy(favorite = !current.favorite))
    }

    fun setTags(bookId: String, input: String): LibraryMetadata {
        val current = load(bookId)
        val tags = input
            .split(',', '，', ';', '；', '\n')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { it.take(MAX_TAG_CHARS) }
            .distinct()
            .take(MAX_TAGS)
        return save(bookId, current.copy(tags = tags))
    }

    fun clear(bookId: String) {
        prefs.edit().remove(bookId).apply()
    }

    private fun save(bookId: String, value: LibraryMetadata): LibraryMetadata {
        val json = JSONObject()
            .put("favorite", value.favorite)
            .put("tags", JSONArray(value.tags))
        prefs.edit().putString(bookId, json.toString()).apply()
        return value
    }

    private companion object {
        const val MAX_TAGS = 12
        const val MAX_TAG_CHARS = 24
    }
}
