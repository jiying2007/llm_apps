package com.junchen.jingdu

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable

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
    ReaderScreenV3(
        state = state,
        actions = actions,
        snackbar = snackbar,
        adaptiveLayout = layout,
        canLocationBack = canLocationBack,
        canLocationForward = canLocationForward,
        onLocationBack = onLocationBack,
        onLocationForward = onLocationForward,
    )
}
