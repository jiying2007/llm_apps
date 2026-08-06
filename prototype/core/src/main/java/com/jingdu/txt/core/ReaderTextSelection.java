package com.jingdu.txt.core;

import java.text.BreakIterator;
import java.util.Locale;

/** Immutable UTF-16 selection whose offsets are global document character offsets. */
public final class ReaderTextSelection {
    private final int startOffset;
    private final int endOffset;
    private final String selectedText;

    private ReaderTextSelection(int startOffset, int endOffset, String selectedText) {
        if (startOffset < 0 || endOffset <= startOffset || selectedText == null
                || selectedText.length() != endOffset - startOffset) {
            throw new IllegalArgumentException("selection range and text must agree");
        }
        rejectUnpairedSurrogates(selectedText);
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.selectedText = selectedText;
    }

    public static ReaderTextSelection selectRange(String windowText, int windowStartOffset,
            int localStartOffset, int localEndOffset) {
        requireWindow(windowText, windowStartOffset);
        if (localStartOffset < 0 || localStartOffset > windowText.length()
                || localEndOffset < 0 || localEndOffset > windowText.length()) {
            throw new IllegalArgumentException("selection offsets must stay inside the window");
        }
        int start = localStartOffset;
        int end = localEndOffset;
        if (start > end) {
            int swap = start;
            start = end;
            end = swap;
        }
        start = codePointBoundary(windowText, start, true);
        end = codePointBoundary(windowText, end, false);
        if (start == end) {
            if (end < windowText.length()) {
                end += Character.charCount(Character.codePointAt(windowText, end));
            } else if (start > 0) {
                start -= Character.charCount(Character.codePointBefore(windowText, start));
            } else {
                throw new IllegalArgumentException("cannot select from an empty window");
            }
        }
        return new ReaderTextSelection(windowStartOffset + start, windowStartOffset + end,
                windowText.substring(start, end));
    }

    public static ReaderTextSelection selectWord(String windowText, int windowStartOffset,
            int localOffset) {
        requireWindow(windowText, windowStartOffset);
        if (windowText.isEmpty()) {
            throw new IllegalArgumentException("cannot select from an empty window");
        }
        int cursor = Math.max(0, Math.min(localOffset, windowText.length() - 1));
        cursor = codePointBoundary(windowText, cursor, true);

        BreakIterator characters = BreakIterator.getCharacterInstance(Locale.ROOT);
        characters.setText(windowText);
        int start = characters.preceding(cursor + 1);
        if (start == BreakIterator.DONE) {
            start = 0;
        }
        int end = characters.following(cursor);
        if (end == BreakIterator.DONE) {
            end = windowText.length();
        }

        int codePoint = Character.codePointAt(windowText, start);
        if (isExpandableWordCodePoint(codePoint)) {
            while (start > 0) {
                int previous = characters.preceding(start);
                if (previous == BreakIterator.DONE
                        || !isExpandableWordCodePoint(
                                Character.codePointAt(windowText, previous))) {
                    break;
                }
                start = previous;
            }
            while (end < windowText.length()) {
                if (!isExpandableWordCodePoint(Character.codePointAt(windowText, end))) {
                    break;
                }
                int next = characters.following(end);
                if (next == BreakIterator.DONE) {
                    end = windowText.length();
                    break;
                }
                end = next;
            }
        }
        return selectRange(windowText, windowStartOffset, start, end);
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    public String getSelectedText() {
        return selectedText;
    }

    public int length() {
        return selectedText.length();
    }

    private static boolean isExpandableWordCodePoint(int codePoint) {
        if (!(Character.isLetterOrDigit(codePoint) || codePoint == '_')) {
            return false;
        }
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script != Character.UnicodeScript.HAN
                && script != Character.UnicodeScript.HIRAGANA
                && script != Character.UnicodeScript.KATAKANA
                && script != Character.UnicodeScript.HANGUL;
    }

    private static int codePointBoundary(String text, int offset, boolean moveBackward) {
        if (offset > 0 && offset < text.length()
                && Character.isLowSurrogate(text.charAt(offset))
                && Character.isHighSurrogate(text.charAt(offset - 1))) {
            return moveBackward ? offset - 1 : offset + 1;
        }
        return offset;
    }

    private static void requireWindow(String text, int windowStartOffset) {
        if (text == null || windowStartOffset < 0) {
            throw new IllegalArgumentException("window text and non-negative start are required");
        }
        long end = (long) windowStartOffset + text.length();
        if (end > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("window global range exceeds integer offsets");
        }
    }

    private static void rejectUnpairedSurrogates(String value) {
        for (int offset = 0; offset < value.length(); offset++) {
            char current = value.charAt(offset);
            if (Character.isHighSurrogate(current)) {
                if (offset + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(offset + 1))) {
                    throw new IllegalArgumentException("text contains an unpaired surrogate");
                }
                offset++;
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException("text contains an unpaired surrogate");
            }
        }
    }
}
