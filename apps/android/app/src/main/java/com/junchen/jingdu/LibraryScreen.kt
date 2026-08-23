package com.junchen.jingdu

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun LibraryScreen(state: AppUiState, actions: JingduActions, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val folderStore = remember { FolderLibraryStore(context) }
    var deleteTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var tagTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var tagInput by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf("ALL") }
    var sortName by rememberSaveable { mutableStateOf(LibrarySort.RECENT.name) }
    var sortMenu by remember { mutableStateOf(false) }
    var importPreview by remember { mutableStateOf<ImportPreview?>(null) }
    var previewBusy by remember { mutableStateOf(false) }
    var previewBookId by remember { mutableStateOf<String?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var syncBusy by remember { mutableStateOf(false) }
    var syncResult by remember { mutableStateOf<FolderLibraryStore.SyncResult?>(null) }
    var batchBusy by remember { mutableStateOf(false) }
    var batchReport by remember { mutableStateOf<BatchAutomationReport?>(null) }
    var pendingBatchExport by remember { mutableStateOf<BatchAutomationReport?>(null) }

    val progressiveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        previewBusy = true; previewBookId = null; previewError = null; importPreview = null
        scope.launch {
            val prepared = runCatching { withContext(Dispatchers.IO) { ProgressiveImport(context).prepare(uri) } }
            if (prepared.isFailure) {
                previewBusy = false; previewError = prepared.exceptionOrNull()?.message
                return@launch
            }
            importPreview = prepared.getOrThrow()
            val imported = runCatching { withContext(Dispatchers.IO) { BookRepository(context).importUri(uri, BookRepository.AUTO) } }
            previewBusy = false
            imported.onSuccess { previewBookId = it.id }.onFailure { previewError = it.message }
        }
    }

    suspend fun syncFolders(): FolderLibraryStore.SyncResult = withContext(Dispatchers.IO) {
        val repository = BookRepository(context)
        var discovered = 0; var imported = 0; var skipped = 0; var failed = 0
        val roots = folderStore.roots()
        roots.forEach { root ->
            val entries = folderStore.scanTxt(root)
            discovered += entries.size
            entries.forEach { entry ->
                if (!folderStore.needsImport(entry)) { skipped++; return@forEach }
                try {
                    repository.importUri(entry.uri, BookRepository.AUTO)
                    folderStore.markImported(entry)
                    imported++
                } catch (_: Throwable) { failed++ }
            }
        }
        FolderLibraryStore.SyncResult(roots.size, discovered, imported, skipped, failed)
    }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        folderStore.addRoot(uri)
        syncBusy = true
        scope.launch {
            syncResult = runCatching { syncFolders() }.getOrElse { FolderLibraryStore.SyncResult(folderStore.roots().size, 0, 0, 0, 1) }
            syncBusy = false
        }
    }

    fun startFolderSync() {
        if (folderStore.roots().isEmpty()) { folderLauncher.launch(null); return }
        syncBusy = true
        scope.launch {
            syncResult = runCatching { syncFolders() }.getOrElse { FolderLibraryStore.SyncResult(folderStore.roots().size, 0, 0, 0, 1) }
            syncBusy = false
        }
    }

    fun runBatch(applySafe: Boolean) {
        if (!state.proUnlocked) { actions.onUpgradePro(); return }
        batchBusy = true
        scope.launch {
            batchReport = withContext(Dispatchers.IO) {
                val repository = BookRepository(context)
                val activityPreferences = (context as? Activity)?.getPreferences(Context.MODE_PRIVATE)
                    ?: context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
                BatchAutomation(
                    repository = repository,
                    activityPreferences = activityPreferences,
                    globalRules = RuleLibrary(context).load(),
                    feedback = SmartCleanFeedbackStore(context),
                    cleanHistory = CleanHistory(context),
                ).run(repository.list(), applySafe)
            }
            batchBusy = false
        }
    }

    val batchExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val report = pendingBatchExport
        pendingBatchExport = null
        if (uri != null && report != null) runCatching {
            context.contentResolver.openOutputStream(uri, "w")?.use { output -> output.write(BatchAutomation.toJson(report).toByteArray(Charsets.UTF_8)) }
        }
    }

    val filteredBooks = remember(state.books, filterName, sortName) {
        val filtered = state.books.filter { book ->
            when (filterName) {
                "FAVORITES" -> book.favorite
                "READING" -> book.status == LibraryBookStatus.READING
                "FINISHED" -> book.status == LibraryBookStatus.FINISHED
                else -> true
            }
        }
        when (runCatching { LibrarySort.valueOf(sortName) }.getOrDefault(LibrarySort.RECENT)) {
            LibrarySort.RECENT -> filtered.sortedByDescending(BookCardModel::touchedAt)
            LibrarySort.NAME -> filtered.sortedBy { stripTxt(it.name).lowercase() }
            LibrarySort.PROGRESS -> filtered.sortedByDescending(BookCardModel::progressFraction)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(Modifier.fillMaxWidth().statusBarsPaddingCompat().padding(horizontal = 24.dp, vertical = 18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.app_title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                    if (state.books.isNotEmpty()) TextButton(onClick = actions.onBatchImport) { Text(stringResource(R.string.batch_import)) }
                }
                Text(stringResource(R.string.library_tagline_terminal), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { folderLauncher.launch(null) }, label = { Text(stringResource(R.string.add_folder_library)) }, leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) })
                    AssistChip(onClick = ::startFolderSync, enabled = !syncBusy, label = { Text(stringResource(R.string.sync_folders, folderStore.roots().size)) }, leadingIcon = { if (syncBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, null) })
                    AssistChip(onClick = { runBatch(false) }, enabled = !batchBusy && state.books.isNotEmpty(), label = { Text(stringResource(R.string.batch_optimize)) }, leadingIcon = { Icon(Icons.Default.AutoFixHigh, null) })
                }
                if (state.books.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        LibraryFilterChip(filterName == "ALL", stringResource(R.string.library_filter_all)) { filterName = "ALL" }
                        LibraryFilterChip(filterName == "FAVORITES", stringResource(R.string.library_filter_favorites)) { filterName = "FAVORITES" }
                        LibraryFilterChip(filterName == "READING", stringResource(R.string.library_filter_reading)) { filterName = "READING" }
                        LibraryFilterChip(filterName == "FINISHED", stringResource(R.string.library_filter_finished)) { filterName = "FINISHED" }
                        Box {
                            AssistChip(onClick = { sortMenu = true }, label = { Text("${stringResource(R.string.library_sort_label)} · ${sortLabel(sortName)}") })
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                LibrarySort.entries.forEach { sort -> DropdownMenuItem(text = { Text(sortLabel(sort.name)) }, onClick = { sortName = sort.name; sortMenu = false }) }
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { progressiveLauncher.launch(arrayOf("text/plain", "text/*", "application/octet-stream")) }, icon = { Icon(Icons.Default.Add, contentDescription = null) }, text = { Text(stringResource(R.string.import_txt)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.books.isEmpty() -> EmptyLibrary(Modifier.padding(padding), { progressiveLauncher.launch(arrayOf("text/plain", "text/*", "application/octet-stream")) }, actions.onBatchImport, { folderLauncher.launch(null) })
            filteredBooks.isEmpty() -> Column(Modifier.fillMaxSize().padding(padding).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.search_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { filterName = "ALL" }) { Text(stringResource(R.string.library_filter_all)) }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(300.dp), modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 112.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(filteredBooks, key = { it.id }) { book ->
                    BookCard(book, { actions.onOpenBook(book.id) }, { deleteTarget = book.id }, { actions.onToggleFavorite(book.id) }, { tagTarget = book.id; tagInput = book.tags.joinToString(", ") })
                }
            }
        }
    }

    importPreview?.let { preview ->
        ModalBottomSheet(onDismissRequest = { if (!previewBusy) importPreview = null }) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.first_readable_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.first_readable_meta, preview.name, preview.encoding, preview.sampledBytes / 1024), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (previewBusy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.full_import_background), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                previewError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Surface(Modifier.fillMaxWidth().weight(1f), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                    SelectionContainer { Text(preview.text, Modifier.padding(16.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyLarge) }
                }
                previewBookId?.let { id -> Button(onClick = { importPreview = null; actions.onOpenBook(id) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.open_full_book)) } }
            }
        }
    }

    if (previewError != null && importPreview == null) AlertDialog(onDismissRequest = { previewError = null }, title = { Text(stringResource(R.string.error_import)) }, text = { Text(previewError.orEmpty()) }, confirmButton = { TextButton(onClick = { previewError = null }) { Text(stringResource(R.string.ok)) } })

    syncResult?.let { result -> AlertDialog(
        onDismissRequest = { syncResult = null },
        title = { Text(stringResource(R.string.folder_sync_complete)) },
        text = { Text(stringResource(R.string.folder_sync_result, result.roots, result.discovered, result.imported, result.skipped, result.failed)) },
        confirmButton = { TextButton(onClick = { syncResult = null; (context as? Activity)?.recreate() }) { Text(stringResource(R.string.refresh_library)) } },
    ) }

    batchReport?.let { report -> AlertDialog(
        onDismissRequest = { if (!batchBusy) batchReport = null },
        title = { Text(stringResource(R.string.batch_optimize_report)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.batch_report_summary, report.booksScanned, report.totalCandidates, report.safeCandidates, report.tocAnomalies, report.failedBooks))
            if (report.appliedRules > 0) Text(stringResource(R.string.batch_report_applied, report.appliedRules), color = MaterialTheme.colorScheme.primary)
            report.books.take(5).forEach { Text("${stripTxt(it.name)} · ${it.healthScore}/100 · ${it.safeCandidates}", style = MaterialTheme.typography.bodySmall) }
        } },
        confirmButton = { Button(onClick = { runBatch(true) }, enabled = !batchBusy && report.safeCandidates > 0) { Text(stringResource(R.string.apply_safe_batch)) } },
        dismissButton = { Row { TextButton(onClick = { pendingBatchExport = report; batchExportLauncher.launch("jingdu-batch-report.json") }) { Text(stringResource(R.string.export_action)) }; TextButton(onClick = { batchReport = null }) { Text(stringResource(R.string.close)) } } },
    ) }

    deleteTarget?.let { target -> AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text(stringResource(R.string.remove_from_library_title)) }, text = { Text(stringResource(R.string.remove_from_library_body)) }, confirmButton = { TextButton(onClick = { actions.onDeleteLibraryBook(target); deleteTarget = null }) { Text(stringResource(R.string.delete)) } }, dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } }) }
    tagTarget?.let { target -> AlertDialog(onDismissRequest = { tagTarget = null }, title = { Text(stringResource(R.string.book_tags_title)) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(stringResource(R.string.book_tags_body), color = MaterialTheme.colorScheme.onSurfaceVariant); OutlinedTextField(value = tagInput, onValueChange = { tagInput = it }, singleLine = false, placeholder = { Text(stringResource(R.string.book_tags_hint)) }) } }, confirmButton = { TextButton(onClick = { actions.onSetBookTags(target, tagInput); tagTarget = null }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = { tagTarget = null }) { Text(stringResource(R.string.cancel)) } }) }
}

