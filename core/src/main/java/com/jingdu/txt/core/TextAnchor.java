package com.jingdu.txt.core;

public final class TextAnchor {
    private final int expectedOffset;
    private final int paragraphStart;
    private final String paragraphHash;
    private final String contextHash;

    public TextAnchor(int expectedOffset, int paragraphStart, String paragraphHash, String contextHash) {
        this.expectedOffset = expectedOffset;
        this.paragraphStart = paragraphStart;
        this.paragraphHash = paragraphHash;
        this.contextHash = contextHash;
    }

    public int getExpectedOffset() { return expectedOffset; }
    public int getParagraphStart() { return paragraphStart; }
    public String getParagraphHash() { return paragraphHash; }
    public String getContextHash() { return contextHash; }
}
