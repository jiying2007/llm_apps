package com.jingdu.txt.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

final class RepairRules {
    private RepairRules() {}

    static List<RepairRule> enabled(List<RepairRule> inputRules) {
        List<RepairRule> rules = new ArrayList<RepairRule>();
        Set<String> ids = new HashSet<String>();
        for (RepairRule rule : inputRules) {
            if (!ids.add(rule.getId())) {
                throw new IllegalArgumentException("duplicate repair rule id: " + rule.getId());
            }
            if (rule.isEnabled()) {
                rules.add(rule);
            }
        }
        Collections.sort(rules, new Comparator<RepairRule>() {
            @Override
            public int compare(RepairRule left, RepairRule right) {
                int byOrder = Integer.compare(left.getOrder(), right.getOrder());
                return byOrder != 0 ? byOrder : left.getId().compareTo(right.getId());
            }
        });
        return rules;
    }

    static RepairRule firstMatching(CharSequence text, int offset, List<RepairRule> rules) {
        for (RepairRule rule : rules) {
            String match = rule.getMatchText();
            if (offset + match.length() <= text.length()) {
                boolean matched = true;
                for (int i = 0; i < match.length(); i++) {
                    if (text.charAt(offset + i) != match.charAt(i)) {
                        matched = false;
                        break;
                    }
                }
                if (matched) {
                    return rule;
                }
            }
        }
        return null;
    }

    static List<String> warnings(List<RepairRule> rules) {
        List<String> warnings = new ArrayList<String>();
        for (int i = 0; i < rules.size(); i++) {
            RepairRule left = rules.get(i);
            for (int j = i + 1; j < rules.size(); j++) {
                RepairRule right = rules.get(j);
                if (left.getMatchText().equals(right.getMatchText())
                        && !left.getReplacement().equals(right.getReplacement())) {
                    warnings.add("duplicate match with different replacements: "
                            + left.getId() + " before " + right.getId());
                }
                if (!left.getMatchText().equals(right.getMatchText())
                        && (left.getMatchText().startsWith(right.getMatchText())
                                || right.getMatchText().startsWith(left.getMatchText()))) {
                    warnings.add("overlapping match priority: "
                            + left.getId() + " before " + right.getId());
                }
                if (left.getReplacement().contains(right.getMatchText())) {
                    warnings.add("non-cascading rule overlap: " + left.getId() + " -> " + right.getId());
                }
                if (right.getReplacement().contains(left.getMatchText())) {
                    warnings.add("non-cascading rule overlap: " + right.getId() + " -> " + left.getId());
                }
            }
        }
        return warnings;
    }
}
