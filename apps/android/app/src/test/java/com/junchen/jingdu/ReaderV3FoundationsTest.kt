package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ReaderV3FoundationsTest {
    @Test fun equalLengthProjectionIsExactOneToOne() {
        val map = SourceDisplayMap.between("汉语龙门", "漢語龍門")
        for (offset in 0L..4L) {
            assertEquals(offset, map.displayForSource(offset))
            assertEquals(offset, map.sourceForDisplay(offset))
        }
    }

    @Test fun localizedDeletionDoesNotScaleUnchangedSuffix() {
        val map = SourceDisplayMap.between("abcXXXdef", "abcdef")
        assertEquals(0L, map.displayForSource(0))
        assertEquals(3L, map.displayForSource(3))
        assertEquals(3L, map.displayForSource(6))
        assertEquals(6L, map.displayForSource(9))
        assertEquals(6L, map.sourceForDisplay(6))
    }

    @Test fun projectionCompositionKeepsBoundariesMonotonic() {
        val first = TextProjection.between("a\n\n\n尾巴", "a\n\n尾巴")
        val second = TextProjection.between("a\n\n尾巴", "a\n\n尾巴")
        val map = SourceDisplayMap.compose(first, second)
        var last = 0L
        for (source in 0L..map.sourceCodePoints) {
            val display = map.displayForSource(source)
            assertTrue(display >= last)
            last = display
        }
    }

    @Test fun wordSelectionMapsThroughProjection() {
        val source = "hello XXX world"
        val display = "hello world"
        val map = SourceDisplayMap.between(source, display)
        val selected = ReaderSelectionController.wordAt(
            sourceBase = 100,
            displayText = display,
            displayUtf16 = display.indexOf("world") + 1,
            map = map,
            locale = Locale.ENGLISH,
        ) ?: error("selection missing")
        assertEquals("world", selected.excerpt)
        assertEquals(109L, selected.sourceStart)
        assertEquals(114L, selected.sourceEnd)
    }

    @Test fun typographyFingerprintCoversPaginationInputs() {
        val base = ReaderSettings()
        val fingerprint = ReaderTypographySpec.from(base).fingerprint
        assertNotEquals(fingerprint, ReaderTypographySpec.from(base.copy(fontSizeSp = 24f)).fingerprint)
        assertNotEquals(fingerprint, ReaderTypographySpec.from(base.copy(fontWeight = ReaderFontWeight.SEMIBOLD)).fingerprint)
        assertNotEquals(fingerprint, ReaderTypographySpec.from(base.copy(firstLineIndentEm = 2f)).fingerprint)
        assertNotEquals(fingerprint, ReaderTypographySpec.from(base.copy(paragraphSpacingEm = 1f)).fingerprint)
        assertNotEquals(fingerprint, ReaderTypographySpec.from(base.copy(textAlignment = ReaderTextAlignment.START)).fingerprint)
        assertNotEquals(fingerprint, ReaderTypographySpec.from(base.copy(typeface = ReaderTypeface.SERIF)).fingerprint)
    }

    @Test fun lowVisionPresetIsLegibleAndNonAnimatedByDefault() {
        val lowVision = ReaderSettings().applyPreset(ReaderPreset.LOW_VISION)
        assertTrue(lowVision.fontSizeSp >= 30f)
        assertTrue(lowVision.lineHeightMultiplier >= 1.7f)
        assertEquals(ReaderTextAlignment.START, lowVision.textAlignment)
        assertEquals(ReaderPageAnimation.NONE, lowVision.pageAnimation)
    }
}
