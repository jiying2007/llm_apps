package com.junchen.jingdu

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.util.ArrayDeque
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class MainActivity : ComponentActivity() {
    private data class OpenedBook(
        val book: BookRepository.Book,
        val reader: ReaderController,
        val clean: Boolean,
        val contentFile: File,
    )

    private val main = Handler(Looper.getMainLooper())
    private val workers: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "jingdu-worker").apply { isDaemon = true }
    }
    private val workGeneration = AtomicLong()
    private val pageHistory = ArrayDeque<Long>()

    private lateinit var repository: BookRepository
    private lateinit var tts: TtsController
    private lateinit var readerPreferences: ReaderPreferences
    private var reader = ReaderController()
    private var currentBook: BookRepository.Book? = null
    private var cleanMode = false
    private var visiblePageChars = ReaderController.DEFAULT_PAGE_CHARS
    private var pendingExport: File? = null
    private var uiState by mutableStateOf(AppUiState())

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importUri(it) }
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val source = pendingExport
        pendingExport = null
        if (uri != null && source != null) exportFile(source, uri)
    }

    private val actions by lazy {
        JingduActions(
            onImport = { importLauncher.launch(arrayOf("text/plain", "text/*", "application/octet-stream")) },
            onOpenBook = ::openBookById,
            onDeleteLibraryBook = ::deleteLibraryBook,
            onBackToLibrary = ::backToLibrary,
            onNavigatePrevious = { navigatePrevious(userInitiated = true) },
            onNavigateNext = { navigateNext(userInitiated = true) },
            onSeekFraction = ::seekFraction,
            onVisibleCharsChanged = { visiblePageChars = it.coerceAtLeast(ReaderController.MIN_PAGE_CHARS) },
            onOpenPanel = ::openPanel,
            onClosePanel = { uiState = uiState.copy(panel = null) },
            onSearchQueryChanged = { uiState = uiState.copy(searchQuery = it) },
            onSearch = ::search,
            onJump = ::jumpTo,
            onAddBookmark = ::addBookmark,
            onDeleteBookmark = ::deleteBookmark,
            onAddRule = ::addRule,
            onDeleteRule = ::deleteRule,
            onClearRules = ::clearRules,
            onToggleCleanPreview = ::toggleCleanPreview,
            onExportClean = ::exportClean,
            onEncodingSelected = ::redecode,
            onSettingsChanged = ::updateSettings,
            onToggleTts = ::toggleTts,
            onToggleAutoPaging = ::toggleAutoPaging,
            onSleepTimer = ::setSleepTimer,
            onRequestDeleteCurrent = { uiState = uiState.copy(deleteConfirmation = true) },
            onDismissDelete = { uiState = uiState.copy(deleteConfirmation = false) },
            onConfirmDeleteCurrent = ::deleteCurrentBook,
            onMessageConsumed = { uiState = uiState.copy(message = null) },
        )
    }

    private val autoStep = object : Runnable {
        override fun run() {
            if (!uiState.autoPaging || uiState.busyLabel != null || currentBook == null) return
            if (reader.position() >= (reader.length() - 1).coerceAtLeast(0)) {
                stopAutoPaging()
                return
            }
            navigateNext(userInitiated = false)
            main.postDelayed(this, uiState.settings.autoPageDelayMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = BookRepository(this)
        readerPreferences = ReaderPreferences(this)
        tts = TtsController(this)
        val settings = readerPreferences.load()
        tts.setRate(settings.ttsRate)
        tts.setPitch(settings.ttsPitch)
        uiState = uiState.copy(settings = settings)
        refreshLibrary()

        setContent { JingduApp(uiState, actions) }

        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        } else {
            restoreSession(savedInstanceState)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val book = currentBook
        if (uiState.screen == AppScreen.READER && book != null) {
            outState.putString(STATE_BOOK_ID, book.id)
            outState.putString(STATE_NORMALIZED_SHA, book.normalizedSha256)
            outState.putLong(STATE_POSITION, reader.position())
            outState.putBoolean(STATE_CLEAN_MODE, cleanMode)
        }
        pendingExport?.absolutePath?.let { outState.putString(STATE_PENDING_EXPORT, it) }
        super.onSaveInstanceState(outState)
    }

    private fun restoreSession(state: Bundle) {
        state.getString(STATE_PENDING_EXPORT)?.let { path ->
            File(path).takeIf(File::isFile)?.let { pendingExport = it }
        }
        val id = state.getString(STATE_BOOK_ID) ?: return
        val book = findBook(id) ?: return
        val sameRevision = state.getString(STATE_NORMALIZED_SHA) == book.normalizedSha256
        val restoredPosition = if (sameRevision) state.getLong(STATE_POSITION, book.progress) else book.progress
        openBook(
            book = book,
            clean = state.getBoolean(STATE_CLEAN_MODE, false),
            restoredOverride = restoredPosition,
        )
    }

    private fun handleIncomingIntent(intent: Intent?) {
        incomingUri(intent)?.let { importUri(it) }
    }

    private fun incomingUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        else -> null
    }

    private fun refreshLibrary() {
        uiState = uiState.copy(books = repository.list().map(::toCard))
    }

    private fun toCard(book: BookRepository.Book) = BookCardModel(
        id = book.id,
        name = book.name,
        encoding = book.encoding,
        sizeBytes = book.size,
        progress = book.progress,
        charCount = book.charCount,
        touchedAt = book.touchedAt,
        normalizedSha256 = book.normalizedSha256,
    )

    private fun findBook(id: String): BookRepository.Book? = repository.list().firstOrNull { it.id == id }

    private fun importUri(uri: Uri) {
        runWork(
            label = "正在识别编码并导入…",
            task = { repository.importUri(uri, BookRepository.AUTO) },
            success = { openBook(it, clean = false) },
            errorTitle = "导入失败",
        )
    }

    private fun openBookById(id: String) {
        val book = findBook(id) ?: return showMessage("书籍私有副本不存在，请重新导入。")
        openBook(book, clean = false)
    }

    private fun openBook(
        book: BookRepository.Book,
        clean: Boolean,
        restoredOverride: Long? = null,
    ) {
        if (uiState.busyLabel != null) return
        stopAutoPaging()
        stopTts()

        val previousBook = currentBook
        val previousReader = reader
        val previousClean = cleanMode
        val previousPosition = previousBook?.let { previousReader.position() } ?: 0L
        val restored = restoredOverride ?: if (clean) {
            0L
        } else if (
            previousBook != null &&
            previousBook.id == book.id &&
            previousBook.normalizedSha256 == book.normalizedSha256 &&
            !previousClean
        ) {
            previousPosition
        } else {
            book.progress
        }

        if (previousBook != null && !previousClean) repository.saveProgress(previousBook, previousPosition)
        val token = workGeneration.incrementAndGet()
        uiState = uiState.copy(
            screen = AppScreen.READER,
            busyLabel = if (clean) "正在生成净读预览…" else "正在打开…",
            panel = null,
            searchQuery = "",
            searchResults = emptyList(),
            chapters = emptyList(),
            chaptersLoaded = false,
            bookmarks = emptyList(),
            repairRules = emptyList(),
        )

        workers.execute {
            val candidate = ReaderController()
            try {
                val file = if (clean) buildClean(book) else repository.normalizedFile(book)
                candidate.open(file, restored)
                if (token != workGeneration.get() || isDestroyed) {
                    candidate.close()
                    return@execute
                }
                main.post {
                    if (token != workGeneration.get() || isDestroyed) {
                        candidate.close()
                        return@post
                    }
                    reader = candidate
                    currentBook = book
                    cleanMode = clean
                    pageHistory.clear()
                    repository.updateCharCount(book, candidate.length())
                    repository.pruneDocumentRevisions(book)
                    repository.pruneCleanRevisions(book, if (clean) file else null)
                    NativeIndexCache.pruneOrphans(repository.normalizedFile(book).parentFile)
                    previousReader.close()
                    uiState = uiState.copy(busyLabel = null, currentBook = toCard(book), cleanMode = clean)
                    render()
                    refreshLibrary()
                }
            } catch (error: Throwable) {
                candidate.close()
                main.post {
                    if (token != workGeneration.get() || isDestroyed) return@post
                    currentBook = previousBook
                    cleanMode = previousClean
                    uiState = uiState.copy(
                        busyLabel = null,
                        screen = if (previousBook == null) AppScreen.LIBRARY else AppScreen.READER,
                        currentBook = previousBook?.let(::toCard),
                        cleanMode = previousClean,
                        message = friendlyError(if (clean) "净读失败" else "打开失败", error),
                    )
                    if (previousBook != null) render()
                }
            }
        }
    }

    private fun render() {
        val book = currentBook ?: return
        if (uiState.busyLabel != null) return
        try {
            val text = reader.page()
            if (!cleanMode) repository.saveProgress(book, reader.position())
            uiState = uiState.copy(
                screen = AppScreen.READER,
                currentBook = toCard(book),
                pageText = text,
                position = reader.position(),
                length = reader.length(),
                cleanMode = cleanMode,
            )
        } catch (error: Throwable) {
            showMessage(friendlyError("读取失败", error))
        }
    }

    private fun navigateNext(userInitiated: Boolean) {
        if (uiState.busyLabel != null || currentBook == null) return
        if (userInitiated) stopAutoPaging()
        val current = reader.position()
        if (current >= (reader.length() - 1).coerceAtLeast(0)) return
        pageHistory.addLast(current)
        reader.move(visiblePageChars.coerceAtLeast(ReaderController.MIN_PAGE_CHARS))
        render()
    }

    private fun navigatePrevious(userInitiated: Boolean) {
        if (uiState.busyLabel != null || currentBook == null) return
        if (userInitiated) stopAutoPaging()
        if (pageHistory.isNotEmpty()) reader.jump(pageHistory.removeLast())
        else reader.move(-visiblePageChars.coerceAtLeast(ReaderController.MIN_PAGE_CHARS))
        render()
    }

    private fun seekFraction(fraction: Float) {
        if (uiState.busyLabel != null || currentBook == null || reader.length() <= 0) return
        stopAutoPaging()
        stopTts()
        pageHistory.clear()
        reader.jump((reader.length().toDouble() * fraction.coerceIn(0f, 1f)).toLong())
        render()
    }

    private fun jumpTo(offset: Long) {
        if (uiState.busyLabel != null || currentBook == null) return
        stopAutoPaging()
        stopTts()
        pageHistory.clear()
        reader.jump(offset)
        uiState = uiState.copy(panel = null)
        render()
    }

    private fun backToLibrary() {
        uiState.panel?.let {
            uiState = uiState.copy(panel = null)
            return
        }
        val book = currentBook
        if (book != null && !cleanMode) repository.saveProgress(book, reader.position())
        stopAutoPaging()
        stopTts()
        reader.close()
        currentBook = null
        cleanMode = false
        pageHistory.clear()
        refreshLibrary()
        uiState = uiState.copy(
            screen = AppScreen.LIBRARY,
            currentBook = null,
            pageText = "",
            position = 0,
            length = 0,
            cleanMode = false,
            panel = null,
            busyLabel = null,
            searchQuery = "",
            searchResults = emptyList(),
            chapters = emptyList(),
            chaptersLoaded = false,
            bookmarks = emptyList(),
            repairRules = emptyList(),
        )
    }

    private fun openPanel(panel: ReaderPanel) {
        if (uiState.busyLabel != null || currentBook == null) return
        uiState = uiState.copy(panel = panel)
        when (panel) {
            ReaderPanel.CHAPTERS -> if (!uiState.chaptersLoaded) loadChapters()
            ReaderPanel.BOOKMARKS -> refreshBookmarks()
            ReaderPanel.CLEAN -> refreshRules()
            else -> Unit
        }
    }

    private fun search(query: String) {
        val value = query.trim()
        if (value.isEmpty() || currentBook == null) return
        runWork(
            label = "正在全文搜索…",
            task = { reader.search(value) },
            success = { hits ->
                uiState = uiState.copy(
                    panel = ReaderPanel.SEARCH,
                    searchResults = hits.map { SearchResultModel(it.offset(), it.context()) },
                    message = if (hits.isEmpty()) "没有找到“$value”" else null,
                )
            },
            errorTitle = "搜索失败",
        )
    }

    private fun loadChapters() {
        runWork(
            label = "正在生成目录…",
            task = reader::chapters,
            success = { chapters ->
                uiState = uiState.copy(
                    panel = ReaderPanel.CHAPTERS,
                    chapters = chapters.map { ChapterModel(it.offset(), it.title()) },
                    chaptersLoaded = true,
                    message = if (chapters.isEmpty()) "未识别到章节标题" else null,
                )
            },
            errorTitle = "目录生成失败",
        )
    }

    private fun bookmarkKey(book: BookRepository.Book): String =
        "bookmarks.${book.id}.${book.normalizedSha256}"

    private fun bookmarkPositions(book: BookRepository.Book): MutableSet<String> =
        getPreferences(MODE_PRIVATE).getStringSet(bookmarkKey(book), emptySet())?.toMutableSet() ?: mutableSetOf()

    private fun refreshBookmarks() {
        val book = currentBook ?: return
        val length = reader.length().coerceAtLeast(1)
        val models = bookmarkPositions(book).mapNotNull { raw ->
            raw.toLongOrNull()?.let { offset ->
                BookmarkModel(offset, (offset.toDouble() / length.toDouble()).toFloat().coerceIn(0f, 1f))
            }
        }.sortedBy { it.offset }
        uiState = uiState.copy(bookmarks = models)
    }

    private fun addBookmark() {
        val book = currentBook ?: return
        if (cleanMode) return showMessage("净读预览不会写入原文书签。")
        val marks = bookmarkPositions(book)
        marks.add(reader.position().toString())
        getPreferences(MODE_PRIVATE).edit().putStringSet(bookmarkKey(book), marks).apply()
        refreshBookmarks()
        showMessage("已添加书签")
    }

    private fun deleteBookmark(offset: Long) {
        val book = currentBook ?: return
        val marks = bookmarkPositions(book)
        marks.remove(offset.toString())
        getPreferences(MODE_PRIVATE).edit().putStringSet(bookmarkKey(book), marks).apply()
        refreshBookmarks()
    }

    private fun rulesKey(book: BookRepository.Book) = "rules.${book.id}"

    private fun rulesFor(book: BookRepository.Book): String =
        getPreferences(MODE_PRIVATE).getString(rulesKey(book), "") ?: ""

    private fun parseRules(packed: String): List<RepairRule> {
        if (packed.isEmpty()) return emptyList()
        return packed.split('\u001e').mapNotNull { record ->
            val separator = record.indexOf('\u001f')
            if (separator <= 0) null else RepairRule(record.substring(0, separator), record.substring(separator + 1))
        }
    }

    private fun packRules(rules: List<RepairRule>): String =
        rules.joinToString("\u001e") { "${it.find}\u001f${it.replacement}" }

    private fun refreshRules() {
        val book = currentBook ?: return
        uiState = uiState.copy(repairRules = parseRules(rulesFor(book)))
    }

    private fun saveRules(rules: List<RepairRule>) {
        val book = currentBook ?: return
        getPreferences(MODE_PRIVATE).edit().putString(rulesKey(book), packRules(rules)).apply()
        uiState = uiState.copy(repairRules = rules)
        if (cleanMode) openBook(book, clean = true)
    }

    private fun addRule(find: String, replacement: String) {
        if (find.isBlank()) return showMessage("查找文本不能为空")
        if (find.any { it == '\u001e' || it == '\u001f' } || replacement.any { it == '\u001e' || it == '\u001f' }) {
            return showMessage("规则包含保留控制字符")
        }
        saveRules(uiState.repairRules + RepairRule(find, replacement))
    }

    private fun deleteRule(index: Int) {
        if (index !in uiState.repairRules.indices) return
        saveRules(uiState.repairRules.filterIndexed { i, _ -> i != index })
    }

    private fun clearRules() {
        saveRules(emptyList())
    }

    private fun buildClean(book: BookRepository.Book): File {
        val packed = rulesFor(book)
        val revision = repository.repairRevision(book, packed)
        val output = repository.cleanFile(book, revision)
        if (output.isFile) return output
        ReaderController().use { source ->
            source.open(repository.normalizedFile(book), 0)
            source.exportRules(packed, output)
        }
        return output
    }

    private fun toggleCleanPreview() {
        val book = currentBook ?: return
        uiState = uiState.copy(panel = null)
        openBook(book, clean = !cleanMode)
    }

    private fun exportClean() {
        val book = currentBook ?: return
        runWork(
            label = "正在生成净读文件…",
            task = { buildClean(book) },
            success = { file ->
                pendingExport = file
                exportLauncher.launch("${stripTxt(book.name)}-净读.txt")
            },
            errorTitle = "准备导出失败",
        )
    }

    private fun exportFile(source: File, uri: Uri) {
        runWork(
            label = "正在导出…",
            task = {
                FileInputStream(source).use { input ->
                    contentResolver.openOutputStream(uri, "w").use { output ->
                        if (output == null) throw IOException("目标位置不可写")
                        copy(input, output)
                    }
                }
                true
            },
            success = { showMessage("导出完成") },
            errorTitle = "导出失败",
        )
    }

    private fun copy(input: FileInputStream, output: OutputStream) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        output.flush()
    }

    private fun redecode(encoding: String) {
        val book = currentBook ?: return
        uiState = uiState.copy(panel = null)
        runWork(
            label = if (encoding == BookRepository.AUTO) "正在重新自动识别编码…" else "正在用 $encoding 重新解码…",
            task = { repository.redecode(book, encoding) },
            success = { updated -> openBook(updated, clean = false) },
            errorTitle = "重新解码失败",
        )
    }

    private fun updateSettings(settings: ReaderSettings) {
        val normalized = settings.copy(
            fontSizeSp = settings.fontSizeSp.coerceIn(16f, 34f),
            lineHeightMultiplier = settings.lineHeightMultiplier.coerceIn(1.2f, 2.0f),
            horizontalPaddingDp = settings.horizontalPaddingDp.coerceIn(12f, 48f),
            ttsRate = settings.ttsRate.coerceIn(0.6f, 1.8f),
            ttsPitch = settings.ttsPitch.coerceIn(0.7f, 1.4f),
            autoPageDelayMs = settings.autoPageDelayMs.coerceIn(2500L, 15000L),
        )
        readerPreferences.save(normalized)
        tts.setRate(normalized.ttsRate)
        tts.setPitch(normalized.ttsPitch)
        uiState = uiState.copy(settings = normalized)
    }

    private fun toggleTts() {
        if (currentBook == null || uiState.busyLabel != null) return
        if (uiState.ttsPlaying) {
            stopTts()
            return
        }
        stopAutoPaging()
        uiState = uiState.copy(ttsPlaying = true)
        tts.start(reader, reader.position(), object : TtsController.Listener {
            override fun onPosition(offset: Long) {
                if (currentBook == null || uiState.busyLabel != null) return
                pageHistory.clear()
                reader.jump(offset)
                render()
            }

            override fun onStopped(reason: String?) {
                uiState = uiState.copy(ttsPlaying = false)
                if (reason != null && reason != "end") showMessage(ttsReason(reason))
            }
        })
    }

    private fun stopTts() {
        if (::tts.isInitialized) tts.stop(null)
        uiState = uiState.copy(ttsPlaying = false)
    }

    private fun toggleAutoPaging() {
        if (currentBook == null || uiState.busyLabel != null) return
        if (uiState.autoPaging) {
            stopAutoPaging()
        } else {
            stopTts()
            uiState = uiState.copy(autoPaging = true)
            main.postDelayed(autoStep, uiState.settings.autoPageDelayMs)
        }
    }

    private fun stopAutoPaging() {
        main.removeCallbacks(autoStep)
        if (uiState.autoPaging) uiState = uiState.copy(autoPaging = false)
    }

    private fun setSleepTimer(minutes: Int) {
        main.removeCallbacksAndMessages(SLEEP_TOKEN)
        uiState = uiState.copy(sleepMinutes = minutes)
        if (minutes > 0) {
            main.postAtTime({
                stopAutoPaging()
                tts.stop("sleep")
                uiState = uiState.copy(ttsPlaying = false, sleepMinutes = 0, message = "睡眠定时结束")
            }, SLEEP_TOKEN, SystemClock.uptimeMillis() + minutes * 60_000L)
        }
    }

    private fun deleteCurrentBook() {
        val book = currentBook ?: return
        uiState = uiState.copy(deleteConfirmation = false)
        stopAutoPaging()
        stopTts()
        workGeneration.incrementAndGet()
        reader.close()
        repository.delete(book)
        clearBookPreferences(book)
        currentBook = null
        cleanMode = false
        pageHistory.clear()
        refreshLibrary()
        uiState = uiState.copy(
            screen = AppScreen.LIBRARY,
            currentBook = null,
            pageText = "",
            position = 0,
            length = 0,
            cleanMode = false,
            panel = null,
            chaptersLoaded = false,
            message = "已从净读书架移除",
        )
    }

    private fun deleteLibraryBook(id: String) {
        val book = findBook(id) ?: return
        if (currentBook?.id == id) {
            deleteCurrentBook()
            return
        }
        repository.delete(book)
        clearBookPreferences(book)
        refreshLibrary()
        showMessage("已从净读书架移除")
    }

    private fun clearBookPreferences(book: BookRepository.Book) {
        val preferences = getPreferences(MODE_PRIVATE)
        val editor = preferences.edit().remove(rulesKey(book))
        preferences.all.keys
            .filter { it.startsWith("bookmarks.${book.id}.") || it == "bookmarks.${book.id}" }
            .forEach(editor::remove)
        editor.apply()
    }

    private fun <T> runWork(
        label: String,
        task: Callable<T>,
        success: (T) -> Unit,
        errorTitle: String,
    ) {
        if (uiState.busyLabel != null) return
        val token = workGeneration.incrementAndGet()
        uiState = uiState.copy(busyLabel = label)
        workers.execute {
            try {
                val result = task.call()
                main.post {
                    if (isDestroyed || token != workGeneration.get()) return@post
                    uiState = uiState.copy(busyLabel = null)
                    success(result)
                }
            } catch (error: Throwable) {
                main.post {
                    if (isDestroyed || token != workGeneration.get()) return@post
                    uiState = uiState.copy(busyLabel = null, message = friendlyError(errorTitle, error))
                }
            }
        }
    }

    private fun showMessage(message: String) {
        uiState = uiState.copy(message = message)
    }

    private fun friendlyError(title: String, error: Throwable): String {
        val detail = error.message?.takeIf { it.isNotBlank() }
        return if (detail == null) title else "$title：$detail"
    }

    private fun ttsReason(reason: String): String = when {
        reason == "audio focus" -> "朗读已停止：其他应用正在使用音频。"
        reason == "sleep" -> "睡眠定时结束"
        reason.startsWith("tts error") -> "系统朗读引擎出现错误，请稍后重试。"
        reason == "TTS engine not ready" -> "系统朗读引擎尚未就绪，请稍后重试。"
        reason == "audio focus denied" -> "暂时无法获取音频焦点。"
        else -> reason
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (uiState.screen == AppScreen.READER && uiState.busyLabel == null) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                navigateNext(userInitiated = true)
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                navigatePrevious(userInitiated = true)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        val book = currentBook
        if (book != null && !cleanMode) repository.saveProgress(book, reader.position())
    }

    override fun onDestroy() {
        workGeneration.incrementAndGet()
        main.removeCallbacksAndMessages(null)
        workers.shutdownNow()
        if (::tts.isInitialized) tts.close()
        reader.close()
        super.onDestroy()
    }

    companion object {
        private const val SLEEP_TOKEN = "jingdu-sleep"
        private const val STATE_BOOK_ID = "jingdu.activeBookId"
        private const val STATE_NORMALIZED_SHA = "jingdu.normalizedSha"
        private const val STATE_POSITION = "jingdu.position"
        private const val STATE_CLEAN_MODE = "jingdu.cleanMode"
        private const val STATE_PENDING_EXPORT = "jingdu.pendingExport"
    }
}
