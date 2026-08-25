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
                val mib = (arg?.toIntOrNull() ?: 10).coerceIn(1, 256)
                val fixture = File(context.cacheDir, "Benchmark Novel ${mib} MiB.txt")
                val target = mib.toLong() * 1024L * 1024L
                if (!fixture.isFile || fixture.length() < target) writeFixture(fixture, target)
                val repository = BookRepository(context)
                val existing = repository.list().firstOrNull { it.name == fixture.name }
                val book = existing ?: repository.importUri(Uri.fromFile(fixture), BookRepository.AUTO)
                Bundle().apply {
                    putString("bookId", book.id)
                    putLong("bytes", fixture.length())
                    putInt("mib", mib)
                }
            }
            "mode" -> {
                val mode = when (arg?.lowercase()) {
                    "paged" -> ReaderMode.PAGED
                    "continuous" -> ReaderMode.CONTINUOUS
                    else -> error("Unsupported Reader V3 benchmark mode: $arg")
                }
                val preferences = ReaderPreferences(context)
                preferences.flush(preferences.load().copy(readingMode = mode, autoScrollEnabled = false))
                Bundle().apply { putString("mode", mode.name) }
            }
            "clear" -> {
                val repository = BookRepository(context)
                repository.list().filter { it.name.startsWith("Benchmark Novel ") }.forEach(repository::delete)
                context.cacheDir.listFiles()?.filter { it.name.startsWith("Benchmark Novel ") }?.forEach(File::delete)
                Bundle.EMPTY
            }
            else -> super.call(method, arg, extras)
        }
    }

    private fun writeFixture(file: File, target: Long) {
        val paragraph = ("第%05d章 Reader V3 基准阅读旅程\n" +
            "这是用于净读 Reader V3 性能与长文本稳定性验证的本地夹具。The quick brown fox jumps over the lazy dog.\n\n")
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
