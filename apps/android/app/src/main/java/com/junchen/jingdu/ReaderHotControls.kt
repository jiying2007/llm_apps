package com.junchen.jingdu

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Hot-path overload for the reader bottom scrubber. The signature intentionally matches the
 * ReaderScreenV3 call that does not provide valueRange/steps, so advanced settings continue to use
 * Material3 Slider while page/continuous position updates use the fixed-cost Canvas implementation.
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

/**
 * ReaderTopBarV3 is updated on every reader-state publication. Keep the existing navigation/action
 * children and their accessibility semantics, but avoid Material's multi-pass TopAppBar measure
 * tree in the frame-critical reader composition.
 */
@Composable
internal fun CenterAlignedTopAppBar(
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth().height(64.dp)) {
        Box(Modifier.align(Alignment.CenterStart), contentAlignment = Alignment.Center) { navigationIcon() }
        Box(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 148.dp),
            contentAlignment = Alignment.Center,
        ) { title() }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Exact overload used by ReaderReadingStatusV3: one-line status text stays on the Canvas hot path. */
@Composable
internal fun Text(
    text: String,
    modifier: Modifier,
    style: TextStyle,
    color: Color,
    maxLines: Int,
) {
    if (maxLines != 1) {
        androidx.compose.material3.Text(text = text, modifier = modifier, style = style, color = color, maxLines = maxLines)
    } else {
        ReaderHotLine(text, modifier, style, color)
    }
}

/** One-line reader-title overload. Multiline callers retain Material text/layout semantics. */
@Composable
internal fun Text(
    text: String,
    maxLines: Int,
    overflow: TextOverflow,
) {
    if (maxLines != 1) {
        androidx.compose.material3.Text(text = text, maxLines = maxLines, overflow = overflow)
    } else {
        ReaderHotLine(text, Modifier, MaterialTheme.typography.titleMedium, MaterialTheme.colorScheme.onSurface)
    }
}

/** One-line chapter-label overload used by the reader top bar. */
@Composable
internal fun Text(
    text: String,
    maxLines: Int,
    overflow: TextOverflow,
    style: TextStyle,
) {
    if (maxLines != 1) {
        androidx.compose.material3.Text(text = text, maxLines = maxLines, overflow = overflow, style = style)
    } else {
        ReaderHotLine(text, Modifier, style, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One-line chapter-label overload used by the reader bottom bar. */
@Composable
internal fun Text(
    text: String,
    modifier: Modifier,
    maxLines: Int,
    overflow: TextOverflow,
    style: TextStyle,
) {
    if (maxLines != 1) {
        androidx.compose.material3.Text(text = text, modifier = modifier, maxLines = maxLines, overflow = overflow, style = style)
    } else {
        ReaderHotLine(text, modifier, style, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReaderHotLine(
    text: String,
    modifier: Modifier,
    style: TextStyle,
    color: Color,
) {
    val density = LocalDensity.current
    val paint = remember(color, style.fontSize, style.fontWeight, density.density, density.fontScale) {
        val size = if (style.fontSize == TextUnit.Unspecified || style.fontSize.value <= 0f) 12.sp else style.fontSize
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.color = color.toArgb()
            textSize = with(density) { size.toPx() }
            typeface = Typeface.create(
                Typeface.SANS_SERIF,
                if ((style.fontWeight ?: FontWeight.Normal) >= FontWeight.SemiBold) Typeface.BOLD else Typeface.NORMAL,
            )
        }
    }
    Canvas(modifier.fillMaxWidth().height(22.dp)) {
        if (text.isNotEmpty()) drawReaderText(text, paint, 0f, size.height / 2f, size.width, centered = true)
    }
}
