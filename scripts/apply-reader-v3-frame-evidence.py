from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"{label} drifted")
    return text.replace(old, new, 1)

# Paged rendering was already comfortably within the SLO before the native wrapper cut. Keep the
# worker-built/reused StaticLayout, but replay it through Compose Canvas so FrameTimingMetric sees
# normal hardware redraws on every page change.
fast_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderFastText.kt")
fast = fast_path.read_text()
old_paged = '''        AndroidView(
            factory = { ReaderPagedTextView(it) },
            modifier = Modifier.fillMaxSize(),
            update = { view -> view.setTextLayout(layout, resolvedColor.toArgb()) },
        )
    }
}

private class ReaderPagedTextView(context: Context) : View(context) {
    private var textLayout: StaticLayout? = null
    private var color: Int = 0
    private var recorded: ReaderStaticLayoutRenderNode? = null

    fun setTextLayout(layout: StaticLayout?, nextColor: Int) {
        val changed = textLayout !== layout || color != nextColor
        if (!changed) return
        textLayout = layout
        color = nextColor
        if (layout == null) {
            recorded = null
            invalidate()
            return
        }
        layout.paint.color = nextColor
        if (Build.VERSION.SDK_INT >= 29) {
            recorded = (recorded ?: ReaderStaticLayoutRenderNode()).also { it.record(layout) }
        }
        invalidate()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.clipRect(0, 0, width, height)
        if (Build.VERSION.SDK_INT < 29 || recorded?.draw(canvas) != true) textLayout?.draw(canvas)
        canvas.restore()
    }
}
'''
new_paged = '''        Canvas(Modifier.fillMaxSize()) {
            layout?.let { ready ->
                // The layout is already measured off-main. Canvas replay is deliberately retained
                // for paged mode so page changes create normal RenderThread-observable frames.
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
'''
fast = replace_once(fast, old_paged, new_paged, "paged observable Canvas replay")
fast = replace_once(
    fast,
    '''    private val applyPendingScroll = Runnable {
        scrollScheduled = false
        val translation = -pendingScrollY.toFloat()
        if (content.translationY != translation) content.translationY = translation
    }
''',
    '''    private val applyPendingScroll = Runnable {
        scrollScheduled = false
        val translation = -pendingScrollY.toFloat()
        if (content.translationY != translation) {
            content.translationY = translation
            // View-property translation keeps the recorded text display list intact. Explicitly
            // invalidate the tiny wrapper once per vsync so FrameTimingMetric observes each real
            // swipe frame instead of seeing only the first/last property transaction.
            content.postInvalidateOnAnimation()
        }
    }
''',
    "continuous observable frame commit",
)
fast_path.write_text(fast)

# A hidden full-screen panel must not consume reader input. Keep complete subtree display-list
# caching, but move hidden panels offscreen through a layer transform (the pre-#736 interaction
# behavior) so center taps can reliably restore the Aa controls and UIAutomator semantics.
app_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/JingduApp.kt")
app = app_path.read_text()
old_guard = '''            .semantics { if (panelState.value != target) hideFromAccessibility() }
            .pointerInput(panelState, target) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (panelState.value != target) event.changes.forEach { it.consume() }
                    }
                }
            }
            .drawWithContent panelDraw@{
'''
new_guard = '''            .graphicsLayer {
                val visible = panelState.value == target
                translationY = if (visible) 0f else READER_PANEL_HIDDEN_OFFSET_PX.toFloat()
                alpha = if (visible) 1f else 0f
            }
            .semantics { if (panelState.value != target) hideFromAccessibility() }
            .drawWithContent panelDraw@{
'''
app = replace_once(app, old_guard, new_guard, "hidden panel input isolation")
app = replace_once(
    app,
    '''}

@Composable private fun BusyOverlay(label: String) {
''',
    '''}

private const val READER_PANEL_HIDDEN_OFFSET_PX = 16_384

@Composable private fun BusyOverlay(label: String) {
''',
    "panel hidden offset constant",
)
app_path.write_text(app)

