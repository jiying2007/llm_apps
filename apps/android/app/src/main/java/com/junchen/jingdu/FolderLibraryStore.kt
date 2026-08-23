package com.junchen.jingdu

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.security.MessageDigest
import java.util.ArrayDeque

/** User-selected SAF roots only. No broad storage permission is requested. */
internal class FolderLibraryStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("jingdu.folder.library.v1", Context.MODE_PRIVATE)

    data class FolderEntry(
        val uri: Uri,
        val documentId: String,
        val name: String,
        val size: Long,
        val lastModified: Long,
        val signature: String,
    ) {
        val hasReliableMetadata: Boolean get() = size >= 0 && lastModified > 0
    }

    data class SyncResult(val roots: Int, val discovered: Int, val imported: Int, val skipped: Int, val failed: Int)

    fun roots(): List<Uri> = prefs.getStringSet(KEY_ROOTS, emptySet()).orEmpty()
        .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
        .sortedBy(Uri::toString)

    fun addRoot(uri: Uri) {
        val updated = prefs.getStringSet(KEY_ROOTS, emptySet()).orEmpty().toMutableSet()
        updated.add(uri.toString())
        prefs.edit().putStringSet(KEY_ROOTS, updated).apply()
    }

    fun removeRoot(uri: Uri) {
        val updated = prefs.getStringSet(KEY_ROOTS, emptySet()).orEmpty().toMutableSet()
        updated.remove(uri.toString())
        prefs.edit().putStringSet(KEY_ROOTS, updated).apply()
    }

    fun scanTxt(root: Uri, maxFiles: Int = 500): List<FolderEntry> {
        if (!DocumentsContract.isTreeUri(root)) return emptyList()
        val resolver = app.contentResolver
        val output = ArrayList<FolderEntry>()
        val queue = ArrayDeque<Pair<String, Int>>()
        val rootId = DocumentsContract.getTreeDocumentId(root)
        queue.add(rootId to 0)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        while (queue.isNotEmpty() && output.size < maxFiles) {
            val (parentId, depth) = queue.removeFirst()
            if (depth > MAX_DEPTH) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(root, parentId)
            runCatching {
                resolver.query(children, projection, null, null, null)?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    while (cursor.moveToNext() && output.size < maxFiles) {
                        val documentId = cursor.getString(idIndex) ?: continue
                        val name = cursor.getString(nameIndex).orEmpty()
                        val mime = cursor.getString(mimeIndex).orEmpty()
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (depth < MAX_DEPTH) queue.add(documentId to depth + 1)
                        } else if (name.endsWith(".txt", ignoreCase = true) || mime == "text/plain") {
                            val uri = DocumentsContract.buildDocumentUriUsingTree(root, documentId)
                            val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L
                            val lastModified = if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) cursor.getLong(modifiedIndex) else -1L
                            val signature = "$documentId:$size:$lastModified"
                            output += FolderEntry(uri, documentId, name, size, lastModified, signature)
                        }
                    }
                }
            }
        }
        return output.distinctBy { it.uri.toString() }
    }

    /** Unknown size/mtime is intentionally treated as changed so providers cannot hide updates. */
    fun needsImport(entry: FolderEntry): Boolean =
        !entry.hasReliableMetadata || prefs.getString(seenKey(entry.uri), null) != entry.signature

    fun markImported(entry: FolderEntry) {
        if (entry.hasReliableMetadata) prefs.edit().putString(seenKey(entry.uri), entry.signature).apply()
        else prefs.edit().remove(seenKey(entry.uri)).apply()
    }

    fun clearSeen(uri: Uri) { prefs.edit().remove(seenKey(uri)).apply() }

    private fun seenKey(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray(Charsets.UTF_8))
        return "seen." + digest.take(12).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_ROOTS = "roots"
        private const val MAX_DEPTH = 12
    }
}
