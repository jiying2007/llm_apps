package com.junchen.jingdu.macrobenchmark

import android.os.SystemClock
import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val baselineProfileRule = BaselineProfileRule()

    /** Startup Profile contains only the first-display path; runtime CUJs belong in Baseline only. */
    @Test fun readerV3Startup() {
        prepareProfileTarget("paged")
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            maxIterations = 10,
            stableIterations = 3,
            includeInStartupProfile = true,
            outputFilePrefix = "jingdu-reader-v3-startup",
        ) {
            pressHome()
            startActivityAndWait()
            openFixture()
        }
    }

    /** Page turn, continuous scroll and panel navigation are runtime-critical Baseline Profile CUJs. */
    @Test fun readerV3CriticalJourneys() {
        prepareProfileTarget("paged")
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            maxIterations = 10,
            stableIterations = 3,
            includeInStartupProfile = false,
            outputFilePrefix = "jingdu-reader-v3-critical",
        ) {
            pressHome()
            startActivityAndWait()
            openFixture()

            repeat(10) {
                check(device.pressKeyCode(KeyEvent.KEYCODE_VOLUME_DOWN)) { "Reader V3 baseline volume-key page turn was not injected" }
                device.waitForIdle()
            }

            // Exercise the real Quick Settings composition, but do not depend on translated or
            // Canvas-only UI text to mutate test state. The benchmark-only provider is the same
            // deterministic protocol used by the frame gate and synchronously persists DataStore.
            ensureTopControlsVisible()
            requireClick(By.text("Aa"), "quick reading settings control")
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()
            setProfileMode("continuous")
            startActivityAndWait()
            openFixture()
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
    }

    /**
     * Provision fixture state before BaselineProfileRule enters its compilation/reset loop. Provider
     * startup is test infrastructure, not part of the product CUJ being profiled, and launching it
     * from inside CompilationMode.Partial made provider publication race the profile compiler on CI.
     */
    private fun prepareProfileTarget(mode: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("logcat -c")
        val seed = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method seed --arg 10",
        )
        check(seed.contains("bytes=")) {
            "Reader V3 baseline fixture seed failed before profile collection: $seed\n===== Reader V3 profile target diagnostics =====\n${profileDiagnostics(device)}"
        }
        setProfileMode(device, mode)
    }

    private fun setProfileMode(device: UiDevice, mode: String) {
        val modeResult = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method mode --arg $mode",
        )
        check(modeResult.contains("Result: Bundle[{}]")) {
            "Reader V3 baseline mode setup failed before profile collection: $modeResult\n===== Reader V3 profile target diagnostics =====\n${profileDiagnostics(device)}"
        }
        // BaselineProfileRule owns product-process startup. Leave a fully provisioned but stopped
        // package so compilation/profile collection begins from a deterministic lifecycle boundary.
        device.executeShellCommand("am force-stop $PACKAGE_NAME")
    }

    private fun MacrobenchmarkScope.setProfileMode(mode: String) {
        setProfileMode(device, mode)
    }

    private fun profileDiagnostics(device: UiDevice): String = buildString {
        append("package-path:\n")
        append(device.executeShellCommand("pm path $PACKAGE_NAME").trim().ifEmpty { "<not-installed>" })
        append("\nprovider:\n")
        append(device.executeShellCommand("dumpsys package $PACKAGE_NAME | grep -A 12 -B 3 benchmarkfixture").takeLast(8_000))
        append("\npidof: ")
        append(device.executeShellCommand("pidof $PACKAGE_NAME").trim().ifEmpty { "<not-running>" })
        append("\nexit-info:\n")
        append(device.executeShellCommand("dumpsys activity exit-info $PACKAGE_NAME").takeLast(12_000))
        append("\nlogcat:\n")
        append(
            device.executeShellCommand(
                "logcat -d -t 300 AndroidRuntime:E ActivityManager:I ActivityTaskManager:I '*:S'",
            ).takeLast(20_000),
        )
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
