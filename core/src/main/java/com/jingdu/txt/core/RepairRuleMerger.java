package com.jingdu.txt.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RepairRuleMerger {
    public static final int MAXIMUM_RULES = 1000;
    public RepairRuleMergeResult merge(List<RepairRule> existing,
            List<RepairRule> imported, RepairRuleMergePolicy policy) {
        if (existing == null || imported == null || policy == null) {
            throw new IllegalArgumentException("merge inputs must not be null");
        }
        ensureUniqueIds(existing, "existing");
        ensureUniqueIds(imported, "imported");
        List<RepairRule> merged = new ArrayList<RepairRule>(existing);
        Map<String, Integer> positions = new HashMap<String, Integer>();
        for (int index = 0; index < merged.size(); index++) {
            positions.put(merged.get(index).getId(), index);
        }
        int added = 0;
        int replaced = 0;
        int skipped = 0;
        for (RepairRule rule : imported) {
            Integer position = positions.get(rule.getId());
            if (position == null) {
                positions.put(rule.getId(), merged.size());
                merged.add(rule);
                added++;
            } else if (policy == RepairRuleMergePolicy.REPLACE_EXISTING) {
                merged.set(position, rule);
                replaced++;
            } else {
                skipped++;
            }
        }
        if (merged.size() > MAXIMUM_RULES) {
            throw new IllegalArgumentException("merged repair rules exceed " + MAXIMUM_RULES);
        }
        List<RepairRule> normalized = new ArrayList<RepairRule>(merged.size());
        for (int index = 0; index < merged.size(); index++) {
            RepairRule rule = merged.get(index);
            normalized.add(new RepairRule(rule.getId(), rule.getMatchText(),
                    rule.getReplacement(), rule.isEnabled(), (index + 1) * 10,
                    rule.getScope(), rule.getNote()));
        }
        return new RepairRuleMergeResult(normalized, added, replaced, skipped);
    }

    private static void ensureUniqueIds(List<RepairRule> rules, String label) {
        Set<String> ids = new HashSet<String>();
        for (RepairRule rule : rules) {
            if (rule == null || !ids.add(rule.getId())) {
                throw new IllegalArgumentException(label + " rules contain duplicate/null id");
            }
        }
    }
}
