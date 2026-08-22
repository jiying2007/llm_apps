package com.jingdu.txt.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class AnchorResolver {
    private static final int CONTEXT_CHARACTERS = 32;

    public TextAnchor create(CharSequence original, int offset) {
        int clamped = Math.max(0, Math.min(offset, original.length()));
        int start = paragraphStart(original, clamped);
        int end = paragraphEnd(original, clamped);
        return new TextAnchor(clamped, start,
                hash(original.subSequence(start, end)),
                contextHash(original, start, end));
    }

    public int resolveOriginalOffset(CharSequence original, TextAnchor anchor) {
        int expected = Math.max(0, Math.min(anchor.getExpectedOffset(), original.length()));
        int expectedStart = paragraphStart(original, expected);
        int expectedEnd = paragraphEnd(original, expected);
        if (hash(original.subSequence(expectedStart, expectedEnd)).equals(anchor.getParagraphHash())) {
            return mapColumn(anchor, expectedStart, expectedEnd);
        }

        int bestStart = -1;
        int bestEnd = -1;
        int bestDistance = Integer.MAX_VALUE;
        int cursor = 0;
        while (cursor <= original.length()) {
            int end = paragraphEnd(original, cursor);
            if (hash(original.subSequence(cursor, end)).equals(anchor.getParagraphHash())) {
                if (contextHash(original, cursor, end).equals(anchor.getContextHash())) {
                    return mapColumn(anchor, cursor, end);
                }
                int distance = Math.abs(cursor - anchor.getParagraphStart());
                if (distance < bestDistance) {
                    bestStart = cursor;
                    bestEnd = end;
                    bestDistance = distance;
                }
            }
            if (end == original.length()) {
                break;
            }
            cursor = end + 1;
        }
        if (bestStart >= 0) {
            return mapColumn(anchor, bestStart, bestEnd);
        }
        return expected;
    }

    private static int mapColumn(TextAnchor anchor, int paragraphStart, int paragraphEnd) {
        int column = Math.max(0, anchor.getExpectedOffset() - anchor.getParagraphStart());
        return paragraphStart + Math.min(column, paragraphEnd - paragraphStart);
    }

    private static int paragraphStart(CharSequence text, int offset) {
        for (int cursor = Math.min(offset, text.length()) - 1; cursor >= 0; cursor--) {
            if (text.charAt(cursor) == '\n') {
                return cursor + 1;
            }
        }
        return 0;
    }

    private static int paragraphEnd(CharSequence text, int offset) {
        for (int cursor = Math.min(offset, text.length()); cursor < text.length(); cursor++) {
            if (text.charAt(cursor) == '\n') {
                return cursor;
            }
        }
        return text.length();
    }

    private static String contextHash(CharSequence text, int paragraphStart, int paragraphEnd) {
        int start = Math.max(0, paragraphStart - CONTEXT_CHARACTERS);
        int end = Math.min(text.length(), paragraphEnd + CONTEXT_CHARACTERS);
        return hash(text.subSequence(start, end));
    }

    private static String hash(CharSequence text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                result.append(String.format("%02x", value & 0xFF));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
