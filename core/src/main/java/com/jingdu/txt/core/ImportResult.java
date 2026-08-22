package com.jingdu.txt.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ImportResult {
    private final Path output;
    private final DetectedEncoding encoding;
    private final long sourceBytes;
    private final long outputBytes;
    private final long normalizedCharacters;
    private final long firstWindowNanos;
    private final long totalNanos;
    private final String sourceSha256;
    private final String outputSha256;
    private final long decodingReplacementCount;
    private final long firstDecodingErrorByteOffset;
    private final long firstReplacementCharacterOffset;
    private final List<DecodingReplacement> decodingReplacements;

    public ImportResult(Path output, DetectedEncoding encoding, long sourceBytes, long outputBytes,
            long normalizedCharacters, long firstWindowNanos, long totalNanos,
            String sourceSha256, String outputSha256,
            long decodingReplacementCount, long firstDecodingErrorByteOffset,
            long firstReplacementCharacterOffset,
            List<DecodingReplacement> decodingReplacements) {
        if (decodingReplacementCount < 0
                || (decodingReplacementCount == 0
                        && (firstDecodingErrorByteOffset != -1
                                || firstReplacementCharacterOffset != -1))
                || (decodingReplacementCount > 0
                        && (firstDecodingErrorByteOffset < 0
                                || firstReplacementCharacterOffset < 0))) {
            throw new IllegalArgumentException("invalid decoding diagnostics");
        }
        validateLocations(decodingReplacementCount, firstDecodingErrorByteOffset,
                firstReplacementCharacterOffset, decodingReplacements, false);
        this.output = output;
        this.encoding = encoding;
        this.sourceBytes = sourceBytes;
        this.outputBytes = outputBytes;
        this.normalizedCharacters = normalizedCharacters;
        this.firstWindowNanos = firstWindowNanos;
        this.totalNanos = totalNanos;
        this.sourceSha256 = sourceSha256;
        this.outputSha256 = outputSha256;
        this.decodingReplacementCount = decodingReplacementCount;
        this.firstDecodingErrorByteOffset = firstDecodingErrorByteOffset;
        this.firstReplacementCharacterOffset = firstReplacementCharacterOffset;
        this.decodingReplacements = Collections.unmodifiableList(
                new ArrayList<DecodingReplacement>(decodingReplacements));
    }

    public Path getOutput() { return output; }
    public DetectedEncoding getEncoding() { return encoding; }
    public long getSourceBytes() { return sourceBytes; }
    public long getOutputBytes() { return outputBytes; }
    public long getNormalizedCharacters() { return normalizedCharacters; }
    public long getFirstWindowNanos() { return firstWindowNanos; }
    public long getTotalNanos() { return totalNanos; }
    public String getSourceSha256() { return sourceSha256; }
    public String getOutputSha256() { return outputSha256; }
    public long getDecodingReplacementCount() { return decodingReplacementCount; }
    public long getFirstDecodingErrorByteOffset() { return firstDecodingErrorByteOffset; }
    public long getFirstReplacementCharacterOffset() { return firstReplacementCharacterOffset; }
    public List<DecodingReplacement> getDecodingReplacements() {
        return decodingReplacements;
    }

    static void validateLocations(long totalCount, long firstByteOffset,
            long firstCharacterOffset, List<DecodingReplacement> locations,
            boolean allowLegacyEmpty) {
        if (locations == null || locations.size() > DecodingReplacement.MAXIMUM_RETAINED
                || locations.size() > totalCount
                || (!allowLegacyEmpty && totalCount > 0 && locations.isEmpty())) {
            throw new IllegalArgumentException("invalid decoding replacement locations");
        }
        long previousByte = -1;
        long previousCharacter = -1;
        for (DecodingReplacement location : locations) {
            if (location == null || location.getSourceByteOffset() <= previousByte
                    || location.getNormalizedCharacterOffset() <= previousCharacter) {
                throw new IllegalArgumentException("unordered decoding replacement locations");
            }
            previousByte = location.getSourceByteOffset();
            previousCharacter = location.getNormalizedCharacterOffset();
        }
        if (!locations.isEmpty()
                && (locations.get(0).getSourceByteOffset() != firstByteOffset
                        || locations.get(0).getNormalizedCharacterOffset()
                                != firstCharacterOffset)) {
            throw new IllegalArgumentException("first decoding replacement mismatch");
        }
    }
}
