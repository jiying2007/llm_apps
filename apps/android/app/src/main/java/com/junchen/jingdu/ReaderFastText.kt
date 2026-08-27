package com.junchen.jingdu

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.LineBreaker
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.LineHeightSpan
import android.text.style.StyleSpan
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.resolveAsTypeface
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Hot-path overload for paged AnnotatedString rendering.
 *
 * ReaderPageLayoutCache already bounded the visible page on a worker. Ordinary reading draws one
 * Android StaticLayout directly, avoiding repeated Compose Text/selection measurement. Touch
 * exploration always keeps the native selectable Compose path. A non-consuming long press arms
 * the selectable path without stealing Reader tap/swipe gestures; this remains a Draft-only
 * performance experiment until the same-gesture SelectionState hand-off is validated.
 */
@Composable
internal fun Text(
    text: AnnotatedString,
    modifier: Modifier,
    style: TextStyle,
    overflow: TextOverflow,
) {
    val context = LocalContext.current
    val accessibility = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    }
    var selectionMode by remember(text.text) { mutableStateOf(false) }
    if (selectionMode || accessibility.isTouchExplorationEnabled) {
        androidx.compose.material3.Text(text = text, modifier = modifier, style = style, overflow = overflow)
        return
    }

    val density = LocalDensity.current
    val resolver = LocalFontFamilyResolver.current
    val nativeTypeface by resolver.resolveAsTypeface(
        fontFamily = style.fontFamily,
        fontWeight = style.fontWeight ?: FontWeight.Normal,
        fontStyle = style.fontStyle ?: FontStyle.Normal,
        fontSynthesis = style.fontSynthesis ?: FontSynthesis.All,
    )
    val resolvedColor = if (style.color == Color.Unspecified) MaterialTheme.colorScheme.onBackground else style.color
    val fontSizePx = with(density) { style.fontSize.toPx() }.coerceAtLeast(1f)
    val lineHeightMultiplier = if (style.lineHeight == TextUnit.Unspecified || style.lineHeight.value <= 0f || style.fontSize.value <= 0f) {
        1f
    } else {
        (style.lineHeight.value / style.fontSize.value).coerceAtLeast(1f)
    }
    val letterSpacingEm = if (style.letterSpacing == TextUnit.Unspecified || style.fontSize.value <= 0f) {
        0f
    } else {
        style.letterSpacing.value / style.fontSize.value
    }
    val paint = remember(resolvedColor, fontSizePx, letterSpacingEm, nativeTypeface) {
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = resolvedColor.toArgb()
            textSize = fontSizePx
            letterSpacing = letterSpacingEm
            typeface = nativeTypeface
        }
    }
    val rendered = remember(text, style, density.density, nativeTypeface, resolvedColor) {
        fastSpannable(text, style, density, resolvedColor)
    }

    Spacer(
        modifier
            .fillMaxWidth()
            .armSelectionOnLongPress(text.text) { selectionMode = true }
            .drawWithCache {
                val width = size.width.roundToInt().coerceAtLeast(1)
                val builder = StaticLayout.Builder.obtain(rendered, 0, rendered.length, paint, width)
                    .setIncludePad(false)
                    .setLineSpacing(0f, lineHeightMultiplier)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
                if (style.textAlign == TextAlign.Justify) builder.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD)
                val layout = builder.build()
                onDrawBehind {
                    val canvas = drawContext.canvas.nativeCanvas
                    canvas.save()
                    layout.draw(canvas)
                    canvas.restore()
                }
            },
    )
}

/**
 * Continuous reader overload. The existing 4K window and TextLayoutResult-based offset/scroll
 * authority stay unchanged; TextMeasurer runs only when content/style/width changes and Canvas
 * reuses that result during ordinary scrolling.
 */
