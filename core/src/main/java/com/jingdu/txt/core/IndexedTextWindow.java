package com.jingdu.txt.core;

public final class IndexedTextWindow {
    private final int startOffset;
    private final String text;

    public IndexedTextWindow(int startOffset, String text) {
        this.startOffset = startOffset;
        this.text = text;
    }

    public int getStartOffset() { return startOffset; }
    public String getText() { return text; }
}
