package com.jingdu.txt.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SegmentedText implements CharSequence {
    private final List<String> segments;
    private final int[] starts;
    private final int length;

    public SegmentedText(List<String> segments) {
        if (segments == null) {
            throw new IllegalArgumentException("segments must not be null");
        }
        List<String> copy = new ArrayList<String>(segments.size());
        starts = new int[segments.size()];
        int total = 0;
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            if (segment == null) {
                throw new IllegalArgumentException("segment must not be null");
            }
            starts[i] = total;
            copy.add(segment);
            total = Math.addExact(total, segment.length());
        }
        this.segments = Collections.unmodifiableList(copy);
        this.length = total;
    }

    public List<String> getSegments() {
        return segments;
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public char charAt(int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("index=" + index + ", length=" + length);
        }
        int low = 0;
        int high = starts.length - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int start = starts[middle];
            int end = start + segments.get(middle).length();
            if (index < start) {
                high = middle - 1;
            } else if (index >= end) {
                low = middle + 1;
            } else {
                return segments.get(middle).charAt(index - start);
            }
        }
        throw new IllegalStateException("segment lookup failed at " + index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        if (start < 0 || end < start || end > length) {
            throw new IndexOutOfBoundsException("start=" + start + ", end=" + end + ", length=" + length);
        }
        StringBuilder result = new StringBuilder(end - start);
        for (int i = start; i < end; i++) {
            result.append(charAt(i));
        }
        return result.toString();
    }

    @Override
    public String toString() {
        return subSequence(0, length).toString();
    }
}
