from pathlib import Path


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, got {count}")
    return text.replace(old, new, 1)

# 1) Continuous: scroll state drives a cached graphics layer, never the layout phase.
screen_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreenV3.kt")
screen = screen_path.read_text()
screen = once(
    screen,
    "import androidx.compose.foundation.gestures.detectTransformGestures\n",
    "import androidx.compose.foundation.gestures.detectTransformGestures\nimport androidx.compose.foundation.gestures.rememberScrollableState\n",
    "scrollable state import",
)
screen = once(
    screen,
    "    val scrollState = rememberScrollState()\n",
    """    var scrollOffsetPx by remember(book.id) { mutableFloatStateOf(0f) }\n    var maxScrollPx by remember(book.id) { mutableFloatStateOf(0f) }\n    val scrollableState = rememberScrollableState { delta ->\n        val previous = scrollOffsetPx\n        val next = (previous - delta).coerceIn(0f, maxScrollPx)\n        scrollOffsetPx = next\n        previous - next\n    }\n""",
    "continuous scroll state",
)
screen = once(
    screen,
    "        scrollState.scrollTo(layout.getLineTop(line).roundToInt().coerceIn(0, scrollState.maxValue))\n",
    "        scrollOffsetPx = layout.getLineTop(line).coerceIn(0f, maxScrollPx)\n",
    "continuous initial offset",
)
screen = once(
    screen,
    "    LaunchedEffect(scrollState, layoutResult, window, viewportHeight, state.autoScrolling) {\n        snapshotFlow { scrollState.value to scrollState.isScrollInProgress }.distinctUntilChanged().collect { (y, scrolling) ->\n",
    "    LaunchedEffect(scrollableState, layoutResult, window, viewportHeight, state.autoScrolling) {\n        snapshotFlow { scrollOffsetPx.roundToInt() to scrollableState.isScrollInProgress }.distinctUntilChanged().collect { (y, scrolling) ->\n",
    "continuous offset observation",
)
screen = once(
    screen,
    "            val nearBottom = scrollState.maxValue > 0 && scrollState.maxValue - y <= edge && w.start + w.map.sourceCodePoints < w.documentLength - 1\n",
    "            val nearBottom = maxScrollPx > 0f && maxScrollPx - y.toFloat() <= edge.toFloat() && w.start + w.map.sourceCodePoints < w.documentLength - 1\n",
    "continuous bottom edge",
)
screen = once(
    screen,
    "                    scrollState.scrollBy(with(density) { (settings.autoScrollSpeedDpPerSecond * seconds).dp.toPx() })\n",
    """                    val deltaPx = with(density) { (settings.autoScrollSpeedDpPerSecond * seconds).dp.toPx() }\n                    scrollOffsetPx = (scrollOffsetPx + deltaPx).coerceIn(0f, maxScrollPx)\n""",
    "continuous auto scroll",
)
screen = once(
    screen,
    "            if (scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue - 1 && w.start + w.map.sourceCodePoints >= w.documentLength - 1) {\n",
    "            if (maxScrollPx > 0f && scrollOffsetPx >= maxScrollPx - 1f && w.start + w.map.sourceCodePoints >= w.documentLength - 1) {\n",
    "continuous end edge",
)
screen = once(
    screen,
    "            Text(annotated, Modifier.fillMaxWidth().verticalScroll(scrollState).padding(horizontal = settings.horizontalPaddingDp.dp, vertical = settings.verticalPaddingDp.dp).widthIn(max = 760.dp), style = style, overflow = TextOverflow.Clip, onTextLayout = { layoutResult = it })\n",
    """            Text(\n                annotated,\n                Modifier.fillMaxSize().padding(horizontal = settings.horizontalPaddingDp.dp, vertical = settings.verticalPaddingDp.dp).widthIn(max = 760.dp),\n                style = style,\n                overflow = TextOverflow.Clip,\n                scrollableState = scrollableState,\n                scrollOffsetPx = { scrollOffsetPx },\n                onScrollRange = { range ->\n                    maxScrollPx = range.toFloat().coerceAtLeast(0f)\n                    scrollOffsetPx = scrollOffsetPx.coerceIn(0f, maxScrollPx)\n                },\n                onTextLayout = { layoutResult = it },\n            )\n""",
    "continuous layer renderer call",
)
screen_path.write_text(screen)

