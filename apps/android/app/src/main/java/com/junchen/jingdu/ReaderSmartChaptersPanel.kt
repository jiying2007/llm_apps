package com.junchen.jingdu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 * Performance-oriented canonical target for the chapters route. It renders in the app composition
 * tree and retains the derived quality report, so reopening the panel never reevaluates the same
 * chapter collection or recreates a modal window. Current reader position is read through a stable
 * callback so background page turns do not invalidate the hidden chapter composition.
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

    LaunchedEffect(book?.id, state.chaptersLoaded, state.chapters, state.length) {
        if (book == null) {
            base = null
            report = null
            loading = false
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

    ReaderPanelSurface(onDismiss = actions.onClosePanel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.smart_toc), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    report?.let { Text(stringResource(R.string.smart_toc_quality, it.score, it.chapters.size, it.anomalyCount), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                IconButton(onClick = { addDialog = true }) { Icon(Icons.Default.Add, stringResource(R.string.toc_add_here)) }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            report?.let { value ->
                if (value.anomalyCount > 0) AssistChip(onClick = {}, label = { Text(stringResource(R.string.smart_toc_anomalies, value.duplicateTitles, value.numericGaps, value.suspiciousTitles)) })
                LazyColumn(Modifier.heightIn(max = 560.dp)) {
                    items(value.chapters, key = { it.offset }) { chapter ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable(role = Role.Button) { actions.onJump(chapter.offset) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Text(chapter.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (chapter.source != "core") Text(stringResource(R.string.toc_source_user_or_special), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = {
                                val currentBook = book ?: return@IconButton
                                store.hide(currentBook.id, chapter.offset, state.length)
                                val updated = base?.let { store.apply(it, store.load(currentBook.id, state.length)) }
                                report = updated
                                val currentBase = base
                                if (currentBase != null && updated != null) {
                                    TocPanelCache.put(TocPanelKey(currentBook.id, state.length, state.chapters.hashCode()), TocPanelEntry(currentBase, updated))
                                }
                            }) { Icon(Icons.Default.Delete, stringResource(R.string.toc_hide_heading)) }
                        }
                        HorizontalDivider()
                    }
                }
                TextButton(onClick = {
                    val currentBook = book ?: return@TextButton
                    store.reset(currentBook.id)
                    report = base
                    val currentBase = base
                    if (currentBase != null) TocPanelCache.put(TocPanelKey(currentBook.id, state.length, state.chapters.hashCode()), TocPanelEntry(currentBase, currentBase))
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.toc_reset_repairs)) }
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
