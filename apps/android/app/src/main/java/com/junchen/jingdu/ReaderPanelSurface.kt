package com.junchen.jingdu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * In-tree reader panel shell kept in the same composition tree as Reader. It creates no second
 * window and no Material Surface/elevation/shadow layer. Hot Quick/Chapters panels render one Canvas
 * below this shell, avoiding the texture uploads and deep Material measure tree visible in hosted
 * Perfetto while preserving scrim, outside-tap and Back dismissal behavior.
 */
@Composable
internal fun ReaderPanelSurface(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f))
                .clickable(onClick = onDismiss),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                )
                .navigationBarsPadding()
                .padding(top = 8.dp),
        ) { content() }
    }
}
