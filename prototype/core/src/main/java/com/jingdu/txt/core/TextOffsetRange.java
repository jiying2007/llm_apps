package com.jingdu.txt.core;

public final class TextOffsetRange {
    private final long startOffset;
    private final long endOffset;

    public TextOffsetRange(long startOffset, long endOffset) {
        if (startOffset < 0 || endOffset < startOffset) {
            throw new IllegalArgumentException("text range must be non-negative and ordered");
        }
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    public long getStartOffset() { return startOffset; }
    public long getEndOffset() { return endOffset; }
    public long length() { return endOffset - startOffset; }
}
