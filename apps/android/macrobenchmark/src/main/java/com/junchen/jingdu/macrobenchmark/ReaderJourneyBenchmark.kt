package com.junchen.jingdu.macrobenchmark

import android.view.KeyEvent
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ReaderJourneyBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    /** PR-sized long-text launch/open journey. */
    @Test fun open10MiBTxt() = openJourney(fixtureMiB = 10, iterations = 5)

    /** V3 soak-sized long-text launch/open journey. Kept deliberately short but real. */
    @Test fun open100MiBTxt() = openJourney(fixtureMiB = 100, iterations = 2)

    @Test fun pageTurn10MiB() = interactionJourney(
        name = "page-turn",
        fixtureMiB = 10,
        prepareBlock = { setReaderMode("paged") },
    ) {
        val before = readerPosition()
        var previous = before
        check(previous >= 0) { "Reader V3 page-turn journey has no authoritative starting position: $previous" }
        repeat(6) {
            // Use Android's real shell input path rather than UiAutomator's key wrapper. This still
            // traverses InputDispatcher -> Activity dispatch, but is stable on hosted emulators.
            device.executeShellCommand("input keyevent ${KeyEvent.KEYCODE_VOLUME_DOWN}")
            previous = waitForReaderAdvance(previous)
        }
        val after = readerPosition()
        check(after > before) {
            "Reader V3 volume-key journey did not advance overall: before=$before after=$after"
        }
    }

    @Test fun continuousScroll10MiB() = interactionJourney(
        name = "continuous-scroll",
        fixtureMiB = 10,
        prepareBlock = { setReaderMode("continuous") },
        afterOpenPrepareBlock = { waitForContinuousReady() },
    ) {
        repeat(6) {
            check(device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.80).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.25).toInt(),
                24,
            )) { "Reader V3 continuous-scroll swipe was not injected" }
        }
        device.waitForIdle()
    }

    @Test fun chaptersAndSettings10MiB() = interactionJourney(
        name = "chapters-settings",
        fixtureMiB = 10,
        prepareBlock = { setReaderMode("paged") },
    ) {
        repeat(2) {
            ensureTopControlsVisible()
            requireChaptersClick()
            device.waitForIdle()
            device.executeShellCommand("input keyevent ${KeyEvent.KEYCODE_BACK}")
            device.waitForIdle()
            ensureTopControlsVisible()
            requireClick(By.text("Aa"), "quick reading settings control")
            device.waitForIdle()
            device.executeShellCommand("input keyevent ${KeyEvent.KEYCODE_BACK}")
            device.waitForIdle()
        }
    }

    private fun openJourney(fixtureMiB: Int, iterations: Int) = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CI_COMPILATION_MODE,
        startupMode = StartupMode.WARM,
        iterations = iterations,
        setupBlock = {
            pressHome()
            seedFixture(fixtureMiB)
            setReaderMode("paged")
        },
        measureBlock = {
            startTargetAndWait()
            openFixture(fixtureMiB)
        },
    )

    private fun interactionJourney(
        @Suppress("UNUSED_PARAMETER") name: String,
        fixtureMiB: Int,
        iterations: Int = 5,
        prepareBlock: MacrobenchmarkScope.() -> Unit = {},
        afterOpenPrepareBlock: MacrobenchmarkScope.() -> Unit = {},
        block: MacrobenchmarkScope.() -> Unit,
    ) = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = frameMetrics(),
        compilationMode = CI_COMPILATION_MODE,
        startupMode = StartupMode.WARM,
        iterations = iterations,
        setupBlock = {
            pressHome()
            seedFixture(fixtureMiB)
            prepareBlock()
            startTargetAndWait()
            openFixture(fixtureMiB)
            afterOpenPrepareBlock()
        },
        measureBlock = block,
    )

    private fun MacrobenchmarkScope.seedFixture(mib: Int) {
        val result = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method seed --arg $mib",
        )
        check(result.contains("bytes=")) { "Reader V3 benchmark fixture seed failed: $result" }
    }

    private fun MacrobenchmarkScope.setReaderMode(mode: String) {
        val result = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method mode --arg $mode",
        )
        check(result.contains("Result: Bundle[{}]")) { "Reader V3 benchmark mode setup failed: $result" }
    }

    private fun MacrobenchmarkScope.readerPosition(): Long {
        val result = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method position",
        )
        return Regex("""position=(-?\d+)""").find(result)?.groupValues?.get(1)?.toLongOrNull()
            ?: error("Reader V3 benchmark position query failed: $result")
    }

    private fun MacrobenchmarkScope.waitForReaderAdvance(before: Long): Long {
        val deadline = System.nanoTime() + INPUT_STATE_TIMEOUT_NS
        var after = before
        while (System.nanoTime() < deadline) {
            device.waitForIdle()
            after = readerPosition()
            if (after > before) return after
            Thread.sleep(INPUT_POLL_MS)
        }
        error(
            "Reader V3 volume-key journey used the real shell input path but did not advance the authoritative reader position: " +
                "before=$before after=$after",
        )
    }

    private fun MacrobenchmarkScope.waitForContinuousReady() {
        val deadline = System.nanoTime() + CONTINUOUS_READY_TIMEOUT_NS
        var result = ""
        while (System.nanoTime() < deadline) {
            result = device.executeShellCommand(
                "content call --uri content://com.junchen.jingdu.benchmarkfixture --method continuousReady",
            )
            if (Regex("""ready=(\d+)""").find(result)?.groupValues?.get(1) == "1") {
                device.waitForIdle()
                return
            }
            Thread.sleep(CONTINUOUS_READY_POLL_MS)
        }
        error("Reader V3 continuous viewport did not publish layout/raster readiness: $result")
    }

    private fun MacrobenchmarkScope.startTargetAndWait() {
        try {
            startActivityAndWait()
        } catch (error: IllegalStateException) {
            throw IllegalStateException(
                buildString {
                    append(error.message ?: "Reader V3 target launch failed")
                    append("\n===== Reader V3 target diagnostics =====\n")
                    append(failureDiagnostics())
                },
                error,
            )
        }
    }

    private fun MacrobenchmarkScope.openFixture(mib: Int) {
        val title = "Benchmark Novel $mib MiB"
        if (!device.wait(Until.hasObject(By.textContains(title)), 8_000)) {
            error("fixture card missing: $title\n===== Reader V3 target diagnostics =====\n${failureDiagnostics()}")
        }
        val card = device.findObject(By.textContains(title)) ?: error("fixture card unavailable: $title")
        card.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.failureDiagnostics(): String = buildString {
        append("pidof: ")
        append(device.executeShellCommand("pidof $PACKAGE_NAME").trim().ifEmpty { "<not-running>" })
        append('\n')
        append("exit-info:\n")
        append(device.executeShellCommand("dumpsys activity exit-info $PACKAGE_NAME").takeLast(12_000))
        append("\nlogcat:\n")
        append(
            device.executeShellCommand(
                "logcat -d -t 300 AndroidRuntime:E ActivityManager:I ActivityTaskManager:I '*:S'",
            ).takeLast(20_000),
        )
    }

    private fun MacrobenchmarkScope.ensureTopControlsVisible() {
        if (device.wait(Until.hasObject(By.text("Aa")), 750)) return
        val taps = listOf(0.50f to 0.52f, 0.50f to 0.68f, 0.50f to 0.36f)
        repeat(2) {
            for ((x, y) in taps) {
                val px = (device.displayWidth * x).toInt()
                val py = (device.displayHeight * y).toInt()
                // Match the key journey: use the platform input command so Compose sees a normal
                // DOWN/UP stream even when hosted UiAutomator click injection is flaky.
                device.executeShellCommand("input tap $px $py")
                if (device.wait(Until.hasObject(By.text("Aa")), 1_100)) return
            }
        }
        error("Reader V3 top reading controls did not become visible after real shell tap input")
    }

    private fun MacrobenchmarkScope.requireClick(selector: BySelector, label: String) {
        check(device.wait(Until.hasObject(selector), 3_000)) { "Reader V3 $label missing" }
        val target = device.findObject(selector) ?: error("Reader V3 $label unavailable")
        target.click()
    }

    private fun MacrobenchmarkScope.requireChaptersClick() {
        device.findObject(By.desc("Chapters"))?.let { target -> target.click(); return }
        device.findObject(By.descContains("Chapter"))?.let { target -> target.click(); return }

        val aa = device.findObjects(By.text("Aa")).minByOrNull { it.visibleBounds.centerY() }
            ?: error("Reader V3 top reading controls missing")
        val anchor = aa.visibleBounds
        val candidate = device.findObjects(By.clickable(true))
            .asSequence()
            .filter { node ->
                val bounds = node.visibleBounds
                bounds.centerX() > anchor.centerX() &&
                    abs(bounds.centerY() - anchor.centerY()) <= maxOf(anchor.height(), bounds.height())
            }
            .minByOrNull { node -> node.visibleBounds.centerX() - anchor.centerX() }
            ?: error("Reader V3 chapters control missing beside Aa")
        candidate.click()
    }

    private fun frameMetrics(): List<Metric> = listOf(FrameTimingMetric())

    private companion object {
        const val PACKAGE_NAME = "com.junchen.jingdu"
        const val INPUT_POLL_MS = 25L
        const val CONTINUOUS_READY_POLL_MS = 50L
        const val INPUT_STATE_TIMEOUT_NS = 2_500_000_000L
        const val CONTINUOUS_READY_TIMEOUT_NS = 12_000_000_000L

        // This target APK already contains the curated, provenance-checked product Baseline Profile.
        // Android's Macrobenchmark contract defines Partial + Require as the fresh-install state of an
        // app partially precompiled by the installer (for example Google Play). The separately run
        // BaselineProfileGenerator below is freshness evidence only and never feeds this same CI run.
        val CI_COMPILATION_MODE = CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Require,
            warmupIterations = 0,
        )
    }
}