fast_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderFastText.kt")
fast = fast_path.read_text()
fast = once(
    fast,
    "import androidx.compose.foundation.gestures.awaitFirstDown\n",
    """import androidx.compose.foundation.gestures.awaitFirstDown\nimport androidx.compose.foundation.gestures.Orientation\nimport androidx.compose.foundation.gestures.ScrollableState\nimport androidx.compose.foundation.gestures.scrollable\n""",
    "continuous gesture imports",
)
fast = once(
    fast,
    "import androidx.compose.foundation.layout.height\n",
    "import androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.verticalScroll\n",
    "selection fallback scroll imports",
)
fast = once(
    fast,
    "import androidx.compose.ui.graphics.Color\n",
    "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.draw.clipToBounds\nimport androidx.compose.ui.graphics.graphicsLayer\n",
    "layer imports",
)
fast = once(
    fast,
    "import androidx.compose.ui.input.pointer.pointerInput\n",
    "import androidx.compose.ui.input.pointer.pointerInput\nimport androidx.compose.ui.layout.Layout\n",
    "layout import",
)
start = fast.index("/** Continuous keeps the 4K window and TextLayoutResult authority, but measures off the frame thread. */")
end = fast.index("private fun buildFastStaticLayout", start)
continuous = '''/** Continuous measures once, records one static text layer, then scrolls by layer translation only. */\n@Composable\ninternal fun Text(\n    text: AnnotatedString,\n    modifier: Modifier,\n    style: TextStyle,\n    overflow: TextOverflow,\n    scrollableState: ScrollableState,\n    scrollOffsetPx: () -> Float,\n    onScrollRange: (Int) -> Unit,\n    onTextLayout: (TextLayoutResult) -> Unit,\n) {\n    val context = LocalContext.current\n    val accessibility = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }\n    var selectionMode by remember(text.text) { mutableStateOf(false) }\n    if (selectionMode || accessibility.isTouchExplorationEnabled) {\n        val fallbackScroll = rememberScrollState(initial = scrollOffsetPx().roundToInt().coerceAtLeast(0))\n        LaunchedEffect(fallbackScroll.maxValue) { onScrollRange(fallbackScroll.maxValue) }\n        androidx.compose.material3.Text(\n            text = text,\n            modifier = modifier.verticalScroll(fallbackScroll),\n            style = style,\n            overflow = overflow,\n            onTextLayout = onTextLayout,\n        )\n        return\n    }\n    val density = LocalDensity.current\n    val measurer = rememberTextMeasurer(cacheSize = 4)\n    BoxWithConstraints(\n        modifier\n            .fillMaxSize()\n            .clipToBounds()\n            .scrollable(scrollableState, Orientation.Vertical)\n            .armSelectionOnLongPress(text.text) { selectionMode = true },\n    ) {\n        val widthPx = constraints.maxWidth.coerceAtLeast(1)\n        val viewportHeightPx = constraints.maxHeight.coerceAtLeast(1)\n        val layout by produceState<TextLayoutResult?>(null, text, style, overflow, widthPx, density.density, density.fontScale) {\n            value = withContext(Dispatchers.Default) {\n                measurer.measure(text = text, style = style, overflow = overflow, constraints = Constraints(maxWidth = widthPx))\n            }\n        }\n        LaunchedEffect(layout, viewportHeightPx) {\n            layout?.let { ready ->\n                onTextLayout(ready)\n                onScrollRange((ready.size.height - viewportHeightPx).coerceAtLeast(0))\n            }\n        }\n        layout?.let { ready ->\n            Layout(\n                modifier = Modifier.fillMaxSize(),\n                content = {\n                    Canvas(\n                        Modifier\n                            .fillMaxWidth()\n                            .height(with(density) { ready.size.height.toDp() })\n                            .graphicsLayer { translationY = -scrollOffsetPx().coerceAtLeast(0f) },\n                    ) { drawText(ready) }\n                },\n            ) { measurables, viewport ->\n                val width = viewport.maxWidth.coerceAtLeast(1)\n                val height = viewport.maxHeight.coerceAtLeast(1)\n                val placeable = measurables.firstOrNull()?.measure(\n                    Constraints(minWidth = 0, maxWidth = width, minHeight = 0, maxHeight = Constraints.Infinity),\n                )\n                layout(width, height) { placeable?.place(0, 0) }\n            }\n        }\n    }\n}\n\n'''
fast = fast[:start] + continuous + fast[end:]

