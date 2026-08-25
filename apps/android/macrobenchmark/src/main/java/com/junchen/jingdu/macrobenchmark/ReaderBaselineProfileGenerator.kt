package com.junchen.jingdu.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Reader V3 critical-user-journey profile. Generation is executed and archived by hosted CI. */
@RunWith(AndroidJUnit4::class)
class ReaderBaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun readerV3CriticalJourneys() = rule.collect(packageName = PACKAGE_NAME) {
        val seed = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method seed --arg 10",
        )
        check(seed.contains("bytes=")) { "Reader V3 baseline fixture seed failed: $seed" }

        startActivityAndWait()
        val title = "Benchmark Novel 10 MiB"
        check(device.wait(Until.hasObject(By.textContains(title)), 8_000)) { "baseline fixture missing" }
        device.findObject(By.textContains(title))?.click()
        device.waitForIdle()

        // Cover paged reading and page measurement/cache hot paths.
        repeat(10) {
            device.click((device.displayWidth * 0.86).toInt(), (device.displayHeight * 0.52).toInt())
            device.waitForIdle()
        }

        // Cover typography/settings and continuous reading navigation.
        device.findObject(By.text("Aa"))?.click()
        device.waitForIdle()
        device.findObject(By.textContains("Continuous"))?.click()
        device.pressBack()
        repeat(4) {
            device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.80).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.25).toInt(),
                20,
            )
        }

        // Cover chapter navigation without relying on network or external fixtures.
        device.findObject(By.descContains("chapter"))?.click()
        device.waitForIdle()
        device.pressBack()
    }

    private companion object {
        const val PACKAGE_NAME = "com.junchen.jingdu"
    }
}
