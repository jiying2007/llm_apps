from pathlib import Path

p = Path('apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt')
text = p.read_text(encoding='utf-8')

old = '            requireClick(By.desc("Reading settings"), "quick reading settings control")\n'
new = '            requireReadingSettingsClick()\n'
assert old in text
text = text.replace(old, new, 1)

start = text.index('    private fun MacrobenchmarkScope.ensureTopControlsVisible() {')
end = text.index('    private fun frameMetrics(): List<Metric>', start)
replacement = '''    private fun MacrobenchmarkScope.readerTopControlsVisible(): Boolean =
        visibleObject(By.desc("Reading settings")) != null ||
            visibleObject(By.desc("Chapters")) != null ||
            visibleObject(By.textContains("Benchmark Novel")) != null

    private fun MacrobenchmarkScope.waitForTopControlsVisible(timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (System.nanoTime() < deadline) {
            if (readerTopControlsVisible()) return true
            Thread.sleep(INPUT_POLL_MS)
        }
        return readerTopControlsVisible()
    }

    private fun MacrobenchmarkScope.ensureTopControlsVisible() {
        if (readerTopControlsVisible()) return
        val taps = listOf(0.50f to 0.52f, 0.50f to 0.68f, 0.50f to 0.36f)
        repeat(2) {
            for ((x, y) in taps) {
                val px = (device.displayWidth * x).toInt()
                val py = (device.displayHeight * y).toInt()
                check(device.click(px, py)) { "Reader top-control tap was not injected through UiDevice" }
                if (waitForTopControlsVisible(1_100L)) return
            }
        }
        error(
            "Reader top reading controls did not become visibly on-screen after real UiDevice tap input; " +
                "runtime=${readerInputState()}",
        )
    }

    private fun MacrobenchmarkScope.requireClick(selector: BySelector, label: String) {
        check(device.wait(Until.hasObject(selector), 3_000)) { "Reader $label missing" }
        val target = visibleObject(selector) ?: error("Reader $label exists only off-screen")
        val bounds = runCatching { target.visibleBounds }.getOrNull() ?: error("Reader $label became stale before input")
        check(device.click(bounds.centerX(), bounds.centerY())) { "Reader $label tap was not injected" }
    }

    private fun MacrobenchmarkScope.visibleTopActionNodes(): List<UiObject2> {
        val title = visibleObject(By.textContains("Benchmark Novel")) ?: return emptyList()
        val titleBounds = runCatching { title.visibleBounds }.getOrNull() ?: return emptyList()
        return device.findObjects(By.clickable(true))
            .asSequence()
            .filter { node -> runCatching { isOnScreen(node) }.getOrDefault(false) }
            .filter { node ->
                val bounds = runCatching { node.visibleBounds }.getOrNull() ?: return@filter false
                bounds.centerX() > titleBounds.centerX() &&
                    abs(bounds.centerY() - titleBounds.centerY()) <= maxOf(titleBounds.height(), bounds.height())
            }
            .sortedBy { node -> runCatching { node.visibleBounds.centerX() }.getOrDefault(Int.MAX_VALUE) }
            .toList()
    }

    private fun MacrobenchmarkScope.clickNode(target: UiObject2, label: String) {
        val bounds = runCatching { target.visibleBounds }.getOrNull() ?: error("Reader $label became stale")
        check(device.click(bounds.centerX(), bounds.centerY())) { "Reader $label tap was not injected" }
    }

    private fun MacrobenchmarkScope.requireReadingSettingsClick() {
        visibleObject(By.desc("Reading settings"))?.let { target ->
            clickNode(target, "quick reading settings control")
            return
        }
        val target = visibleTopActionNodes().firstOrNull()
            ?: error("Reader visible quick reading settings control missing from top action row")
        clickNode(target, "quick reading settings control")
    }

    private fun MacrobenchmarkScope.requireChaptersClick() {
        visibleObject(By.desc("Chapters"))?.let { target ->
            clickNode(target, "chapters control")
            return
        }
        visibleObject(By.descContains("Chapter"))?.let { target ->
            clickNode(target, "chapters control")
            return
        }
        val actions = visibleTopActionNodes()
        val target = actions.getOrNull(1)
            ?: error("Reader visible chapters control missing from top action row: actions=${actions.size}")
        clickNode(target, "chapters control")
    }

'''
text = text[:start] + replacement + text[end:]
p.write_text(text, encoding='utf-8')
