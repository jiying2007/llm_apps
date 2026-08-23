package com.junchen.jingdu

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.util.ArrayDeque

/** User-selected SAF roots only. No broad storage permission is requested. */
internal class FolderLibraryStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("jingdu.folder.library.v1", Context.MODE_PRIVATE)

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

    fun scanTxt(root: Uri, maxFiles: Int = 500): List<Uri> {
        if (!DocumentsContract.isTreeUri(root)) return emptyList()
        val resolver = app.contentResolver
        val output = ArrayList<Uri>()
        val queue = ArrayDeque<Pair<String, Int>>()
        val rootId = DocumentsContract.getTreeDocumentId(root)
        queue.add(rootId to 0)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
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
                    while (cursor.moveToNext() && output.size < maxFiles) {
                        val id = cursor.getString(idIndex) ?: continue
                        val name = cursor.getString(nameIndex).orEmpty()
                        val mime = cursor.getString(mimeIndex).orEmpty()
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (depth < MAX_DEPTH) queue.add(id to depth + 1)
                        } else if (name.endsWith(".txt", ignoreCase = true) || mime == "text/plain") {
                            output.add(DocumentsContract.buildDocumentUriUsingTree(root, id))
                        }
                    }
                }
            }
        }
        return output.distinctBy(Uri::toString)
    }

    data class SyncResult(val roots: Int, val discovered: Int, val imported: Int, val failed: Int)

    companion object {
        private const val KEY_ROOTS = "roots"
        private const val MAX_DEPTH = 12
    }
}
