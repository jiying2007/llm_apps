package com.junchen.jingdu

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class TocOverrides(
    val hiddenOffsets: Set<Long> = emptySet(),
    val custom: List<SmartChapter> = emptyList(),
)

/** Local, source-offset-based TOC repairs. No regex and no document text is persisted automatically. */
internal class TocOverrideStore(context: Context) {
    private val prefs = context.getSharedPreferences("jingdu.toc.overrides.v1", Context.MODE_PRIVATE)

    fun load(bookId: String): TocOverrides {
        val raw = prefs.getString(bookId, null) ?: return TocOverrides()
        return runCatching {
            val root = JSONObject(raw)
            val hiddenArray = root.optJSONArray("hidden") ?: JSONArray()
            val hidden = buildSet {
                for (index in 0 until hiddenArray.length()) add(hiddenArray.getLong(index))
            }
            val customArray = root.optJSONArray("custom") ?: JSONArray()
            val custom = buildList {
                for (index in 0 until customArray.length()) {
                    val item = customArray.getJSONObject(index)
                    val offset = item.getLong("offset")
                    val title = item.getString("title").trim().take(80)
                    if (offset >= 0 && title.isNotEmpty()) add(SmartChapter(offset, title, "user", 100))
                }
            }
            TocOverrides(hidden, custom.distinctBy { it.offset })
        }.getOrDefault(TocOverrides())
    }

    fun hide(bookId: String, offset: Long) {
        val current = load(bookId)
        save(bookId, current.copy(hiddenOffsets = current.hiddenOffsets + offset))
    }

    fun add(bookId: String, offset: Long, title: String) {
        val value = title.trim().take(80)
        if (value.isEmpty() || offset < 0) return
        val current = load(bookId)
        save(bookId, current.copy(custom = (current.custom.filterNot { it.offset == offset } + SmartChapter(offset, value, "user", 100)).take(500)))
    }

    fun reset(bookId: String) { prefs.edit().remove(bookId).apply() }
    fun clear(bookId: String) { prefs.edit().remove(bookId).apply() }

    fun apply(report: TocQualityReport, overrides: TocOverrides): TocQualityReport {
        val chapters = (report.chapters.filterNot { it.offset in overrides.hiddenOffsets } + overrides.custom)
            .distinctBy { it.offset }
            .sortedBy { it.offset }
        return report.copy(chapters = chapters)
    }

    private fun save(bookId: String, value: TocOverrides) {
        val root = JSONObject()
        val hidden = JSONArray()
        value.hiddenOffsets.sorted().take(500).forEach(hidden::put)
        root.put("hidden", hidden)
        val custom = JSONArray()
        value.custom.sortedBy { it.offset }.take(500).forEach { chapter ->
            custom.put(JSONObject().put("offset", chapter.offset).put("title", chapter.title))
        }
        root.put("custom", custom)
        prefs.edit().putString(bookId, root.toString()).apply()
    }
}
