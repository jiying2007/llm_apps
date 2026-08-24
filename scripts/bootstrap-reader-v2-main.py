#!/usr/bin/env python3
from pathlib import Path
import re

path = Path('apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt')
text = path.read_text(encoding='utf-8')


def once(old: str, new: str, label: str):
    global text
    if old not in text:
        raise SystemExit(f'missing marker: {label}')
    text = text.replace(old, new, 1)


def between(start: str, end: str, new: str, label: str):
    global text
    a = text.find(start)
    b = text.find(end, a + len(start))
    if a < 0 or b < 0:
        raise SystemExit(f'missing range: {label}')
    text = text[:a] + new + text[b:]

once('import android.content.Intent\n', 'import android.content.BroadcastReceiver\nimport android.content.Intent\nimport android.content.IntentFilter\n', 'android receiver imports')
once('import androidx.activity.result.contract.ActivityResultContracts\n', 'import androidx.activity.result.contract.ActivityResultContracts\nimport androidx.core.content.ContextCompat\n', 'context compat import')
text = text.replace('import java.util.ArrayDeque\n', '')

once(
'''    private val workGeneration = AtomicLong()
    private val pageHistory = ArrayDeque<Long>()

    private lateinit var repository: BookRepository
    private lateinit var tts: TtsController
    private lateinit var readerPreferences: ReaderPreferences
''',
'''    private val workGeneration = AtomicLong()
    private val session = ReaderSession()
    private val motionController = ReaderMotionController()

    private lateinit var repository: BookRepository
    private lateinit var ttsCatalog: TtsController
    private lateinit var readerPreferences: ReaderPreferences
''',
'core fields')

once(
'''    private lateinit var smartCleanFeedback: SmartCleanFeedbackStore
    @Volatile private var proUnlocked = false
    private var reader = ReaderController()
    private var currentBook: BookRepository.Book? = null
    private var cleanMode = false
    private var visiblePageChars = ReaderController.DEFAULT_PAGE_CHARS
''',
'''    private lateinit var smartCleanFeedback: SmartCleanFeedbackStore
    private lateinit var annotationStore: ReaderAnnotationStore
    private lateinit var fontStore: ReaderFontStore
    private lateinit var statsStore: ReaderStatsStore
    @Volatile private var proUnlocked = false
    private var reader: ReaderController
        get() = session.reader
        set(value) { session.reader = value }
    private var currentBook: BookRepository.Book?
        get() = session.book
        set(value) { session.book = value }
    private var cleanMode: Boolean
        get() = session.cleanMode
        set(value) { session.cleanMode = value }
    private var visiblePageChars: Long
        get() = session.visiblePageChars
        set(value) { session.visiblePageChars = value }
    private val pageHistory get() = session.pageHistory
''',
'session delegates')

once(
'''    private val backupImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::importBackupFromUri) }
    private val backupExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(::exportBackupToUri) }

    private val actions by lazy {
''',
'''    private val backupImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::importBackupFromUri) }
    private val backupExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(::exportBackupToUri) }
    private val fontImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runWork(
            label = getString(R.string.busy_import),
            task = { fontStore.import(uri, this) },
            success = { id -> updateSettings(uiState.settings.copy(typeface = ReaderTypeface.CUSTOM, customFontId = id, preset = ReaderPreset.CUSTOM)); showMessage(getString(R.string.reader_font_imported)) },
            errorTitle = getString(R.string.reader_import_font),
        )
    }

    private val ttsStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action != TtsPlaybackService.ACTION_STATE) return
            val active = intent.getBooleanExtra(TtsPlaybackService.EXTRA_ACTIVE, false)
            val playing = intent.getBooleanExtra(TtsPlaybackService.EXTRA_PLAYING, false)
            val offset = intent.getLongExtra(TtsPlaybackService.EXTRA_OFFSET, -1L)
            val nextOffset = intent.getLongExtra(TtsPlaybackService.EXTRA_NEXT_OFFSET, -1L)
            val rangeStart = intent.getLongExtra(TtsPlaybackService.EXTRA_RANGE_START, -1L)
            val rangeEnd = intent.getLongExtra(TtsPlaybackService.EXTRA_RANGE_END, -1L)
            val reason = intent.getStringExtra(TtsPlaybackService.EXTRA_REASON)
            ReaderInteractionRuntime.backgroundTtsPlaying = playing
            if (active) motionController.start(ReaderMotionState.TTS)
            else if (motionController.state == ReaderMotionState.TTS) motionController.stop()
            uiState = uiState.copy(
                motion = motionController.state,
                tts = TtsPlaybackModel(active, playing, offset, nextOffset, rangeStart, rangeEnd, reason),
            )
            if (active && offset >= 0 && uiState.screen == AppScreen.READER && !cleanMode) syncTtsPosition(offset)
            if (reason != null && reason !in setOf("paused", "focus-paused", "user", "stopped", "destroyed", "end")) showMessage(ttsReason(reason))
        }
    }

    private val actions by lazy {
''',
'launchers and tts receiver')

