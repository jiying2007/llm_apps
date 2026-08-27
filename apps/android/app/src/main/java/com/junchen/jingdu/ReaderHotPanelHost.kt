package com.junchen.jingdu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap
import kotlin.math.roundToInt

private val HOT_QUICK_PALETTES = listOf(
    ReaderPalette.PAPER,
    ReaderPalette.SEPIA,
    ReaderPalette.LIGHT,
    ReaderPalette.NIGHT,
    ReaderPalette.OLED,
)
private data class HotTocKey(val bookId: String, val length: Long, val chaptersHash: Int)
private data class HotTocEntry(val base: TocQualityReport, val report: TocQualityReport)
private object HotTocCache {
    private val entries = object : LinkedHashMap<HotTocKey, HotTocEntry>(5, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<HotTocKey, HotTocEntry>?): Boolean = size > 4
    }
    @Synchronized fun get(key: HotTocKey): HotTocEntry? = entries[key]
    @Synchronized fun put(key: HotTocKey, entry: HotTocEntry) { entries[key] = entry }
}

/**
 * Layout-stable Reader V3 hot overlay. The full-screen Canvas and the UIAutomator semantics target
 * are always present while Reader is mounted; Quick/Chapters only change draw/input/semantics.
 * This avoids adding/removing root layout nodes on every hot-panel open/close without premeasuring
 * hidden panel content or polluting page/continuous rendering.
 */
