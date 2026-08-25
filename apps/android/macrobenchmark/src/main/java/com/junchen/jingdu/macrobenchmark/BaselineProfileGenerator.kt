package com.junchen.jingdu.macrobenchmark

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val baselineProfileRule = BaselineProfileRule()

    @Test fun readerV3CriticalJourneys() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        maxIterations = 10,
        stableIterations = 3,
        includeInStartupProfile = true,
        outputFilePrefix = "jingdu-reader-v3",
    ) {
        pressHome()
        seedFixture()
        setReaderMode("paged")
        startActivityAndWait()
        openFixture()

        repeat(10) {
            check(device.swipe(
                (device.displayWidth * 0.78).toInt(),
                device.displayHeight / 2,
                (device.displayWidth * 0.22).toInt(),
                device.displayHeight / 2,
                24,
            )) { "Reader V3 baseline page-turn swipe was not injected" }
            device.waitForIdle()
        }

        requireClick(By.text("Aa"), "quick reading settings control")
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()

        setReaderMode("continuous")
        SystemClock.sleep(500)
        repeat(4) {
            check(device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.80).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.25).toInt(),
                24,
            )) { "Reader V3 baseline continuous-scroll swipe was not injected" }
        }
        device.waitForIdle()

        requireClick(By.descContains("Chapter"), "chapters control")
        device.waitForIdle()
        device.pressBack()
    }

    private fun MacrobenchmarkScope.seedFixture() {
        val result = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method seed --arg 10",
        )
        check(result.contains("bytes=")) { "Reader V3 baseline fixture seed failed: $result" }
    }

    private fun MacrobenchmarkScope.setReaderMode(mode: String) {
        val expected = mode.uppercase()
        val result = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method mode --arg $mode",
        )
        check(result.contains("mode=$expected")) { "Reader V3 baseline mode setup failed: $result" }
    }

    private fun MacrobenchmarkScope.openFixture() {
        val title = "Benchmark Novel 10 MiB"
        check(device.wait(Until.hasObject(By.textContains(title)), 8_000)) { "Reader V3 baseline fixture missing" }
        val target = device.findObject(By.textContains(title)) ?: error("Reader V3 baseline fixture unavailable")
        target.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.requireClick(selector: BySelector, label: String) {
        check(device.wait(Until.hasObject(selector), 3_000)) { "Reader V3 baseline $label missing" }
        val target = device.findObject(selector) ?: error("Reader V3 baseline $label unavailable")
        target.click()
    }

    private companion object { const val PACKAGE_NAME = "com.junchen.jingdu" }
}