@Composable private fun LibraryFilterChip(selected: Boolean, label: String, onClick: () -> Unit) { FilterChip(selected = selected, onClick = onClick, label = { Text(label) }) }
@Composable private fun sortLabel(name: String): String = when (runCatching { LibrarySort.valueOf(name) }.getOrDefault(LibrarySort.RECENT)) { LibrarySort.RECENT -> stringResource(R.string.library_sort_recent); LibrarySort.NAME -> stringResource(R.string.library_sort_name); LibrarySort.PROGRESS -> stringResource(R.string.library_sort_progress) }

@Composable
private fun EmptyLibrary(modifier: Modifier, onImport: () -> Unit, onBatchImport: () -> Unit, onFolder: () -> Unit) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(modifier = Modifier.size(88.dp), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) } }
        Spacer(Modifier.height(24.dp)); Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp)); Text(stringResource(R.string.empty_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp)); Button(onClick = onImport) { Icon(Icons.Default.MenuBook, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.select_txt)) }
        TextButton(onClick = onBatchImport) { Text(stringResource(R.string.select_multiple_txt)) }
        TextButton(onClick = onFolder) { Text(stringResource(R.string.add_folder_library)) }
    }
}

@Composable
private fun BookCard(book: BookCardModel, onOpen: () -> Unit, onDelete: () -> Unit, onToggleFavorite: () -> Unit, onEditTags: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(onClick = onOpen, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(width = 58.dp, height = 78.dp), shape = MaterialTheme.shapes.medium, color = coverColor(book.id)) { Box(contentAlignment = Alignment.Center) { Text(book.name.trim().firstOrNull()?.toString()?.uppercase() ?: "TXT", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White) } }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(stripTxt(book.name), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); if (book.favorite) Icon(Icons.Default.Star, contentDescription = stringResource(R.string.favorite_book), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.height(6.dp)); Text("${book.encoding} · ${formatBytes(book.sizeBytes)} · ${statusLabel(book.status)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (book.tags.isNotEmpty()) { Spacer(Modifier.height(5.dp)); Text(book.tags.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.height(10.dp)); LinearProgressIndicator(progress = { book.progressFraction }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(MaterialTheme.shapes.small))
                Spacer(Modifier.height(6.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(if (book.charCount > 0) "${(book.progressFraction * 100).roundToInt()}%" else stringResource(R.string.progress_pending), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(formatTouched(book.touchedAt, stringResource(R.string.not_read)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.book_actions)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.continue_reading)) }, leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null) }, onClick = { menu = false; onOpen() })
                    DropdownMenuItem(text = { Text(stringResource(if (book.favorite) R.string.unfavorite_book else R.string.favorite_book)) }, leadingIcon = { Icon(if (book.favorite) Icons.Default.Star else Icons.Outlined.StarBorder, contentDescription = null) }, onClick = { menu = false; onToggleFavorite() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.edit_tags)) }, leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }, onClick = { menu = false; onEditTags() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete_private_copy)) }, leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable private fun statusLabel(status: LibraryBookStatus): String = when (status) { LibraryBookStatus.UNREAD -> stringResource(R.string.status_unread); LibraryBookStatus.READING -> stringResource(R.string.status_reading); LibraryBookStatus.FINISHED -> stringResource(R.string.status_finished) }