# Strengthen the performance gate: a fast percentile from two frames is invalid evidence. Require
# the historically established order of magnitude for each real interaction journey.
slo_path = Path("scripts/check-android-performance-slo.py")
slo = slo_path.read_text()
slo = replace_once(
    slo,
    '''from typing import Any, Iterable


''',
    '''from typing import Any, Iterable


REQUIRED_MIN_SAMPLES = {
    "pageTurn10MiB": 20,
    "continuousScroll10MiB": 500,
    "chaptersAndSettings10MiB": 50,
}


''',
    "minimum sample contract",
)
slo = replace_once(
    slo,
    '''def collect_files(paths: list[str]) -> list[pathlib.Path]:
''',
    '''def required_sample_failures(sample_sets: list[tuple[str, list[float]]]) -> list[str]:
    failures: list[str] = []
    for suffix, minimum in REQUIRED_MIN_SAMPLES.items():
        counts = [len(samples) for label, samples in sample_sets if label.endswith(suffix)]
        if not counts:
            failures.append(f"missing required frame evidence: {suffix}")
            continue
        observed = max(counts)
        if observed < minimum:
            failures.append(
                f"insufficient frame evidence: {suffix} samples={observed} minimum={minimum}"
            )
    return failures


def collect_files(paths: list[str]) -> list[pathlib.Path]:
''',
    "sample validation helper",
)
slo = replace_once(
    slo,
    '''    rows: list[tuple[str, str, int, float, float, int]] = []
    limits = {95: args.p95_ms, 99: args.p99_ms}
''',
    '''    rows: list[tuple[str, str, int, float, float, int]] = []
    all_sample_sets: list[tuple[str, list[float]]] = []
    limits = {95: args.p95_ms, 99: args.p99_ms}
''',
    "sample set accumulator",
)
slo = replace_once(
    slo,
    '''        for benchmark, samples in frame_sample_sets(payload):
            for percentile in (95, 99):
''',
    '''        sample_sets = frame_sample_sets(payload)
        all_sample_sets.extend(sample_sets)
        for benchmark, samples in sample_sets:
            for percentile in (95, 99):
''',
    "accumulate sample sets",
)
slo = replace_once(
    slo,
    '''    if not rows:
        print("performance SLO: no sampledMetrics.frameDurationCpuMs.runs evidence found", file=sys.stderr)
        return 2

    # Every frame-producing benchmark is independently gated. This prevents a fast journey from
''',
    '''    if not rows:
        print("performance SLO: no sampledMetrics.frameDurationCpuMs.runs evidence found", file=sys.stderr)
        return 2

    evidence_failures = required_sample_failures(all_sample_sets)
    if evidence_failures:
        for failure in evidence_failures:
            print(f"performance SLO: {failure}", file=sys.stderr)
        return 2

    # Every frame-producing benchmark is independently gated. This prevents a fast journey from
''',
    "enforce sample validity",
)
slo_path.write_text(slo)

test_path = Path("scripts/test-android-performance-slo.py")
test = test_path.read_text()
test = replace_once(
    test,
    '''    def test_real_shape_file_discovery(self) -> None:
''',
    '''    def test_required_interaction_sample_counts_reject_truncated_evidence(self) -> None:
        valid = [
            ("ReaderJourneyBenchmark.pageTurn10MiB", [1.0] * 20),
            ("ReaderJourneyBenchmark.continuousScroll10MiB", [1.0] * 500),
            ("ReaderJourneyBenchmark.chaptersAndSettings10MiB", [1.0] * 50),
        ]
        self.assertEqual([], slo.required_sample_failures(valid))
        truncated = [
            ("ReaderJourneyBenchmark.pageTurn10MiB", [1.0] * 20),
            ("ReaderJourneyBenchmark.continuousScroll10MiB", [1.0] * 2),
        ]
        failures = slo.required_sample_failures(truncated)
        self.assertTrue(any("continuousScroll10MiB samples=2" in failure for failure in failures))
        self.assertTrue(any("missing required frame evidence: chaptersAndSettings10MiB" in failure for failure in failures))

    def test_real_shape_file_discovery(self) -> None:
''',
    "sample validity unit test",
)
test_path.write_text(test)

verify_path = Path("scripts/verify-reader-v3.sh")
verify = verify_path.read_text()
verify = verify.replace("require_literal \"$fast_text\" 'ReaderPagedTextView' 'paged native RenderNode view'\n", "forbid_literal \"$fast_text\" 'ReaderPagedTextView' 'paged native wrapper that bypasses FrameTiming redraw'\n")
verify = verify.replace("require_literal \"$fast_text\" 'AndroidView(' 'paged AndroidView renderer'\n", "require_literal \"$fast_text\" 'Canvas(Modifier.fillMaxSize())' 'paged observable Canvas replay'\n")
verify = verify.replace("require_literal \"$fast_text\" 'ReaderStaticLayoutRenderNode' 'shared paged/continuous RenderNode display list'\n", "require_literal \"$fast_text\" 'ReaderStaticLayoutRenderNode' 'continuous RenderNode display list'\n")
verify = verify.replace("require_literal \"$fast_text\" 'content.translationY = -pendingScrollY.toFloat()' 'continuous child RenderNode translation'\n", "require_literal \"$fast_text\" 'content.translationY = translation' 'continuous child RenderNode translation'\nrequire_literal \"$fast_text\" 'content.postInvalidateOnAnimation()' 'observable continuous frame commit'\n")
verify = verify.replace("require_literal \"$app\" 'awaitPointerEvent(PointerEventPass.Initial)' 'hidden hot-panel pointer guard'\n", "forbid_literal \"$app\" 'awaitPointerEvent(PointerEventPass.Initial)' 'hidden panel swallowing reader input'\nrequire_literal \"$app\" 'translationY = if (visible) 0f else READER_PANEL_HIDDEN_OFFSET_PX.toFloat()' 'offscreen hidden hot-panel hit isolation'\n")
verify += '''\n# Frame evidence itself is a contract: truncated two-frame traces can never satisfy the SLO.\nrequire_literal scripts/check-android-performance-slo.py 'REQUIRED_MIN_SAMPLES' 'performance evidence minimums'\nrequire_literal scripts/check-android-performance-slo.py '"pageTurn10MiB": 20' 'page-turn evidence floor'\nrequire_literal scripts/check-android-performance-slo.py '"continuousScroll10MiB": 500' 'continuous evidence floor'\nrequire_literal scripts/check-android-performance-slo.py '"chaptersAndSettings10MiB": 50' 'panel evidence floor'\nrequire_literal scripts/test-android-performance-slo.py 'test_required_interaction_sample_counts_reject_truncated_evidence' 'performance evidence regression test'\n'''
verify_path.write_text(verify)
