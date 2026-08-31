#!/usr/bin/env python3
from pathlib import Path

screen = Path('apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt')
text = screen.read_text(encoding='utf-8')

old = 'import androidx.compose.ui.layout.onSizeChanged\n'
new = 'import androidx.compose.ui.layout.layout\nimport androidx.compose.ui.layout.onSizeChanged\n'
assert old in text and 'import androidx.compose.ui.layout.layout\n' not in text
text = text.replace(old, new, 1)

old = 'import androidx.compose.ui.semantics.customActions\nimport androidx.compose.ui.semantics.semantics\n'
new = 'import androidx.compose.ui.semantics.customActions\nimport androidx.compose.ui.semantics.hideFromAccessibility\nimport androidx.compose.ui.semantics.semantics\n'
assert old in text and 'import androidx.compose.ui.semantics.hideFromAccessibility\n' not in text
text = text.replace(old, new, 1)

old = '''        // Keep hot controls composed for the whole reader session. Visibility changes are a
        // layout-phase placement only, so reopening controls never rebuilds the Material button tree.
        Box(
            Modifier.align(Alignment.TopCenter).graphicsLayer {
                translationY = if (controlsVisible) 0f else -READER_HIDDEN_LAYER_OFFSET_PX.toFloat()
                alpha = if (controlsVisible) 1f else 0f
            },
        ) {
            ReaderTopBar(book.name, currentChapter, actions) { more = true }
        }
        if (more) Box(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 56.dp, end = 8.dp).graphicsLayer {
                translationY = if (controlsVisible) 0f else -READER_HIDDEN_LAYER_OFFSET_PX.toFloat()
                alpha = if (controlsVisible) 1f else 0f
            },
        ) { ReaderMoreMenu(state.cleanMode, actions) { more = false } }
        Box(
            Modifier.align(Alignment.BottomCenter).graphicsLayer {
                translationY = if (controlsVisible) 0f else READER_HIDDEN_LAYER_OFFSET_PX.toFloat()
                alpha = if (controlsVisible) 1f else 0f
            },
        ) {
'''
new = '''        // Keep hot controls composed for the whole reader session, but move hidden controls in
        // layout space rather than only transforming their pixels. Visual bounds, pointer hit testing
        // and accessibility therefore share one authoritative placement while reopening stays cheap.
        Box(
            Modifier.align(Alignment.TopCenter)
                .readerControlLayer(controlsVisibility, -READER_HIDDEN_LAYER_OFFSET_PX),
        ) {
            ReaderTopBar(book.name, currentChapter, actions) { more = true }
        }
        if (more) Box(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 56.dp, end = 8.dp)
                .readerControlLayer(controlsVisibility, -READER_HIDDEN_LAYER_OFFSET_PX),
        ) { ReaderMoreMenu(state.cleanMode, actions) { more = false } }
        Box(
            Modifier.align(Alignment.BottomCenter)
                .readerControlLayer(controlsVisibility, READER_HIDDEN_LAYER_OFFSET_PX),
        ) {
'''
assert old in text
text = text.replace(old, new, 1)

marker = 'private fun Modifier.readerAccessibilityActions(\n'
helper = '''/**
 * Reader chrome stays resident, but hidden chrome is physically placed off-screen instead of only
 * receiving a graphics transform. This mirrors PersistentReaderPanelLayer: layout, hit testing and
 * accessibility all agree on visibility while placeWithLayer keeps reopening allocation-free.
 */
private fun Modifier.readerControlLayer(
    visibility: State<Boolean>,
    hiddenOffsetPx: Int,
): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            val visible = visibility.value
            placeable.placeWithLayer(
                x = 0,
                y = if (visible) 0 else hiddenOffsetPx,
            ) { alpha = if (visible) 1f else 0f }
        }
    }
    .semantics { if (!visibility.value) hideFromAccessibility() }

'''
assert marker in text and 'private fun Modifier.readerControlLayer(' not in text
text = text.replace(marker, helper + marker, 1)
screen.write_text(text, encoding='utf-8')

verify = Path('scripts/verify-reader.sh')
text = verify.read_text(encoding='utf-8')
old = '''require_literal "$screen" 'graphicsLayer {' 'RenderNode reader controls'
require_literal "$screen" 'READER_HIDDEN_LAYER_OFFSET_PX' 'resident reader controls'
require_literal "$screen" 'snapshotFlow { controlsVisible }' 'layer-only controls visibility'
'''
new = '''require_literal "$screen" 'private fun Modifier.readerControlLayer(' 'resident reader control placement owner'
require_literal "$screen" 'placeable.placeWithLayer(' 'layer-backed reader control placement'
require_literal "$screen" 'y = if (visible) 0 else hiddenOffsetPx' 'layout-owned reader control visibility'
require_literal "$screen" 'hideFromAccessibility()' 'hidden reader control accessibility isolation'
require_literal "$screen" '.readerControlLayer(controlsVisibility, -READER_HIDDEN_LAYER_OFFSET_PX)' 'top reader control placement'
require_literal "$screen" '.readerControlLayer(controlsVisibility, READER_HIDDEN_LAYER_OFFSET_PX)' 'bottom reader control placement'
forbid_literal "$screen" 'translationY = if (controlsVisible) 0f else -READER_HIDDEN_LAYER_OFFSET_PX.toFloat()' 'graphics-only top control hiding'
forbid_literal "$screen" 'translationY = if (controlsVisible) 0f else READER_HIDDEN_LAYER_OFFSET_PX.toFloat()' 'graphics-only bottom control hiding'
require_literal "$screen" 'READER_HIDDEN_LAYER_OFFSET_PX' 'resident reader controls'
require_literal "$screen" 'snapshotFlow { controlsVisible }' 'layer-only controls visibility'
'''
assert old in text
text = text.replace(old, new, 1)
verify.write_text(text, encoding='utf-8')
