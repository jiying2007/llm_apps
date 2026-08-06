package com.jingdu.txt.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RepairRuleMergeResult {
    private final List<RepairRule> rules;
    private final int added;
    private final int replaced;
    private final int skipped;

    RepairRuleMergeResult(List<RepairRule> rules, int added, int replaced, int skipped) {
        this.rules = Collections.unmodifiableList(new ArrayList<RepairRule>(rules));
        this.added = added;
        this.replaced = replaced;
        this.skipped = skipped;
    }

    public List<RepairRule> getRules() { return rules; }
    public int getAdded() { return added; }
    public int getReplaced() { return replaced; }
    public int getSkipped() { return skipped; }
}
