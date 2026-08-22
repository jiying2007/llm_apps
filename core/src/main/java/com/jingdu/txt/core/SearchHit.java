package com.jingdu.txt.core;

public final class SearchHit {
    private final int startOffset;
    private final int endOffset;
    private final String context;

    public SearchHit(int startOffset, int endOffset, String context) {
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.context = context;
    }

    public int getStartOffset() { return startOffset; }
    public int getEndOffset() { return endOffset; }
    public String getContext() { return context; }
}
