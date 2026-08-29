package com.junchen.jingdu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

data class JingduActions(
    val onImport: () -> Unit, val onBatchImport: () -> Unit, val onOpenBook: (String) -> Unit, val onDeleteLibraryBook: (String) -> Unit,
    val onToggleFavorite: (String) -> Unit, val onSetBookTags: (String, String) -> Unit,
    val onBackToLibrary: () -> Unit, val onNavigatePrevious: () -> Unit, val onNavigateNext: () -> Unit, val onSeekFraction: (Float) -> Unit,
    val onVisibleCharsChanged: (Long) -> Unit, val onOpenPanel: (ReaderPanel) -> Unit, val onClosePanel: () -> Unit,
    val onSearchQueryChanged: (String) -> Unit, val onSearch: (String) -> Unit, val onJump: (Long) -> Unit, val onSyncTtsPosition: (Long) -> Unit,
    val onEnsureChapters: () -> Unit,
    val onAddBookmark: () -> Unit, val onDeleteBookmark: (Long) -> Unit,
    val onAddAnnotation: (Long, Long, ReaderAnnotationKind, ReaderHighlightStyle, String, String) -> Unit,
    val onDeleteAnnotation: (String) -> Unit,
    val onImportFont: () -> Unit,
    val onAddRule: (RepairRuleMode, String, String) -> Unit, val onDeleteRule: (Int) -> Unit,
    val onClearRules: () -> Unit, val onAnalyzeSmartClean: () -> Unit, val onToggleNoiseCandidate: (Int) -> Unit, val onApplySmartClean: () -> Unit,
    val onUndoSmartClean: () -> Unit,
    val onAddGlobalRule: (RepairRuleMode, String, String) -> Unit, val onDeleteGlobalRule: (Int) -> Unit, val onClearGlobalRules: () -> Unit,
    val onInstallRecommendedRules: () -> Unit, val onExportGlobalRules: () -> Unit, val onImportGlobalRules: () -> Unit,
    val onUpgradePro: () -> Unit, val onRestorePro: () -> Unit, val onExportBackup: () -> Unit, val onImportBackup: () -> Unit,
    val onToggleCleanPreview: () -> Unit, val onExportClean: () -> Unit, val onEncodingSelected: (String) -> Unit,
    val onSettingsChanged: (ReaderSettings) -> Unit, val onToggleTts: () -> Unit, val onToggleAutoPaging: () -> Unit,
    val onSleepTimer: (Int) -> Unit, val onRequestDeleteCurrent: () -> Unit, val onDismissDelete: () -> Unit,
    val onConfirmDeleteCurrent: () -> Unit, val onMessageConsumed: () -> Unit,
)

private val BrandLight = lightColorScheme(primary = Color(0xFF386A52), onPrimary = Color.White, primaryContainer = Color(0xFFB9F0D1), onPrimaryContainer = Color(0xFF002114), secondary = Color(0xFF4F6357), surface = Color(0xFFFFFBFE), background = Color(0xFFF8FAF6))
private val BrandSepia = lightColorScheme(primary = Color(0xFF6D5C34), onPrimary = Color.White, primaryContainer = Color(0xFFF2E2B8), onPrimaryContainer = Color(0xFF241A00), secondary = Color(0xFF6A6048), surface = Color(0xFFFFF7E6), background = Color(0xFFFFF5DF))
private val BrandDark = darkColorScheme(primary = Color(0xFF9DD5B6), onPrimary = Color(0xFF073823), primaryContainer = Color(0xFF20513A), onPrimaryContainer = Color(0xFFB9F0D1), secondary = Color(0xFFB6CCBD), surface = Color(0xFF111411), background = Color(0xFF0E110F))
private val BrandOled = darkColorScheme(primary = Color(0xFF9DD5B6), onPrimary = Color(0xFF073823), primaryContainer = Color(0xFF173D2B), onPrimaryContainer = Color(0xFFB9F0D1), secondary = Color(0xFFB6CCBD), surface = Color.Black, background = Color.Black)

