package com.junchen.jingdu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * In-tree Reader V3 panel shell kept in the same composition tree with no second window. The scrim
 * is a raw pointer/draw node rather than clickable/ripple Material machinery, and the hot panel body
 * is a flat bottom Box. This keeps outside-tap/Back dismissal while avoiding recurring clickable,
 * ripple, rounded-shape and Column measure/JIT work on every Quick/Chapters open.
 */
@Composable
internal fun ReaderPanelSurface(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f)
    val surface = MaterialTheme.colorScheme.surface
    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
        ) { drawRect(scrim) }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(surface)
                .navigationBarsPadding()
                .padding(top = 8.dp),
        ) {
            content()
        }
    }
}
