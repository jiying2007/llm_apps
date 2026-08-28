package com.junchen.jingdu

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
 * Exact overload used by ReaderReadingStatusV3. A one-line status update must not construct a
 * Compose/StaticLayout on every page or settled scroll position; it is simple display-only text and
 * can be drawn directly with a native TextPaint. Material Text remains authoritative everywhere
 * else because those call sites have different signatures.
 */
@Composable
internal fun Text(
    text: String,
    modifier: Modifier,
    style: TextStyle,
    color: Color,
    maxLines: Int,
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
        if (maxLines > 0 && text.isNotEmpty()) {
            drawReaderText(text, paint, 0f, size.height / 2f, size.width, centered = true)
        }
    }
}
