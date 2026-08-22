package com.jingdu.txt.core;

public final class RepairOrdinalRange {
    private final long zeroBasedOffset;
    private final int count;

    private RepairOrdinalRange(long zeroBasedOffset, int count) {
        this.zeroBasedOffset = zeroBasedOffset;
        this.count = count;
    }

    public static RepairOrdinalRange fromOneBased(long first, long last,
            long totalCandidates, int maximumCount) {
        if (totalCandidates < 0) {
            throw new IllegalArgumentException("totalCandidates must not be negative");
        }
        if (maximumCount < 1) {
            throw new IllegalArgumentException("maximumCount must be positive");
        }
        if (first < 1 || last < first || last > totalCandidates) {
            throw new IllegalArgumentException("repair candidate range is outside preview");
        }
        long count = last - first + 1;
        if (count > maximumCount) {
            throw new IllegalArgumentException("repair candidate range exceeds " + maximumCount);
        }
        return new RepairOrdinalRange(first - 1, (int) count);
    }

    public long getZeroBasedOffset() { return zeroBasedOffset; }

    public int getCount() { return count; }

    public long getFirstOneBased() { return zeroBasedOffset + 1; }

    public long getLastOneBased() { return zeroBasedOffset + count; }
}
