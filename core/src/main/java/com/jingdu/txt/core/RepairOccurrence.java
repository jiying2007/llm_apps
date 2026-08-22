package com.jingdu.txt.core;

public final class RepairOccurrence implements Comparable<RepairOccurrence> {
    private final String ruleId;
    private final long originalOffset;

    public RepairOccurrence(String ruleId, long originalOffset) {
        if (ruleId == null || ruleId.trim().isEmpty()) {
            throw new IllegalArgumentException("rule id must not be blank");
        }
        if (originalOffset < 0) {
            throw new IllegalArgumentException("original offset must not be negative");
        }
        this.ruleId = ruleId;
        this.originalOffset = originalOffset;
    }

    public String getRuleId() { return ruleId; }
    public long getOriginalOffset() { return originalOffset; }

    @Override
    public int compareTo(RepairOccurrence other) {
        int byOffset = Long.compare(originalOffset, other.originalOffset);
        return byOffset != 0 ? byOffset : ruleId.compareTo(other.ruleId);
    }

    @Override
    public boolean equals(Object value) {
        if (!(value instanceof RepairOccurrence)) {
            return false;
        }
        RepairOccurrence other = (RepairOccurrence) value;
        return originalOffset == other.originalOffset && ruleId.equals(other.ruleId);
    }

    @Override
    public int hashCode() {
        return 31 * ruleId.hashCode() + Long.hashCode(originalOffset);
    }
}
