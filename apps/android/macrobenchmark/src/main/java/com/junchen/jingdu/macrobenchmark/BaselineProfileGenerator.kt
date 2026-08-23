package com.junchen.jingdu.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        maxIterations = 10,
        stableIterations = 3,
        includeInStartupProfile = true,
        outputFilePrefix = "jingdu-long-form",
    ) {
        pressHome()
        startActivityAndWait()
    }

    private companion object {
        const val PACKAGE_NAME = "com.junchen.jingdu"
    }
}
