package com.junchen.jingdu.macrobenchmark

import android.os.SystemClock
import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val baselineProfileRule = BaselineProfileRule()

    /** Startup Profile contains only the first-display path; runtime CUJs belong in Baseline only. */
    @Test fun readerV3Startup() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        maxIterations = 10,
        stableIterations = 3,
        includeInStartupProfile = true,
        outputFilePrefix = "jingdu-reader-v3-startup",
    ) {
        pressHome()
        seedFixture()
        setReaderMode("paged")
        startActivityAndWait()
        openFixture()
    }

    /** Page turn, continuous scroll and panel navigation are runtime-critical Baseline Profile CUJs. */
    @Test fun readerV3CriticalJourneys() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        maxIterations = 10,
        stableIterations = 3,
        includeInStartupProfile = false,
        outputFilePrefix = "jingdu-reader-v3-critical",
    ) {
        pressHome()
        seedFixture()
        setReaderMode("paged")
        startActivityAndWait()
        openFixture()

        repeat(10) {
            check(device.pressKeyCode(KeyEvent.KEYCODE_VOLUME_DOWN)) { "Reader V3 baseline volume-key page turn was not injected" }
            device.waitForIdle()
        }

        ensureTopControlsVisible()
        requireClick(By.text("Aa"), "quick reading settings control")
        device.waitForIdle()
        requireContinuousModeClick()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
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

        ensureTopControlsVisible()
        requireChaptersClick()
        device.waitForIdle()
        device.pressBack()
    }

    private fun MacrobenchmarkScope.seedFixture() {
        val result = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method seed --arg 10",
        )
        check(result.contains("bytes=")) {
            "Reader V3 baseline fixture seed failed: $result\n===== Reader V3 profile target diagnostics =====\n${profileDiagnostics()}"
        }
    }

    private fun MacrobenchmarkScope.setReaderMode(mode: String) {
        val result = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method mode --arg $mode",
        )
        check(result.contains("Result: Bundle[{}]")) {
            "Reader V3 baseline mode setup failed: $result\n===== Reader V3 profile target diagnostics =====\n${profileDiagnostics()}"
        }
    }

    private fun MacrobenchmarkScope.profileDiagnostics(): String = buildString {
        append("package-path:\n")
        append(device.executeShellCommand("pm path $PACKAGE_NAME").trim().ifEmpty { "<not-installed>" })
        append("\nprovider:\n")
        append(device.executeShellCommand("dumpsys package $PACKAGE_NAME | grep -A 12 -B 3 benchmarkfixture").takeLast(8_000))
        append("\npidof: ")
        append(device.executeShellCommand("pidof $PACKAGE_NAME").trim().ifEmpty { "<not-running>" })
        append("\nexit-info:\n")
        append(device.executeShellCommand("dumpsys activity exit-info $PACKAGE_NAME").takeLast(12_000))
    }

    private fun MacrobenchmarkScope.openFixture() {
        val title = "Benchmark Novel 10 MiB"
        check(device.wait(Until.hasObject(By.textContains(title)), 8_000)) { "Reader V3 baseline fixture missing" }
        val target = device.findObject(By.textContains(title)) ?: error("Reader V3 baseline fixture unavailable")
        target.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.ensureTopControlsVisible() {
        if (device.wait(Until.hasObject(By.text("Aa")), 750)) return
        val taps = listOf(0.50f to 0.52f, 0.50f to 0.68f, 0.50f to 0.36f)
        repeat(2) {
            for ((x, y) in taps) {
                device.click((device.displayWidth * x).toInt(), (device.displayHeight * y).toInt())
                if (device.wait(Until.hasObject(By.text("Aa")), 900)) return
            }
        }
        error("Reader V3 baseline top reading controls did not become visible")
    }

    private fun MacrobenchmarkScope.requireClick(selector: BySelector, label: String) {
        check(device.wait(Until.hasObject(selector), 3_000)) { "Reader V3 baseline $label missing" }
        val target = device.findObject(selector) ?: error("Reader V3 baseline $label unavailable")
        target.click()
    }

    private fun MacrobenchmarkScope.requireContinuousModeClick() {
        val selectors = listOf(
            By.textContains("Continuous"),
            By.descContains("Continuous"),
        )
        for (selector in selectors) {
            device.findObject(selector)?.let { target -> target.click(); return }
        }
        error("Reader V3 baseline continuous reading mode missing")
    }

    private fun MacrobenchmarkScope.requireChaptersClick() {
        device.findObject(By.desc("Chapters"))?.let { target -> target.click(); return }
        device.findObject(By.descContains("Chapter"))?.let { target -> target.click(); return }

        val aa = device.findObjects(By.text("Aa")).minByOrNull { it.visibleBounds.centerY() }
            ?: error("Reader V3 baseline top reading controls missing")
        val anchor = aa.visibleBounds
        val candidate = device.findObjects(By.clickable(true))
            .asSequence()
            .filter { node ->
                val bounds = node.visibleBounds
                bounds.centerX() > anchor.centerX() &&
                    abs(bounds.centerY() - anchor.centerY()) <= maxOf(anchor.height(), bounds.height())
            }
            .minByOrNull { node -> node.visibleBounds.centerX() - anchor.centerX() }
            ?: error("Reader V3 baseline chapters control missing beside Aa")
        candidate.click()
    }

    private companion object { const val PACKAGE_NAME = "com.junchen.jingdu" }
}
