package com.junchen.jingdu

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsLocalePolicyTest {
    @Test
    fun traditionalPrefersTaiwanWhenInstalled() {
        val selected = TtsLocalePolicy.choose(
            mode = ChineseDisplayMode.TRADITIONAL,
            documentLocale = Locale.forLanguageTag("zh-CN"),
            systemLocale = Locale.ENGLISH,
        ) { it.toLanguageTag() in setOf("zh-TW", "zh-CN") }

        assertEquals("zh-TW", selected?.toLanguageTag())
    }

    @Test
    fun traditionalFallsBackToInstalledChineseVoice() {
        val selected = TtsLocalePolicy.choose(
            mode = ChineseDisplayMode.TRADITIONAL,
            documentLocale = Locale.forLanguageTag("zh-CN"),
            systemLocale = Locale.ENGLISH,
        ) { it.toLanguageTag() == "zh-CN" }

        assertEquals("zh-CN", selected?.toLanguageTag())
    }

    @Test
    fun hongKongFallsBackWithoutChangingConvertedTextLocalePolicy() {
        val selected = TtsLocalePolicy.choose(
            mode = ChineseDisplayMode.HONG_KONG,
            documentLocale = Locale.forLanguageTag("zh-CN"),
            systemLocale = Locale.ENGLISH,
        ) { it.toLanguageTag() == "zh-TW" }

        assertEquals("zh-TW", selected?.toLanguageTag())
    }

    @Test
    fun originalKeepsDetectedDocumentLocale() {
        val selected = TtsLocalePolicy.choose(
            mode = ChineseDisplayMode.ORIGINAL,
            documentLocale = Locale.ENGLISH,
            systemLocale = Locale.forLanguageTag("zh-CN"),
        ) { true }

        assertEquals(Locale.ENGLISH, selected)
    }

    @Test
    fun noSupportedVoiceReturnsNull() {
        val selected = TtsLocalePolicy.choose(
            mode = ChineseDisplayMode.TRADITIONAL,
            documentLocale = Locale.forLanguageTag("zh-CN"),
            systemLocale = Locale.ENGLISH,
        ) { false }

        assertNull(selected)
    }
}
