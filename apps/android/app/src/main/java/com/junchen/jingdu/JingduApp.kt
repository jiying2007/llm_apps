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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.abs

data class JingduActions(
    val onImport: () -> Unit, val onBatchImport: () -> Unit, val onOpenBook: (String) -> Unit, val onDeleteLibraryBook: (String) -> Unit,
    val onToggleFavorite: (String) -> Unit, val onSetBookTags: (String, String) -> Unit,
    val onBackToLibrary: () -> Unit, val onNavigatePrevious: () -> Unit, val onNavigateNext: () -> Unit, val onSeekFraction: (Float) -> Unit,
    val onVisibleCharsChanged: (Long) -> Unit, val onOpenPanel: (ReaderPanel) -> Unit, val onClosePanel: () -> Unit,
    val onSearchQueryChanged: (String) -> Unit, val onSearch: (String) -> Unit, val onJump: (Long) -> Unit, val onSyncTtsPosition: (Long) -> Unit,
    val onAddBookmark: () -> Unit, val onDeleteBookmark: (Long) -> Unit, val onAddRule: (RepairRuleMode, String, String) -> Unit, val onDeleteRule: (Int) -> Unit,
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
private val BrandDark = darkColorScheme(primary = Color(0xFF9DD5B6), onPrimary = Color(0xFF073823), primaryContainer = Color(0xFF20513A), onPrimaryContainer = Color(0xFFB9F0D1), secondary = Color(0xFFB6CCBD), surface = Color(0xFF111411), background = Color(0xFF0E110F))
private val BrandOled = darkColorScheme(primary = Color(0xFF9DD5B6), onPrimary = Color(0xFF073823), primaryContainer = Color(0xFF173D2B), onPrimaryContainer = Color(0xFFB9F0D1), secondary = Color(0xFFB6CCBD), surface = Color.Black, background = Color.Black)

@Composable
fun JingduApp(state: AppUiState, actions: JingduActions) {
    val scheme = when (state.settings.palette) {
        ReaderPalette.NIGHT -> BrandDark
        ReaderPalette.OLED -> BrandOled
        else -> BrandLight
    }
    MaterialTheme(colorScheme = scheme) {
        val snackbar = remember { SnackbarHostState() }
        val locationBack = remember { mutableStateListOf<Long>() }
        val locationForward = remember { mutableStateListOf<Long>() }
        val bookId = state.currentBook?.id

        LaunchedEffect(bookId) {
            locationBack.clear()
            locationForward.clear()
        }
        LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); actions.onMessageConsumed() } }

        fun pushLocation(target: Long) {
            if (state.screen != AppScreen.READER || state.length <= 0) return
            val bounded = target.coerceIn(0L, (state.length - 1).coerceAtLeast(0L))
            if (abs(bounded - state.position) < 2L) return
            locationBack.add(state.position)
            while (locationBack.size > 100) locationBack.removeAt(0)
            locationForward.clear()
        }

        val trackedActions = actions.copy(
            onBackToLibrary = {
                if (state.settings.autoScrollEnabled) actions.onSettingsChanged(state.settings.copy(autoScrollEnabled = false))
                actions.onBackToLibrary()
            },
            onJump = { target -> pushLocation(target); actions.onJump(target) },
            onSeekFraction = { fraction ->
                val target = (state.length.toDouble() * fraction.coerceIn(0f, 1f)).toLong()
                pushLocation(target)
                actions.onSeekFraction(fraction)
            },
        )

        fun locationBackAction() {
            if (locationBack.isEmpty()) return
            val target = locationBack.removeAt(locationBack.lastIndex)
            locationForward.add(state.position)
            actions.onJump(target)
        }

        fun locationForwardAction() {
            if (locationForward.isEmpty()) return
            val target = locationForward.removeAt(locationForward.lastIndex)
            locationBack.add(state.position)
            actions.onJump(target)
        }

        BackHandler(enabled = state.panel != null || state.screen == AppScreen.READER) {
            when {
                state.panel != null -> actions.onClosePanel()
                locationBack.isNotEmpty() -> locationBackAction()
                else -> trackedActions.onBackToLibrary()
            }
        }

        Box(Modifier.fillMaxSize()) {
            when (state.screen) {
                AppScreen.LIBRARY -> LibraryScreen(state, trackedActions, snackbar)
                AppScreen.READER -> ReaderScreen(
                    state = state,
                    actions = trackedActions,
                    snackbar = snackbar,
                    canLocationBack = locationBack.isNotEmpty(),
                    canLocationForward = locationForward.isNotEmpty(),
                    onLocationBack = ::locationBackAction,
                    onLocationForward = ::locationForwardAction,
                )
            }
            state.busyLabel?.let { BusyOverlay(it) }
        }
        when (state.panel) {
            ReaderPanel.SEARCH -> SearchSheet(state, trackedActions)
            ReaderPanel.CHAPTERS -> SmartChaptersSheet(state, trackedActions)
            ReaderPanel.BOOKMARKS -> BookmarksSheet(state, trackedActions)
            ReaderPanel.CLEAN -> CleanSheet(state, trackedActions)
            ReaderPanel.SETTINGS -> ProductSettingsSheet(state, trackedActions)
            ReaderPanel.ENCODING -> EncodingSheet(state, trackedActions)
            ReaderPanel.DOCTOR -> DoctorSheet(state, trackedActions)
            ReaderPanel.SMART_CLEAN_LAB -> SmartCleanLabSheet(state, trackedActions)
            ReaderPanel.PRIVACY -> PrivacySheet(state, trackedActions)
            null -> Unit
        }
        if (state.deleteConfirmation) AlertDialog(
            onDismissRequest = actions.onDismissDelete,
            title = { Text(stringResource(R.string.delete_current_title)) }, text = { Text(stringResource(R.string.delete_current_body)) },
            confirmButton = { TextButton(onClick = actions.onConfirmDeleteCurrent) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = actions.onDismissDelete) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun BusyOverlay(label: String) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.24f)), contentAlignment = Alignment.Center) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Row(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                androidx.compose.foundation.layout.Spacer(Modifier.width(14.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
