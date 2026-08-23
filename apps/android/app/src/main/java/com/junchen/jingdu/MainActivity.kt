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
import java.io.ByteArrayOutputStream
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
    private val main = Handler(Looper.getMainLooper())
    private val workers: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "jingdu-worker").apply { isDaemon = true }
    }
    private val workGeneration = AtomicLong()
    private val pageHistory = ArrayDeque<Long>()

    private lateinit var repository: BookRepository
    private lateinit var tts: TtsController
    private lateinit var readerPreferences: ReaderPreferences
    private lateinit var ruleLibrary: RuleLibrary
    private lateinit var reviewPrompter: ReviewPrompter
    private lateinit var billing: BillingManager
    @Volatile private var proUnlocked = false
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

    private val ruleImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::importGlobalRulesFromUri)
    }

    private val ruleExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(::exportGlobalRulesToUri)
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
            onAnalyzeSmartClean = ::analyzeSmartClean,
            onToggleNoiseCandidate = ::toggleNoiseCandidate,
            onApplySmartClean = ::applySmartClean,
            onAddGlobalRule = ::addGlobalRule,
            onDeleteGlobalRule = ::deleteGlobalRule,
            onClearGlobalRules = ::clearGlobalRules,
            onInstallRecommendedRules = ::installRecommendedRules,
            onExportGlobalRules = ::exportGlobalRules,
            onImportGlobalRules = ::importGlobalRules,
            onUpgradePro = { billing.purchase() },
            onRestorePro = { billing.restore() },
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
        ruleLibrary = RuleLibrary(this)
        reviewPrompter = ReviewPrompter(this)
        tts = TtsController(this)
        val settings = readerPreferences.load()
        tts.setRate(settings.ttsRate)
        tts.setPitch(settings.ttsPitch)
        uiState = uiState.copy(settings = settings, globalRules = ruleLibrary.load())
        refreshLibrary()

        billing = BillingManager(
            activity = this,
            onState = { state ->
                proUnlocked = state.unlocked
                uiState = uiState.copy(
                    proUnlocked = state.unlocked,
                    proAvailable = state.available,
                    proConnected = state.connected,
                    proPrice = state.price,
                    globalRules = ruleLibrary.load(),
                )
            },
            onMessage = ::showMessage,
        )
        billing.start()

        setContent { JingduApp(uiState, actions) }

        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        } else {
            restoreSession(savedInstanceState)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::billing.isInitialized) billing.start()
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
            globalRules = ruleLibrary.load(),
            noiseCandidates = emptyList(),
            smartCleanAnalyzed = false,
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
                    if (!clean && previousBook?.id != book.id) reviewPrompter.recordBookOpened()
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
            globalRules = ruleLibrary.load(),
            noiseCandidates = emptyList(),
            smartCleanAnalyzed = false,
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

    private fun bookRulesPacked(book: BookRepository.Book): String =
        getPreferences(MODE_PRIVATE).getString(rulesKey(book), "") ?: ""

    private fun bookRules(book: BookRepository.Book): List<RepairRule> = RuleCodec.parse(bookRulesPacked(book))

    private fun effectiveRules(book: BookRepository.Book): List<RepairRule> =
        RuleCodec.combined(bookRules(book), ruleLibrary.load(), proUnlocked)

    private fun effectivePackedRules(book: BookRepository.Book): String = RuleCodec.pack(effectiveRules(book))

    private fun refreshRules() {
        val book = currentBook ?: return
        uiState = uiState.copy(
            repairRules = bookRules(book),
            globalRules = ruleLibrary.load(),
        )
    }

    private fun saveRules(rules: List<RepairRule>, rebuildIfClean: Boolean = true) {
        val book = currentBook ?: return
        val valid = rules.filter(RuleCodec::isValid).distinctBy { Triple(it.mode, it.find, it.replacement) }.take(500)
        getPreferences(MODE_PRIVATE).edit().putString(rulesKey(book), RuleCodec.pack(valid)).apply()
        uiState = uiState.copy(repairRules = valid)
        if (rebuildIfClean && cleanMode) openBook(book, clean = true)
    }

    private fun addRule(mode: RepairRuleMode, find: String, replacement: String) {
        if (mode == RepairRuleMode.LINE_GLOB && !proUnlocked) {
            billing.purchase()
            return
        }
        val rule = RepairRule(find.trim(), replacement, mode)
        if (!RuleCodec.isValid(rule)) return showMessage("规则为空、过长或包含保留控制字符。")
        if (mode == RepairRuleMode.LINE_GLOB && '*' !in rule.find) {
            return showMessage("整行通配规则至少需要一个 *。")
        }
        saveRules(uiState.repairRules + rule)
    }

    private fun deleteRule(index: Int) {
        if (index !in uiState.repairRules.indices) return
        saveRules(uiState.repairRules.filterIndexed { i, _ -> i != index })
    }

    private fun clearRules() {
        saveRules(emptyList())
    }

    private fun analyzeSmartClean() {
        val book = currentBook ?: return
        runWork(
            label = "正在本地扫描重复广告与水印…",
            task = {
                ReaderController().use { source ->
                    source.open(repository.normalizedFile(book), 0)
                    source.noiseCandidates()
                }
            },
            success = { candidates ->
                uiState = uiState.copy(
                    panel = ReaderPanel.CLEAN,
                    smartCleanAnalyzed = true,
                    noiseCandidates = candidates.map {
                        NoiseCandidateModel(
                            score = it.score(),
                            count = it.count(),
                            reason = it.reason(),
                            text = it.text(),
                            selected = it.score() >= 60,
                        )
                    },
                    message = if (candidates.isEmpty()) "没有发现高置信度干扰文本。" else null,
                )
            },
            errorTitle = "智能净读扫描失败",
        )
    }

    private fun toggleNoiseCandidate(index: Int) {
        if (index !in uiState.noiseCandidates.indices) return
        uiState = uiState.copy(
            noiseCandidates = uiState.noiseCandidates.mapIndexed { i, candidate ->
                if (i == index) candidate.copy(selected = !candidate.selected) else candidate
            },
        )
    }

    private fun applySmartClean() {
        val book = currentBook ?: return
        if (!proUnlocked) {
            billing.purchase()
            return
        }
        val selected = uiState.noiseCandidates.filter { it.selected }
        if (selected.isEmpty()) return showMessage("请至少选择一条智能净读建议。")
        val additions = selected.map { RepairRule(it.text, "", RepairRuleMode.LITERAL) }
        val updated = (bookRules(book) + additions)
            .distinctBy { Triple(it.mode, it.find, it.replacement) }
            .take(500)
        saveRules(updated, rebuildIfClean = false)
        reviewPrompter.recordSmartCleanApplied()
        uiState = uiState.copy(panel = null)
        openBook(book, clean = true)
    }

    private fun addGlobalRule(mode: RepairRuleMode, find: String, replacement: String) {
        if (!requirePro()) return
        val rule = RepairRule(find.trim(), replacement, mode)
        if (!RuleCodec.isValid(rule)) return showMessage("规则为空、过长或包含保留控制字符。")
        if (mode == RepairRuleMode.LINE_GLOB && '*' !in rule.find) {
            return showMessage("整行通配规则至少需要一个 *。")
        }
        uiState = uiState.copy(globalRules = ruleLibrary.add(rule))
        if (cleanMode) currentBook?.let { openBook(it, clean = true) }
    }

    private fun deleteGlobalRule(index: Int) {
        if (!requirePro()) return
        uiState = uiState.copy(globalRules = ruleLibrary.remove(index))
        if (cleanMode) currentBook?.let { openBook(it, clean = true) }
    }

    private fun clearGlobalRules() {
        if (!requirePro()) return
        ruleLibrary.save(emptyList())
        uiState = uiState.copy(globalRules = emptyList())
        if (cleanMode) currentBook?.let { openBook(it, clean = true) }
    }

    private fun installRecommendedRules() {
        if (!requirePro()) return
        uiState = uiState.copy(globalRules = ruleLibrary.installRecommended())
        showMessage("已加入推荐的中文网文净读规则，可在全局规则中继续调整。")
    }

    private fun exportGlobalRules() {
        if (!requirePro()) return
        ruleExportLauncher.launch("jingdu-global-clean-rules.json")
    }

    private fun importGlobalRules() {
        if (!requirePro()) return
        ruleImportLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
    }

    private fun exportGlobalRulesToUri(uri: Uri) {
        runWork(
            label = "正在导出全局规则…",
            task = {
                val bytes = ruleLibrary.exportJson().toByteArray(Charsets.UTF_8)
                contentResolver.openOutputStream(uri, "w").use { output ->
                    if (output == null) throw IOException("目标位置不可写")
                    output.write(bytes)
                    output.flush()
                }
                true
            },
            success = { showMessage("全局规则已导出") },
            errorTitle = "规则导出失败",
        )
    }

    private fun importGlobalRulesFromUri(uri: Uri) {
        runWork(
            label = "正在导入全局规则…",
            task = { ruleLibrary.importJson(readLimitedUtf8(uri, MAX_RULE_IMPORT_BYTES)) },
            success = { rules ->
                uiState = uiState.copy(globalRules = rules, panel = ReaderPanel.CLEAN)
                showMessage("已导入 ${rules.size} 条全局规则")
            },
            errorTitle = "规则导入失败",
        )
    }

    private fun readLimitedUtf8(uri: Uri, limit: Int): String {
        val input = contentResolver.openInputStream(uri) ?: throw IOException("规则文件不可读")
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > limit) throw IOException("规则文件过大")
                output.write(buffer, 0, count)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }

    private fun requirePro(): Boolean {
        if (proUnlocked) return true
        billing.purchase()
        return false
    }

    private fun buildClean(book: BookRepository.Book): File {
        val packed = effectivePackedRules(book)
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
            success = { updated ->
                reviewPrompter.recordEncodingRescue()
                openBook(updated, clean = false)
            },
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
            repairRules = emptyList(),
            noiseCandidates = emptyList(),
            smartCleanAnalyzed = false,
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
        if (Looper.myLooper() == Looper.getMainLooper()) {
            uiState = uiState.copy(message = message)
        } else {
            main.post { uiState = uiState.copy(message = message) }
        }
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
        if (::billing.isInitialized) billing.close()
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
        private const val MAX_RULE_IMPORT_BYTES = 1024 * 1024

        private fun stripTxt(name: String): String =
            if (name.lowercase().endsWith(".txt")) name.dropLast(4) else name
    }
}
