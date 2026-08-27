package com.junchen.jingdu

import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap

private data class TocPanelKey(val bookId: String, val length: Long, val chaptersHash: Int)
private data class TocPanelEntry(val base: TocQualityReport, val report: TocQualityReport)

/** Retains derived TOC quality across panel close/reopen; Core/MainActivity remain offset authority. */
private object TocPanelCache {
    private val entries = object : LinkedHashMap<TocPanelKey, TocPanelEntry>(5, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TocPanelKey, TocPanelEntry>?): Boolean = size > 4
    }

    @Synchronized fun get(key: TocPanelKey): TocPanelEntry? = entries[key]
    @Synchronized fun put(key: TocPanelKey, entry: TocPanelEntry) { entries[key] = entry }
}

/**
 * Canonical chapter route with a bounded eight-row viewport. All chapters remain reachable through
 * the previous/next viewport controls, while panel-open cost is independent of book chapter count.
 * Core offsets stay authoritative and rows retain explicit Button semantics.
 */
@Composable
internal fun ReaderSmartChaptersPanel(
    state: AppUiState,
    actions: JingduActions,
    currentPosition: () -> Long,
) {
    val context = LocalContext.current
    val book = state.currentBook
    val store = remember { TocOverrideStore(context) }
    var base by remember(book?.id) { mutableStateOf<TocQualityReport?>(null) }
    var report by remember(book?.id) { mutableStateOf<TocQualityReport?>(null) }
    var loading by remember(book?.id) { mutableStateOf(!state.chaptersLoaded) }
    var addDialog by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var windowStart by rememberSaveable(book?.id) { mutableIntStateOf(0) }

    LaunchedEffect(book?.id, state.chaptersLoaded, state.chapters, state.length) {
        if (book == null) {
            base = null
            report = null
            loading = false
            windowStart = 0
            return@LaunchedEffect
        }
        if (!state.chaptersLoaded) {
            loading = true
            actions.onEnsureChapters()
            return@LaunchedEffect
        }
        val key = TocPanelKey(book.id, state.length, state.chapters.hashCode())
        TocPanelCache.get(key)?.let { cached ->
            base = cached.base
            report = cached.report
            loading = false
            return@LaunchedEffect
        }
        val computed = withContext(Dispatchers.Default) {
            SmartToc.evaluate(state.chapters.map { SmartChapter(it.offset, it.title, it.source, it.confidence) })
        }
        base = computed
        report = computed
        TocPanelCache.put(key, TocPanelEntry(computed, computed))
        loading = false
    }

    LaunchedEffect(report?.chapters?.size) {
        val count = report?.chapters?.size ?: 0
        windowStart = windowStart.coerceIn(0, maxOf(0, count - CHAPTER_WINDOW_ROWS))
    }

    ReaderPanelSurface(onDismiss = actions.onClosePanel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    ReaderPanelText(
                        stringResource(R.string.smart_toc),
                        Modifier.fillMaxWidth().height(34.dp),
                        fontSizeSp = 22f,
                        bold = true,
                    )
                    report?.let {
                        ReaderPanelText(
                            stringResource(R.string.smart_toc_quality, it.score, it.chapters.size, it.anomalyCount),
                            Modifier.fillMaxWidth().height(24.dp),
                            fontSizeSp = 12f,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { addDialog = true }) { Icon(Icons.Default.Add, stringResource(R.string.toc_add_here)) }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            report?.let { value ->
                val chapters = value.chapters
                val end = minOf(chapters.size, windowStart + CHAPTER_WINDOW_ROWS)
                for (index in windowStart until end) {
                    val chapter = chapters[index]
                    Row(Modifier.fillMaxWidth().height(CHAPTER_ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                        val sourceHint = if (chapter.source != "core") stringResource(R.string.toc_source_user_or_special) else null
                        ChapterTitleCanvas(
                            title = chapter.title,
                            special = sourceHint != null,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(role = Role.Button) { actions.onJump(chapter.offset) }
                                .semantics { contentDescription = if (sourceHint == null) chapter.title else "${chapter.title}, $sourceHint" }
                                .padding(horizontal = 12.dp),
                        )
                        ChapterDeleteButton(stringResource(R.string.toc_hide_heading)) {
                            val currentBook = book ?: return@ChapterDeleteButton
                            store.hide(currentBook.id, chapter.offset, state.length)
                            val updated = base?.let { store.apply(it, store.load(currentBook.id, state.length)) }
                            report = updated
                            val currentBase = base
                            if (currentBase != null && updated != null) {
                                TocPanelCache.put(TocPanelKey(currentBook.id, state.length, state.chapters.hashCode()), TocPanelEntry(currentBase, updated))
                            }
                        }
                    }
                    HorizontalDivider()
                }
                if (chapters.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(
                            onClick = { windowStart = (windowStart - CHAPTER_WINDOW_ROWS).coerceAtLeast(0) },
                            enabled = windowStart > 0,
                        ) { Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.reader_access_previous)) }
                        ReaderPanelText(
                            "${windowStart + 1}–$end / ${chapters.size}",
                            Modifier.weight(1f).height(32.dp),
                            fontSizeSp = 12f,
                            centered = true,
                        )
                        IconButton(
                            onClick = { windowStart = (windowStart + CHAPTER_WINDOW_ROWS).coerceAtMost(maxOf(0, chapters.size - CHAPTER_WINDOW_ROWS)) },
                            enabled = end < chapters.size,
                        ) { Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.reader_access_next)) }
                    }
                }
                ReaderPanelAction(
                    text = stringResource(R.string.toc_reset_repairs),
                    onClick = {
                        val currentBook = book ?: return@ReaderPanelAction
                        store.reset(currentBook.id)
                        report = base
                        windowStart = 0
                        val currentBase = base
                        if (currentBase != null) TocPanelCache.put(TocPanelKey(currentBook.id, state.length, state.chapters.hashCode()), TocPanelEntry(currentBase, currentBase))
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                )
            }
        }
    }

    if (addDialog && book != null) AlertDialog(
        onDismissRequest = { addDialog = false },
        title = { Text(stringResource(R.string.toc_add_here)) },
        text = { OutlinedTextField(value = title, onValueChange = { title = it.take(80) }, label = { Text(stringResource(R.string.toc_custom_title)) }) },
        confirmButton = { TextButton(onClick = {
            store.add(book.id, currentPosition(), title, state.length)
            val updated = base?.let { store.apply(it, store.load(book.id, state.length)) }
            report = updated
            val currentBase = base
            if (currentBase != null && updated != null) TocPanelCache.put(TocPanelKey(book.id, state.length, state.chapters.hashCode()), TocPanelEntry(currentBase, updated))
            title = ""
            addDialog = false
        }, enabled = title.isNotBlank()) { Text(stringResource(R.string.toc_add_action)) } },
        dismissButton = { TextButton(onClick = { addDialog = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ChapterTitleCanvas(title: String, special: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val markerColor = MaterialTheme.colorScheme.primary.toArgb()
    val textSizePx = with(density) { 16.sp.toPx() }
    val horizontalPaddingPx = with(density) { 2.dp.toPx() }
    val paint = remember(textColor, textSizePx) {
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor; textSize = textSizePx }
    }
    val markerPaint = remember(markerColor) { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = markerColor } }

    Canvas(modifier) {
        val markerSpace = if (special) 14.dp.toPx() else 0f
        val available = (size.width - markerSpace - horizontalPaddingPx * 2f).coerceAtLeast(1f)
        val shown = TextUtils.ellipsize(title, paint, available, TextUtils.TruncateAt.END).toString()
        val metrics = paint.fontMetrics
        val baseline = size.height / 2f - (metrics.ascent + metrics.descent) / 2f
        drawContext.canvas.nativeCanvas.drawText(shown, horizontalPaddingPx, baseline, paint)
        if (special) drawContext.canvas.nativeCanvas.drawCircle(size.width - 6.dp.toPx(), size.height / 2f, 3.dp.toPx(), markerPaint)
    }
}

@Composable
private fun ChapterDeleteButton(description: String, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        Modifier
            .size(44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(13.dp),
    ) {
        val stroke = 1.8.dp.toPx()
        drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.25f, size.height * 0.25f), androidx.compose.ui.geometry.Offset(size.width * 0.75f, size.height * 0.75f), strokeWidth = stroke)
        drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.75f, size.height * 0.25f), androidx.compose.ui.geometry.Offset(size.width * 0.25f, size.height * 0.75f), strokeWidth = stroke)
    }
}

private val CHAPTER_ROW_HEIGHT = 48.dp
private const val CHAPTER_WINDOW_ROWS = 8
