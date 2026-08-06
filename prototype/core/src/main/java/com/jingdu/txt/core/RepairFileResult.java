package com.jingdu.txt.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RepairFileResult {
    private final Path derivedFile;
    private final Path projectionFile;
    private final Path candidateIndexFile;
    private final String revisionId;
    private final String sourceSha256;
    private final String derivedSha256;
    private final long originalCharacters;
    private final long derivedCharacters;
    private final long matchCount;
    private final long candidateMatchCount;
    private final List<RepairMatch> previews;
    private final List<String> warnings;
    private final Map<String, Long> ruleMatchCounts;
    private final Map<String, Long> ruleCandidateCounts;

    public RepairFileResult(Path derivedFile, Path projectionFile, String revisionId,
            String sourceSha256, String derivedSha256, long originalCharacters,
            long derivedCharacters, long matchCount, long candidateMatchCount,
            List<RepairMatch> previews, List<String> warnings,
            Map<String, Long> ruleMatchCounts, Map<String, Long> ruleCandidateCounts) {
        this(derivedFile, projectionFile, null, revisionId, sourceSha256, derivedSha256,
                originalCharacters, derivedCharacters, matchCount, candidateMatchCount,
                previews, warnings, ruleMatchCounts, ruleCandidateCounts);
    }

    public RepairFileResult(Path derivedFile, Path projectionFile, Path candidateIndexFile,
            String revisionId, String sourceSha256, String derivedSha256,
            long originalCharacters, long derivedCharacters, long matchCount,
            long candidateMatchCount, List<RepairMatch> previews, List<String> warnings,
            Map<String, Long> ruleMatchCounts, Map<String, Long> ruleCandidateCounts) {
        this.derivedFile = derivedFile;
        this.projectionFile = projectionFile;
        this.candidateIndexFile = candidateIndexFile;
        this.revisionId = revisionId;
        this.sourceSha256 = sourceSha256;
        this.derivedSha256 = derivedSha256;
        this.originalCharacters = originalCharacters;
        this.derivedCharacters = derivedCharacters;
        this.matchCount = matchCount;
        this.candidateMatchCount = candidateMatchCount;
        this.previews = Collections.unmodifiableList(new ArrayList<RepairMatch>(previews));
        this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
        this.ruleMatchCounts = Collections.unmodifiableMap(
                new LinkedHashMap<String, Long>(ruleMatchCounts));
        this.ruleCandidateCounts = Collections.unmodifiableMap(
                new LinkedHashMap<String, Long>(ruleCandidateCounts));
    }

    public Path getDerivedFile() { return derivedFile; }
    public Path getProjectionFile() { return projectionFile; }
    public Path getCandidateIndexFile() { return candidateIndexFile; }
    public String getRevisionId() { return revisionId; }
    public String getSourceSha256() { return sourceSha256; }
    public String getDerivedSha256() { return derivedSha256; }
    public long getOriginalCharacters() { return originalCharacters; }
    public long getDerivedCharacters() { return derivedCharacters; }
    public long getMatchCount() { return matchCount; }
    public long getCandidateMatchCount() { return candidateMatchCount; }
    public List<RepairMatch> getPreviews() { return previews; }
    public List<String> getWarnings() { return warnings; }
    public Map<String, Long> getRuleMatchCounts() { return ruleMatchCounts; }
    public Map<String, Long> getRuleCandidateCounts() { return ruleCandidateCounts; }
}
