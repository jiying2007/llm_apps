package com.jingdu.txt.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.DigestOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RepairFilePipeline {
    private static final int CONTEXT_CHARACTERS = 16;
    private static final int MAX_RULE_CHARACTERS = 4096;

    public RepairFileResult apply(Path originalUtf8, Path derivedTarget, Path projectionTarget,
            List<RepairRule> inputRules, int previewLimit) throws IOException {
        return apply(originalUtf8, derivedTarget, projectionTarget, inputRules,
                RepairSelection.all(), 0, previewLimit);
    }

    public RepairFileResult apply(Path originalUtf8, Path derivedTarget, Path projectionTarget,
            List<RepairRule> inputRules, RepairSelection selection, int previewLimit)
            throws IOException {
        return apply(originalUtf8, derivedTarget, projectionTarget, inputRules,
                selection, 0, previewLimit);
    }

    public RepairFileResult apply(Path originalUtf8, Path derivedTarget, Path projectionTarget,
            List<RepairRule> inputRules, RepairSelection selection, long previewOffset,
            int previewLimit) throws IOException {
        return apply(originalUtf8, derivedTarget, projectionTarget, null, inputRules,
                selection, previewOffset, previewLimit);
    }

    public RepairFileResult apply(Path originalUtf8, Path derivedTarget, Path projectionTarget,
            Path candidateIndexTarget, List<RepairRule> inputRules, RepairSelection selection,
            long previewOffset, int previewLimit) throws IOException {
        Path normalizedDerived = derivedTarget.toAbsolutePath().normalize();
        Path normalizedProjection = projectionTarget.toAbsolutePath().normalize();
        Path normalizedCandidates = candidateIndexTarget == null ? null
                : candidateIndexTarget.toAbsolutePath().normalize();
        if (normalizedDerived.equals(normalizedProjection)
                || normalizedCandidates != null
                        && (normalizedCandidates.equals(normalizedDerived)
                                || normalizedCandidates.equals(normalizedProjection))) {
            throw new IllegalArgumentException("repair output targets must be distinct");
        }
        if (selection == null) {
            throw new IllegalArgumentException("repair selection must not be null");
        }
        if (previewOffset < 0) {
            throw new IllegalArgumentException("previewOffset must not be negative");
        }
        if (Files.exists(derivedTarget)) {
            throw new FileAlreadyExistsException(derivedTarget.toString());
        }
        if (Files.exists(projectionTarget)) {
            throw new FileAlreadyExistsException(projectionTarget.toString());
        }
        if (candidateIndexTarget != null && Files.exists(candidateIndexTarget)) {
            throw new FileAlreadyExistsException(candidateIndexTarget.toString());
        }
        if (previewLimit < 0 || previewLimit > 1000) {
            throw new IllegalArgumentException("previewLimit must be between 0 and 1000");
        }
        List<RepairRule> rules = RepairRules.enabled(inputRules);
        validateRules(rules);
        List<String> warnings = RepairRules.warnings(rules);
        String sourceSha256 = DiskDocumentIndexBuilder.computeSha256(originalUtf8);
        String revisionId = revisionId(sourceSha256, rules, selection);
        int maximumMatch = maximumMatchLength(rules);

        Path derivedTemporary = derivedTarget.resolveSibling(
                derivedTarget.getFileName().toString() + ".tmp");
        Path projectionTemporary = projectionTarget.resolveSibling(
                projectionTarget.getFileName().toString() + ".tmp");
        Path candidateTemporary = candidateIndexTarget == null ? null
                : candidateIndexTarget.resolveSibling(
                        candidateIndexTarget.getFileName().toString() + ".tmp");
        Files.deleteIfExists(derivedTemporary);
        Files.deleteIfExists(projectionTemporary);
        if (candidateTemporary != null) {
            Files.deleteIfExists(candidateTemporary);
        }
        MessageDigest derivedDigest = newSha256();
        List<RepairMatch> previews = new ArrayList<RepairMatch>();
        long originalOffset = 0;
        long derivedOffset = 0;
        long matchCount = 0;
        long candidateMatchCount = 0;
        boolean selective = selection.hasExclusions();
        Map<String, Long> ruleMatchCounts = new LinkedHashMap<String, Long>();
        Map<String, Long> ruleCandidateCounts = selective
                ? new LinkedHashMap<String, Long>() : ruleMatchCounts;
        for (RepairRule rule : rules) {
            ruleMatchCounts.put(rule.getId(), 0L);
            if (selective) {
                ruleCandidateCounts.put(rule.getId(), 0L);
            }
        }
        Map<String, Integer> ruleIndexes = new HashMap<String, Integer>();
        for (int index = 0; index < rules.size(); index++) {
            ruleIndexes.put(rules.get(index).getId(), index);
        }
        boolean projectionPublished = false;
        boolean candidatePublished = false;
        try {
            try (ReaderCursor cursor = new ReaderCursor(originalUtf8);
                    BufferedWriter output = new BufferedWriter(new OutputStreamWriter(
                            new DigestOutputStream(Files.newOutputStream(derivedTemporary),
                                    derivedDigest), StandardCharsets.UTF_8), 128 * 1024);
                    DiskRepairProjection.Writer projection =
                            DiskRepairProjection.create(projectionTemporary);
                    DiskRepairCandidateIndex.Writer candidates = candidateTemporary == null
                            ? null : DiskRepairCandidateIndex.create(
                                    candidateTemporary, sourceSha256, rules)) {
                StringBuilder window = new StringBuilder(maximumMatch + CONTEXT_CHARACTERS);
                StringBuilder before = new StringBuilder(CONTEXT_CHARACTERS);
                StringBuilder unchanged = new StringBuilder(64 * 1024);
                boolean endOfInput = false;
                while (!endOfInput || window.length() > 0) {
                    while (!endOfInput
                            && window.length() < maximumMatch + CONTEXT_CHARACTERS) {
                        int next = cursor.next();
                        if (next < 0) {
                            endOfInput = true;
                        } else {
                            window.append((char) next);
                        }
                    }
                    if (window.length() == 0) {
                        break;
                    }
                    RepairRule selected = RepairRules.firstMatching(window, 0, rules);
                    if (selected == null) {
                        char value = window.charAt(0);
                        unchanged.append(value);
                        if (unchanged.length() >= 64 * 1024) {
                            output.write(unchanged.toString());
                            unchanged.setLength(0);
                        }
                        appendBefore(before, window, 1);
                        window.deleteCharAt(0);
                        originalOffset++;
                        derivedOffset++;
                        continue;
                    }

                    int matchLength = selected.getMatchText().length();
                    long originalEnd = originalOffset + matchLength;
                    if (candidates != null) {
                        candidates.writeRecord(candidateMatchCount, originalOffset,
                                matchLength, ruleIndexes.get(selected.getId()));
                    }
                    boolean applied = selection.isApplied(selected.getId(), originalOffset);
                    long derivedEnd = derivedOffset + (applied
                            ? selected.getReplacement().length() : matchLength);
                    if (unchanged.length() > 0) {
                        output.write(unchanged.toString());
                        unchanged.setLength(0);
                    }
                    if (candidateMatchCount >= previewOffset
                            && previews.size() < previewLimit
                            && originalOffset <= Integer.MAX_VALUE) {
                        int afterStart = Math.min(matchLength, window.length());
                        int afterEnd = Math.min(window.length(), afterStart + CONTEXT_CHARACTERS);
                        previews.add(new RepairMatch(selected.getId(), (int) originalOffset,
                                (int) Math.min(Integer.MAX_VALUE, originalEnd), before.toString(),
                                selected.getMatchText(), selected.getReplacement(),
                                window.substring(afterStart, afterEnd), applied));
                    }
                    candidateMatchCount++;
                    if (selective) {
                        ruleCandidateCounts.put(selected.getId(),
                                ruleCandidateCounts.get(selected.getId()) + 1L);
                    }
                    if (applied) {
                        projection.writeRecord(originalOffset, originalEnd,
                                derivedOffset, derivedEnd);
                        output.write(selected.getReplacement());
                        matchCount++;
                        ruleMatchCounts.put(selected.getId(),
                                ruleMatchCounts.get(selected.getId()) + 1L);
                    } else {
                        output.write(selected.getMatchText());
                    }
                    appendBefore(before, window, matchLength);
                    window.delete(0, matchLength);
                    originalOffset = originalEnd;
                    derivedOffset = derivedEnd;
                }
                if (unchanged.length() > 0) {
                    output.write(unchanged.toString());
                }
            }
            if (candidateTemporary != null) {
                atomicMove(candidateTemporary, candidateIndexTarget);
                candidatePublished = true;
            }
            atomicMove(projectionTemporary, projectionTarget);
            projectionPublished = true;
            atomicMove(derivedTemporary, derivedTarget);
            return new RepairFileResult(derivedTarget, projectionTarget, candidateIndexTarget,
                    revisionId,
                    sourceSha256, hex(derivedDigest.digest()), originalOffset, derivedOffset,
                    matchCount, candidateMatchCount, previews, warnings,
                    ruleMatchCounts, ruleCandidateCounts);
        } catch (IOException error) {
            Files.deleteIfExists(derivedTemporary);
            Files.deleteIfExists(projectionTemporary);
            if (candidateTemporary != null) {
                Files.deleteIfExists(candidateTemporary);
            }
            if (candidatePublished) {
                Files.deleteIfExists(candidateIndexTarget);
            }
            if (projectionPublished) {
                Files.deleteIfExists(projectionTarget);
            }
            throw error;
        }
    }

    private static void validateRules(List<RepairRule> rules) {
        for (RepairRule rule : rules) {
            if (rule.getMatchText().length() > MAX_RULE_CHARACTERS
                    || rule.getReplacement().length() > MAX_RULE_CHARACTERS) {
                throw new IllegalArgumentException("rule exceeds 4096 UTF-16 characters: "
                        + rule.getId());
            }
        }
    }

    private static int maximumMatchLength(List<RepairRule> rules) {
        int maximum = 1;
        for (RepairRule rule : rules) {
            maximum = Math.max(maximum, rule.getMatchText().length());
        }
        return maximum;
    }

    private static void appendBefore(StringBuilder before, CharSequence consumed, int length) {
        for (int i = 0; i < length; i++) {
            before.append(consumed.charAt(i));
            if (before.length() > CONTEXT_CHARACTERS) {
                before.deleteCharAt(0);
            }
        }
    }

    private static String revisionId(String sourceSha256, List<RepairRule> rules,
            RepairSelection selection) {
        MessageDigest digest = newSha256();
        update(digest, "jingdu-repair-revision-v2");
        update(digest, sourceSha256);
        update(digest, "rules");
        for (RepairRule rule : rules) {
            update(digest, rule.getId());
            update(digest, rule.getMatchText());
            update(digest, rule.getReplacement());
            update(digest, Integer.toString(rule.getOrder()));
        }
        update(digest, "selection");
        for (RepairOccurrence occurrence : selection.getExclusions()) {
            update(digest, occurrence.getRuleId());
            update(digest, Long.toString(occurrence.getOriginalOffset()));
        }
        return hex(digest.digest());
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xFF));
        }
        return result.toString();
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target);
        }
    }

    private static final class ReaderCursor implements AutoCloseable {
        private final BufferedReader reader;
        private final char[] buffer = new char[16 * 1024];
        private int offset;
        private int length;

        ReaderCursor(Path path) throws IOException {
            reader = new BufferedReader(new InputStreamReader(
                    Files.newInputStream(path), StandardCharsets.UTF_8), 128 * 1024);
        }

        int next() throws IOException {
            if (offset >= length) {
                length = reader.read(buffer);
                offset = 0;
                if (length < 0) {
                    return -1;
                }
            }
            return buffer[offset++];
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }
}
