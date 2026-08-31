package com.junchen.jingdu

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal class ReaderCanvasTextPaint(
    val normal: TextPaint,
    val small: TextPaint,
    val title: TextPaint,
    val action: TextPaint,
)

@Composable
internal fun rememberReaderCanvasTextPaint(
    textColor: Color,
    secondaryColor: Color,
    actionColor: Color,
): ReaderCanvasTextPaint {
    val density = LocalDensity.current
    fun paint(sizeSp: Float, color: Color, bold: Boolean = false) = TextPaint(
        Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG,
    ).apply {
        this.color = color.toArgb()
        textSize = with(density) { sizeSp.sp.toPx() }
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    return remember(textColor, secondaryColor, actionColor, density.density, density.fontScale) {
        ReaderCanvasTextPaint(
            normal = paint(15f, textColor),
            small = paint(12f, secondaryColor),
            title = paint(22f, textColor, true),
            action = paint(14f, actionColor, true),
        )
    }
}

internal fun DrawScope.drawReaderText(
    text: String,
    paint: TextPaint,
    x: Float,
    centerY: Float,
    maxWidth: Float,
    centered: Boolean = false,
) {
    val shown = TextUtils.ellipsize(text, paint, maxWidth.coerceAtLeast(1f), TextUtils.TruncateAt.END).toString()
    val metrics = paint.fontMetrics
    val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
    val drawX = if (centered) x + ((maxWidth - paint.measureText(shown)) / 2f).coerceAtLeast(0f) else x
    drawContext.canvas.nativeCanvas.drawText(shown, drawX, baseline, paint)
}

internal fun DrawScope.drawReaderButton(
    rect: Rect,
    text: String,
    paint: TextPaint,
    foreground: Color,
    background: Color = Color.Transparent,
    outline: Color? = null,
) {
    if (background.alpha > 0f) drawRoundRect(background, rect.topLeft, rect.size, CornerRadius(12.dp.toPx()))
    if (outline != null) drawRoundRect(
        outline,
        rect.topLeft,
        rect.size,
        CornerRadius(12.dp.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
    )
    val oldColor = paint.color
    paint.color = foreground.toArgb()
    drawReaderText(text, paint, rect.left + 10.dp.toPx(), rect.center.y, rect.width - 20.dp.toPx(), centered = true)
    paint.color = oldColor
}

@Composable
internal fun ReaderCanvasPanel(
    height: Dp,
    description: String,
    actions: List<CustomAccessibilityAction>,
    onTap: (Offset, Float, Float) -> Unit,
    onSwipe: ((Offset) -> Unit)? = null,
    recordKey: Any? = Unit,
    draw: DrawScope.() -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val layer = rememberGraphicsLayer()
    val latestDraw = rememberUpdatedState(draw)
    val latestTap = rememberUpdatedState(onTap)
    val latestSwipe = rememberUpdatedState(onSwipe)
    var layerSize by remember { mutableStateOf(IntSize.Zero) }
    var recordedKey by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(recordKey, layerSize, density.density, density.fontScale, layoutDirection) {
        if (layerSize.width <= 0 || layerSize.height <= 0) return@LaunchedEffect
        layer.record(density, layoutDirection, layerSize) { latestDraw.value(this) }
        recordedKey = recordKey
    }
    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { layerSize = it }
            .pointerInput(description) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var last = down
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull { it.id == down.id }?.let { last = it }
                    } while (last.pressed)
                    val delta = last.position - down.position
                    val swipeHandler = latestSwipe.value
                    if (swipeHandler != null && delta.getDistance() >= 36.dp.toPx()) {
                        swipeHandler(delta)
                    } else if (delta.getDistance() <= viewConfiguration.touchSlop * 1.5f) {
                        latestTap.value(last.position, size.width.toFloat(), size.height.toFloat())
                    }
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = description
                customActions = actions
                role = Role.Button
            },
    ) {
        if (recordedKey == recordKey && layerSize.width > 0 && layerSize.height > 0) drawLayer(layer)
        else latestDraw.value(this)
    }
}

/** Positioned transparent hit target for hot Canvas panels. It is a real pointer target as well as an accessibility node. */
@Composable
internal fun ReaderCanvasSemanticTarget(
    description: String,
    x: Dp,
    y: Dp,
    width: Dp,
    height: Dp,
    onClickAction: () -> Unit,
) {
    Box(
        Modifier
            .offset(x, y)
            .width(width)
            .height(height)
            .clickable(role = Role.Button, onClick = onClickAction)
            .semantics { contentDescription = description },
    )
}
