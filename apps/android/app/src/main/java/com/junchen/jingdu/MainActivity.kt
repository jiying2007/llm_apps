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
import kotlin.math.abs
import kotlin.math.roundToLong

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
    private lateinit var userBackup: UserBackup
    private lateinit var reviewPrompter: ReviewPrompter
    private lateinit var billing: BillingManager
    private lateinit var cleanHistory: CleanHistory
    private lateinit var libraryMetadata: LibraryMetadataStore
    private lateinit var smartCleanFeedback: SmartCleanFeedbackStore
    @Volatile private var proUnlocked = false
    private var reader = ReaderController()
    private var currentBook: BookRepository.Book? = null
    private var cleanMode = false
    private var visiblePageChars = ReaderController.DEFAULT_PAGE_CHARS
    private var pendingExport: File? = null
    private var lastProgressPersistAt = 0L
    private var lastProgressPersistPosition = -1L
    private var uiState by mutableStateOf(AppUiState())

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { importUri(it) } }
    private val batchImportLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) batchImportUris(uris) }
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val source = pendingExport
        pendingExport = null
        if (uri != null && source != null) exportFile(source, uri)
    }
    private val ruleImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::importGlobalRulesFromUri) }
    private val ruleExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(::exportGlobalRulesToUri) }
    private val backupImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::importBackupFromUri) }
    private val backupExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(::exportBackupToUri) }

    private val actions by lazy {
        JingduActions(
            onImport = { importLauncher.launch(arrayOf("text/plain", "text/*", "application/octet-stream")) },
            onBatchImport = { batchImportLauncher.launch(arrayOf("text/plain", "text/*", "application/octet-stream")) },
            onOpenBook = ::openBookById,
            onDeleteLibraryBook = ::deleteLibraryBook,
            onToggleFavorite = ::toggleFavorite,
            onSetBookTags = ::setBookTags,
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
            onSyncTtsPosition = ::syncTtsPosition,
            onAddBookmark = ::addBookmark,
            onDeleteBookmark = ::deleteBookmark,
            onAddRule = ::addRule,
            onDeleteRule = ::deleteRule,
            onClearRules = ::clearRules,
            onAnalyzeSmartClean = ::analyzeSmartClean,
            onToggleNoiseCandidate = ::toggleNoiseCandidate,
            onApplySmartClean = ::applySmartClean,
            onUndoSmartClean = ::undoSmartClean,
            onAddGlobalRule = ::addGlobalRule,
            onDeleteGlobalRule = ::deleteGlobalRule,
            onClearGlobalRules = ::clearGlobalRules,
            onInstallRecommendedRules = ::installRecommendedRules,
            onExportGlobalRules = ::exportGlobalRules,
            onImportGlobalRules = ::importGlobalRules,
            onUpgradePro = { billing.purchase() },
            onRestorePro = { billing.restore() },
            onExportBackup = ::exportBackup,
            onImportBackup = ::importBackup,
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
        userBackup = UserBackup(readerPreferences, ruleLibrary)
        reviewPrompter = ReviewPrompter(this)
        cleanHistory = CleanHistory(this)
        libraryMetadata = LibraryMetadataStore(this)
        smartCleanFeedback = SmartCleanFeedbackStore(this)
        tts = TtsController(this)
        val settings = readerPreferences.load()
        tts.setRate(settings.ttsRate)
        tts.setPitch(settings.ttsPitch)
        tts.setVoiceName(settings.ttsVoiceName)
        uiState = uiState.copy(settings = settings, globalRules = ruleLibrary.load())
        refreshLibrary()

        billing = BillingManager(
            activity = this,
            onState = { state ->
                val wasUnlocked = proUnlocked
                proUnlocked = state.unlocked
                uiState = uiState.copy(
                    proUnlocked = state.unlocked,
                    proAvailable = state.available,
                    proConnected = state.connected,
                    proPrice = state.price,
                    globalRules = ruleLibrary.load(),
                )
                if (!wasUnlocked && state.unlocked) {
                    refreshTtsVoices()
                    if (cleanMode) currentBook?.let { openBook(it, clean = true) }
                }
            },
            onMessage = ::showMessage,
        )
        billing.start()
        setContent { JingduApp(uiState, actions) }
        if (savedInstanceState == null) handleIncomingIntent(intent) else restoreSession(savedInstanceState)
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
        state.getString(STATE_PENDING_EXPORT)?.let { path -> File(path).takeIf(File::isFile)?.let { pendingExport = it } }
        val id = state.getString(STATE_BOOK_ID) ?: return
        val book = findBook(id) ?: return
        val sameRevision = state.getString(STATE_NORMALIZED_SHA) == book.normalizedSha256
        val restoredPosition = if (sameRevision) state.getLong(STATE_POSITION, book.progress) else book.progress
        openBook(book = book, clean = state.getBoolean(STATE_CLEAN_MODE, false), restoredOverride = restoredPosition)
    }

    private fun handleIncomingIntent(intent: Intent?) { incomingUri(intent)?.let { importUri(it) } }

    private fun incomingUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        else -> null
    }

    private fun refreshLibrary() { uiState = uiState.copy(books = repository.list().map(::toCard)) }

    private fun toCard(book: BookRepository.Book): BookCardModel {
        val metadata = libraryMetadata.load(book.id)
        return BookCardModel(
            id = book.id, name = book.name, encoding = book.encoding, sizeBytes = book.size,
            progress = book.progress, charCount = book.charCount, touchedAt = book.touchedAt,
            normalizedSha256 = book.normalizedSha256, favorite = metadata.favorite, tags = metadata.tags,
        )
    }

    private fun findBook(id: String): BookRepository.Book? = repository.list().firstOrNull { it.id == id }

    private fun toggleFavorite(id: String) {
        if (findBook(id) == null) return
        libraryMetadata.toggleFavorite(id)
        refreshLibrary()
    }

    private fun setBookTags(id: String, tags: String) {
        if (findBook(id) == null) return
        libraryMetadata.setTags(id, tags)
        refreshLibrary()
    }

    private fun importUri(uri: Uri) {
        runWork(
            label = getString(R.string.busy_import),
            task = { repository.importUri(uri, BookRepository.AUTO) },
            success = { openBook(it, clean = false) },
            errorTitle = getString(R.string.error_import),
        )
    }

    private fun batchImportUris(uris: List<Uri>) {
        val selected = uris.take(MAX_BATCH_IMPORT_FILES)
        runWork(
            label = getString(R.string.busy_batch_import, selected.size),
            task = {
                var imported = 0
                var failed = 0
                selected.forEach { uri ->
                    try { repository.importUri(uri, BookRepository.AUTO); imported++ } catch (_: Throwable) { failed++ }
                }
                imported to failed
            },
            success = { (imported, failed) ->
                refreshLibrary()
                showMessage(buildString {
                    append(getString(R.string.batch_import_result, imported))
                    if (failed > 0) append(getString(R.string.batch_import_failed_suffix, failed))
                    if (uris.size > selected.size) append(getString(R.string.batch_import_limit_suffix, MAX_BATCH_IMPORT_FILES))
                })
            },
            errorTitle = getString(R.string.error_batch_import),
        )
    }

    private fun openBookById(id: String) {
        val book = findBook(id) ?: return showMessage(getString(R.string.private_copy_missing))
        openBook(book, clean = false)
    }

    private fun openBook(book: BookRepository.Book, clean: Boolean, restoredOverride: Long? = null) {
        if (uiState.busyLabel != null) return
        stopAutoPaging()
        stopTts()
        stopBackgroundTts()

        val previousBook = currentBook
        val previousReader = reader
        val previousClean = cleanMode
        val previousPosition = previousBook?.let { previousReader.position() } ?: 0L
        val restored = restoredOverride ?: if (clean) 0L else if (
            previousBook != null && previousBook.id == book.id &&
            previousBook.normalizedSha256 == book.normalizedSha256 && !previousClean
        ) previousPosition else book.progress

        if (previousBook != null && !previousClean) repository.saveProgress(previousBook, previousPosition)
        val token = workGeneration.incrementAndGet()
        uiState = uiState.copy(
            screen = AppScreen.READER,
            busyLabel = getString(if (clean) R.string.busy_clean_preview else R.string.busy_open),
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
            smartCleanUndoAvailable = cleanHistory.has(book.id),
        )

        workers.execute {
            val candidate = ReaderController()
            try {
                val file = if (clean) buildClean(book) else repository.normalizedFile(book)
                candidate.open(file, restored)
                if (token != workGeneration.get() || isDestroyed) { candidate.close(); return@execute }
                main.post {
                    if (token != workGeneration.get() || isDestroyed) { candidate.close(); return@post }
                    reader = candidate
                    currentBook = book
                    cleanMode = clean
                    pageHistory.clear()
                    lastProgressPersistAt = 0L
                    lastProgressPersistPosition = candidate.position()
                    repository.updateCharCount(book, candidate.length())
                    repository.pruneDocumentRevisions(book)
                    repository.pruneCleanRevisions(book, if (clean) file else null)
                    NativeIndexCache.pruneOrphans(repository.normalizedFile(book).parentFile)
                    previousReader.close()
                    uiState = uiState.copy(busyLabel = null, currentBook = toCard(book), cleanMode = clean, smartCleanUndoAvailable = cleanHistory.has(book.id))
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
                        message = friendlyError(getString(if (clean) R.string.error_clean else R.string.error_open), error),
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
            if (!cleanMode) persistProgress(book)
            uiState = uiState.copy(
                screen = AppScreen.READER, currentBook = toCard(book), pageText = text,
                position = reader.position(), length = reader.length(), cleanMode = cleanMode,
            )
        } catch (error: Throwable) {
            showMessage(friendlyError(getString(R.string.error_read), error))
        }
    }

    private fun persistProgress(book: BookRepository.Book, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val position = reader.position()
        if (!force && now - lastProgressPersistAt < PROGRESS_SAVE_INTERVAL_MS &&
            abs(position - lastProgressPersistPosition) < PROGRESS_SAVE_CHAR_DELTA) return
        repository.saveProgress(book, position)
        lastProgressPersistAt = now
        lastProgressPersistPosition = position
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
        if (pageHistory.isNotEmpty()) reader.jump(pageHistory.removeLast()) else reader.move(-visiblePageChars.coerceAtLeast(ReaderController.MIN_PAGE_CHARS))
        render()
    }

    private fun seekFraction(fraction: Float) {
        if (uiState.busyLabel != null || currentBook == null || reader.length() <= 0) return
        stopAutoPaging(); stopTts(); pageHistory.clear()
        reader.jump((reader.length().toDouble() * fraction.coerceIn(0f, 1f)).toLong())
        render()
    }

    private fun jumpTo(offset: Long) {
        if (uiState.busyLabel != null || currentBook == null) return
        stopAutoPaging(); stopTts(); pageHistory.clear(); reader.jump(offset)
        uiState = uiState.copy(panel = null)
        render()
    }

    private fun syncTtsPosition(offset: Long) {
        if (uiState.busyLabel != null || currentBook == null || cleanMode || reader.length() <= 0) return
        val bounded = offset.coerceIn(0L, (reader.length() - 1).coerceAtLeast(0L))
        if (bounded == reader.position()) return
        reader.jump(bounded)
        render()
    }

    private fun backToLibrary() {
        uiState.panel?.let { uiState = uiState.copy(panel = null); return }
        val book = currentBook
        if (book != null && !cleanMode) persistProgress(book, force = true)
        stopAutoPaging(); stopTts(); reader.close()
        currentBook = null; cleanMode = false; pageHistory.clear(); refreshLibrary()
        uiState = uiState.copy(
            screen = AppScreen.LIBRARY, currentBook = null, pageText = "", position = 0, length = 0,
            cleanMode = false, panel = null, busyLabel = null, searchQuery = "", searchResults = emptyList(),
            chapters = emptyList(), chaptersLoaded = false, bookmarks = emptyList(), repairRules = emptyList(),
            globalRules = ruleLibrary.load(), noiseCandidates = emptyList(), smartCleanAnalyzed = false,
            smartCleanUndoAvailable = false,
        )
    }

    private fun openPanel(panel: ReaderPanel) {
        if (uiState.busyLabel != null || currentBook == null) return
        uiState = uiState.copy(panel = panel)
        when (panel) {
            ReaderPanel.BOOKMARKS -> refreshBookmarks()
            ReaderPanel.CLEAN -> refreshRules()
            ReaderPanel.SETTINGS -> refreshTtsVoices()
            else -> Unit
        }
    }

    private fun search(query: String) {
        val value = query.trim()
        if (value.isEmpty() || currentBook == null) return
        runWork(
            label = getString(R.string.busy_search),
            task = { reader.search(value) },
            success = { hits ->
                uiState = uiState.copy(
                    panel = ReaderPanel.SEARCH,
                    searchResults = hits.map { SearchResultModel(it.offset(), it.context()) },
                    message = if (hits.isEmpty()) getString(R.string.search_no_result, value) else null,
                )
            },
            errorTitle = getString(R.string.error_search),
        )
    }

    private fun bookmarkKey(book: BookRepository.Book): String = "bookmarks.${book.id}"
    private fun legacyBookmarkKey(book: BookRepository.Book): String = "bookmarks.${book.id}.${book.normalizedSha256}"
    private fun bookmarkPositions(book: BookRepository.Book): MutableSet<String> {
        val preferences = getPreferences(MODE_PRIVATE)
        val current = preferences.getStringSet(bookmarkKey(book), emptySet())?.toMutableSet() ?: mutableSetOf()
        val legacy = preferences.getStringSet(legacyBookmarkKey(book), emptySet()).orEmpty()
        if (legacy.isNotEmpty()) {
            current.addAll(legacy)
            preferences.edit().putStringSet(bookmarkKey(book), current).remove(legacyBookmarkKey(book)).apply()
        }
        return current
    }

    private fun refreshBookmarks() {
        val book = currentBook ?: return
        val length = reader.length().coerceAtLeast(1)
        val models = bookmarkPositions(book).mapNotNull { raw -> raw.toLongOrNull()?.let { offset -> BookmarkModel(offset.coerceIn(0, length - 1), (offset.toDouble() / length.toDouble()).toFloat().coerceIn(0f, 1f)) } }.distinctBy { it.offset }.sortedBy { it.offset }
        uiState = uiState.copy(bookmarks = models)
    }

    private fun addBookmark() {
        val book = currentBook ?: return
        if (cleanMode) return showMessage(getString(R.string.bookmark_clean_blocked))
        val marks = bookmarkPositions(book)
        marks.add(reader.position().toString())
        getPreferences(MODE_PRIVATE).edit().putStringSet(bookmarkKey(book), marks).apply()
        refreshBookmarks()
        showMessage(getString(R.string.bookmark_added))
    }

    private fun deleteBookmark(offset: Long) {
        val book = currentBook ?: return
        val marks = bookmarkPositions(book)
        marks.remove(offset.toString())
        getPreferences(MODE_PRIVATE).edit().putStringSet(bookmarkKey(book), marks).apply()
        refreshBookmarks()
    }

    private fun rulesKey(book: BookRepository.Book) = "rules.${book.id}"
    private fun bookRulesPacked(book: BookRepository.Book): String = getPreferences(MODE_PRIVATE).getString(rulesKey(book), "") ?: ""
    private fun bookRules(book: BookRepository.Book): List<RepairRule> = RuleCodec.parse(bookRulesPacked(book))
    private fun effectiveRules(book: BookRepository.Book): List<RepairRule> = RuleCodec.combined(bookRules(book), ruleLibrary.load(), proUnlocked)
    private fun effectivePackedRules(book: BookRepository.Book): String = RuleCodec.pack(effectiveRules(book))

    private fun refreshRules() {
        val book = currentBook ?: return
        uiState = uiState.copy(repairRules = bookRules(book), globalRules = ruleLibrary.load(), smartCleanUndoAvailable = cleanHistory.has(book.id))
    }

    private fun saveRules(rules: List<RepairRule>, rebuildIfClean: Boolean = true, preserveSmartCleanUndo: Boolean = false) {
        val book = currentBook ?: return
        val valid = rules.filter(RuleCodec::isValid).distinctBy { Triple(it.mode, it.find, it.replacement) }.take(500)
        getPreferences(MODE_PRIVATE).edit().putString(rulesKey(book), RuleCodec.pack(valid)).apply()
        if (!preserveSmartCleanUndo) cleanHistory.clear(book.id)
        uiState = uiState.copy(repairRules = valid, smartCleanUndoAvailable = preserveSmartCleanUndo && cleanHistory.has(book.id))
        if (rebuildIfClean && cleanMode) openBook(book, clean = true)
    }

    private fun addRule(mode: RepairRuleMode, find: String, replacement: String) {
        if (mode == RepairRuleMode.LINE_GLOB && !proUnlocked) { billing.purchase(); return }
        val rule = RepairRule(find.trim(), replacement, mode)
        if (!RuleCodec.isValid(rule)) return showMessage(getString(R.string.invalid_rule))
        if (mode == RepairRuleMode.LINE_GLOB && '*' !in rule.find) return showMessage(getString(R.string.glob_requires_star))
        saveRules(uiState.repairRules + rule)
    }

    private fun deleteRule(index: Int) {
        if (index !in uiState.repairRules.indices) return
        saveRules(uiState.repairRules.filterIndexed { i, _ -> i != index })
    }

    private fun clearRules() { saveRules(emptyList()) }

    private fun analyzeSmartClean() {
        val book = currentBook ?: return
        runWork(
            label = getString(R.string.busy_smart_clean),
            task = { ReaderController().use { source -> source.open(repository.normalizedFile(book), 0); source.noiseCandidates() } },
            success = { candidates ->
                uiState = uiState.copy(
                    panel = ReaderPanel.CLEAN,
                    smartCleanAnalyzed = true,
                    smartCleanUndoAvailable = cleanHistory.has(book.id),
                    noiseCandidates = candidates.map { candidate -> smartCleanModel(book.id, candidate) },
                    message = if (candidates.isEmpty()) getString(R.string.smart_clean_empty) else null,
                )
            },
            errorTitle = getString(R.string.error_smart_clean),
        )
    }

    private fun smartCleanModel(bookId: String, candidate: ReaderController.NoiseCandidate): NoiseCandidateModel {
        val semantic = TinyLocalSemanticCandidateClassifier.classifyCandidate(candidate.text())
        val feedback = smartCleanFeedback.decision(bookId, candidate.reason(), candidate.text())
        val semanticDelta = when (semantic.label) {
            SemanticCandidateLabel.AD -> if (semantic.confidence >= 0.65f) 8 else 0
            SemanticCandidateLabel.BODY -> if (semantic.confidence >= 0.65f) -16 else 0
            SemanticCandidateLabel.UNCERTAIN -> 0
        }
        val adjustedScore = (candidate.score() + smartCleanFeedback.modelDelta(candidate.reason(), candidate.text()) + semanticDelta).coerceIn(0, 100)
        val model = NoiseCandidateModel(
            score = adjustedScore,
            count = candidate.count(),
            reason = candidate.reason(),
            text = candidate.text(),
            semanticLabel = semantic.label,
            semanticConfidence = semantic.confidence,
            semanticScore = semantic.score,
            feedback = feedback,
        )
        return model.copy(selected = model.defaultSafeSelection)
    }

    private fun toggleNoiseCandidate(index: Int) {
        if (index !in uiState.noiseCandidates.indices) return
        uiState = uiState.copy(noiseCandidates = uiState.noiseCandidates.mapIndexed { i, candidate -> if (i == index) candidate.copy(selected = !candidate.selected) else candidate })
    }

    private fun applySmartClean() {
        val book = currentBook ?: return
        if (!proUnlocked) { billing.purchase(); return }
        val selected = uiState.noiseCandidates.filter { it.selected }
        if (selected.isEmpty()) return showMessage(getString(R.string.select_clean_suggestion))
        cleanHistory.save(book.id, bookRulesPacked(book))
        selected.forEach { candidate -> smartCleanFeedback.record(book.id, candidate.reason, candidate.text, SmartCleanFeedback.DELETE) }
        val additions = selected.map { RepairRule(it.text, "", RepairRuleMode.LITERAL) }
        val updated = (bookRules(book) + additions).distinctBy { Triple(it.mode, it.find, it.replacement) }.take(500)
        saveRules(updated, rebuildIfClean = false, preserveSmartCleanUndo = true)
        reviewPrompter.recordSmartCleanApplied()
        uiState = uiState.copy(panel = null, smartCleanUndoAvailable = true)
        openBook(book, clean = true)
    }

    private fun undoSmartClean() {
        val book = currentBook ?: return
        val snapshot = cleanHistory.peek(book.id) ?: return
        val restored = RuleCodec.parse(snapshot)
        cleanHistory.clear(book.id)
        saveRules(restored, rebuildIfClean = false, preserveSmartCleanUndo = true)
        uiState = uiState.copy(panel = null, smartCleanUndoAvailable = false)
        openBook(book, clean = true)
        showMessage(getString(R.string.smart_clean_undone))
    }

    private fun addGlobalRule(mode: RepairRuleMode, find: String, replacement: String) {
        if (!requirePro()) return
        val rule = RepairRule(find.trim(), replacement, mode)
        if (!RuleCodec.isValid(rule)) return showMessage(getString(R.string.invalid_rule))
        if (mode == RepairRuleMode.LINE_GLOB && '*' !in rule.find) return showMessage(getString(R.string.glob_requires_star))
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
        showMessage(getString(R.string.recommended_rules_added))
    }

    private fun exportGlobalRules() { if (requirePro()) ruleExportLauncher.launch("jingdu-global-clean-rules.json") }
    private fun importGlobalRules() { if (requirePro()) ruleImportLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) }

    private fun exportGlobalRulesToUri(uri: Uri) {
        runWork(
            label = getString(R.string.busy_export_rules),
            task = { writeUtf8(uri, ruleLibrary.exportJson()); true },
            success = { showMessage(getString(R.string.rules_exported)) },
            errorTitle = getString(R.string.error_rule_export),
        )
    }

    private fun importGlobalRulesFromUri(uri: Uri) {
        runWork(
            label = getString(R.string.busy_import_rules),
            task = { ruleLibrary.importJson(readLimitedUtf8(uri, MAX_RULE_IMPORT_BYTES)) },
            success = { rules -> uiState = uiState.copy(globalRules = rules, panel = ReaderPanel.CLEAN); showMessage(getString(R.string.rules_imported, rules.size)) },
            errorTitle = getString(R.string.error_rule_import),
        )
    }

    private fun exportBackup() { if (requirePro()) backupExportLauncher.launch("jingdu-local-settings-rules-backup.json") }
    private fun importBackup() { if (requirePro()) backupImportLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) }

    private fun exportBackupToUri(uri: Uri) {
        runWork(
            label = getString(R.string.busy_export_backup),
            task = { writeUtf8(uri, userBackup.exportJson()); true },
            success = { showMessage(getString(R.string.backup_exported)) },
            errorTitle = getString(R.string.error_backup_export),
        )
    }

    private fun importBackupFromUri(uri: Uri) {
        runWork(
            label = getString(R.string.busy_restore_backup),
            task = { userBackup.importJson(readLimitedUtf8(uri, MAX_BACKUP_BYTES)) },
            success = { result ->
                tts.setRate(result.settings.ttsRate); tts.setPitch(result.settings.ttsPitch); tts.setVoiceName(result.settings.ttsVoiceName)
                uiState = uiState.copy(settings = result.settings, globalRules = result.globalRules, panel = ReaderPanel.SETTINGS)
                refreshTtsVoices()
                showMessage(getString(R.string.backup_restored, result.globalRules.size))
            },
            errorTitle = getString(R.string.error_backup_restore),
        )
    }

    private fun readLimitedUtf8(uri: Uri, limit: Int): String {
        val input = contentResolver.openInputStream(uri) ?: throw IOException(getString(R.string.file_unreadable))
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > limit) throw IOException(getString(R.string.file_too_large))
                output.write(buffer, 0, count)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }

    private fun writeUtf8(uri: Uri, text: String) {
        contentResolver.openOutputStream(uri, "w").use { output ->
            if (output == null) throw IOException(getString(R.string.destination_unwritable))
            output.write(text.toByteArray(Charsets.UTF_8)); output.flush()
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
        ReaderController().use { source -> source.open(repository.normalizedFile(book), 0); source.exportRules(packed, output) }
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
            label = getString(R.string.busy_generate_clean),
            task = { buildClean(book) },
            success = { file -> pendingExport = file; exportLauncher.launch("${stripTxt(book.name)}-${getString(R.string.clean)}.txt") },
            errorTitle = getString(R.string.error_prepare_export),
        )
    }

    private fun exportFile(source: File, uri: Uri) {
        runWork(
            label = getString(R.string.busy_export),
            task = {
                FileInputStream(source).use { input ->
                    contentResolver.openOutputStream(uri, "w").use { output ->
                        if (output == null) throw IOException(getString(R.string.destination_unwritable))
                        copy(input, output)
                    }
                }
                true
            },
            success = { showMessage(getString(R.string.export_complete)) },
            errorTitle = getString(R.string.error_export),
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
        val oldPosition = reader.position()
        val oldLength = reader.length()
        val oldBookmarks = bookmarkPositions(book).mapNotNull(String::toLongOrNull)
        uiState = uiState.copy(panel = null)
        runWork(
            label = if (encoding == BookRepository.AUTO) getString(R.string.busy_redecode_auto) else getString(R.string.busy_redecode, encoding),
            task = {
                val updated = repository.redecode(book, encoding)
                val newLength = ReaderController().use { candidate ->
                    candidate.open(repository.normalizedFile(updated), 0)
                    candidate.length()
                }
                updated to newLength
            },
            success = { (updated, newLength) ->
                val mappedProgress = mapOffset(oldPosition, oldLength, newLength)
                val mappedBookmarks = oldBookmarks
                    .map { offset -> mapOffset(offset, oldLength, newLength).toString() }
                    .toSet()
                getPreferences(MODE_PRIVATE).edit().putStringSet(bookmarkKey(updated), mappedBookmarks).apply()
                repository.saveProgress(updated, mappedProgress)
                repository.updateCharCount(updated, newLength)
                reviewPrompter.recordEncodingRescue()
                openBook(updated, clean = false, restoredOverride = mappedProgress)
            },
            errorTitle = getString(R.string.error_redecode),
        )
    }

    private fun mapOffset(value: Long, oldLength: Long, newLength: Long): Long {
        if (newLength <= 1) return 0
        if (oldLength <= 1) return value.coerceIn(0, newLength - 1)
        val fraction = value.coerceIn(0, oldLength - 1).toDouble() / (oldLength - 1).toDouble()
        return (fraction * (newLength - 1).toDouble()).roundToLong().coerceIn(0, newLength - 1)
    }

    private fun refreshTtsVoices() { uiState = uiState.copy(ttsVoices = tts.offlineVoices().map { TtsVoiceModel(it.name(), it.label()) }) }

    private fun updateSettings(settings: ReaderSettings) {
        if (settings.ttsVoiceName != uiState.settings.ttsVoiceName && !proUnlocked) { billing.purchase(); return }
        val normalized = settings.copy(
            fontSizeSp = settings.fontSizeSp.coerceIn(16f, 34f), lineHeightMultiplier = settings.lineHeightMultiplier.coerceIn(1.2f, 2.0f),
            horizontalPaddingDp = settings.horizontalPaddingDp.coerceIn(12f, 48f), ttsRate = settings.ttsRate.coerceIn(0.6f, 1.8f),
            ttsPitch = settings.ttsPitch.coerceIn(0.7f, 1.4f), ttsVoiceName = settings.ttsVoiceName.take(256),
            autoPageDelayMs = settings.autoPageDelayMs.coerceIn(2500L, 15000L),
        )
        readerPreferences.save(normalized)
        tts.setRate(normalized.ttsRate); tts.setPitch(normalized.ttsPitch); tts.setVoiceName(normalized.ttsVoiceName)
        uiState = uiState.copy(settings = normalized)
    }

    private fun toggleTts() {
        if (currentBook == null || uiState.busyLabel != null) return
        if (uiState.ttsPlaying) { stopTts(); return }
        stopAutoPaging()
        uiState = uiState.copy(ttsPlaying = true)
        tts.start(reader, reader.position(), object : TtsController.Listener {
            override fun onPosition(offset: Long) {
                if (currentBook == null || uiState.busyLabel != null) return
                pageHistory.clear(); reader.jump(offset); render()
            }
            override fun onPaused() { uiState = uiState.copy(ttsPlaying = false) }
            override fun onResumed() { uiState = uiState.copy(ttsPlaying = true) }
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

    private fun stopBackgroundTts() {
        runCatching { startService(Intent(this, TtsPlaybackService::class.java).setAction(TtsPlaybackService.ACTION_STOP)) }
    }

    private fun toggleAutoPaging() {
        if (currentBook == null || uiState.busyLabel != null) return
        if (uiState.autoPaging) stopAutoPaging() else {
            stopTts(); stopBackgroundTts(); uiState = uiState.copy(autoPaging = true); main.postDelayed(autoStep, uiState.settings.autoPageDelayMs)
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
                stopAutoPaging(); tts.stop("sleep")
                uiState = uiState.copy(ttsPlaying = false, sleepMinutes = 0, message = getString(R.string.sleep_timer_finished))
            }, SLEEP_TOKEN, SystemClock.uptimeMillis() + minutes * 60_000L)
        }
    }

    private fun deleteCurrentBook() {
        val book = currentBook ?: return
        uiState = uiState.copy(deleteConfirmation = false)
        stopAutoPaging(); stopTts(); stopBackgroundTts(); workGeneration.incrementAndGet(); reader.close()
        repository.delete(book)
        clearBookPreferences(book)
        libraryMetadata.clear(book.id)
        cleanHistory.clearAllForBook(book.id)
        smartCleanFeedback.clearBook(book.id)
        TocOverrideStore(this).reset(book.id)
        currentBook = null; cleanMode = false; pageHistory.clear(); refreshLibrary()
        uiState = uiState.copy(
            screen = AppScreen.LIBRARY, currentBook = null, pageText = "", position = 0, length = 0,
            cleanMode = false, panel = null, chaptersLoaded = false, repairRules = emptyList(),
            noiseCandidates = emptyList(), smartCleanAnalyzed = false, smartCleanUndoAvailable = false, message = getString(R.string.removed_from_library),
        )
    }

    private fun deleteLibraryBook(id: String) {
        val book = findBook(id) ?: return
        if (currentBook?.id == id) { deleteCurrentBook(); return }
        repository.delete(book)
        clearBookPreferences(book)
        libraryMetadata.clear(book.id)
        cleanHistory.clearAllForBook(book.id)
        smartCleanFeedback.clearBook(book.id)
        TocOverrideStore(this).reset(book.id)
        refreshLibrary()
        showMessage(getString(R.string.removed_from_library))
    }

    private fun clearBookPreferences(book: BookRepository.Book) {
        val preferences = getPreferences(MODE_PRIVATE)
        val editor = preferences.edit().remove(rulesKey(book)).remove(bookmarkKey(book))
        preferences.all.keys.filter { it.startsWith("bookmarks.${book.id}.") || it == "bookmarks.${book.id}" }.forEach(editor::remove)
        editor.apply()
    }

    private fun <T> runWork(label: String, task: Callable<T>, success: (T) -> Unit, errorTitle: String) {
        if (uiState.busyLabel != null) return
        val token = workGeneration.incrementAndGet()
        uiState = uiState.copy(busyLabel = label)
        workers.execute {
            try {
                val result = task.call()
                main.post {
                    if (isDestroyed || token != workGeneration.get()) return@post
                    uiState = uiState.copy(busyLabel = null); success(result)
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
        if (Looper.myLooper() == Looper.getMainLooper()) uiState = uiState.copy(message = message)
        else main.post { uiState = uiState.copy(message = message) }
    }

    private fun friendlyError(title: String, error: Throwable): String {
        val raw = error.message?.takeIf { it.isNotBlank() }
        val detail = when (raw) {
            "unsupported_rule_file", "rule_limit_exceeded", "no_valid_rules" -> getString(R.string.invalid_rule)
            else -> raw
        }
        return if (detail == null) title else "$title: $detail"
    }

    private fun ttsReason(reason: String): String = when {
        reason == "audio focus" -> getString(R.string.tts_audio_focus)
        reason == "sleep" -> getString(R.string.sleep_timer_finished)
        reason.startsWith("tts error") -> getString(R.string.tts_engine_error)
        reason == "TTS engine not ready" -> getString(R.string.tts_engine_not_ready)
        reason == "audio focus denied" -> getString(R.string.tts_audio_focus_denied)
        else -> reason
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (uiState.screen == AppScreen.READER && uiState.busyLabel == null) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) { navigateNext(userInitiated = true); return true }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) { navigatePrevious(userInitiated = true); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        val book = currentBook
        if (book != null && !cleanMode) persistProgress(book, force = true)
    }

    override fun onDestroy() {
        workGeneration.incrementAndGet(); main.removeCallbacksAndMessages(null); workers.shutdownNow()
        if (::billing.isInitialized) billing.close()
        if (::tts.isInitialized) tts.close()
        reader.close(); super.onDestroy()
    }

    companion object {
        private const val SLEEP_TOKEN = "jingdu-sleep"
        private const val STATE_BOOK_ID = "jingdu.activeBookId"
        private const val STATE_NORMALIZED_SHA = "jingdu.normalizedSha"
        private const val STATE_POSITION = "jingdu.position"
        private const val STATE_CLEAN_MODE = "jingdu.cleanMode"
        private const val STATE_PENDING_EXPORT = "jingdu.pendingExport"
        private const val MAX_RULE_IMPORT_BYTES = 1024 * 1024
        private const val MAX_BACKUP_BYTES = 2 * 1024 * 1024
        private const val MAX_BATCH_IMPORT_FILES = 100
        private const val PROGRESS_SAVE_INTERVAL_MS = 2_000L
        private const val PROGRESS_SAVE_CHAR_DELTA = 4_096L
        private fun stripTxt(name: String): String = if (name.lowercase().endsWith(".txt")) name.dropLast(4) else name
    }
}
