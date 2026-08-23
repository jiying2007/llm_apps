package com.junchen.jingdu

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun LibraryScreen(state: AppUiState, actions: JingduActions, snackbar: SnackbarHostState) {
    var deleteTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var tagTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var tagInput by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf("ALL") }
    var sortName by rememberSaveable { mutableStateOf(LibrarySort.RECENT.name) }
    var sortMenu by remember { mutableStateOf(false) }

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
                if (state.books.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LibraryFilterChip(filterName == "ALL", stringResource(R.string.library_filter_all)) { filterName = "ALL" }
                        LibraryFilterChip(filterName == "FAVORITES", stringResource(R.string.library_filter_favorites)) { filterName = "FAVORITES" }
                        LibraryFilterChip(filterName == "READING", stringResource(R.string.library_filter_reading)) { filterName = "READING" }
                        LibraryFilterChip(filterName == "FINISHED", stringResource(R.string.library_filter_finished)) { filterName = "FINISHED" }
                        Box {
                            AssistChip(onClick = { sortMenu = true }, label = { Text("${stringResource(R.string.library_sort_label)} · ${sortLabel(sortName)}") })
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                LibrarySort.entries.forEach { sort ->
                                    DropdownMenuItem(text = { Text(sortLabel(sort.name)) }, onClick = { sortName = sort.name; sortMenu = false })
                                }
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = actions.onImport, icon = { Icon(Icons.Default.Add, contentDescription = null) }, text = { Text(stringResource(R.string.import_txt)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.books.isEmpty() -> EmptyLibrary(Modifier.padding(padding), actions.onImport, actions.onBatchImport)
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
                    BookCard(
                        book = book,
                        onOpen = { actions.onOpenBook(book.id) },
                        onDelete = { deleteTarget = book.id },
                        onToggleFavorite = { actions.onToggleFavorite(book.id) },
                        onEditTags = { tagTarget = book.id; tagInput = book.tags.joinToString(", ") },
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null }, title = { Text(stringResource(R.string.remove_from_library_title)) },
            text = { Text(stringResource(R.string.remove_from_library_body)) },
            confirmButton = { TextButton(onClick = { actions.onDeleteLibraryBook(target); deleteTarget = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    tagTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { tagTarget = null },
            title = { Text(stringResource(R.string.book_tags_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.book_tags_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = tagInput, onValueChange = { tagInput = it }, singleLine = false, placeholder = { Text(stringResource(R.string.book_tags_hint)) })
                }
            },
            confirmButton = { TextButton(onClick = { actions.onSetBookTags(target, tagInput); tagTarget = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { tagTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun LibraryFilterChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun sortLabel(name: String): String = when (runCatching { LibrarySort.valueOf(name) }.getOrDefault(LibrarySort.RECENT)) {
    LibrarySort.RECENT -> stringResource(R.string.library_sort_recent)
    LibrarySort.NAME -> stringResource(R.string.library_sort_name)
    LibrarySort.PROGRESS -> stringResource(R.string.library_sort_progress)
}

@Composable
private fun EmptyLibrary(modifier: Modifier, onImport: () -> Unit, onBatchImport: () -> Unit) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(modifier = Modifier.size(88.dp), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
        Spacer(Modifier.height(24.dp)); Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp)); Text(stringResource(R.string.empty_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp)); Button(onClick = onImport) { Icon(Icons.Default.MenuBook, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.select_txt)) }
        TextButton(onClick = onBatchImport) { Text(stringResource(R.string.select_multiple_txt)) }
    }
}

@Composable
private fun BookCard(book: BookCardModel, onOpen: () -> Unit, onDelete: () -> Unit, onToggleFavorite: () -> Unit, onEditTags: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(onClick = onOpen, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(width = 58.dp, height = 78.dp), shape = MaterialTheme.shapes.medium, color = coverColor(book.id)) {
                Box(contentAlignment = Alignment.Center) { Text(book.name.trim().firstOrNull()?.toString()?.uppercase() ?: "TXT", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stripTxt(book.name), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (book.favorite) Icon(Icons.Default.Star, contentDescription = stringResource(R.string.favorite_book), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(6.dp)); Text("${book.encoding} · ${formatBytes(book.sizeBytes)} · ${statusLabel(book.status)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (book.tags.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp)); Text(book.tags.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(10.dp)); LinearProgressIndicator(progress = { book.progressFraction }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(MaterialTheme.shapes.small))
                Spacer(Modifier.height(6.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (book.charCount > 0) "${(book.progressFraction * 100).roundToInt()}%" else stringResource(R.string.progress_pending), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatTouched(book.touchedAt, stringResource(R.string.not_read)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.book_actions)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.continue_reading)) }, leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null) }, onClick = { menu = false; onOpen() })
                    DropdownMenuItem(
                        text = { Text(stringResource(if (book.favorite) R.string.unfavorite_book else R.string.favorite_book)) },
                        leadingIcon = { Icon(if (book.favorite) Icons.Default.Star else Icons.Outlined.StarBorder, contentDescription = null) },
                        onClick = { menu = false; onToggleFavorite() },
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.edit_tags)) }, leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }, onClick = { menu = false; onEditTags() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete_private_copy)) }, leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun statusLabel(status: LibraryBookStatus): String = when (status) {
    LibraryBookStatus.UNREAD -> stringResource(R.string.status_unread)
    LibraryBookStatus.READING -> stringResource(R.string.status_reading)
    LibraryBookStatus.FINISHED -> stringResource(R.string.status_finished)
}