once(
'''            onJump = ::jumpTo,
            onSyncTtsPosition = ::syncTtsPosition,
            onAddBookmark = ::addBookmark,
            onDeleteBookmark = ::deleteBookmark,
''',
'''            onJump = ::jumpTo,
            onSyncTtsPosition = ::syncTtsPosition,
            onEnsureChapters = ::ensureChapters,
            onAddBookmark = ::addBookmark,
            onDeleteBookmark = ::deleteBookmark,
            onAddAnnotation = ::addAnnotation,
            onDeleteAnnotation = ::deleteAnnotation,
            onImportFont = { fontImportLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream")) },
''',
'new actions')

once(
'''    private val autoStep = object : Runnable {
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
''',
'''    private val autoStep = object : Runnable {
        override fun run() {
            if (!motionController.isActive(ReaderMotionState.AUTO_PAGE) || uiState.busyLabel != null || currentBook == null) return
            if (reader.position() >= (reader.length() - 1).coerceAtLeast(0)) {
                stopAutoPaging()
                return
            }
            navigateNext(userInitiated = false)
            val delay = motionController.adaptivePageDelayMs(visiblePageChars, statsStore.charsPerMinute(), uiState.settings)
            main.postDelayed(this, delay)
        }
    }
''',
'auto page runnable')

once(
'''        smartCleanFeedback = SmartCleanFeedbackStore(this)
        tts = TtsController(this)
        val settings = readerPreferences.load()
        tts.setRate(settings.ttsRate)
        tts.setPitch(settings.ttsPitch)
        tts.setVoiceName(settings.ttsVoiceName)
        uiState = uiState.copy(settings = settings, globalRules = ruleLibrary.load())
        refreshLibrary()

        billing = BillingManager(
''',
'''        smartCleanFeedback = SmartCleanFeedbackStore(this)
        annotationStore = ReaderAnnotationStore(this)
        fontStore = ReaderFontStore(this)
        statsStore = ReaderStatsStore(this)
        ttsCatalog = TtsController(this)
        userBackup = UserBackup(readerPreferences, ruleLibrary, annotationStore)
        uiState = uiState.copy(globalRules = ruleLibrary.load())
        refreshLibrary()
        ContextCompat.registerReceiver(this, ttsStateReceiver, IntentFilter(TtsPlaybackService.ACTION_STATE), ContextCompat.RECEIVER_NOT_EXPORTED)

        billing = BillingManager(
''',
'onCreate stores')
# The original userBackup assignment now precedes this block; remove it.
text = text.replace('        userBackup = UserBackup(readerPreferences, ruleLibrary)\n', '')

once(
'''        billing.start()
        setContent { JingduApp(uiState, actions) }
        if (savedInstanceState == null) handleIncomingIntent(intent) else restoreSession(savedInstanceState)
''',
'''        billing.start()
        setContent { JingduApp(uiState, actions) }
        workers.execute {
            val settings = readerPreferences.load()
            main.post {
                if (isDestroyed) return@post
                ttsCatalog.setRate(settings.ttsRate); ttsCatalog.setPitch(settings.ttsPitch); ttsCatalog.setVoiceName(settings.ttsVoiceName)
                uiState = uiState.copy(settings = settings)
                refreshTtsVoices()
            }
        }
        startService(Intent(this, TtsPlaybackService::class.java).setAction(TtsPlaybackService.ACTION_STATE))
        if (savedInstanceState == null) handleIncomingIntent(intent) else restoreSession(savedInstanceState)
''',
'onCreate async settings')

