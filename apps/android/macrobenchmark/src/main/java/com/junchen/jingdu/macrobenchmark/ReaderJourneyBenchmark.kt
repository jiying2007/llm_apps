package com.junchen.jingdu.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
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
        repeat(6) {
            check(device.swipe(
                (device.displayWidth * 0.78).toInt(),
                (device.displayHeight * 0.52).toInt(),
                (device.displayWidth * 0.22).toInt(),
                (device.displayHeight * 0.52).toInt(),
                24,
            )) { "Reader V3 page-turn swipe was not injected" }
            device.waitForIdle()
        }
    }

    @Test fun continuousScroll10MiB() = interactionJourney(
        name = "continuous-scroll",
        fixtureMiB = 10,
        prepareBlock = { setReaderMode("continuous") },
    ) {
        repeat(6) {
            check(device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.80).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.25).toInt(),
                32,
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
            requireClick(By.descContains("Chapter"), "chapters control")
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()
            requireClick(By.text("Aa"), "quick reading settings control")
            device.waitForIdle()
            device.pressBack()
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
            startActivityAndWait()
            openFixture(fixtureMiB)
        },
    )

    private fun interactionJourney(
        @Suppress("UNUSED_PARAMETER") name: String,
        fixtureMiB: Int,
        iterations: Int = 5,
        prepareBlock: androidx.benchmark.macro.MacrobenchmarkScope.() -> Unit = {},
        block: androidx.benchmark.macro.MacrobenchmarkScope.() -> Unit,
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
            startActivityAndWait()
            openFixture(fixtureMiB)
            ensureReaderControls()
        },
        measureBlock = block,
    )

    private fun androidx.benchmark.macro.MacrobenchmarkScope.seedFixture(mib: Int) {
        val result = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method seed --arg $mib",
        )
        check(result.contains("bytes=")) { "Reader V3 benchmark fixture seed failed: $result" }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.setReaderMode(mode: String) {
        val expected = mode.uppercase()
        val result = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method mode --arg $mode",
        )
        check(result.contains("mode=$expected")) { "Reader V3 benchmark mode setup failed: $result" }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.openFixture(mib: Int) {
        val title = "Benchmark Novel $mib MiB"
        check(device.wait(Until.hasObject(By.textContains(title)), 8_000)) { "fixture card missing: $title" }
        val card = device.findObject(By.textContains(title)) ?: error("fixture card unavailable: $title")
        card.click()
        device.waitForIdle()
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.ensureReaderControls() {
        if (!device.wait(Until.hasObject(By.text("Aa")), 1_500)) {
            device.click(device.displayWidth / 2, device.displayHeight / 2)
        }
        check(device.wait(Until.hasObject(By.text("Aa")), 3_000)) { "Reader V3 controls did not become visible" }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.requireClick(selector: BySelector, label: String) {
        check(device.wait(Until.hasObject(selector), 3_000)) { "Reader V3 $label missing" }
        val target = device.findObject(selector) ?: error("Reader V3 $label unavailable")
        target.click()
    }

    private fun frameMetrics(): List<Metric> = listOf(FrameTimingMetric())

    private companion object {
        const val PACKAGE_NAME = "com.junchen.jingdu"

        // The CI regression gate must not require a profile that this same job generates later.
        // One warmup iteration provides a repeatable partial/JIT state while BaselineProfileRule
        // remains the separate authority for generating and archiving the real Reader V3 profile.
        val CI_COMPILATION_MODE = CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Disable,
            warmupIterations = 1,
        )
    }
}
