package com.jingdu.txt.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Maps immutable original chapter anchors into one active reading revision. */
public final class ChapterOutlineMapper {
    public interface OriginalToViewOffset {
        long map(int originalCharacterOffset) throws IOException;
    }

    public List<ChapterEntry> map(ChapterOutline outline,
            OriginalToViewOffset offsetMapper) throws IOException {
        if (outline == null || offsetMapper == null) {
            throw new IllegalArgumentException("chapter outline mapping is required");
        }
        List<ChapterEntry> mapped = new ArrayList<ChapterEntry>();
        long previous = -1L;
        for (ChapterOutlineEntry entry : outline.getEntries()) {
            long viewOffset = offsetMapper.map(entry.getOriginalCharacterOffset());
            if (viewOffset < 0 || viewOffset > Integer.MAX_VALUE
                    || viewOffset < previous) {
                throw new IOException("invalid or non-monotonic chapter mapping");
            }
            mapped.add(new ChapterEntry(entry.getTitle(), (int) viewOffset,
                    entry.getConfidencePercent()));
            previous = viewOffset;
        }
        return Collections.unmodifiableList(mapped);
    }

    public List<ChapterEntry> identity(ChapterOutline outline) throws IOException {
        return map(outline, originalOffset -> originalOffset);
    }
}