once(
'''    override fun onResume() {
        super.onResume()
        if (::billing.isInitialized) billing.start()
    }
''',
'''    override fun onResume() {
        super.onResume()
        if (::billing.isInitialized) billing.start()
        currentBook?.let { statsStore.begin(it.id, reader.position()) }
    }
''',
'onResume stats')

once(
'''        stopAutoPaging()
        stopTts()
        stopBackgroundTts()
''',
'''        stopAllMotion()
''',
'openBook motion reset')

once(
'''                    uiState = uiState.copy(busyLabel = null, currentBook = toCard(book), cleanMode = clean, smartCleanUndoAvailable = cleanHistory.has(book.id))
                    render()
                    refreshLibrary()
''',
'''                    uiState = uiState.copy(busyLabel = null, currentBook = toCard(book), cleanMode = clean, smartCleanUndoAvailable = cleanHistory.has(book.id))
                    statsStore.begin(book.id, candidate.position())
                    refreshAnnotations()
                    render()
                    refreshLibrary()
''',
'openBook refresh annotations')

once(
'''            uiState = uiState.copy(
                screen = AppScreen.READER, currentBook = toCard(book), pageText = text,
                position = reader.position(), length = reader.length(), cleanMode = cleanMode,
            )
''',
'''            statsStore.mark(book.id, reader.position())
            uiState = uiState.copy(
                screen = AppScreen.READER, currentBook = toCard(book), pageText = text,
                position = reader.position(), length = reader.length(), cleanMode = cleanMode,
            )
''',
'render stats')

text = text.replace('        if (userInitiated) stopAutoPaging()\n', '        if (userInitiated) stopAllMotion()\n', 2)
once('        stopAutoPaging(); stopTts(); pageHistory.clear()\n', '        stopAllMotion(); pageHistory.clear()\n', 'seek motion')
once('        stopAutoPaging(); stopTts(); pageHistory.clear(); reader.jump(offset)\n', '        stopAllMotion(); pageHistory.clear(); reader.jump(offset)\n', 'jump motion')
once('        stopAutoPaging(); stopTts(); reader.close()\n', '        stopAllMotion(); reader.close()\n', 'library motion')
once('        uiState = uiState.copy(panel = panel)\n', '        stopAutoScroll()\n        uiState = uiState.copy(panel = panel)\n', 'panel stops scroll')

# Add chapter loading after search().
marker = '    private fun bookmarkKey(book: BookRepository.Book): String = "bookmarks.${book.id}"\n'
if marker not in text: raise SystemExit('missing bookmark section marker')
chapters = '''    private fun ensureChapters() {
        val book = currentBook ?: return
        if (uiState.chaptersLoaded) return
        runWork(
            label = getString(R.string.busy_search),
            task = {
                ReaderController().use { source ->
                    source.open(repository.normalizedFile(book), reader.position())
                    val base = SmartToc.analyze(source)
                    TocOverrideStore(this).apply(base, TocOverrideStore(this).load(book.id, source.length()))
                }
            },
            success = { report -> uiState = uiState.copy(chaptersLoaded = true, chapters = report.chapters) },
            errorTitle = getString(R.string.chapters),
        )
    }

'''
text = text.replace(marker, chapters + marker, 1)

start = '    private fun bookmarkKey(book: BookRepository.Book): String = "bookmarks.${book.id}"\n'
end = '    private fun rulesKey(book: BookRepository.Book) = "rules.${book.id}"\n'
new_annotations = '''    private fun refreshAnnotations() {
        val book = currentBook ?: return
        val values = annotationStore.list(book.id)
        val length = reader.length().coerceAtLeast(1)
        uiState = uiState.copy(
            annotations = values,
            bookmarks = values.filter { it.kind == ReaderAnnotationKind.BOOKMARK }
                .map { BookmarkModel(it.sourceStart.coerceIn(0, length - 1), (it.sourceStart.toDouble() / length.toDouble()).toFloat().coerceIn(0f, 1f)) },
        )
    }

    private fun refreshBookmarks() = refreshAnnotations()

    private fun addBookmark() {
        val book = currentBook ?: return
        if (cleanMode) return showMessage(getString(R.string.bookmark_clean_blocked))
        annotationStore.addBookmark(book.id, reader.position())
        refreshAnnotations()
        showMessage(getString(R.string.bookmark_added))
    }

    private fun deleteBookmark(offset: Long) {
        val book = currentBook ?: return
        annotationStore.deleteBookmark(book.id, offset)
        refreshAnnotations()
    }

    private fun addAnnotation(start: Long, end: Long, kind: ReaderAnnotationKind, style: ReaderHighlightStyle, note: String, excerpt: String) {
        val book = currentBook ?: return
        if (cleanMode || kind == ReaderAnnotationKind.BOOKMARK) return
        annotationStore.upsertRange(book.id, start, end, kind, style, note, excerpt)
        refreshAnnotations()
        showMessage(getString(R.string.reader_annotation_added))
    }

    private fun deleteAnnotation(id: String) {
        val book = currentBook ?: return
        annotationStore.delete(book.id, id)
        refreshAnnotations()
    }

'''
between(start, end, new_annotations, 'bookmark hard cut')

