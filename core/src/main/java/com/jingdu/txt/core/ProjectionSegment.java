package com.jingdu.txt.core;

public final class ProjectionSegment {
    private final String ruleId;
    private final int originalStart;
    private final int originalEnd;
    private final int derivedStart;
    private final int derivedEnd;

    public ProjectionSegment(String ruleId, int originalStart, int originalEnd,
            int derivedStart, int derivedEnd) {
        this.ruleId = ruleId;
        this.originalStart = originalStart;
        this.originalEnd = originalEnd;
        this.derivedStart = derivedStart;
        this.derivedEnd = derivedEnd;
    }

    public String getRuleId() { return ruleId; }
    public int getOriginalStart() { return originalStart; }
    public int getOriginalEnd() { return originalEnd; }
    public int getDerivedStart() { return derivedStart; }
    public int getDerivedEnd() { return derivedEnd; }
}
