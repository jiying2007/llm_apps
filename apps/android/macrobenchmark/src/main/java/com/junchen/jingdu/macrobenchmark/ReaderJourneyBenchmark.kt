package com.junchen.jingdu.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
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

    @Test fun pageTurn10MiB() = interactionJourney("page-turn", fixtureMiB = 10) {
        repeat(8) {
            device.click((device.displayWidth * 0.86).toInt(), (device.displayHeight * 0.52).toInt())
            device.waitForIdle()
        }
    }

    @Test fun continuousScroll10MiB() = interactionJourney("continuous-scroll", fixtureMiB = 10) {
        device.findObject(By.text("Aa"))?.click()
        device.wait(Until.hasObject(By.textContains("Continuous")), 2_000)
        device.findObject(By.textContains("Continuous"))?.click()
        device.pressBack()
        repeat(6) {
            device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.80).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.25).toInt(),
                20,
            )
        }
    }

    @Test fun chaptersAndSettings10MiB() = interactionJourney("chapters-settings", fixtureMiB = 10) {
        device.findObject(By.descContains("chapter"))?.click()
        device.waitForIdle()
        device.pressBack()
        device.findObject(By.text("Aa"))?.click()
        device.waitForIdle()
    }

    private fun openJourney(fixtureMiB: Int, iterations: Int) = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = iterations,
        setupBlock = {
            pressHome()
            seedFixture(fixtureMiB)
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
        block: androidx.benchmark.macro.MacrobenchmarkScope.() -> Unit,
    ) = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = frameMetrics(),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = iterations,
        setupBlock = {
            pressHome()
            seedFixture(fixtureMiB)
            startActivityAndWait()
            openFixture(fixtureMiB)
        },
        measureBlock = block,
    )

    private fun androidx.benchmark.macro.MacrobenchmarkScope.seedFixture(mib: Int) {
        val result = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method seed --arg $mib",
        )
        check(result.contains("bytes=")) { "Reader V3 benchmark fixture seed failed: $result" }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.openFixture(mib: Int) {
        val title = "Benchmark Novel $mib MiB"
        check(device.wait(Until.hasObject(By.textContains(title)), 8_000)) { "fixture card missing: $title" }
        val card = device.findObject(By.textContains(title)) ?: error("fixture card unavailable: $title")
        card.click()
        device.waitForIdle()
    }

    private fun frameMetrics(): List<Metric> = listOf(FrameTimingMetric())

    private companion object {
        const val PACKAGE_NAME = "com.junchen.jingdu"
    }
}