text = text.replace('        val oldBookmarks = bookmarkPositions(book).mapNotNull(String::toLongOrNull)\n', '')
old_remap = '''                val mappedBookmarks = oldBookmarks
                    .map { offset -> mapOffset(offset, oldLength, newLength).toString() }
                    .toSet()
                getPreferences(MODE_PRIVATE).edit().putStringSet(bookmarkKey(updated), mappedBookmarks).apply()
'''
if old_remap in text:
    text = text.replace(old_remap, '                annotationStore.remapBook(book.id, oldLength, newLength)\n', 1)
else:
    raise SystemExit('missing redecode bookmark remap')

text = text.replace('    private fun refreshTtsVoices() { uiState = uiState.copy(ttsVoices = tts.offlineVoices().map { TtsVoiceModel(it.name(), it.label()) }) }\n',
'''    private fun refreshTtsVoices() { uiState = uiState.copy(ttsVoices = ttsCatalog.offlineVoices().map { TtsVoiceModel(it.name(), it.label()) }) }
''')

# Replace Reader settings + all motion/TTS functions as one block.
start = '    private fun updateSettings(settings: ReaderSettings) {\n'
end = '    private fun deleteCurrentBook() {\n'
new_motion = '''    private fun updateSettings(settings: ReaderSettings) {
        if (settings.ttsVoiceName != uiState.settings.ttsVoiceName && !proUnlocked) { billing.purchase(); return }
        var normalized = settings.copy(
            fontSizeSp = settings.fontSizeSp.coerceIn(14f, 40f),
            lineHeightMultiplier = settings.lineHeightMultiplier.coerceIn(1.15f, 2.2f),
            letterSpacingEm = settings.letterSpacingEm.coerceIn(-0.02f, 0.12f),
            paragraphSpacingEm = settings.paragraphSpacingEm.coerceIn(0f, 1.5f),
            horizontalPaddingDp = settings.horizontalPaddingDp.coerceIn(8f, 56f),
            verticalPaddingDp = settings.verticalPaddingDp.coerceIn(4f, 56f),
            firstLineIndentEm = settings.firstLineIndentEm.coerceIn(0f, 3f),
            readerBrightness = settings.readerBrightness.coerceIn(0.03f, 1f),
            autoScrollSpeedDpPerSecond = settings.autoScrollSpeedDpPerSecond.coerceIn(12f, 320f),
            autoPagePaceMultiplier = settings.autoPagePaceMultiplier.coerceIn(0.5f, 2f),
            autoPageDelayMs = settings.autoPageDelayMs.coerceIn(2_000L, 120_000L),
            ttsRate = settings.ttsRate.coerceIn(0.5f, 2f),
            ttsPitch = settings.ttsPitch.coerceIn(0.6f, 1.6f),
            ttsVoiceName = settings.ttsVoiceName.take(256),
        )
        if (cleanMode && normalized.autoScrollEnabled) normalized = normalized.copy(autoScrollEnabled = false)
        when {
            normalized.autoScrollEnabled && motionController.state != ReaderMotionState.AUTO_SCROLL -> beginMotion(ReaderMotionState.AUTO_SCROLL)
            !normalized.autoScrollEnabled && motionController.state == ReaderMotionState.AUTO_SCROLL -> motionController.stop(ReaderMotionState.AUTO_SCROLL)
        }
        normalized = normalized.copy(autoScrollEnabled = motionController.state == ReaderMotionState.AUTO_SCROLL)
        readerPreferences.save(normalized)
        ttsCatalog.setRate(normalized.ttsRate); ttsCatalog.setPitch(normalized.ttsPitch); ttsCatalog.setVoiceName(normalized.ttsVoiceName)
        ReaderPageLayoutCache.clear()
        uiState = uiState.copy(settings = normalized, motion = motionController.state)
    }

    private fun beginMotion(target: ReaderMotionState) {
        if (target != ReaderMotionState.AUTO_PAGE) main.removeCallbacks(autoStep)
        if (target != ReaderMotionState.TTS && uiState.tts.active) {
            runCatching { startService(Intent(this, TtsPlaybackService::class.java).setAction(TtsPlaybackService.ACTION_STOP)) }
        }
        motionController.start(target)
        val settings = uiState.settings.copy(autoScrollEnabled = target == ReaderMotionState.AUTO_SCROLL)
        uiState = uiState.copy(motion = target, settings = settings)
    }

    private fun stopAutoScroll() {
        if (motionController.state == ReaderMotionState.AUTO_SCROLL) {
            motionController.stop(ReaderMotionState.AUTO_SCROLL)
            uiState = uiState.copy(motion = motionController.state, settings = uiState.settings.copy(autoScrollEnabled = false))
        }
    }

    private fun stopAllMotion() {
        main.removeCallbacks(autoStep)
        if (uiState.tts.active || motionController.state == ReaderMotionState.TTS) {
            runCatching { startService(Intent(this, TtsPlaybackService::class.java).setAction(TtsPlaybackService.ACTION_STOP)) }
        }
        motionController.stop()
        uiState = uiState.copy(motion = ReaderMotionState.IDLE, settings = uiState.settings.copy(autoScrollEnabled = false))
    }

    private fun startTtsPlayback() {
        val book = currentBook ?: return
        if (cleanMode) return
        beginMotion(ReaderMotionState.TTS)
        val intent = Intent(this, TtsPlaybackService::class.java)
            .setAction(TtsPlaybackService.ACTION_START)
            .putExtra(TtsPlaybackService.EXTRA_PATH, repository.normalizedFile(book).absolutePath)
            .putExtra(TtsPlaybackService.EXTRA_BOOK_ID, book.id)
            .putExtra(TtsPlaybackService.EXTRA_TITLE, stripTxt(book.name))
            .putExtra(TtsPlaybackService.EXTRA_OFFSET, reader.position())
            .putExtra(TtsPlaybackService.EXTRA_RATE, uiState.settings.ttsRate)
            .putExtra(TtsPlaybackService.EXTRA_PITCH, uiState.settings.ttsPitch)
            .putExtra(TtsPlaybackService.EXTRA_VOICE, uiState.settings.ttsVoiceName)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
    }

    private fun toggleTts() {
        if (currentBook == null || uiState.busyLabel != null || cleanMode) return
        if (uiState.tts.active) startService(Intent(this, TtsPlaybackService::class.java).setAction(TtsPlaybackService.ACTION_TOGGLE))
        else startTtsPlayback()
    }

    private fun stopTts() {
        if (uiState.tts.active || motionController.state == ReaderMotionState.TTS) {
            runCatching { startService(Intent(this, TtsPlaybackService::class.java).setAction(TtsPlaybackService.ACTION_STOP)) }
        }
        if (motionController.state == ReaderMotionState.TTS) motionController.stop(ReaderMotionState.TTS)
        uiState = uiState.copy(motion = motionController.state)
    }

    private fun stopBackgroundTts() = stopTts()

    private fun toggleAutoPaging() {
        if (currentBook == null || uiState.busyLabel != null) return
        if (motionController.state == ReaderMotionState.AUTO_PAGE) stopAutoPaging() else {
            beginMotion(ReaderMotionState.AUTO_PAGE)
            val delay = motionController.adaptivePageDelayMs(visiblePageChars, statsStore.charsPerMinute(), uiState.settings)
            main.postDelayed(autoStep, delay)
        }
    }

    private fun stopAutoPaging() {
        main.removeCallbacks(autoStep)
        if (motionController.state == ReaderMotionState.AUTO_PAGE) motionController.stop(ReaderMotionState.AUTO_PAGE)
        uiState = uiState.copy(motion = motionController.state)
    }

    private fun setSleepTimer(minutes: Int) {
        main.removeCallbacksAndMessages(SLEEP_TOKEN)
        uiState = uiState.copy(sleepMinutes = minutes)
        if (uiState.tts.active) startService(Intent(this, TtsPlaybackService::class.java).setAction(TtsPlaybackService.ACTION_SLEEP).putExtra(TtsPlaybackService.EXTRA_MINUTES, minutes))
        if (minutes > 0 && motionController.state != ReaderMotionState.TTS) {
            main.postAtTime({
                stopAllMotion()
                uiState = uiState.copy(sleepMinutes = 0, message = getString(R.string.sleep_timer_finished))
            }, SLEEP_TOKEN, SystemClock.uptimeMillis() + minutes * 60_000L)
        }
    }

'''
between(start, end, new_motion, 'settings and motion block')

