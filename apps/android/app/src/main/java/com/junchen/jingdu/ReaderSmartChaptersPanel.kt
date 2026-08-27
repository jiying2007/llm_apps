package com.junchen.jingdu

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
 * Canonical chapters route. Derived TOC quality stays cached while the long list is rendered by a
 * native ListView: one virtualized host replaces the previous Compose LazyColumn/item subtrees,
 * keeping first-open measure/JIT bounded without changing chapter offsets, jump semantics, hide
 * controls, keyboard focus or TalkBack behavior.
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
            Spacer(Modifier.height(8.dp))
            report?.let { value ->
                if (value.anomalyCount > 0) {
                    Text(
                        stringResource(R.string.smart_toc_anomalies, value.duplicateTitles, value.numericGaps, value.suspiciousTitles),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                val sourceHint = stringResource(R.string.toc_source_user_or_special)
                val hideDescription = stringResource(R.string.toc_hide_heading)
                val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
                val accentColor = MaterialTheme.colorScheme.primary.toArgb()
                val dividerColor = MaterialTheme.colorScheme.outlineVariant.toArgb()
                AndroidView(
                    factory = { NativeChapterListView(it) },
                    update = { list ->
                        list.bind(
                            chapters = value.chapters,
                            sourceHint = sourceHint,
                            hideDescription = hideDescription,
                            textColor = textColor,
                            accentColor = accentColor,
                            dividerColor = dividerColor,
                            onJump = actions.onJump,
                            onHide = { chapter ->
                                val currentBook = book
                                if (currentBook != null) {
                                    store.hide(currentBook.id, chapter.offset, state.length)
                                    val updated = base?.let { store.apply(it, store.load(currentBook.id, state.length)) }
                                    report = updated
                                    val currentBase = base
                                    if (currentBase != null && updated != null) {
                                        TocPanelCache.put(
                                            TocPanelKey(currentBook.id, state.length, state.chapters.hashCode()),
                                            TocPanelEntry(currentBase, updated),
                                        )
                                    }
                                }
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(500.dp),
                )
                TextButton(onClick = {
                    val currentBook = book ?: return@TextButton
                    store.reset(currentBook.id)
                    report = base
                    val currentBase = base
                    if (currentBase != null) {
                        TocPanelCache.put(
                            TocPanelKey(currentBook.id, state.length, state.chapters.hashCode()),
                            TocPanelEntry(currentBase, currentBase),
                        )
                    }
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
            if (currentBase != null && updated != null) {
                TocPanelCache.put(
                    TocPanelKey(book.id, state.length, state.chapters.hashCode()),
                    TocPanelEntry(currentBase, updated),
                )
            }
            title = ""
            addDialog = false
        }, enabled = title.isNotBlank()) { Text(stringResource(R.string.toc_add_action)) } },
        dismissButton = { TextButton(onClick = { addDialog = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Native virtualization for the hundreds/thousands-of-chapters path. */
private class NativeChapterListView(context: Context) : ListView(context) {
    private val chapterAdapter = NativeChapterAdapter(context)

    init {
        adapter = chapterAdapter
        isVerticalScrollBarEnabled = true
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
    }

    fun bind(
        chapters: List<SmartChapter>,
        sourceHint: String,
        hideDescription: String,
        textColor: Int,
        accentColor: Int,
        dividerColor: Int,
        onJump: (Long) -> Unit,
        onHide: (SmartChapter) -> Unit,
    ) {
        divider = ColorDrawable(dividerColor)
        dividerHeight = dp(1)
        chapterAdapter.bind(chapters, sourceHint, hideDescription, textColor, accentColor, onJump, onHide)
        isFastScrollEnabled = chapters.size >= 64
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}

private class NativeChapterAdapter(private val context: Context) : BaseAdapter() {
    private var chapters: List<SmartChapter> = emptyList()
    private var sourceHint: String = ""
    private var hideDescription: String = ""
    private var textColor: Int = android.graphics.Color.DKGRAY
    private var accentColor: Int = android.graphics.Color.BLUE
    private var onJump: (Long) -> Unit = {}
    private var onHide: (SmartChapter) -> Unit = {}

    fun bind(
        chapters: List<SmartChapter>,
        sourceHint: String,
        hideDescription: String,
        textColor: Int,
        accentColor: Int,
        onJump: (Long) -> Unit,
        onHide: (SmartChapter) -> Unit,
    ) {
        val changed = this.chapters !== chapters || this.sourceHint != sourceHint ||
            this.textColor != textColor || this.accentColor != accentColor
        this.chapters = chapters
        this.sourceHint = sourceHint
        this.hideDescription = hideDescription
        this.textColor = textColor
        this.accentColor = accentColor
        this.onJump = onJump
        this.onHide = onHide
        if (changed) notifyDataSetChanged()
    }

    override fun getCount(): Int = chapters.size
    override fun getItem(position: Int): SmartChapter = chapters[position]
    override fun getItemId(position: Int): Long = chapters[position].offset
    override fun hasStableIds(): Boolean = true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = convertView as? NativeChapterRow ?: NativeChapterRow(context)
        row.bind(
            chapter = chapters[position],
            sourceHint = sourceHint,
            hideDescription = hideDescription,
            textColor = textColor,
            accentColor = accentColor,
            onJump = onJump,
            onHide = onHide,
        )
        return row
    }
}

private class NativeChapterRow(context: Context) : LinearLayout(context) {
    private val titleView = TextView(context).apply {
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
        setPadding(dp(12), 0, dp(8), 0)
        layoutParams = LayoutParams(0, dp(52), 1f)
    }
    private val hideButton = ImageButton(context).apply {
        setImageResource(android.R.drawable.ic_menu_delete)
        background = null
        layoutParams = LayoutParams(dp(48), dp(48))
        scaleType = ImageView.ScaleType.CENTER
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(52)
        isClickable = true
        isFocusable = true
        addView(titleView)
        addView(hideButton)
    }

    fun bind(
        chapter: SmartChapter,
        sourceHint: String,
        hideDescription: String,
        textColor: Int,
        accentColor: Int,
        onJump: (Long) -> Unit,
        onHide: (SmartChapter) -> Unit,
    ) {
        titleView.text = chapter.title
        titleView.setTextColor(textColor)
        if (chapter.source != "core") {
            val marker = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accentColor)
                setSize(dp(7), dp(7))
                setBounds(0, 0, dp(7), dp(7))
            }
            titleView.setCompoundDrawablesRelative(marker, null, null, null)
            titleView.compoundDrawablePadding = dp(8)
            contentDescription = "${chapter.title}, $sourceHint"
        } else {
            titleView.setCompoundDrawablesRelative(null, null, null, null)
            contentDescription = chapter.title
        }
        setOnClickListener { onJump(chapter.offset) }
        hideButton.contentDescription = "$hideDescription: ${chapter.title}"
        hideButton.setColorFilter(accentColor)
        hideButton.setOnClickListener { onHide(chapter) }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Button"
        info.isClickable = true
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
