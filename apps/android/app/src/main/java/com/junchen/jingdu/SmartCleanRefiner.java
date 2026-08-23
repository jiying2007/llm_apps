package com.junchen.jingdu;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Streaming second-stage Smart Clean detector for patterns that are deliberately outside the
 * native whole-line/repetition engine. It never loads the full document and never mutates text.
 */
final class SmartCleanRefiner {
    record Candidate(int score, int count, String reason, String text) {}

    private static final int MAX_LINE_CHARS = 2048;
    private static final int MAX_UNIQUE = 160;
    private static final String[] INLINE_MARKERS = {
            "https://", "http://", "www.",
            "最新网址", "备用网址", "请收藏", "请记住", "手机用户", "关注公众号", "牢记本站域名",
            "最新網址", "備用網址", "請收藏", "請記住", "手機用戶", "關注公眾號", "請牢記網域"
    };

    private SmartCleanRefiner() {}

    static List<Candidate> scan(File normalizedUtf8, int maxCandidates) throws IOException {
        LinkedHashMap<String, MutableCandidate> merged = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(normalizedUtf8), StandardCharsets.UTF_8), 64 * 1024)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() > MAX_LINE_CHARS) continue;
                String trimmed = line.trim();
                if (trimmed.length() < 4) continue;

                String fragment = inlineFragment(trimmed);
                if (fragment != null) add(merged, "inline_fragment", fragment, 72);

                double badRatio = malformedRatio(trimmed);
                if (badRatio >= 0.16 && trimmed.length() <= 512) {
                    int score = badRatio >= 0.35 ? 78 : 68;
                    add(merged, "garbled_line", trimmed, score);
                }
            }
        }

        ArrayList<Candidate> output = new ArrayList<>();
        for (MutableCandidate value : merged.values()) {
            output.add(new Candidate(value.score, value.count, value.reason, value.text));
        }
        output.sort(Comparator
                .comparingInt(Candidate::score).reversed()
                .thenComparing(Comparator.comparingInt(Candidate::count).reversed())
                .thenComparing(Candidate::text));
        int limit = Math.max(0, Math.min(maxCandidates, output.size()));
        return new ArrayList<>(output.subList(0, limit));
    }

    private static void add(Map<String, MutableCandidate> merged, String reason, String text, int score) {
        String key = reason + '\u001f' + text;
        MutableCandidate existing = merged.get(key);
        if (existing != null) {
            existing.count++;
            existing.score = Math.max(existing.score, score);
            return;
        }
        if (merged.size() >= MAX_UNIQUE) return;
        merged.put(key, new MutableCandidate(reason, text, score));
    }

    private static String inlineFragment(String line) {
        int first = Integer.MAX_VALUE;
        for (String marker : INLINE_MARKERS) {
            int found = line.indexOf(marker);
            if (found >= 6 && found < first) first = found;
        }
        if (first == Integer.MAX_VALUE) return null;

        int start = first;
        int backtrack = 0;
        while (start > 0 && backtrack < 4) {
            char ch = line.charAt(start - 1);
            if (Character.isWhitespace(ch) || "【】[]（）()<>《》｜|·-—:：，,。；;".indexOf(ch) >= 0) {
                start--;
                backtrack++;
            } else break;
        }
        String fragment = line.substring(start).trim();
        if (fragment.length() < 6 || fragment.length() > 512) return null;
        return fragment;
    }

    private static double malformedRatio(String text) {
        int total = 0;
        int suspicious = 0;
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset);
            offset += Character.charCount(cp);
            total++;
            int type = Character.getType(cp);
            if (cp == 0xFFFD || cp == 0 ||
                    (type == Character.CONTROL && cp != '\t') ||
                    type == Character.UNASSIGNED || type == Character.SURROGATE) {
                suspicious++;
            }
        }
        return total == 0 ? 0.0 : (double) suspicious / (double) total;
    }

    private static final class MutableCandidate {
        final String reason;
        final String text;
        int score;
        int count = 1;

        MutableCandidate(String reason, String text, int score) {
            this.reason = reason;
            this.text = text;
            this.score = score;
        }
    }
}
