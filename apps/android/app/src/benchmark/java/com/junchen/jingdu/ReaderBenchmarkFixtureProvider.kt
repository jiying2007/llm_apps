package com.junchen.jingdu

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

/** Benchmark-build only. Never merged into the production manifest/source set. */
class ReaderBenchmarkFixtureProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return Bundle.EMPTY
        return when (method) {
            "seed" -> {
                val mib = (arg?.toIntOrNull() ?: 8).coerceIn(1, 128)
                val fixture = File(context.cacheDir, "Benchmark Novel.txt")
                writeFixture(fixture, mib)
                val book = BookRepository(context).importUri(Uri.fromFile(fixture), BookRepository.AUTO)
                Bundle().apply { putString("bookId", book.id); putLong("bytes", fixture.length()) }
            }
            "clear" -> {
                BookRepository(context).list().forEach { BookRepository(context).delete(it) }
                Bundle.EMPTY
            }
            else -> super.call(method, arg, extras)
        }
    }

    private fun writeFixture(file: File, mib: Int) {
        val target = mib.toLong() * 1024L * 1024L
        val paragraph = ("第%05d章 基准阅读旅程\n" +
            "这是用于净读 Reader V2 性能验证的本地长文本。The quick brown fox jumps over the lazy dog.\n\n")
        FileOutputStream(file).buffered().use { output ->
            var bytes = 0L
            var chapter = 1
            while (bytes < target) {
                val chunk = paragraph.format(chapter++).toByteArray(Charsets.UTF_8)
                output.write(chunk)
                bytes += chunk.size
            }
            output.flush()
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? = null
}
