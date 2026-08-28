package com.junchen.jingdu

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Paged normal reading reuses the exact StaticLayout that established the page boundary whenever
 * the visible body has no span that changes its appearance. Styled pages deliberately rebuild so
 * headings, highlights and TTS emphasis remain authoritative. Either way, layout work stays off the
 * frame thread and the Canvas only draws a ready layout.
 */
@Composable
internal fun Text(
    text: AnnotatedString,
    modifier: Modifier,
    style: TextStyle,
    overflow: TextOverflow,
    sourceBase: Long = 0L,
    sourceMap: SourceDisplayMap? = null,
) {
    val context = LocalContext.current
    val accessibility = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var selectionMode by remember(text.text) { mutableStateOf(false) }
    if (selectionMode || accessibility.isTouchExplorationEnabled) {
        val selectable = remember(text, sourceBase, sourceMap) {
            sourceMap?.let { ReaderSelectionController.annotatedForSelection(sourceBase, text, it) } ?: text
        }
        androidx.compose.material3.Text(text = selectable, modifier = modifier, style = style, overflow = overflow)
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
    BoxWithConstraints(modifier.fillMaxWidth().armSelectionOnLongPress(text.text) { selectionMode = true }) {
        val widthPx = constraints.maxWidth.coerceAtLeast(1)
        val heightPx = constraints.maxHeight.coerceAtLeast(1)
        val reusable = remember(text, widthPx, heightPx) {
            if (text.spanStyles.isEmpty()) ReaderPageLayoutCache.reusableLayoutFor(text.text, widthPx, heightPx) else null
        }
        val layout by produceState<StaticLayout?>(reusable, text, style, widthPx, density.density, density.fontScale, nativeTypeface, resolvedColor, reusable) {
            if (reusable == null) {
                value = withContext(Dispatchers.Default) { buildFastStaticLayout(text, style, density, nativeTypeface, resolvedColor, widthPx) }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            layout?.let { ready ->
                // Measurement layouts intentionally carry no palette color. Apply the current reader
                // color immediately before drawing; all reusable pages are plain-body pages.
                ready.paint.color = resolvedColor.toArgb()
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save()
                canvas.clipRect(0f, 0f, size.width, size.height)
                ready.draw(canvas)
                canvas.restore()
            }
        }
    }
}

/** Continuous keeps the 4K window and TextLayoutResult authority, but measures off the frame thread. */
@Composable
internal fun Text(
    text: AnnotatedString,
    modifier: Modifier,
    style: TextStyle,
    overflow: TextOverflow,
    sourceBase: Long = 0L,
    sourceMap: SourceDisplayMap? = null,
    onTextLayout: (TextLayoutResult) -> Unit,
) {
    val context = LocalContext.current
    val accessibility = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var selectionMode by remember(text.text) { mutableStateOf(false) }
    if (selectionMode || accessibility.isTouchExplorationEnabled) {
        val selectable = remember(text, sourceBase, sourceMap) {
            sourceMap?.let { ReaderSelectionController.annotatedForSelection(sourceBase, text, it) } ?: text
        }
        androidx.compose.material3.Text(text = selectable, modifier = modifier, style = style, overflow = overflow, onTextLayout = onTextLayout)
        return
    }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 4)
    BoxWithConstraints(modifier.armSelectionOnLongPress(text.text) { selectionMode = true }) {
        val widthPx = constraints.maxWidth.coerceAtLeast(1)
        val layout by produceState<TextLayoutResult?>(null, text, style, overflow, widthPx, density.density, density.fontScale) {
            value = null
            value = withContext(Dispatchers.Default) {
                measurer.measure(text = text, style = style, overflow = overflow, constraints = Constraints(maxWidth = widthPx))
            }
        }
        LaunchedEffect(layout) { layout?.let(onTextLayout) }
        layout?.let { ready ->
            Canvas(Modifier.fillMaxWidth().height(with(density) { ready.size.height.toDp() })) { drawText(ready) }
        }
    }
}

private fun buildFastStaticLayout(text: AnnotatedString, style: TextStyle, density: androidx.compose.ui.unit.Density, nativeTypeface: Typeface, resolvedColor: Color, widthPx: Int): StaticLayout {
    val fontSizePx = with(density) { style.fontSize.toPx() }.coerceAtLeast(1f)
    val lineHeightMultiplier = if (style.lineHeight == TextUnit.Unspecified || style.lineHeight.value <= 0f || style.fontSize.value <= 0f) 1f else (style.lineHeight.value / style.fontSize.value).coerceAtLeast(1f)
    val letterSpacingEm = if (style.letterSpacing == TextUnit.Unspecified || style.fontSize.value <= 0f) 0f else style.letterSpacing.value / style.fontSize.value
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { color = resolvedColor.toArgb(); textSize = fontSizePx; letterSpacing = letterSpacingEm; typeface = nativeTypeface }
    val rendered = fastSpannable(text, style, density, resolvedColor)
    return StaticLayout.Builder.obtain(rendered, 0, rendered.length, paint, widthPx)
        .setIncludePad(false)
        .setLineSpacing(0f, lineHeightMultiplier)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
        .apply { if (style.textAlign == TextAlign.Justify) setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD) }
        .build()
}

private fun Modifier.armSelectionOnLongPress(key: String, onLongPress: () -> Unit): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val finishedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                if (!change.pressed) return@withTimeoutOrNull true
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE") false
        }
        if (finishedEarly == null) onLongPress()
    }
}

private fun fastSpannable(text: AnnotatedString, style: TextStyle, density: androidx.compose.ui.unit.Density, fallbackColor: Color): SpannableString {
    val value = SpannableString(text.text)
    text.spanStyles.forEach { range ->
        val start = range.start.coerceIn(0, value.length); val end = range.end.coerceIn(start, value.length)
        if (end <= start) return@forEach
        val span = range.item
        if (span.background != Color.Unspecified) value.setSpan(BackgroundColorSpan(span.background.toArgb()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (span.color != Color.Unspecified && span.color != fallbackColor) value.setSpan(ForegroundColorSpan(span.color.toArgb()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if ((span.fontWeight ?: FontWeight.Normal) >= FontWeight.SemiBold) value.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    text.paragraphStyles.forEach { range ->
        val lineHeight = range.item.lineHeight
        if (lineHeight != TextUnit.Unspecified && lineHeight.value > 0f) {
            val start = range.start.coerceIn(0, value.length); val end = range.end.coerceIn(start, value.length)
            if (end > start) value.setSpan(FastExactLineHeightSpan(with(density) { lineHeight.toPx() }.roundToInt().coerceAtLeast(1)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    val indent = style.textIndent?.firstLine
    if (indent != null && indent != TextUnit.Unspecified && indent.value > 0f) {
        val margin = with(density) { indent.toPx() }.roundToInt().coerceAtLeast(0)
        if (margin > 0) {
            var start = 0
            while (start < value.length) {
                val end = text.text.indexOf('\n', start).let { if (it < 0) value.length else it + 1 }
                if (start < end && text.text[start] != ReaderTypographySpec.PARAGRAPH_SPACER) value.setSpan(LeadingMarginSpan.Standard(margin, 0), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                start = end
            }
        }
    }
    return value
}

private class FastExactLineHeightSpan(private val heightPx: Int) : LineHeightSpan {
    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt) {
        val original = fm.descent - fm.ascent
        if (original <= 0) return
        val ratio = heightPx.toFloat() / original.toFloat()
        fm.descent = (fm.descent * ratio).roundToInt(); fm.ascent = fm.descent - heightPx
    }
}
