package com.jingdu.txt.core;

public final class RepairMatch {
    private final String ruleId;
    private final int originalStart;
    private final int originalEnd;
    private final String beforeContext;
    private final String matchedText;
    private final String replacement;
    private final String afterContext;
    private final boolean applied;

    public RepairMatch(String ruleId, int originalStart, int originalEnd, String beforeContext,
            String matchedText, String replacement, String afterContext) {
        this(ruleId, originalStart, originalEnd, beforeContext, matchedText, replacement,
                afterContext, true);
    }

    public RepairMatch(String ruleId, int originalStart, int originalEnd, String beforeContext,
            String matchedText, String replacement, String afterContext, boolean applied) {
        this.ruleId = ruleId;
        this.originalStart = originalStart;
        this.originalEnd = originalEnd;
        this.beforeContext = beforeContext;
        this.matchedText = matchedText;
        this.replacement = replacement;
        this.afterContext = afterContext;
        this.applied = applied;
    }

    public String getRuleId() { return ruleId; }
    public int getOriginalStart() { return originalStart; }
    public int getOriginalEnd() { return originalEnd; }
    public String getBeforeContext() { return beforeContext; }
    public String getMatchedText() { return matchedText; }
    public String getReplacement() { return replacement; }
    public String getAfterContext() { return afterContext; }
    public boolean isApplied() { return applied; }
}
