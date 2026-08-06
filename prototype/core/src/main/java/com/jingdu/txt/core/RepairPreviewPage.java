package com.jingdu.txt.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RepairPreviewPage {
    private final long matchOffset;
    private final List<RepairMatch> matches;
    private final boolean hasNext;

    RepairPreviewPage(long matchOffset, List<RepairMatch> matches, boolean hasNext) {
        this.matchOffset = matchOffset;
        this.matches = Collections.unmodifiableList(new ArrayList<RepairMatch>(matches));
        this.hasNext = hasNext;
    }

    public long getMatchOffset() { return matchOffset; }
    public List<RepairMatch> getMatches() { return matches; }
    public boolean hasPrevious() { return matchOffset > 0; }
    public boolean hasNext() { return hasNext; }
}