@Composable
internal fun Text(
    text: AnnotatedString,
    modifier: Modifier,
    style: TextStyle,
    overflow: TextOverflow,
    onTextLayout: (TextLayoutResult) -> Unit,
) {
    val context = LocalContext.current
    val accessibility = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    }
    var selectionMode by remember(text.text) { mutableStateOf(false) }
    if (selectionMode || accessibility.isTouchExplorationEnabled) {
        androidx.compose.material3.Text(
            text = text,
            modifier = modifier,
            style = style,
            overflow = overflow,
            onTextLayout = onTextLayout,
        )
        return
    }

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 4)
    BoxWithConstraints(
        modifier.armSelectionOnLongPress(text.text) { selectionMode = true },
    ) {
        val widthPx = constraints.maxWidth.coerceAtLeast(1)
        val layout = remember(text, style, overflow, widthPx, density.density, density.fontScale) {
            measurer.measure(
                text = text,
                style = style,
                overflow = overflow,
                constraints = Constraints(maxWidth = widthPx),
            )
        }
        LaunchedEffect(layout) { onTextLayout(layout) }
        Canvas(Modifier.fillMaxWidth().height(with(density) { layout.size.height.toDp() })) {
            drawText(layout)
        }
    }
}

private fun Modifier.armSelectionOnLongPress(key: String, onLongPress: () -> Unit): Modifier =
    pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val finishedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: return@withTimeoutOrNull true
                    if (!change.pressed) return@withTimeoutOrNull true
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        return@withTimeoutOrNull true
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                false
            }
            if (finishedEarly == null) onLongPress()
        }
    }

private fun fastSpannable(
    text: AnnotatedString,
    style: TextStyle,
    density: androidx.compose.ui.unit.Density,
    fallbackColor: Color,
): SpannableString {
    val value = SpannableString(text.text)
    text.spanStyles.forEach { range ->
        val start = range.start.coerceIn(0, value.length)
        val end = range.end.coerceIn(start, value.length)
        if (end <= start) return@forEach
        val span = range.item
        if (span.background != Color.Unspecified) value.setSpan(BackgroundColorSpan(span.background.toArgb()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (span.color != Color.Unspecified && span.color != fallbackColor) value.setSpan(ForegroundColorSpan(span.color.toArgb()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if ((span.fontWeight ?: FontWeight.Normal) >= FontWeight.SemiBold) value.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    text.paragraphStyles.forEach { range ->
        val lineHeight = range.item.lineHeight
        if (lineHeight != TextUnit.Unspecified && lineHeight.value > 0f) {
            val start = range.start.coerceIn(0, value.length)
            val end = range.end.coerceIn(start, value.length)
            if (end > start) {
                val heightPx = with(density) { lineHeight.toPx() }.roundToInt().coerceAtLeast(1)
                value.setSpan(FastExactLineHeightSpan(heightPx), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
    val indent = style.textIndent?.firstLine
    if (indent != null && indent != TextUnit.Unspecified && indent.value > 0f) {
        val margin = with(density) { indent.toPx() }.roundToInt().coerceAtLeast(0)
        if (margin > 0) {
            var start = 0
            while (start < value.length) {
                val end = text.text.indexOf('\n', start).let { if (it < 0) value.length else it + 1 }
                if (start < end && text.text[start] != ReaderTypographySpec.PARAGRAPH_SPACER) {
                    value.setSpan(LeadingMarginSpan.Standard(margin, 0), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                start = end
            }
        }
    }
    return value
}

private class FastExactLineHeightSpan(private val heightPx: Int) : LineHeightSpan {
    override fun chooseHeight(
        text: CharSequence,
        start: Int,
        end: Int,
        spanstartv: Int,
        lineHeight: Int,
        fm: Paint.FontMetricsInt,
    ) {
        val original = fm.descent - fm.ascent
        if (original <= 0) return
        val ratio = heightPx.toFloat() / original.toFloat()
        fm.descent = (fm.descent * ratio).roundToInt()
        fm.ascent = fm.descent - heightPx
    }
}
