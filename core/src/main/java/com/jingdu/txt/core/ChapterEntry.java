package com.jingdu.txt.core;

public final class ChapterEntry {
    private final String title;
    private final int characterOffset;
    private final int confidencePercent;

    public ChapterEntry(String title, int characterOffset, int confidencePercent) {
        this.title = title;
        this.characterOffset = characterOffset;
        this.confidencePercent = confidencePercent;
    }

    public String getTitle() { return title; }
    public int getCharacterOffset() { return characterOffset; }
    public int getConfidencePercent() { return confidencePercent; }
}
