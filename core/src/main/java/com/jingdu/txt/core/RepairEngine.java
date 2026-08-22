package com.jingdu.txt.core;

import java.util.ArrayList;
import java.util.List;

public final class RepairEngine {
    private static final int CONTEXT_CHARACTERS = 16;

    public RepairApplyResult apply(String original, List<RepairRule> inputRules) {
        return apply(original, inputRules, RepairSelection.all());
    }

    public RepairApplyResult apply(String original, List<RepairRule> inputRules,
            RepairSelection selection) {
        if (selection == null) {
            throw new IllegalArgumentException("repair selection must not be null");
        }
        List<RepairRule> rules = RepairRules.enabled(inputRules);
        List<String> warnings = RepairRules.warnings(rules);
        List<RepairMatch> matches = new ArrayList<RepairMatch>();
        List<ProjectionSegment> segments = new ArrayList<ProjectionSegment>();
        StringBuilder derived = new StringBuilder(original.length());

        int originalOffset = 0;
        while (originalOffset < original.length()) {
            RepairRule selected = RepairRules.firstMatching(original, originalOffset, rules);
            if (selected == null) {
                derived.append(original.charAt(originalOffset));
                originalOffset++;
                continue;
            }

            int originalEnd = originalOffset + selected.getMatchText().length();
            boolean applied = selection.isApplied(selected.getId(), originalOffset);
            if (!applied) {
                derived.append(selected.getMatchText());
                matches.add(new RepairMatch(selected.getId(), originalOffset, originalEnd,
                        original.substring(Math.max(0,
                                originalOffset - CONTEXT_CHARACTERS), originalOffset),
                        selected.getMatchText(), selected.getReplacement(),
                        original.substring(originalEnd,
                                Math.min(original.length(), originalEnd + CONTEXT_CHARACTERS)),
                        false));
                originalOffset = originalEnd;
                continue;
            }
            int derivedStart = derived.length();
            derived.append(selected.getReplacement());
            int derivedEnd = derived.length();
            segments.add(new ProjectionSegment(selected.getId(), originalOffset, originalEnd,
                    derivedStart, derivedEnd));
            matches.add(new RepairMatch(
                    selected.getId(),
                    originalOffset,
                    originalEnd,
                    original.substring(Math.max(0, originalOffset - CONTEXT_CHARACTERS), originalOffset),
                    selected.getMatchText(),
                    selected.getReplacement(),
                    original.substring(originalEnd,
                            Math.min(original.length(), originalEnd + CONTEXT_CHARACTERS)), true));
            originalOffset = originalEnd;
        }
        return new RepairApplyResult(derived.toString(), matches, segments, warnings);
    }

}