# Book deletion/cleanup is source-offset local metadata aware.
text = text.replace('        stopAutoPaging(); stopTts(); stopBackgroundTts(); workGeneration.incrementAndGet(); reader.close()\n',
                    '        stopAllMotion(); workGeneration.incrementAndGet(); reader.close()\n', 1)
text = text.replace('        smartCleanFeedback.clearBook(book.id)\n        TocOverrideStore(this).reset(book.id)\n',
                    '        smartCleanFeedback.clearBook(book.id)\n        annotationStore.clearBook(book.id)\n        TocOverrideStore(this).reset(book.id)\n')

# Backup restore no longer controls a foreground playback engine.
text = text.replace('                tts.setRate(result.settings.ttsRate); tts.setPitch(result.settings.ttsPitch); tts.setVoiceName(result.settings.ttsVoiceName)\n',
                    '                ttsCatalog.setRate(result.settings.ttsRate); ttsCatalog.setPitch(result.settings.ttsPitch); ttsCatalog.setVoiceName(result.settings.ttsVoiceName)\n                refreshAnnotations()\n')

# Drop legacy bookmark cleanup while retaining per-book cleaning rules.
old_clear = '''    private fun clearBookPreferences(book: BookRepository.Book) {
        val preferences = getPreferences(MODE_PRIVATE)
        val editor = preferences.edit().remove(rulesKey(book)).remove(bookmarkKey(book))
        preferences.all.keys.filter { it.startsWith("bookmarks.${book.id}.") || it == "bookmarks.${book.id}" }.forEach(editor::remove)
        editor.apply()
    }
'''
new_clear = '''    private fun clearBookPreferences(book: BookRepository.Book) {
        getPreferences(MODE_PRIVATE).edit().remove(rulesKey(book)).apply()
        annotationStore.clearBook(book.id)
    }
'''
once(old_clear, new_clear, 'book preference cleanup')

