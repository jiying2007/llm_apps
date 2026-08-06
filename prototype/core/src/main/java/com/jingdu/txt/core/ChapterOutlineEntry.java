package com.jingdu.txt.core;

/** One chapter boundary anchored to the immutable normalized base text. */
public final class ChapterOutlineEntry {
    public enum Origin {
        AUTO_DETECTED,
        MANUAL
    }

    public static final int MAXIMUM_TITLE_CHARACTERS = 120;

    private final String title;
    private final int originalCharacterOffset;
    private final int confidencePercent;
    private final Origin origin;

    public ChapterOutlineEntry(String title, int originalCharacterOffset,
            int confidencePercent, Origin origin) {
        validateTitle(title);
        if (originalCharacterOffset < 0) {
            throw new IllegalArgumentException("chapter offset must not be negative");
        }
        if (confidencePercent < 0 || confidencePercent > 100 || origin == null) {
            throw new IllegalArgumentException("invalid chapter confidence or origin");
        }
        if (origin == Origin.MANUAL && confidencePercent != 100) {
            throw new IllegalArgumentException("manual chapter confidence must be 100");
        }
        this.title = title;
        this.originalCharacterOffset = originalCharacterOffset;
        this.confidencePercent = confidencePercent;
        this.origin = origin;
    }

    public String getTitle() {
        return title;
    }

    public int getOriginalCharacterOffset() {
        return originalCharacterOffset;
    }

    public int getConfidencePercent() {
        return confidencePercent;
    }

    public Origin getOrigin() {
        return origin;
    }

    static void validateTitle(String title) {
        if (title == null || title.isEmpty() || !title.equals(title.trim())
                || title.length() > MAXIMUM_TITLE_CHARACTERS) {
            throw new IllegalArgumentException("invalid chapter title");
        }
        for (int index = 0; index < title.length(); index++) {
            char value = title.charAt(index);
            if (Character.isISOControl(value)) {
                throw new IllegalArgumentException("invalid chapter title");
            }
            if (Character.isHighSurrogate(value)) {
                if (index + 1 >= title.length()
                        || !Character.isLowSurrogate(title.charAt(index + 1))) {
                    throw new IllegalArgumentException("invalid chapter title");
                }
                index++;
            } else if (Character.isLowSurrogate(value)) {
                throw new IllegalArgumentException("invalid chapter title");
            }
        }
    }
}
