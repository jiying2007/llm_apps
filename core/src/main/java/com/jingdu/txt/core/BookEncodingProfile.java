package com.jingdu.txt.core;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable import-encoding diagnostics bound to one book and base revision. */
public final class BookEncodingProfile {
    private final String bookId;
    private final String baseRevision;
    private final String charsetName;
    private final double confidence;
    private final DetectedEncoding.SelectionMode selectionMode;
    private final DetectedEncoding.Advisory advisory;
    private final long decodingReplacementCount;
    private final long firstDecodingErrorByteOffset;
    private final long firstReplacementCharacterOffset;
    private final List<DecodingReplacement> decodingReplacements;

    public BookEncodingProfile(String bookId, String baseRevision,
            String charsetName, double confidence,
            DetectedEncoding.SelectionMode selectionMode,
            DetectedEncoding.Advisory advisory) {
        this(bookId, baseRevision, charsetName, confidence, selectionMode,
                advisory, 0, -1, -1, Collections.emptyList());
    }

    public BookEncodingProfile(String bookId, String baseRevision,
            String charsetName, double confidence,
            DetectedEncoding.SelectionMode selectionMode,
            DetectedEncoding.Advisory advisory, long decodingReplacementCount,
            long firstDecodingErrorByteOffset) {
        this(bookId, baseRevision, charsetName, confidence, selectionMode,
                advisory, decodingReplacementCount,
                firstDecodingErrorByteOffset, -1, Collections.emptyList());
    }

    public BookEncodingProfile(String bookId, String baseRevision,
            String charsetName, double confidence,
            DetectedEncoding.SelectionMode selectionMode,
            DetectedEncoding.Advisory advisory, long decodingReplacementCount,
            long firstDecodingErrorByteOffset,
            long firstReplacementCharacterOffset) {
        this(bookId, baseRevision, charsetName, confidence, selectionMode,
                advisory, decodingReplacementCount, firstDecodingErrorByteOffset,
                firstReplacementCharacterOffset, legacyLocations(
                        firstDecodingErrorByteOffset,
                        firstReplacementCharacterOffset));
    }

    public BookEncodingProfile(String bookId, String baseRevision,
            String charsetName, double confidence,
            DetectedEncoding.SelectionMode selectionMode,
            DetectedEncoding.Advisory advisory, long decodingReplacementCount,
            long firstDecodingErrorByteOffset,
            long firstReplacementCharacterOffset,
            List<DecodingReplacement> decodingReplacements) {
        requireSha256(bookId, "book id");
        requireSha256(baseRevision, "base revision");
        if (charsetName == null || charsetName.isEmpty()
                || charsetName.length() > 40) {
            throw new IllegalArgumentException("invalid charset name");
        }
        Charset charset;
        try {
            charset = Charset.forName(charsetName);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("invalid charset name", invalid);
        }
        if (!charset.name().equals(charsetName)) {
            throw new IllegalArgumentException("charset name must be canonical");
        }
        new DetectedEncoding(charset, 0, confidence, "persisted encoding profile",
                selectionMode, advisory);
        if (decodingReplacementCount < 0
                || (decodingReplacementCount == 0
                        && (firstDecodingErrorByteOffset != -1
                                || firstReplacementCharacterOffset != -1))
                || (decodingReplacementCount > 0
                        && (firstDecodingErrorByteOffset < 0
                                || firstReplacementCharacterOffset < -1))) {
            throw new IllegalArgumentException("invalid decoding diagnostics");
        }
        ImportResult.validateLocations(decodingReplacementCount,
                firstDecodingErrorByteOffset, firstReplacementCharacterOffset,
                decodingReplacements, firstReplacementCharacterOffset < 0);
        this.bookId = bookId;
        this.baseRevision = baseRevision;
        this.charsetName = charsetName;
        this.confidence = confidence;
        this.selectionMode = selectionMode;
        this.advisory = advisory;
        this.decodingReplacementCount = decodingReplacementCount;
        this.firstDecodingErrorByteOffset = firstDecodingErrorByteOffset;
        this.firstReplacementCharacterOffset = firstReplacementCharacterOffset;
        this.decodingReplacements = Collections.unmodifiableList(
                new ArrayList<DecodingReplacement>(decodingReplacements));
    }

    public static BookEncodingProfile from(String bookId, String baseRevision,
            DetectedEncoding encoding) {
        return from(bookId, baseRevision, encoding, 0, -1);
    }

    public static BookEncodingProfile from(String bookId, String baseRevision,
            DetectedEncoding encoding, long decodingReplacementCount,
            long firstDecodingErrorByteOffset) {
        return from(bookId, baseRevision, encoding, decodingReplacementCount,
                firstDecodingErrorByteOffset, -1);
    }

    public static BookEncodingProfile from(String bookId, String baseRevision,
            DetectedEncoding encoding, long decodingReplacementCount,
            long firstDecodingErrorByteOffset,
            long firstReplacementCharacterOffset) {
        if (encoding == null) {
            throw new IllegalArgumentException("detected encoding is required");
        }
        return new BookEncodingProfile(bookId, baseRevision,
                encoding.getCharset().name(), encoding.getConfidence(),
                encoding.getSelectionMode(), encoding.getAdvisory(),
                decodingReplacementCount, firstDecodingErrorByteOffset,
                firstReplacementCharacterOffset);
    }

    public static BookEncodingProfile from(String bookId, String baseRevision,
            DetectedEncoding encoding, long decodingReplacementCount,
            long firstDecodingErrorByteOffset,
            long firstReplacementCharacterOffset,
            List<DecodingReplacement> decodingReplacements) {
        if (encoding == null) {
            throw new IllegalArgumentException("detected encoding is required");
        }
        return new BookEncodingProfile(bookId, baseRevision,
                encoding.getCharset().name(), encoding.getConfidence(),
                encoding.getSelectionMode(), encoding.getAdvisory(),
                decodingReplacementCount, firstDecodingErrorByteOffset,
                firstReplacementCharacterOffset, decodingReplacements);
    }

    public String getBookId() {
        return bookId;
    }

    public String getBaseRevision() {
        return baseRevision;
    }

    public String getCharsetName() {
        return charsetName;
    }

    public double getConfidence() {
        return confidence;
    }

    public DetectedEncoding.SelectionMode getSelectionMode() {
        return selectionMode;
    }

    public DetectedEncoding.Advisory getAdvisory() {
        return advisory;
    }

    public long getDecodingReplacementCount() {
        return decodingReplacementCount;
    }

    public long getFirstDecodingErrorByteOffset() {
        return firstDecodingErrorByteOffset;
    }

    public long getFirstReplacementCharacterOffset() {
        return firstReplacementCharacterOffset;
    }

    public List<DecodingReplacement> getDecodingReplacements() {
        return decodingReplacements;
    }

    private static List<DecodingReplacement> legacyLocations(long byteOffset,
            long characterOffset) {
        if (byteOffset < 0 || characterOffset < 0) {
            return Collections.emptyList();
        }
        return Collections.singletonList(
                new DecodingReplacement(byteOffset, characterOffset));
    }

    private static void requireSha256(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid " + label);
        }
    }
}
