package com.jingdu.txt.core;

/** Platform-neutral metadata needed to render and restore one private TXT copy. */
public final class BookLibraryEntry {
    private static final int MAXIMUM_NAME_CHARACTERS = 255;
    private static final int MAXIMUM_ENCODING_CHARACTERS = 64;

    private final String bookId;
    private final String displayName;
    private final String baseFileName;
    private final String baseRevision;
    private final String activeFileName;
    private final String activeRevision;
    private final String projectionFileName;
    private final String encodingName;
    private final long sourceBytes;
    private final long importedAtEpochMillis;
    private final long lastOpenedAtEpochMillis;
    private final int anchorOffset;
    private final boolean shelved;

    public BookLibraryEntry(String bookId, String displayName,
            String baseFileName, String baseRevision,
            String activeFileName, String activeRevision, String projectionFileName,
            String encodingName, long sourceBytes, long importedAtEpochMillis,
            long lastOpenedAtEpochMillis, int anchorOffset) {
        this(bookId, displayName, baseFileName, baseRevision,
                activeFileName, activeRevision, projectionFileName,
                encodingName, sourceBytes, importedAtEpochMillis,
                lastOpenedAtEpochMillis, anchorOffset, true);
    }

    public BookLibraryEntry(String bookId, String displayName,
            String baseFileName, String baseRevision,
            String activeFileName, String activeRevision, String projectionFileName,
            String encodingName, long sourceBytes, long importedAtEpochMillis,
            long lastOpenedAtEpochMillis, int anchorOffset, boolean shelved) {
        requireRevision(bookId, "book id");
        requireText(displayName, MAXIMUM_NAME_CHARACTERS, "display name");
        requireFileName(baseFileName, "base file name");
        requireRevision(baseRevision, "base revision");
        requireFileName(activeFileName, "active file name");
        requireRevision(activeRevision, "active revision");
        if (projectionFileName == null) {
            throw new IllegalArgumentException("invalid projection file name");
        }
        if (!projectionFileName.isEmpty()) {
            requireFileName(projectionFileName, "projection file name");
        }
        requireText(encodingName, MAXIMUM_ENCODING_CHARACTERS, "encoding name");
        if (sourceBytes < 0) {
            throw new IllegalArgumentException("source bytes must not be negative");
        }
        if (importedAtEpochMillis <= 0 || lastOpenedAtEpochMillis < importedAtEpochMillis) {
            throw new IllegalArgumentException("invalid library timestamps");
        }
        if (anchorOffset < 0) {
            throw new IllegalArgumentException("anchor offset must not be negative");
        }
        this.bookId = bookId;
        this.displayName = displayName;
        this.baseFileName = baseFileName;
        this.baseRevision = baseRevision;
        this.activeFileName = activeFileName;
        this.activeRevision = activeRevision;
        this.projectionFileName = projectionFileName;
        this.encodingName = encodingName;
        this.sourceBytes = sourceBytes;
        this.importedAtEpochMillis = importedAtEpochMillis;
        this.lastOpenedAtEpochMillis = lastOpenedAtEpochMillis;
        this.anchorOffset = anchorOffset;
        this.shelved = shelved;
    }

    public String getBookId() {
        return bookId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBaseFileName() {
        return baseFileName;
    }

    public String getBaseRevision() {
        return baseRevision;
    }

    public String getActiveFileName() {
        return activeFileName;
    }

    public String getActiveRevision() {
        return activeRevision;
    }

    public String getProjectionFileName() {
        return projectionFileName;
    }

    public String getEncodingName() {
        return encodingName;
    }

    public long getSourceBytes() {
        return sourceBytes;
    }

    public long getImportedAtEpochMillis() {
        return importedAtEpochMillis;
    }

    public long getLastOpenedAtEpochMillis() {
        return lastOpenedAtEpochMillis;
    }

    public int getAnchorOffset() {
        return anchorOffset;
    }

    public boolean isShelved() {
        return shelved;
    }

    public BookLibraryEntry withActiveView(String newActiveFileName,
            String newActiveRevision, String newProjectionFileName, int newAnchorOffset,
            long openedAtEpochMillis) {
        return new BookLibraryEntry(bookId, displayName, baseFileName, baseRevision,
                newActiveFileName, newActiveRevision, newProjectionFileName, encodingName,
                sourceBytes, importedAtEpochMillis,
                Math.max(lastOpenedAtEpochMillis, openedAtEpochMillis), newAnchorOffset,
                shelved);
    }

    public BookLibraryEntry withProgress(int newAnchorOffset, long openedAtEpochMillis) {
        return withActiveView(activeFileName, activeRevision, projectionFileName,
                newAnchorOffset, openedAtEpochMillis);
    }

    public BookLibraryEntry withShelved(boolean newShelved, long changedAtEpochMillis) {
        return new BookLibraryEntry(bookId, displayName, baseFileName, baseRevision,
                activeFileName, activeRevision, projectionFileName, encodingName,
                sourceBytes, importedAtEpochMillis,
                Math.max(lastOpenedAtEpochMillis, changedAtEpochMillis), anchorOffset,
                newShelved);
    }

    private static void requireRevision(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid " + label);
        }
    }

    private static void requireFileName(String value, String label) {
        requireText(value, MAXIMUM_NAME_CHARACTERS, label);
        if (".".equals(value) || "..".equals(value)
                || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("invalid " + label);
        }
    }

    private static void requireText(String value, int maximumCharacters, String label) {
        if (value == null || value.isEmpty() || value.length() > maximumCharacters) {
            throw new IllegalArgumentException("invalid " + label);
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException("invalid " + label);
            }
        }
    }
}
