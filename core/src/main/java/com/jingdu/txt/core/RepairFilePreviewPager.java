package com.jingdu.txt.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class RepairFilePreviewPager {
    private static final int CONTEXT_CHARACTERS = 16;
    private static final int MAXIMUM_PAGE_SIZE = 1000;

    public RepairPreviewPage readPage(Path originalUtf8, List<RepairRule> inputRules,
            RepairSelection selection, long matchOffset, int pageSize) throws IOException {
        if (selection == null) {
            throw new IllegalArgumentException("repair selection must not be null");
        }
        if (matchOffset < 0) {
            throw new IllegalArgumentException("matchOffset must not be negative");
        }
        if (pageSize < 1 || pageSize > MAXIMUM_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and 1000");
        }
        List<RepairRule> rules = RepairRules.enabled(inputRules);
        int maximumMatch = maximumMatchLength(rules);
        List<RepairMatch> matches = new ArrayList<RepairMatch>(pageSize);
        long candidateIndex = 0;
        long originalOffset = 0;
        boolean hasNext = false;
        try (ReaderCursor cursor = new ReaderCursor(originalUtf8)) {
            StringBuilder window = new StringBuilder(maximumMatch + CONTEXT_CHARACTERS);
            StringBuilder before = new StringBuilder(CONTEXT_CHARACTERS);
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
                    appendBefore(before, window, 1);
                    window.deleteCharAt(0);
                    originalOffset++;
                    continue;
                }
                int matchLength = selected.getMatchText().length();
                if (candidateIndex >= matchOffset) {
                    if (matches.size() >= pageSize) {
                        hasNext = true;
                        break;
                    }
                    if (originalOffset > Integer.MAX_VALUE) {
                        throw new IOException("repair preview offset exceeds Android range");
                    }
                    int afterStart = Math.min(matchLength, window.length());
                    int afterEnd = Math.min(window.length(), afterStart + CONTEXT_CHARACTERS);
                    matches.add(new RepairMatch(selected.getId(), (int) originalOffset,
                            (int) Math.min(Integer.MAX_VALUE, originalOffset + matchLength),
                            before.toString(), selected.getMatchText(), selected.getReplacement(),
                            window.substring(afterStart, afterEnd),
                            selection.isApplied(selected.getId(), originalOffset)));
                }
                candidateIndex++;
                appendBefore(before, window, matchLength);
                window.delete(0, matchLength);
                originalOffset += matchLength;
            }
        }
        return new RepairPreviewPage(matchOffset, matches, hasNext);
    }

    private static int maximumMatchLength(List<RepairRule> rules) {
        int maximum = 1;
        for (RepairRule rule : rules) {
            maximum = Math.max(maximum, rule.getMatchText().length());
        }
        return maximum;
    }

    private static void appendBefore(StringBuilder before, CharSequence consumed, int length) {
        for (int index = 0; index < length; index++) {
            before.append(consumed.charAt(index));
            if (before.length() > CONTEXT_CHARACTERS) {
                before.deleteCharAt(0);
            }
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
