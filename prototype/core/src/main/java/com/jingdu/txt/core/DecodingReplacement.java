package com.jingdu.txt.core;

/** One recovered decoding error mapped from source bytes to normalized text. */
public final class DecodingReplacement {
    public static final int MAXIMUM_RETAINED = 128;

    private final long sourceByteOffset;
    private final long normalizedCharacterOffset;

    public DecodingReplacement(long sourceByteOffset,
            long normalizedCharacterOffset) {
        if (sourceByteOffset < 0 || normalizedCharacterOffset < 0) {
            throw new IllegalArgumentException("invalid decoding replacement location");
        }
        this.sourceByteOffset = sourceByteOffset;
        this.normalizedCharacterOffset = normalizedCharacterOffset;
    }

    public long getSourceByteOffset() {
        return sourceByteOffset;
    }

    public long getNormalizedCharacterOffset() {
        return normalizedCharacterOffset;
    }
}
