package com.jingdu.txt.core;

/** A user bookmark anchored to the immutable, normalized base text. */
public final class BookBookmark {
    private static final int MAXIMUM_LABEL_CHARACTERS = 160;

    private final String bookmarkId;
    private final String bookId;
    private final int originalAnchorOffset;
    private final String label;
    private final long createdAtEpochMillis;

    public BookBookmark(String bookmarkId, String bookId, int originalAnchorOffset,
            String label, long createdAtEpochMillis) {
        requireHex(bookmarkId, 32, "bookmark id");
        requireHex(bookId, 64, "book id");
        if (originalAnchorOffset < 0) {
            throw new IllegalArgumentException("bookmark anchor must not be negative");
        }
        if (label == null || label.isEmpty() || label.length() > MAXIMUM_LABEL_CHARACTERS) {
            throw new IllegalArgumentException("invalid bookmark label");
        }
        for (int index = 0; index < label.length(); index++) {
            char character = label.charAt(index);
            if (Character.isISOControl(character)) {
                throw new IllegalArgumentException("invalid bookmark label");
            }
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= label.length()
                        || !Character.isLowSurrogate(label.charAt(index + 1))) {
                    throw new IllegalArgumentException("invalid bookmark label");
                }
                index++;
            } else if (Character.isLowSurrogate(character)) {
                throw new IllegalArgumentException("invalid bookmark label");
            }
        }
        if (createdAtEpochMillis <= 0) {
            throw new IllegalArgumentException("invalid bookmark timestamp");
        }
        this.bookmarkId = bookmarkId;
        this.bookId = bookId;
        this.originalAnchorOffset = originalAnchorOffset;
        this.label = label;
        this.createdAtEpochMillis = createdAtEpochMillis;
    }

    public String getBookmarkId() {
        return bookmarkId;
    }

    public String getBookId() {
        return bookId;
    }

    public int getOriginalAnchorOffset() {
        return originalAnchorOffset;
    }

    public String getLabel() {
        return label;
    }

    public long getCreatedAtEpochMillis() {
        return createdAtEpochMillis;
    }

    private static void requireHex(String value, int length, String label) {
        if (value == null || !value.matches("[0-9a-f]{" + length + "}")) {
            throw new IllegalArgumentException("invalid " + label);
        }
    }
}
