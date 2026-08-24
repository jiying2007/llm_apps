package com.junchen.jingdu

import java.util.ArrayDeque

/**
 * Android reader-session boundary. Core ReaderController remains the source-offset authority;
 * this class owns only the currently-open book/revision and transient page navigation state.
 */
internal class ReaderSession {
    var reader: ReaderController = ReaderController()
        private set
    var book: BookRepository.Book? = null
        private set
    var cleanMode: Boolean = false
        private set
    var visiblePageChars: Long = ReaderController.DEFAULT_PAGE_CHARS
    private val pageHistory = ArrayDeque<Long>()

    fun replace(nextReader: ReaderController, nextBook: BookRepository.Book, clean: Boolean): ReaderController {
        val previous = reader
        reader = nextReader
        book = nextBook
        cleanMode = clean
        pageHistory.clear()
        return previous
    }

    fun clear(): ReaderController {
        val previous = reader
        reader = ReaderController()
        book = null
        cleanMode = false
        pageHistory.clear()
        return previous
    }

    fun pushPage(position: Long) {
        if (pageHistory.lastOrNull() != position) pageHistory.addLast(position)
        while (pageHistory.size > MAX_PAGE_HISTORY) pageHistory.removeFirst()
    }

    fun previousPagePosition(): Long? = if (pageHistory.isEmpty()) null else pageHistory.removeLast()

    fun clearPageHistory() = pageHistory.clear()

    fun hasBook(): Boolean = book != null

    private companion object { const val MAX_PAGE_HISTORY = 256 }
}
