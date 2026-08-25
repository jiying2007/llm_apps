package com.junchen.jingdu.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val baselineProfileRule = BaselineProfileRule()

    @Test fun generate() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        maxIterations = 10,
        stableIterations = 3,
        includeInStartupProfile = true,
        outputFilePrefix = "jingdu-reader-v2",
    ) {
        pressHome()
        device.executeShellCommand("content call --uri content://com.junchen.jingdu.benchmarkfixture --method seed --arg 8")
        startActivityAndWait()
        device.wait(Until.hasObject(By.textContains("Benchmark Novel")), 4_000)
        device.findObject(By.textContains("Benchmark Novel"))?.click()
        device.waitForIdle()
        repeat(4) { device.click((device.displayWidth * 0.86).toInt(), device.displayHeight / 2) }
        device.findObject(By.text("Aa"))?.click()
        device.waitForIdle()
        device.pressBack()
        device.findObject(By.descContains("chapter"))?.click()
        device.waitForIdle()
        device.pressBack()
    }

    private companion object { const val PACKAGE_NAME = "com.junchen.jingdu" }
}
