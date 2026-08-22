package com.jingdu.txt.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, editable chapter outline bound to one normalized base revision. */
public final class ChapterOutline {
    public static final int MAXIMUM_ENTRIES = 20_000;

    private final String baseRevision;
    private final List<ChapterOutlineEntry> entries;

    public ChapterOutline(String baseRevision, List<ChapterOutlineEntry> entries) {
        requireRevision(baseRevision);
        if (entries == null || entries.size() > MAXIMUM_ENTRIES) {
            throw new IllegalArgumentException("chapter outline exceeds " + MAXIMUM_ENTRIES);
        }
        List<ChapterOutlineEntry> copy = new ArrayList<ChapterOutlineEntry>(entries);
        int previousOffset = -1;
        for (ChapterOutlineEntry entry : copy) {
            if (entry == null || entry.getOriginalCharacterOffset() <= previousOffset) {
                throw new IllegalArgumentException(
                        "chapter boundaries must be strictly ordered");
            }
            previousOffset = entry.getOriginalCharacterOffset();
        }
        this.baseRevision = baseRevision;
        this.entries = Collections.unmodifiableList(copy);
    }

    public static ChapterOutline fromDetected(String baseRevision,
            List<ChapterEntry> detected) {
        if (detected == null) {
            throw new IllegalArgumentException("detected chapters are required");
        }
        List<ChapterOutlineEntry> entries = new ArrayList<ChapterOutlineEntry>();
        for (ChapterEntry chapter : detected) {
            if (chapter == null) {
                throw new IllegalArgumentException("detected chapter is required");
            }
            entries.add(new ChapterOutlineEntry(chapter.getTitle(),
                    chapter.getCharacterOffset(), chapter.getConfidencePercent(),
                    ChapterOutlineEntry.Origin.AUTO_DETECTED));
        }
        return new ChapterOutline(baseRevision, entries);
    }

    public String getBaseRevision() {
        return baseRevision;
    }

    public List<ChapterOutlineEntry> getEntries() {
        return entries;
    }

    public ChapterOutline rename(int index, String newTitle) {
        ChapterOutlineEntry selected = entryAt(index);
        List<ChapterOutlineEntry> updated = new ArrayList<ChapterOutlineEntry>(entries);
        updated.set(index, new ChapterOutlineEntry(newTitle,
                selected.getOriginalCharacterOffset(), 100,
                ChapterOutlineEntry.Origin.MANUAL));
        return new ChapterOutline(baseRevision, updated);
    }

    public ChapterOutline split(int originalCharacterOffset, String title) {
        if (entries.size() >= MAXIMUM_ENTRIES) {
            throw new IllegalArgumentException("chapter outline is full");
        }
        List<ChapterOutlineEntry> updated = new ArrayList<ChapterOutlineEntry>(entries);
        int insertion = 0;
        while (insertion < updated.size()
                && updated.get(insertion).getOriginalCharacterOffset()
                        < originalCharacterOffset) {
            insertion++;
        }
        if (insertion < updated.size()
                && updated.get(insertion).getOriginalCharacterOffset()
                        == originalCharacterOffset) {
            throw new IllegalArgumentException("a chapter already starts at this position");
        }
        updated.add(insertion, new ChapterOutlineEntry(title,
                originalCharacterOffset, 100, ChapterOutlineEntry.Origin.MANUAL));
        return new ChapterOutline(baseRevision, updated);
    }

    public ChapterOutline mergeWithNext(int index) {
        ChapterOutlineEntry selected = entryAt(index);
        if (index + 1 >= entries.size()) {
            throw new IllegalArgumentException("the last chapter has no next chapter to merge");
        }
        List<ChapterOutlineEntry> updated = new ArrayList<ChapterOutlineEntry>(entries);
        updated.set(index, new ChapterOutlineEntry(selected.getTitle(),
                selected.getOriginalCharacterOffset(), 100,
                ChapterOutlineEntry.Origin.MANUAL));
        updated.remove(index + 1);
        return new ChapterOutline(baseRevision, updated);
    }

    private ChapterOutlineEntry entryAt(int index) {
        if (index < 0 || index >= entries.size()) {
            throw new IllegalArgumentException("chapter selection is out of range");
        }
        return entries.get(index);
    }

    private static void requireRevision(String revision) {
        if (revision == null || !revision.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid base revision");
        }
    }
}