@Composable
fun JingduApp(
    state: AppUiState,
    actions: JingduActions,
    location: ReaderLocationState = ReaderLocationState(),
    hotPanel: StateFlow<ReaderPanel?>? = null,
    onTrackLocation: (current: Long, target: Long, length: Long) -> Unit = { _, _, _ -> },
    onLocationBack: () -> Unit = {},
    onLocationForward: () -> Unit = {},
) {
    val scheme = when (state.settings.palette) {
        ReaderPalette.NIGHT -> BrandDark
        ReaderPalette.OLED -> BrandOled
        ReaderPalette.SEPIA -> BrandSepia
        else -> BrandLight
    }
    MaterialTheme(colorScheme = scheme) {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); actions.onMessageConsumed() } }

        val latestPosition = rememberUpdatedState(state.position)
        val latestLength = rememberUpdatedState(state.length)
        val latestTrackLocation = rememberUpdatedState(onTrackLocation)
        val latestLocationBack = rememberUpdatedState(onLocationBack)
        val latestLocationForward = rememberUpdatedState(onLocationForward)
        val currentReaderPosition = remember { { latestPosition.value } }
        val stableLocationBack = remember { { latestLocationBack.value() } }
        val stableLocationForward = remember { { latestLocationForward.value() } }
        val trackedActions = remember(actions) {
            actions.copy(
                onJump = { target ->
                    latestTrackLocation.value(latestPosition.value, target, latestLength.value)
                    actions.onJump(target)
                },
                onSeekFraction = { fraction ->
                    val length = latestLength.value
                    val target = (length.toDouble() * fraction.coerceIn(0f, 1f)).toLong()
                    latestTrackLocation.value(latestPosition.value, target, length)
                    actions.onSeekFraction(fraction)
                },
            )
        }
        val readerState = remember(
            state.currentBook,
            state.pageText,
            state.position,
            state.length,
            state.cleanMode,
            state.chapters,
            state.chaptersLoaded,
            state.annotations,
            state.motion,
            state.tts,
            state.settings,
        ) {
            AppUiState(
                screen = AppScreen.READER,
                currentBook = state.currentBook,
                pageText = state.pageText,
                position = state.position,
                length = state.length,
                cleanMode = state.cleanMode,
                chapters = state.chapters,
                chaptersLoaded = state.chaptersLoaded,
                annotations = state.annotations,
                motion = state.motion,
                tts = state.tts,
                settings = state.settings,
            )
        }
        val quickPanelState = remember(state.settings, state.motion) {
            AppUiState(settings = state.settings, motion = state.motion)
        }
        val chaptersPanelState = remember(state.currentBook, state.length, state.chapters, state.chaptersLoaded) {
            AppUiState(
                currentBook = state.currentBook,
                length = state.length,
                chapters = state.chapters,
                chaptersLoaded = state.chaptersLoaded,
            )
        }

        BackHandler(enabled = state.panel != null || state.screen == AppScreen.READER) {
            when {
                state.panel != null -> actions.onClosePanel()
                location.canBack -> stableLocationBack()
                else -> trackedActions.onBackToLibrary()
            }
        }
        Box(Modifier.fillMaxSize()) {
            when (state.screen) {
                AppScreen.LIBRARY -> LibraryScreen(state, trackedActions, snackbar)
                AppScreen.READER -> ReaderRoute(readerState, trackedActions, snackbar, location.canBack, location.canForward, stableLocationBack, stableLocationForward)
            }
            if (state.screen == AppScreen.READER) {
                if (hotPanel != null) ReaderHotPanelHost(hotPanel, quickPanelState, chaptersPanelState, trackedActions, currentReaderPosition)
                else {
                    PersistentReaderPanelLayer(state.panel == ReaderPanel.QUICK_SETTINGS) { ReaderQuickSettingsSheet(quickPanelState, trackedActions) }
                    PersistentReaderPanelLayer(state.panel == ReaderPanel.CHAPTERS) { ReaderSmartChaptersPanel(chaptersPanelState, trackedActions, currentReaderPosition) }
                }
            }
            state.busyLabel?.let { BusyOverlay(it) }
            when (state.panel) {
                ReaderPanel.QUICK_SETTINGS, ReaderPanel.CHAPTERS -> Unit
                ReaderPanel.SEARCH -> SearchSheet(state, trackedActions)
                ReaderPanel.BOOKMARKS -> BookmarksSheet(state, trackedActions)
                ReaderPanel.ANNOTATIONS -> ReaderAnnotationsV3Panel(state, trackedActions)
                ReaderPanel.READING_MAP -> ReaderReadingMapV3Panel(state, trackedActions)
                ReaderPanel.READING_HISTORY -> ReaderReadingHistoryPanel(state, trackedActions)
                ReaderPanel.CLEAN -> CleanSheet(state, trackedActions)
                ReaderPanel.SETTINGS -> ReaderSettingsScreen(state, trackedActions)
                ReaderPanel.ENCODING -> EncodingSheet(state, trackedActions)
                ReaderPanel.DOCTOR -> DoctorSheet(state, trackedActions)
                ReaderPanel.SMART_CLEAN_LAB -> SmartCleanLabSheet(state, trackedActions)
                ReaderPanel.PRIVACY -> PrivacySheet(state, trackedActions)
                null -> Unit
            }
        }
        if (state.screen == AppScreen.READER) ReaderGestureCoach(state.settings, trackedActions)
        if (state.deleteConfirmation) AlertDialog(
            onDismissRequest = actions.onDismissDelete,
            title = { Text(stringResource(R.string.delete_current_title)) }, text = { Text(stringResource(R.string.delete_current_body)) },
            confirmButton = { TextButton(onClick = actions.onConfirmDeleteCurrent) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = actions.onDismissDelete) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Hot overlay state is collected in this restart group only; ReaderRoute never subscribes to it. */
@Composable
private fun ReaderHotPanelHost(
    panelFlow: StateFlow<ReaderPanel?>,
    quickState: AppUiState,
    chaptersState: AppUiState,
    actions: JingduActions,
    currentPosition: () -> Long,
) {
    val panel = panelFlow.collectAsStateWithLifecycle().value
    BackHandler(enabled = panel != null) { actions.onClosePanel() }
    PersistentReaderPanelLayer(panel == ReaderPanel.QUICK_SETTINGS) { ReaderQuickSettingsSheet(quickState, actions) }
    PersistentReaderPanelLayer(panel == ReaderPanel.CHAPTERS) { ReaderSmartChaptersPanel(chaptersState, actions, currentPosition) }
}

/**
 * Quick/Chapters are high-frequency reader overlays. Keep them composed after Reader opens and
 * move the complete layer outside the viewport while hidden. Graphics-layer property updates avoid
 * remeasure/recomposition of the reader, while hidden semantics are removed from accessibility.
 */
@Composable
private fun PersistentReaderPanelLayer(visible: Boolean, content: @Composable () -> Unit) {
    val hiddenSemantics = if (visible) Modifier else Modifier.clearAndSetSemantics { }
    Box(
        Modifier.fillMaxSize().then(hiddenSemantics).graphicsLayer {
            translationY = if (visible) 0f else READER_PANEL_HIDDEN_OFFSET_PX.toFloat()
            alpha = if (visible) 1f else 0f
        },
    ) { content() }
}

private const val READER_PANEL_HIDDEN_OFFSET_PX = 16_384

@Composable private fun BusyOverlay(label: String) {
    Box(Modifier.fillMaxSize().zIndex(20f).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.24f)), contentAlignment = Alignment.Center) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Row(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                androidx.compose.foundation.layout.Spacer(Modifier.width(14.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
