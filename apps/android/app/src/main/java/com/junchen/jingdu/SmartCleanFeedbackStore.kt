package com.junchen.jingdu

import android.content.Context
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

enum class SmartCleanFeedback { NONE, KEEP, DELETE, PROTECT }

/**
 * Local correction memory for Smart Clean. Only one-way fingerprints and decisions are retained;
 * candidate/book text is never copied into this store and nothing is uploaded.
 */
internal class SmartCleanFeedbackStore(context: Context) {
    private val prefs = context.getSharedPreferences("jingdu.smartclean.feedback.v1", Context.MODE_PRIVATE)

    fun decision(bookId: String, reason: String, text: String): SmartCleanFeedback = runCatching {
        SmartCleanFeedback.valueOf(prefs.getString(bookKey(bookId, reason, text), null) ?: SmartCleanFeedback.NONE.name)
    }.getOrDefault(SmartCleanFeedback.NONE)

    fun record(bookId: String, reason: String, text: String, feedback: SmartCleanFeedback) {
        if (bookId.isBlank() || text.isBlank()) return
        val fingerprint = fingerprint(reason, text)
        val editor = prefs.edit().putString("book.$bookId.$fingerprint", feedback.name)
        if (feedback != SmartCleanFeedback.NONE) {
            val aggregate = "agg.$fingerprint.${feedback.name.lowercase()}"
            editor.putInt(aggregate, (prefs.getInt(aggregate, 0) + 1).coerceAtMost(10_000))
        }
        editor.apply()
    }

    fun modelDelta(reason: String, text: String): Int {
        val fingerprint = fingerprint(reason, text)
        val deletes = prefs.getInt("agg.$fingerprint.delete", 0)
        val keeps = prefs.getInt("agg.$fingerprint.keep", 0) + prefs.getInt("agg.$fingerprint.protect", 0) * 2
        return ((deletes - keeps) * 6).coerceIn(-24, 24)
    }

    fun clearBook(bookId: String) {
        val prefix = "book.$bookId."
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        editor.apply()
    }

    fun summary(): FeedbackSummary {
        var keep = 0
        var delete = 0
        var protect = 0
        prefs.all.forEach { (key, value) ->
            if (!key.startsWith("agg.")) return@forEach
            val count = value as? Int ?: return@forEach
            when {
                key.endsWith(".keep") -> keep += count
                key.endsWith(".delete") -> delete += count
                key.endsWith(".protect") -> protect += count
            }
        }
        return FeedbackSummary(keep, delete, protect)
    }

    data class FeedbackSummary(val keep: Int, val delete: Int, val protect: Int)

    private fun bookKey(bookId: String, reason: String, text: String) = "book.$bookId.${fingerprint(reason, text)}"

    private fun fingerprint(reason: String, text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((reason + "\u001f" + text).toByteArray(StandardCharsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }
}
