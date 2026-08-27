package com.junchen.jingdu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val LocalReaderHotChrome = staticCompositionLocalOf { false }

/**
 * Reader-screen hot controls. These overloads only become lightweight inside the exact
 * Top/Bottom reader Surface signature below. Elsewhere they delegate to Material3 unchanged.
 */
@Composable
internal fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!LocalReaderHotChrome.current) {
        androidx.compose.material3.IconButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
        return
    }
    Box(
        modifier.size(48.dp).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
internal fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (!LocalReaderHotChrome.current) {
        androidx.compose.material3.TextButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
        return
    }
    Row(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Exact overload used by ReaderTopBarV3 / ReaderBottomBarV3. It removes Material Surface
 * elevation/layer work while preserving the same color and composition semantics.
 */
@Composable
internal fun Surface(
    tonalElevation: Dp,
    color: Color,
    content: @Composable () -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val ignoredElevation = tonalElevation
    Box(Modifier.fillMaxWidth().background(color)) {
        CompositionLocalProvider(LocalReaderHotChrome provides true) { content() }
    }
}

/** Lightweight replacement for the reader-only CenterAlignedTopAppBar star import. */
@Composable
internal fun CenterAlignedTopAppBar(
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(modifier.fillMaxWidth().height(56.dp)) {
        Box(Modifier.align(Alignment.CenterStart)) { navigationIcon() }
        Box(Modifier.align(Alignment.Center)) { title() }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/**
 * Hot-path overload for the reader bottom scrubber. Advanced settings continue to use Material3
 * Slider while page/continuous position updates use the fixed-cost Canvas implementation.
 */
@Composable
internal fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    ReaderLinearSlider(
        value = value,
        valueRange = 0f..1f,
        onValueChange = onValueChange,
        modifier = modifier,
        onValueChangeFinished = { onValueChangeFinished?.invoke() },
    )
}