# 2) Paged heading spans are authoritative in measurement too, so heading-only pages reuse it.
fast = once(
    fast,
    """        val reusable = remember(text, widthPx, heightPx) {\n            if (text.spanStyles.isEmpty()) ReaderPageLayoutCache.reusableLayoutFor(text.text, widthPx, heightPx) else null\n        }\n""",
    """        val reusable = remember(text, widthPx, heightPx) {\n            val headingOnly = text.spanStyles.isNotEmpty() && text.spanStyles.all { range ->\n                val span = range.item\n                span.background == Color.Unspecified &&\n                    span.color == Color.Unspecified &&\n                    (span.fontWeight ?: FontWeight.Normal) >= FontWeight.SemiBold\n            }\n            val measurementCompatible = text.spanStyles.isEmpty() || headingOnly\n            if (measurementCompatible) {\n                ReaderPageLayoutCache.reusableLayoutFor(text.text, widthPx, heightPx, headingOnly)\n            } else null\n        }\n""",
    "heading-compatible measured layout reuse",
)
fast_path.write_text(fast)

typography_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderTypographySpec.kt")
typography = typography_path.read_text()
typography = once(
    typography,
    "    fun androidLayoutText(displayText: String, density: Density): CharSequence {\n",
    "    fun androidLayoutText(displayText: String, density: Density, emphasizeHeadings: Boolean = false): CharSequence {\n",
    "heading-aware android layout signature",
)
typography = once(
    typography,
    """        if (weight != ReaderFontWeight.NORMAL) {\n            value.setSpan(StyleSpan(Typeface.BOLD), 0, value.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)\n        }\n        return value\n""",
    """        if (weight != ReaderFontWeight.NORMAL) {\n            value.setSpan(StyleSpan(Typeface.BOLD), 0, value.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)\n        } else if (emphasizeHeadings) {\n            var cursor = 0\n            displayText.lineSequence().forEach { line ->\n                val end = (cursor + line.length).coerceAtMost(value.length)\n                if (end > cursor && ReaderHeadingClassifier.isHeading(line.trim())) {\n                    value.setSpan(StyleSpan(Typeface.BOLD), cursor, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)\n                }\n                cursor = (end + 1).coerceAtMost(value.length)\n            }\n        }\n        return value\n""",
    "heading span measurement",
)
typography_path.write_text(typography)

engine_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderViewportEngine.kt")
engine = engine_path.read_text()
engine = once(
    engine,
    "    val reusableLayout: StaticLayout? = null,\n",
    "    val reusableLayout: StaticLayout? = null,\n    val reusableHasHeadingStyle: Boolean = false,\n",
    "heading cache metadata",
)
engine = once(
    engine,
    "    fun reusableLayoutFor(visibleText: String, widthPx: Int, heightPx: Int): StaticLayout? {\n",
    "    fun reusableLayoutFor(visibleText: String, widthPx: Int, heightPx: Int, hasHeadingStyle: Boolean): StaticLayout? {\n",
    "heading-aware reusable lookup signature",
)
engine = once(
    engine,
    """                snapshot.reusableHeightPx == heightPx &&\n                snapshot.reusableVisibleText == visibleText\n            ) return snapshot.reusableLayout\n""",
    """                snapshot.reusableHeightPx == heightPx &&\n                snapshot.reusableVisibleText == visibleText &&\n                snapshot.reusableHasHeadingStyle == hasHeadingStyle\n            ) return snapshot.reusableLayout\n""",
    "heading-aware reusable lookup",
)
engine = once(
    engine,
    "            typographyFingerprint = spec.fingerprint,\n",
    "            typographyFingerprint = 31 * spec.fingerprint + settings.emphasizeHeadings.hashCode(),\n",
    "heading cache fingerprint",
)
engine = once(
    engine,
    "        val layoutText = spec.androidLayoutText(displayText, density)\n",
    "        val layoutText = spec.androidLayoutText(displayText, density, settings.emphasizeHeadings)\n",
    "heading-aware measured text",
)
engine = once(
    engine,
    """        val reusable = if (safeColumns == 1 && firstLayout != null) firstLayout else null\n        return PageLayoutSnapshot(\n""",
    """        val reusable = if (safeColumns == 1 && firstLayout != null) firstLayout else null\n        val reusableVisible = if (reusable != null) displayText.substring(0, displayedEnd) else \"\"\n        val reusableHasHeadingStyle = reusable != null && settings.emphasizeHeadings &&\n            reusableVisible.lineSequence().any { ReaderHeadingClassifier.isHeading(it.trim()) }\n        return PageLayoutSnapshot(\n""",
    "heading reusable metadata calculation",
)
engine = once(
    engine,
    """            reusableVisibleText = if (reusable != null) displayText.substring(0, displayedEnd) else \"\",\n            reusableWidthPx = if (reusable != null) columnWidth else 0,\n            reusableHeightPx = if (reusable != null) contentHeight else 0,\n            reusableLayout = reusable,\n""",
    """            reusableVisibleText = reusableVisible,\n            reusableWidthPx = if (reusable != null) columnWidth else 0,\n            reusableHeightPx = if (reusable != null) contentHeight else 0,\n            reusableLayout = reusable,\n            reusableHasHeadingStyle = reusableHasHeadingStyle,\n""",
    "heading reusable snapshot",
)
engine_path.write_text(engine)

