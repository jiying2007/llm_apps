package com.jingdu.txt.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class DocumentIndex {
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "^(第[零〇一二三四五六七八九十百千万0-9０-９]+[章节回卷部篇集].*|序章.*|楔子.*|前言.*|后记.*|番外.*)$");
    private static final Pattern NUMBERED_CHAPTER_PATTERN = Pattern.compile(
            "^第[零〇一二三四五六七八九十百千万0-9０-９]+[章节回卷部篇集].*$");
    private static final int CONTEXT_CHARACTERS = 24;

    private final CharSequence text;
    private final String viewRevision;
    private final List<ChapterEntry> chapters;
    private final Map<String, List<Integer>> bigramPostings;

    private DocumentIndex(CharSequence text, String viewRevision, List<ChapterEntry> chapters,
            Map<String, List<Integer>> bigramPostings) {
        this.text = text;
        this.viewRevision = viewRevision;
        this.chapters = Collections.unmodifiableList(chapters);
        this.bigramPostings = bigramPostings;
    }

    public static DocumentIndex build(CharSequence text, String viewRevision) {
        if (text == null || viewRevision == null || viewRevision.isEmpty()) {
            throw new IllegalArgumentException("text and viewRevision are required");
        }
        List<ChapterEntry> chapters = detectChapters(text);
        Map<String, List<Integer>> postings = new HashMap<String, List<Integer>>();
        int offset = 0;
        while (offset < text.length()) {
            int first = Character.codePointAt(text, offset);
            int nextOffset = offset + Character.charCount(first);
            if (nextOffset >= text.length()) {
                break;
            }
            int second = Character.codePointAt(text, nextOffset);
            String key = codePointPair(first, second);
            List<Integer> positions = postings.get(key);
            if (positions == null) {
                positions = new ArrayList<Integer>();
                postings.put(key, positions);
            }
            positions.add(offset);
            offset = nextOffset;
        }
        Map<String, List<Integer>> immutable = new HashMap<String, List<Integer>>();
        for (Map.Entry<String, List<Integer>> entry : postings.entrySet()) {
            immutable.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        return new DocumentIndex(text, viewRevision, chapters, Collections.unmodifiableMap(immutable));
    }

    public String getViewRevision() {
        return viewRevision;
    }

    public List<ChapterEntry> getChapters() {
        return chapters;
    }

    public List<SearchHit> search(String query, int limit, String requiredViewRevision) {
        if (!viewRevision.equals(requiredViewRevision)) {
            throw new IllegalStateException("index revision is stale");
        }
        if (query == null || query.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        List<SearchHit> result = new ArrayList<SearchHit>();
        int first = Character.codePointAt(query, 0);
        int next = Character.charCount(first);
        if (next >= query.length()) {
            scanSingleCodePoint(first, query.length(), limit, result);
            return Collections.unmodifiableList(result);
        }
        int second = Character.codePointAt(query, next);
        List<Integer> candidates = bigramPostings.get(codePointPair(first, second));
        if (candidates == null) {
            return Collections.emptyList();
        }
        for (Integer candidate : candidates) {
            if (matchesAt(candidate, query)) {
                result.add(hit(candidate, candidate + query.length()));
                if (result.size() == limit) {
                    break;
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    private void scanSingleCodePoint(int expected, int queryLength, int limit, List<SearchHit> result) {
        int offset = 0;
        while (offset < text.length() && result.size() < limit) {
            int actual = Character.codePointAt(text, offset);
            if (actual == expected) {
                result.add(hit(offset, offset + queryLength));
            }
            offset += Character.charCount(actual);
        }
    }

    private boolean matchesAt(int offset, String query) {
        if (offset + query.length() > text.length()) {
            return false;
        }
        for (int i = 0; i < query.length(); i++) {
            if (text.charAt(offset + i) != query.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private SearchHit hit(int start, int end) {
        int contextStart = safeBoundary(Math.max(0, start - CONTEXT_CHARACTERS), true);
        int contextEnd = safeBoundary(Math.min(text.length(), end + CONTEXT_CHARACTERS), false);
        return new SearchHit(start, end, text.subSequence(contextStart, contextEnd).toString());
    }

    private int safeBoundary(int offset, boolean moveBackward) {
        if (offset > 0 && offset < text.length()
                && Character.isLowSurrogate(text.charAt(offset))
                && Character.isHighSurrogate(text.charAt(offset - 1))) {
            return moveBackward ? offset - 1 : offset + 1;
        }
        return offset;
    }

    private static List<ChapterEntry> detectChapters(CharSequence text) {
        List<ChapterEntry> result = new ArrayList<ChapterEntry>();
        int lineStart = 0;
        for (int cursor = 0; cursor <= text.length(); cursor++) {
            if (cursor == text.length() || text.charAt(cursor) == '\n') {
                String line = text.subSequence(lineStart, cursor).toString().trim();
                if (!line.isEmpty() && line.length() <= 80 && isChapterTitle(line)) {
                    int leading = 0;
                    while (lineStart + leading < cursor
                            && Character.isWhitespace(text.charAt(lineStart + leading))) {
                        leading++;
                    }
                    result.add(new ChapterEntry(line, lineStart + leading,
                            chapterConfidencePercent(line)));
                }
                lineStart = cursor + 1;
            }
        }
        return result;
    }

    static boolean isChapterTitle(String line) {
        return CHAPTER_PATTERN.matcher(line).matches();
    }

    static int chapterConfidencePercent(String line) {
        if (!isChapterTitle(line)) {
            throw new IllegalArgumentException("not a recognized chapter title");
        }
        if (NUMBERED_CHAPTER_PATTERN.matcher(line).matches()) {
            return line.length() <= 40 ? 96 : 88;
        }
        if (line.length() <= 12) {
            return 90;
        }
        return line.length() <= 40 ? 84 : 76;
    }

    private static String codePointPair(int first, int second) {
        return new String(Character.toChars(first)) + new String(Character.toChars(second));
    }
}
