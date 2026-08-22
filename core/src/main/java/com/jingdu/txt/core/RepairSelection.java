package com.jingdu.txt.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public final class RepairSelection {
    public static final int MAXIMUM_EXCLUSIONS = 10000;
    private static final RepairSelection ALL = new RepairSelection(Collections.emptyList());

    private final List<RepairOccurrence> sortedExclusions;

    private RepairSelection(Collection<RepairOccurrence> values) {
        if (values.size() > MAXIMUM_EXCLUSIONS) {
            throw new IllegalArgumentException("repair exclusions exceed " + MAXIMUM_EXCLUSIONS);
        }
        List<RepairOccurrence> sorted = new ArrayList<RepairOccurrence>(
                new HashSet<RepairOccurrence>(values));
        Collections.sort(sorted);
        sortedExclusions = Collections.unmodifiableList(sorted);
    }

    public static RepairSelection all() {
        return ALL;
    }

    public static RepairSelection excluding(Collection<RepairOccurrence> values) {
        if (values == null || values.isEmpty()) {
            return ALL;
        }
        return new RepairSelection(values);
    }

    public boolean isApplied(String ruleId, long originalOffset) {
        int low = 0;
        int high = sortedExclusions.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            RepairOccurrence occurrence = sortedExclusions.get(middle);
            int comparison = Long.compare(originalOffset, occurrence.getOriginalOffset());
            if (comparison == 0) {
                comparison = ruleId.compareTo(occurrence.getRuleId());
            }
            if (comparison < 0) {
                high = middle - 1;
            } else if (comparison > 0) {
                low = middle + 1;
            } else {
                return false;
            }
        }
        return true;
    }

    public List<RepairOccurrence> getExclusions() {
        return sortedExclusions;
    }

    public boolean hasExclusions() {
        return !sortedExclusions.isEmpty();
    }
}