# 3) Smart TOC revision cache is complete; never rehydrate global chapters on a cache hit.
panel_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderSmartChaptersPanel.kt")
panel = panel_path.read_text()
panel = once(
    panel,
    """            TocPanelCache.get(key)?.let { cached ->\n                base = cached.base; report = cached.report; loading = false\n                if (!state.chaptersLoaded) actions.onEnsureChapters()\n                return@LaunchedEffect\n            }\n""",
    """            TocPanelCache.get(key)?.let { cached ->\n                // A revision-cache hit is authoritative for this panel. Do not hydrate global chapter\n                // state behind the visible panel; that only recomposes the reader and duplicates data.\n                base = cached.base; report = cached.report; loading = false\n                return@LaunchedEffect\n            }\n""",
    "panel memory-cache hit without global hydrate",
)
panel = once(
    panel,
    """            TocPanelCache.put(key, TocPanelEntry(cachedBase, computed))\n            loading = false\n            if (!state.chaptersLoaded) actions.onEnsureChapters()\n            return@LaunchedEffect\n""",
    """            TocPanelCache.put(key, TocPanelEntry(cachedBase, computed))\n            loading = false\n            return@LaunchedEffect\n""",
    "panel revision-cache hit without global hydrate",
)
panel_path.write_text(panel)

# Lock the new architecture in the source contract.
contract_path = Path("scripts/verify-reader-v3.sh")
contract = contract_path.read_text()
contract = once(
    contract,
    "  apps/android/app/src/main/java/com/junchen/jingdu/ReaderHotControls.kt\n",
    "  apps/android/app/src/main/java/com/junchen/jingdu/ReaderHotControls.kt\n  apps/android/app/src/main/java/com/junchen/jingdu/ReaderFastText.kt\n",
    "fast text required asset",
)
contract = once(
    contract,
    "hot_controls=apps/android/app/src/main/java/com/junchen/jingdu/ReaderHotControls.kt\n",
    "hot_controls=apps/android/app/src/main/java/com/junchen/jingdu/ReaderHotControls.kt\nfast_text=apps/android/app/src/main/java/com/junchen/jingdu/ReaderFastText.kt\n",
    "fast text contract path",
)
contract = once(
    contract,
    "require_literal \"$engine\" 'typographyFingerprint = spec.fingerprint' 'typography fingerprint'\n",
    "require_literal \"$engine\" 'typographyFingerprint = 31 * spec.fingerprint + settings.emphasizeHeadings.hashCode()' 'heading-aware typography fingerprint'\n",
    "heading fingerprint contract",
)
contract = once(
    contract,
    "require_literal \"$engine\" 'androidLayoutText' 'android pagination layout text'\n",
    "require_literal \"$engine\" 'androidLayoutText(displayText, density, settings.emphasizeHeadings)' 'heading-aware android pagination layout text'\n",
    "heading layout contract",
)
contract = once(
    contract,
    "require_literal \"$screen\" 'scrollState.isScrollInProgress' 'continuous gesture settling'\n",
    """require_literal \"$screen\" 'rememberScrollableState' 'continuous layer scroll state'\nrequire_literal \"$screen\" 'scrollableState.isScrollInProgress' 'continuous gesture settling'\nforbid_literal \"$screen\" '.verticalScroll(scrollState)' 'layout-driven continuous scrolling'\nrequire_literal \"$fast_text\" 'graphicsLayer { translationY = -scrollOffsetPx()' 'continuous cached layer translation'\nrequire_literal \"$fast_text\" 'scrollable(scrollableState, Orientation.Vertical)' 'continuous gesture layer'\nrequire_literal \"$engine\" 'reusableHasHeadingStyle' 'heading-aware measured layout reuse'\n""",
    "continuous layer contract",
)
anchor = "require_literal \"$smart_panel\" 'derivedCache.load(book.id, book.normalizedSha256, state.length)' 'derived TOC reuse'\n"
if anchor not in contract:
    raise SystemExit("smart panel contract anchor missing")
contract = contract.replace(
    anchor,
    anchor + "require_literal \"$smart_panel\" 'A revision-cache hit is authoritative for this panel' 'cache hit avoids global chapter hydration'\n",
    1,
)
contract_path.write_text(contract)
