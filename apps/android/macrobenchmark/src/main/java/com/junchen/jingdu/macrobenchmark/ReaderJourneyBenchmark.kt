package com.junchen.jingdu.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
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

    @Test fun openLongTxt() = journey("open-long-txt") { }

    @Test fun pageTurn() = journey("page-turn") {
        repeat(8) { device.click((device.displayWidth * 0.86).toInt(), (device.displayHeight * 0.52).toInt()) }
    }

    @Test fun continuousScroll() = journey("continuous-scroll") {
        device.findObject(By.text("Aa"))?.click()
        device.wait(Until.hasObject(By.textContains("Continuous")), 2_000)
        device.findObject(By.textContains("Continuous"))?.click()
        device.pressBack()
        repeat(6) { device.swipe(device.displayWidth / 2, (device.displayHeight * 0.80).toInt(), device.displayWidth / 2, (device.displayHeight * 0.25).toInt(), 20) }
    }

    @Test fun chaptersAndSettings() = journey("chapters-settings") {
        device.findObject(By.descContains("chapter"))?.click()
        device.waitForIdle()
        device.pressBack()
        device.findObject(By.text("Aa"))?.click()
        device.waitForIdle()
    }

    private fun journey(name: String, block: androidx.benchmark.macro.MacrobenchmarkScope.() -> Unit) = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 8,
        setupBlock = {
            pressHome()
            device.executeShellCommand("content call --uri content://com.junchen.jingdu.benchmarkfixture --method seed --arg 8")
            startActivityAndWait()
            device.wait(Until.hasObject(By.textContains("Benchmark Novel")), 4_000)
            device.findObject(By.textContains("Benchmark Novel"))?.click()
            device.waitForIdle()
        },
        measureBlock = block,
    )

    private companion object {
        const val PACKAGE_NAME = "com.junchen.jingdu"
    }
}