@Composable
internal fun ReaderHotPanelHost(
    state: AppUiState,
    actions: JingduActions,
    currentPosition: () -> Long,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val paints = rememberReaderCanvasTextPaint(colors.onSurface, colors.onSurfaceVariant, colors.primary)
    val active = state.panel
    val quickActive = active == ReaderPanel.QUICK_SETTINGS
    val chaptersActive = active == ReaderPanel.CHAPTERS
    val hotActive = quickActive || chaptersActive
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    val navBottomDp = with(density) { navBottomPx.toDp() }
    val topPadPx = with(density) { 8.dp.toPx() }
    val edge = with(density) { 18.dp.toPx() }
    val buttonH = with(density) { 38.dp.toPx() }

    val quickTitle = stringResource(R.string.reader_quick_settings)
    val pagedLabel = stringResource(R.string.reader_mode_paged)
    val continuousLabel = stringResource(R.string.reader_mode_continuous)
    val bookmarkLabel = stringResource(R.string.reader_access_bookmark)
    val advancedLabel = stringResource(R.string.reader_advanced_settings)
    val autoLabel = stringResource(if (state.autoScrolling) R.string.reader_stop_auto_scroll else R.string.reader_start_auto_scroll)
    val speedLabel = stringResource(R.string.reader_auto_scroll_speed_value, state.settings.autoScrollSpeedDpPerSecond.roundToInt())
    val paletteLabels = HOT_QUICK_PALETTES.map { hotPaletteLabel(it) }

    val chapterTitle = stringResource(R.string.smart_toc)
    val addLabel = stringResource(R.string.toc_add_here)
    val hideLabel = stringResource(R.string.toc_hide_heading)
    val resetLabel = stringResource(R.string.toc_reset_repairs)
    val previousLabel = stringResource(R.string.reader_access_previous)
    val nextLabel = stringResource(R.string.reader_access_next)

    val book = state.currentBook
    val store = remember { TocOverrideStore(context) }
    var base by remember(book?.id) { mutableStateOf<TocQualityReport?>(null) }
    var report by remember(book?.id) { mutableStateOf<TocQualityReport?>(null) }
    var chapterLoading by remember(book?.id) { mutableStateOf(!state.chaptersLoaded) }
    var chapterWindowStart by rememberSaveable(book?.id) { mutableIntStateOf(0) }
    var addDialog by rememberSaveable { mutableStateOf(false) }
    var customTitle by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(book?.id, state.chaptersLoaded, state.chapters, state.length) {
        if (book == null) {
            base = null; report = null; chapterLoading = false; chapterWindowStart = 0
            return@LaunchedEffect
        }
        if (!state.chaptersLoaded) {
            chapterLoading = true
            return@LaunchedEffect
        }
        val key = HotTocKey(book.id, state.length, state.chapters.hashCode())
        HotTocCache.get(key)?.let { cached ->
            base = cached.base; report = cached.report; chapterLoading = false
            return@LaunchedEffect
        }
        val computed = withContext(Dispatchers.Default) {
            SmartToc.evaluate(state.chapters.map { SmartChapter(it.offset, it.title, it.source, it.confidence) })
        }
        base = computed; report = computed; chapterLoading = false
        HotTocCache.put(key, HotTocEntry(computed, computed))
    }
    LaunchedEffect(report?.chapters?.size) {
        val count = report?.chapters?.size ?: 0
        chapterWindowStart = chapterWindowStart.coerceIn(0, maxOf(0, count - HOT_CHAPTER_ROWS))
    }

    val chapterList = report?.chapters.orEmpty()
    val chapterEnd = minOf(chapterList.size, chapterWindowStart + HOT_CHAPTER_ROWS)
    val quality = report?.let { stringResource(R.string.smart_toc_quality, it.score, it.chapters.size, it.anomalyCount) }.orEmpty()

    fun cacheReport(updated: TocQualityReport?) {
        report = updated
        val currentBook = book
        val currentBase = base
        if (updated != null && currentBase != null && currentBook != null) {
            HotTocCache.put(
                HotTocKey(currentBook.id, state.length, state.chapters.hashCode()),
                HotTocEntry(currentBase, updated),
            )
        }
    }
    fun hideChapter(index: Int) {
        val currentBook = book ?: return
        val chapter = chapterList.getOrNull(index) ?: return
        store.hide(currentBook.id, chapter.offset, state.length)
        cacheReport(base?.let { store.apply(it, store.load(currentBook.id, state.length)) })
    }
    fun resetChapters() {
        val currentBook = book ?: return
        store.reset(currentBook.id)
        report = base
        chapterWindowStart = 0
        base?.let {
            HotTocCache.put(HotTocKey(currentBook.id, state.length, state.chapters.hashCode()), HotTocEntry(it, it))
        }
    }

    val s = state.settings
    fun setPalette(palette: ReaderPalette) = actions.onSettingsChanged(s.copy(palette = palette, preset = ReaderPreset.CUSTOM, activeThemeId = ""))
    fun setPaged() = actions.onSettingsChanged(s.copy(readingMode = ReaderMode.PAGED, autoScrollEnabled = false))
    fun setContinuous() = actions.onSettingsChanged(s.copy(readingMode = ReaderMode.CONTINUOUS))
    fun font(delta: Float) = actions.onSettingsChanged(s.copy(fontSizeSp = (s.fontSizeSp + delta).coerceIn(14f, 40f), preset = ReaderPreset.CUSTOM, activeThemeId = ""))
    fun speed(delta: Float) = actions.onSettingsChanged(s.copy(autoScrollSpeedDpPerSecond = (s.autoScrollSpeedDpPerSecond + delta).coerceIn(12f, 320f)))

    val quickActions = buildList {
        paletteLabels.forEachIndexed { index, label -> add(CustomAccessibilityAction(label) { setPalette(HOT_QUICK_PALETTES[index]); true }) }
        add(CustomAccessibilityAction(pagedLabel) { setPaged(); true })
        add(CustomAccessibilityAction(continuousLabel) { setContinuous(); true })
        add(CustomAccessibilityAction(bookmarkLabel) { actions.onAddBookmark(); true })
        add(CustomAccessibilityAction(advancedLabel) { actions.onClosePanel(); actions.onOpenPanel(ReaderPanel.SETTINGS); true })
    }
    val chapterActions = buildList {
        add(CustomAccessibilityAction(addLabel) { addDialog = true; true })
        for (index in chapterWindowStart until chapterEnd) {
            val chapter = chapterList[index]
            add(CustomAccessibilityAction(chapter.title) { actions.onJump(chapter.offset); true })
            add(CustomAccessibilityAction("$hideLabel: ${chapter.title}") { hideChapter(index); true })
        }
        if (chapterWindowStart > 0) add(CustomAccessibilityAction(previousLabel) { chapterWindowStart = (chapterWindowStart - HOT_CHAPTER_ROWS).coerceAtLeast(0); true })
        if (chapterEnd < chapterList.size) add(CustomAccessibilityAction(nextLabel) { chapterWindowStart = (chapterWindowStart + HOT_CHAPTER_ROWS).coerceAtMost(maxOf(0, chapterList.size - HOT_CHAPTER_ROWS)); true })
        add(CustomAccessibilityAction(resetLabel) { resetChapters(); true })
    }
    val description = when {
        quickActive -> quickTitle
        chaptersActive -> chapterTitle
        else -> ""
    }
    val a11yActions = when {
        quickActive -> quickActions
        chaptersActive -> chapterActions
        else -> emptyList()
    }

    Box(Modifier.fillMaxSize()) {
        val activeModifier = if (hotActive) {
            Modifier
                .pointerInput(active, s, chapterWindowStart, chapterList) {
                    detectTapGestures { point ->
                        val contentHeightPx = with(density) {
                            (if (quickActive) HOT_QUICK_HEIGHT else HOT_CHAPTER_HEIGHT).toPx()
                        }
                        val panelTop = size.height - navBottomPx - contentHeightPx
                        if (point.y < panelTop) {
                            actions.onClosePanel()
                            return@detectTapGestures
                        }
                        val localY = point.y - panelTop - topPadPx
                        val width = size.width.toFloat()
                        if (quickActive) {
                            val row1 = with(density) { 78.dp.toPx() }
                            val row2 = with(density) { 132.dp.toPx() }
                            val row3 = with(density) { 188.dp.toPx() }
                            val row4 = with(density) { 238.dp.toPx() }
                            val row5 = with(density) { 286.dp.toPx() }
                            when {
                                localY in row1 - buttonH / 2f..row1 + buttonH / 2f -> {
                                    val slot = ((width - edge * 2f) / HOT_QUICK_PALETTES.size).coerceAtLeast(1f)
                                    setPalette(HOT_QUICK_PALETTES[((point.x - edge) / slot).toInt().coerceIn(0, HOT_QUICK_PALETTES.lastIndex)])
                                }
                                localY in row2 - buttonH / 2f..row2 + buttonH / 2f -> when {
                                    point.x < width * 0.16f -> font(-1f)
                                    point.x < width * 0.32f -> font(1f)
                                    else -> {
                                        val fraction = ((point.x - width * 0.38f) / (width * 0.56f)).coerceIn(0f, 1f)
                                        actions.onSettingsChanged(s.copy(lineHeightMultiplier = 1.15f + 1.05f * fraction, preset = ReaderPreset.CUSTOM, activeThemeId = ""))
                                    }
                                }
                                localY in row3 - buttonH / 2f..row3 + buttonH / 2f -> when {
                                    point.x < width * 0.28f -> setPaged()
                                    point.x < width * 0.56f -> setContinuous()
                                    point.x > width * 0.78f -> actions.onAddBookmark()
                                }
                                s.readingMode == ReaderMode.CONTINUOUS && localY in row4 - buttonH / 2f..row4 + buttonH / 2f -> when {
                                    point.x < width * 0.24f -> speed(-8f)
                                    point.x > width * 0.76f -> speed(8f)
                                    else -> { actions.onClosePanel(); actions.onSettingsChanged(s.copy(readingMode = ReaderMode.CONTINUOUS, autoScrollEnabled = !state.autoScrolling)) }
                                }
                                localY >= row5 - buttonH / 2f -> { actions.onClosePanel(); actions.onOpenPanel(ReaderPanel.SETTINGS) }
                            }
                        } else if (chaptersActive) {
                            val rowTop = with(density) { 70.dp.toPx() }
                            val rowHeight = with(density) { HOT_CHAPTER_ROW_HEIGHT.toPx() }
                            val navY = rowTop + rowHeight * HOT_CHAPTER_ROWS + with(density) { 24.dp.toPx() }
                            val resetY = navY + with(density) { 48.dp.toPx() }
                            when {
                                localY < rowTop && point.x > width - with(density) { 72.dp.toPx() } -> addDialog = true
                                localY in rowTop..(rowTop + rowHeight * HOT_CHAPTER_ROWS) -> {
                                    val local = ((localY - rowTop) / rowHeight).toInt()
                                    val index = chapterWindowStart + local
                                    val chapter = chapterList.getOrNull(index) ?: return@detectTapGestures
                                    if (point.x > width - with(density) { 54.dp.toPx() }) hideChapter(index) else actions.onJump(chapter.offset)
                                }
                                localY in (navY - rowHeight / 2f)..(navY + rowHeight / 2f) -> {
                                    if (point.x < width * 0.35f) chapterWindowStart = (chapterWindowStart - HOT_CHAPTER_ROWS).coerceAtLeast(0)
                                    else if (point.x > width * 0.65f) chapterWindowStart = (chapterWindowStart + HOT_CHAPTER_ROWS).coerceAtMost(maxOf(0, chapterList.size - HOT_CHAPTER_ROWS))
                                }
                                localY >= resetY - rowHeight / 2f -> resetChapters()
                            }
                        }
                    }
                }
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    customActions = a11yActions
                    role = Role.Button
                }
        } else {
            Modifier.clearAndSetSemantics { }
        }

        Canvas(Modifier.fillMaxSize().then(activeModifier)) {
            if (!hotActive) return@Canvas
            val contentHeightPx = with(density) {
                (if (quickActive) HOT_QUICK_HEIGHT else HOT_CHAPTER_HEIGHT).toPx()
            }
            val panelHeight = contentHeightPx + navBottomPx
            val panelTop = size.height - panelHeight
            val originY = panelTop + topPadPx
            drawRect(colors.scrim.copy(alpha = 0.28f))
            drawRoundRect(
                colors.surface,
                topLeft = Offset(0f, panelTop),
                size = Size(size.width, panelHeight),
                cornerRadius = CornerRadius(24.dp.toPx()),
            )

            if (quickActive) {
                val row1 = originY + 78.dp.toPx()
                val row2 = originY + 132.dp.toPx()
                val row3 = originY + 188.dp.toPx()
                val row4 = originY + 238.dp.toPx()
                val row5 = originY + 286.dp.toPx()
                drawReaderText(quickTitle, paints.title, edge, originY + 30.dp.toPx(), size.width - edge * 2f)
                val slot = (size.width - edge * 2f) / HOT_QUICK_PALETTES.size
                HOT_QUICK_PALETTES.forEachIndexed { index, palette ->
                    val x = edge + slot * (index + 0.5f)
                    drawCircle(hotPaletteSwatch(palette), 13.dp.toPx(), Offset(x, row1))
                    if (s.palette == palette) drawCircle(colors.primary, 17.dp.toPx(), Offset(x, row1), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                }
                drawReaderButton(Rect(edge, row2 - 19.dp.toPx(), edge + 48.dp.toPx(), row2 + 19.dp.toPx()), "−", paints.action, colors.primary, outline = colors.outlineVariant)
                drawReaderText("${s.fontSizeSp.roundToInt()}sp", paints.normal, edge + 54.dp.toPx(), row2, 62.dp.toPx(), centered = true)
                drawReaderButton(Rect(edge + 120.dp.toPx(), row2 - 19.dp.toPx(), edge + 168.dp.toPx(), row2 + 19.dp.toPx()), "+", paints.action, colors.primary, outline = colors.outlineVariant)
                val trackLeft = (edge + 184.dp.toPx()).coerceAtMost(size.width - 80.dp.toPx())
                val trackRight = size.width - edge
                val lineFraction = ((s.lineHeightMultiplier - 1.15f) / 1.05f).coerceIn(0f, 1f)
                drawRoundRect(colors.surfaceVariant, Offset(trackLeft, row2 - 2.dp.toPx()), Size(trackRight - trackLeft, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                drawCircle(colors.primary, 7.dp.toPx(), Offset(trackLeft + (trackRight - trackLeft) * lineFraction, row2))
                val modeWidth = (size.width - edge * 2f) * 0.27f
                val pagedRect = Rect(edge, row3 - 19.dp.toPx(), edge + modeWidth, row3 + 19.dp.toPx())
                val continuousRect = Rect(edge + modeWidth + 8.dp.toPx(), row3 - 19.dp.toPx(), edge + modeWidth * 2f + 8.dp.toPx(), row3 + 19.dp.toPx())
                drawReaderButton(pagedRect, pagedLabel, paints.action, if (s.readingMode == ReaderMode.PAGED) colors.onPrimary else colors.primary, if (s.readingMode == ReaderMode.PAGED) colors.primary else Color.Transparent, if (s.readingMode == ReaderMode.PAGED) null else colors.outlineVariant)
                drawReaderButton(continuousRect, continuousLabel, paints.action, if (s.readingMode == ReaderMode.CONTINUOUS) colors.onPrimary else colors.primary, if (s.readingMode == ReaderMode.CONTINUOUS) colors.primary else Color.Transparent, if (s.readingMode == ReaderMode.CONTINUOUS) null else colors.outlineVariant)
                drawReaderButton(Rect(size.width - edge - 70.dp.toPx(), row3 - 19.dp.toPx(), size.width - edge, row3 + 19.dp.toPx()), "★", paints.action, colors.primary, outline = colors.outlineVariant)
                if (s.readingMode == ReaderMode.CONTINUOUS) {
                    drawReaderButton(Rect(edge, row4 - 19.dp.toPx(), edge + 48.dp.toPx(), row4 + 19.dp.toPx()), "−", paints.action, colors.primary, outline = colors.outlineVariant)
                    drawReaderButton(Rect(size.width - edge - 48.dp.toPx(), row4 - 19.dp.toPx(), size.width - edge, row4 + 19.dp.toPx()), "+", paints.action, colors.primary, outline = colors.outlineVariant)
                    drawReaderText("$speedLabel · $autoLabel", paints.small, edge + 56.dp.toPx(), row4, size.width - edge * 2f - 112.dp.toPx(), centered = true)
                }
                drawReaderButton(Rect(edge, row5 - 20.dp.toPx(), size.width - edge, row5 + 20.dp.toPx()), advancedLabel, paints.action, colors.primary, outline = colors.outlineVariant)
            } else {
                val rowTop = originY + 70.dp.toPx()
                val rowHeight = HOT_CHAPTER_ROW_HEIGHT.toPx()
                val navY = rowTop + rowHeight * HOT_CHAPTER_ROWS + 24.dp.toPx()
                val resetY = navY + 48.dp.toPx()
                drawReaderText(chapterTitle, paints.title, edge, originY + 28.dp.toPx(), size.width - edge * 2f - 58.dp.toPx())
                drawReaderText(if (chapterLoading) "…" else quality, paints.small, edge, originY + 50.dp.toPx(), size.width - edge * 2f - 58.dp.toPx())
                drawReaderButton(Rect(size.width - edge - 46.dp.toPx(), originY + 12.dp.toPx(), size.width - edge, originY + 54.dp.toPx()), "+", paints.action, colors.primary, outline = colors.outlineVariant)
                if (chapterLoading) drawRoundRect(colors.primary, Offset(edge, originY + 61.dp.toPx()), Size((size.width - edge * 2f) * 0.45f, 2.dp.toPx()))
                for (index in chapterWindowStart until chapterEnd) {
                    val chapter = chapterList[index]
                    val local = index - chapterWindowStart
                    val centerY = rowTop + rowHeight * (local + 0.5f)
                    drawReaderText(chapter.title, paints.normal, edge + 8.dp.toPx(), centerY, size.width - edge * 2f - 64.dp.toPx())
                    if (chapter.source != "core") drawCircle(colors.primary, 3.dp.toPx(), Offset(size.width - edge - 46.dp.toPx(), centerY))
                    val x = size.width - edge - 18.dp.toPx()
                    val r = 6.dp.toPx()
                    drawLine(colors.onSurfaceVariant, Offset(x - r, centerY - r), Offset(x + r, centerY + r), 1.6.dp.toPx())
                    drawLine(colors.onSurfaceVariant, Offset(x + r, centerY - r), Offset(x - r, centerY + r), 1.6.dp.toPx())
                    drawLine(colors.outlineVariant, Offset(edge, rowTop + rowHeight * (local + 1f)), Offset(size.width - edge, rowTop + rowHeight * (local + 1f)), 1.dp.toPx())
                }
                if (chapterList.isNotEmpty()) {
                    drawReaderButton(Rect(edge, navY - 18.dp.toPx(), edge + 64.dp.toPx(), navY + 18.dp.toPx()), "↑", paints.action, if (chapterWindowStart > 0) colors.primary else colors.outlineVariant, outline = colors.outlineVariant)
                    drawReaderText("${chapterWindowStart + 1}–$chapterEnd / ${chapterList.size}", paints.small, edge + 72.dp.toPx(), navY, size.width - edge * 2f - 144.dp.toPx(), centered = true)
                    drawReaderButton(Rect(size.width - edge - 64.dp.toPx(), navY - 18.dp.toPx(), size.width - edge, navY + 18.dp.toPx()), "↓", paints.action, if (chapterEnd < chapterList.size) colors.primary else colors.outlineVariant, outline = colors.outlineVariant)
                }
                drawReaderButton(Rect(edge, resetY - 19.dp.toPx(), size.width - edge, resetY + 19.dp.toPx()), resetLabel, paints.action, colors.primary, outline = colors.outlineVariant)
            }
        }

        val continuousSemantics = if (quickActive) {
            Modifier.semantics {
                contentDescription = continuousLabel
                role = Role.Button
                onClick { setContinuous(); true }
            }
        } else {
            Modifier.clearAndSetSemantics { }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = 116.dp, y = -(navBottomDp + 106.dp))
                .size(width = 120.dp, height = 44.dp)
                .then(continuousSemantics),
        )
    }

    if (addDialog && book != null) AlertDialog(
        onDismissRequest = { addDialog = false },
        title = { Text(addLabel) },
        text = {
            OutlinedTextField(
                value = customTitle,
                onValueChange = { customTitle = it.take(80) },
                label = { Text(stringResource(R.string.toc_custom_title)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    store.add(book.id, currentPosition(), customTitle, state.length)
                    cacheReport(base?.let { store.apply(it, store.load(book.id, state.length)) })
                    customTitle = ""
                    addDialog = false
                },
                enabled = customTitle.isNotBlank(),
            ) { Text(stringResource(R.string.toc_add_action)) }
        },
        dismissButton = { TextButton(onClick = { addDialog = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun hotPaletteLabel(palette: ReaderPalette): String = when (palette) {
    ReaderPalette.PAPER -> stringResource(R.string.paper)
    ReaderPalette.SEPIA -> stringResource(R.string.reader_theme_sepia)
    ReaderPalette.LIGHT -> stringResource(R.string.light)
    ReaderPalette.NIGHT -> stringResource(R.string.night)
    ReaderPalette.OLED -> stringResource(R.string.reader_oled)
}

private fun hotPaletteSwatch(palette: ReaderPalette): Color = when (palette) {
    ReaderPalette.PAPER -> Color(0xFFF7F0DE)
    ReaderPalette.SEPIA -> Color(0xFFF3E5C8)
    ReaderPalette.LIGHT -> Color(0xFFFFFBFF)
    ReaderPalette.NIGHT -> Color(0xFF151713)
    ReaderPalette.OLED -> Color.Black
}

private val HOT_QUICK_HEIGHT = 324.dp
private val HOT_CHAPTER_HEIGHT = 478.dp
private val HOT_CHAPTER_ROW_HEIGHT = 42.dp
private const val HOT_CHAPTER_ROWS = 8
