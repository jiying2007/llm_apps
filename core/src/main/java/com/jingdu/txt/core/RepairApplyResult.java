package com.jingdu.txt.core;

import java.util.Collections;
import java.util.List;

public final class RepairApplyResult {
    private final String derivedText;
    private final List<RepairMatch> matches;
    private final List<ProjectionSegment> segments;
    private final List<String> warnings;

    public RepairApplyResult(String derivedText, List<RepairMatch> matches,
            List<ProjectionSegment> segments, List<String> warnings) {
        this.derivedText = derivedText;
        this.matches = Collections.unmodifiableList(matches);
        this.segments = Collections.unmodifiableList(segments);
        this.warnings = Collections.unmodifiableList(warnings);
    }

    public String getDerivedText() { return derivedText; }
    public List<RepairMatch> getMatches() { return matches; }
    public List<ProjectionSegment> getSegments() { return segments; }
    public List<String> getWarnings() { return warnings; }

    public int mapOriginalOffsetToDerived(int originalOffset) {
        int delta = 0;
        for (ProjectionSegment segment : segments) {
            if (originalOffset < segment.getOriginalStart()) {
                return originalOffset + delta;
            }
            int originalLength = segment.getOriginalEnd() - segment.getOriginalStart();
            int derivedLength = segment.getDerivedEnd() - segment.getDerivedStart();
            if (originalOffset <= segment.getOriginalEnd()) {
                int relative = originalOffset - segment.getOriginalStart();
                return segment.getDerivedStart() + Math.min(relative, derivedLength);
            }
            delta += derivedLength - originalLength;
        }
        return originalOffset + delta;
    }
}
