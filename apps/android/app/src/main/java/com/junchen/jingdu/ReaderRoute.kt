package com.junchen.jingdu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

enum class ReaderAdaptiveWidth { COMPACT, MEDIUM, EXPANDED, LARGE, EXTRA_LARGE }

data class ReaderAdaptiveLayout(
    val width: ReaderAdaptiveWidth,
    val hasHinge: Boolean,
    val tabletop: Boolean,
) {
    val prefersTwoColumns: Boolean get() = width >= ReaderAdaptiveWidth.EXPANDED && !tabletop
    val prefersSideControls: Boolean get() = width >= ReaderAdaptiveWidth.LARGE && !tabletop
}

@Composable
internal fun ReaderRoute(
    state: AppUiState,
    actions: JingduActions,
    snackbar: SnackbarHostState,
    panelState: State<ReaderPanel?>,
    canLocationBack: Boolean,
    canLocationForward: Boolean,
    onLocationBack: () -> Unit,
    onLocationForward: () -> Unit,
) {
    val adaptive = currentWindowAdaptiveInfoV2()
    val minWidth = adaptive.windowSizeClass.minWidthDp
    val width = when {
        minWidth >= 1600 -> ReaderAdaptiveWidth.EXTRA_LARGE
        minWidth >= 1200 -> ReaderAdaptiveWidth.LARGE
        minWidth >= 840 -> ReaderAdaptiveWidth.EXPANDED
        minWidth >= 600 -> ReaderAdaptiveWidth.MEDIUM
        else -> ReaderAdaptiveWidth.COMPACT
    }
    val layout = ReaderAdaptiveLayout(
        width = width,
        hasHinge = adaptive.windowPosture.hingeList.isNotEmpty(),
        tabletop = adaptive.windowPosture.isTabletop,
    )
    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val panelOpen = panelState.value != null
            if (panelOpen) {
                if (event.key == Key.Escape) {
                    actions.onClosePanel()
                    true
                } else {
                    false
                }
            } else {
                when {
                    event.isCtrlPressed && event.key == Key.F -> {
                        actions.onOpenPanel(ReaderPanel.SEARCH)
                        true
                    }
                    event.key == Key.DirectionLeft || event.key == Key.PageUp -> {
                        actions.onNavigatePrevious()
                        true
                    }
                    event.key == Key.DirectionRight || event.key == Key.PageDown -> {
                        actions.onNavigateNext()
                        true
                    }
                    else -> false
                }
            }
        },
    ) {
        ReaderScreen(
            state = state,
            actions = actions,
            snackbar = snackbar,
            adaptiveLayout = layout,
            panelState = panelState,
            canLocationBack = canLocationBack,
            canLocationForward = canLocationForward,
            onLocationBack = onLocationBack,
            onLocationForward = onLocationForward,
        )
    }
}
