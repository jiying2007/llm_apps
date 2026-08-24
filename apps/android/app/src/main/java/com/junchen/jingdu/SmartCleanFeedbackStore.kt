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
        val key = "book.$bookId.$fingerprint"
        val previous = runCatching {
            SmartCleanFeedback.valueOf(prefs.getString(key, null) ?: SmartCleanFeedback.NONE.name)
        }.getOrDefault(SmartCleanFeedback.NONE)
        if (previous == feedback) return

        val editor = prefs.edit()
        adjustAggregate(editor, fingerprint, previous, -1)
        if (feedback == SmartCleanFeedback.NONE) editor.remove(key) else editor.putString(key, feedback.name)
        adjustAggregate(editor, fingerprint, feedback, +1)
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
        prefs.all.forEach { (key, value) ->
            if (!key.startsWith(prefix)) return@forEach
            val fingerprint = key.removePrefix(prefix)
            val feedback = runCatching { SmartCleanFeedback.valueOf(value as? String ?: "") }
                .getOrDefault(SmartCleanFeedback.NONE)
            adjustAggregate(editor, fingerprint, feedback, -1)
            editor.remove(key)
        }
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

    private fun adjustAggregate(
        editor: android.content.SharedPreferences.Editor,
        fingerprint: String,
        feedback: SmartCleanFeedback,
        delta: Int,
    ) {
        if (feedback == SmartCleanFeedback.NONE || delta == 0) return
        val aggregate = "agg.$fingerprint.${feedback.name.lowercase()}"
        val value = (prefs.getInt(aggregate, 0) + delta).coerceIn(0, 10_000)
        if (value == 0) editor.remove(aggregate) else editor.putInt(aggregate, value)
    }

    private fun bookKey(bookId: String, reason: String, text: String) = "book.$bookId.${fingerprint(reason, text)}"

    private fun fingerprint(reason: String, text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((reason + "\u001f" + text).toByteArray(StandardCharsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }
}