# Volume keys require an idle reader; TTS/auto modes keep hardware keys for their normal meaning.
text = text.replace('            ReaderInteractionRuntime.shouldUseVolumeKeysForPaging(uiState.settings, uiState.ttsPlaying)\n',
                    '            motionController.state == ReaderMotionState.IDLE && ReaderInteractionRuntime.shouldUseVolumeKeysForPaging(uiState.settings, uiState.tts.active)\n')

once(
'''    override fun onPause() {
        super.onPause()
        val book = currentBook
        if (book != null && !cleanMode) persistProgress(book, force = true)
    }
''',
'''    override fun onPause() {
        super.onPause()
        if (motionController.state == ReaderMotionState.AUTO_SCROLL || motionController.state == ReaderMotionState.AUTO_PAGE) stopAllMotion()
        statsStore.finish()
        val book = currentBook
        if (book != null && !cleanMode) persistProgress(book, force = true)
    }
''',
'onPause hardening')

once(
'''    override fun onDestroy() {
        workGeneration.incrementAndGet(); main.removeCallbacksAndMessages(null); workers.shutdownNow()
        if (::billing.isInitialized) billing.close()
        if (::tts.isInitialized) tts.close()
        reader.close(); super.onDestroy()
    }
''',
'''    override fun onDestroy() {
        workGeneration.incrementAndGet(); main.removeCallbacksAndMessages(null); workers.shutdownNow()
        runCatching { unregisterReceiver(ttsStateReceiver) }
        if (::billing.isInitialized) billing.close()
        if (::ttsCatalog.isInitialized) ttsCatalog.close()
        if (::statsStore.isInitialized) statsStore.finish()
        reader.close(); super.onDestroy()
    }
''',
'onDestroy')

path.write_text(text, encoding='utf-8')
print('Reader V2 MainActivity hard cut applied')
