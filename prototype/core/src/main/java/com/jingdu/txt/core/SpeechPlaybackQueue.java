package com.jingdu.txt.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded-window TTS segmentation and stale-callback-safe playback state. */
public final class SpeechPlaybackQueue {
    public enum State {
        READY,
        PLAYING,
        PAUSED,
        COMPLETE,
        STOPPED
    }

    public static final class Item {
        private final String utteranceId;
        private final int startOffset;
        private final int endOffset;
        private final String text;

        private Item(String utteranceId, int startOffset, int endOffset, String text) {
            this.utteranceId = utteranceId;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.text = text;
        }

        public String getUtteranceId() { return utteranceId; }
        public int getStartOffset() { return startOffset; }
        public int getEndOffset() { return endOffset; }
        public String getText() { return text; }
    }

    public static final class Highlight {
        private final int startOffset;
        private final int endOffset;
        private final int followOffset;
        private final boolean paragraphFallback;

        private Highlight(int startOffset, int endOffset, int followOffset,
                boolean paragraphFallback) {
            if (startOffset < 0 || endOffset <= startOffset
                    || followOffset < startOffset || followOffset >= endOffset) {
                throw new IllegalArgumentException("speech highlight must be non-empty");
            }
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.followOffset = followOffset;
            this.paragraphFallback = paragraphFallback;
        }

        public int getStartOffset() { return startOffset; }
        public int getEndOffset() { return endOffset; }
        public int getFollowOffset() { return followOffset; }
        public boolean isParagraphFallback() { return paragraphFallback; }
    }

    private static final class Segment {
        final int startOffset;
        final int endOffset;

        Segment(int startOffset, int endOffset) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }
    }

    private final String revision;
    private final int windowStartOffset;
    private final String text;
    private final List<Segment> segments;
    private int segmentIndex;
    private int anchor;
    private long issuance;
    private Item issued;
    private State state;
    private int cachedSentenceStart = -1;
    private int cachedSentenceEnd = -1;
    private int cachedParagraphStart = -1;
    private int cachedParagraphEnd = -1;

    public SpeechPlaybackQueue(String revision, int windowStartOffset, String text,
            int requestedStartOffset, int maximumCharacters) {
        if (revision == null || revision.isEmpty()) {
            throw new IllegalArgumentException("speech revision is required");
        }
        if (windowStartOffset < 0 || text == null || maximumCharacters < 2) {
            throw new IllegalArgumentException("invalid speech window");
        }
        long windowEnd = (long) windowStartOffset + text.length();
        if (windowEnd > Integer.MAX_VALUE || requestedStartOffset < windowStartOffset
                || requestedStartOffset > windowEnd) {
            throw new IllegalArgumentException("speech start is outside the window");
        }
        this.revision = revision;
        this.windowStartOffset = windowStartOffset;
        this.text = text;
        this.segments = Collections.unmodifiableList(
                buildSegments(text, windowStartOffset, maximumCharacters));
        this.anchor = requestedStartOffset;
        this.segmentIndex = findSegmentIndex(requestedStartOffset);
        this.state = segmentIndex < segments.size() ? State.READY : State.COMPLETE;
    }

    public Item begin() {
        if (state != State.READY) {
            return null;
        }
        return issue(anchor, segmentIndex);
    }

    public boolean onRange(String utteranceId, int start, int end) {
        return onRangeHighlight(utteranceId, start, end) != null;
    }

    public Highlight onRangeHighlight(String utteranceId, int start, int end) {
        if (!accepts(utteranceId) || start < 0 || end <= start
                || end > issued.text.length()
                || splitsSurrogatePair(issued.text, start)
                || splitsSurrogatePair(issued.text, end)) {
            return null;
        }
        int reportedAnchor = issued.startOffset + start;
        if (reportedAnchor < anchor) {
            return null;
        }
        anchor = reportedAnchor;
        return sentenceHighlight(reportedAnchor);
    }

    public Highlight fallbackHighlight(String utteranceId) {
        if (!accepts(utteranceId)) {
            return null;
        }
        int localAnchor = issued.startOffset - windowStartOffset;
        if (localAnchor < cachedParagraphStart || localAnchor >= cachedParagraphEnd) {
            cachedParagraphStart = text.lastIndexOf(
                    '\n', Math.max(0, localAnchor - 1)) + 1;
            cachedParagraphEnd = text.indexOf('\n', localAnchor);
            if (cachedParagraphEnd < 0) {
                cachedParagraphEnd = text.length();
            }
        }
        if (cachedParagraphEnd <= cachedParagraphStart) {
            return new Highlight(issued.startOffset, issued.endOffset,
                    issued.startOffset, true);
        }
        return new Highlight(windowStartOffset + cachedParagraphStart,
                windowStartOffset + cachedParagraphEnd, issued.startOffset, true);
    }

    public Item onDone(String utteranceId) {
        if (!accepts(utteranceId)) {
            return null;
        }
        anchor = issued.endOffset;
        issued = null;
        segmentIndex++;
        if (segmentIndex >= segments.size()) {
            state = State.COMPLETE;
            return null;
        }
        return issue(segments.get(segmentIndex).startOffset, segmentIndex);
    }

    public boolean onError(String utteranceId) {
        if (!accepts(utteranceId)) {
            return false;
        }
        issued = null;
        state = State.STOPPED;
        return true;
    }

    public void pause() {
        if (state == State.PLAYING) {
            issued = null;
            state = State.PAUSED;
        }
    }

    public Item resume() {
        if (state != State.PAUSED) {
            return null;
        }
        segmentIndex = findSegmentIndex(anchor);
        if (segmentIndex >= segments.size()) {
            state = State.COMPLETE;
            return null;
        }
        return issue(anchor, segmentIndex);
    }

    public Item moveNext() {
        if (segments.isEmpty() || state == State.STOPPED || state == State.COMPLETE) {
            return null;
        }
        int next = segmentIndex + 1;
        if (next >= segments.size()) {
            return null;
        }
        return issue(segments.get(next).startOffset, next);
    }

    public Item movePrevious() {
        if (segments.isEmpty() || state == State.STOPPED || state == State.COMPLETE) {
            return null;
        }
        int previous = segmentIndex - 1;
        if (previous < 0) {
            return null;
        }
        return issue(segments.get(previous).startOffset, previous);
    }

    public void stop() {
        issued = null;
        state = State.STOPPED;
    }

    public boolean accepts(String utteranceId) {
        return state == State.PLAYING && issued != null
                && issued.utteranceId.equals(utteranceId);
    }

    public String getRevision() { return revision; }
    public State getState() { return state; }
    public int getAnchor() { return anchor; }
    public int getWindowEndOffset() { return windowStartOffset + text.length(); }
    public int getSegmentCount() { return segments.size(); }

    public static int paragraphStartAt(String text, int windowStartOffset,
            int requestedStartOffset) {
        if (text == null || windowStartOffset < 0
                || requestedStartOffset < windowStartOffset
                || (long) windowStartOffset + text.length() > Integer.MAX_VALUE
                || (long) requestedStartOffset > (long) windowStartOffset + text.length()) {
            throw new IllegalArgumentException("paragraph anchor is outside the speech window");
        }
        int localOffset = requestedStartOffset - windowStartOffset;
        if (localOffset >= text.length()) {
            return requestedStartOffset;
        }
        return windowStartOffset
                + text.lastIndexOf('\n', Math.max(0, localOffset - 1)) + 1;
    }

    private Item issue(int requestedAnchor, int requestedSegmentIndex) {
        Segment segment = segments.get(requestedSegmentIndex);
        int start = Math.max(segment.startOffset, Math.min(requestedAnchor, segment.endOffset));
        if (start >= segment.endOffset) {
            segmentIndex = requestedSegmentIndex + 1;
            if (segmentIndex >= segments.size()) {
                state = State.COMPLETE;
                issued = null;
                anchor = segment.endOffset;
                return null;
            }
            return issue(segments.get(segmentIndex).startOffset, segmentIndex);
        }
        segmentIndex = requestedSegmentIndex;
        anchor = start;
        int localStart = start - windowStartOffset;
        int localEnd = segment.endOffset - windowStartOffset;
        String id = "speech-" + Integer.toHexString(revision.hashCode()) + "-"
                + start + "-" + segment.endOffset + "-" + (++issuance);
        issued = new Item(id, start, segment.endOffset, text.substring(localStart, localEnd));
        state = State.PLAYING;
        return issued;
    }

    private int findSegmentIndex(int requestedAnchor) {
        for (int index = 0; index < segments.size(); index++) {
            if (requestedAnchor < segments.get(index).endOffset) {
                return index;
            }
        }
        return segments.size();
    }

    private static List<Segment> buildSegments(
            String text, int windowStartOffset, int maximumCharacters) {
        List<Segment> result = new ArrayList<Segment>();
        int cursor = 0;
        while (cursor < text.length()) {
            int hardEnd = (int) Math.min((long) text.length(),
                    (long) cursor + maximumCharacters);
            int end = hardEnd;
            if (hardEnd < text.length()) {
                int minimumPreferred = cursor + Math.max(1, (hardEnd - cursor) / 3);
                for (int index = hardEnd - 1; index >= minimumPreferred; index--) {
                    if (isBoundary(text.charAt(index))) {
                        end = index + 1;
                        break;
                    }
                }
            }
            if (end < text.length() && end > cursor
                    && Character.isHighSurrogate(text.charAt(end - 1))
                    && Character.isLowSurrogate(text.charAt(end))) {
                end--;
            }
            if (end <= cursor) {
                end = Math.min(text.length(), cursor + maximumCharacters);
                if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))
                        && Character.isLowSurrogate(text.charAt(end))) {
                    end++;
                }
            }
            result.add(new Segment(windowStartOffset + cursor, windowStartOffset + end));
            cursor = end;
        }
        return result;
    }

    private static boolean isBoundary(char value) {
        return value == '\n' || value == '\r' || value == '。' || value == '！'
                || value == '？' || value == '；' || value == '!' || value == '?'
                || value == ';' || value == '.';
    }

    private Highlight sentenceHighlight(int reportedStart) {
        int localStart = reportedStart - windowStartOffset;
        if (localStart < cachedSentenceStart || localStart >= cachedSentenceEnd) {
            cachedSentenceStart = localStart;
            while (cachedSentenceStart > 0
                    && !isBoundary(text.charAt(cachedSentenceStart - 1))) {
                cachedSentenceStart--;
            }
            cachedSentenceEnd = localStart + Character.charCount(
                    Character.codePointAt(text, localStart));
            while (cachedSentenceEnd < text.length()) {
                char value = text.charAt(cachedSentenceEnd - 1);
                if (isBoundary(value)) {
                    break;
                }
                cachedSentenceEnd++;
            }
        }
        return new Highlight(windowStartOffset + cachedSentenceStart,
                windowStartOffset + cachedSentenceEnd, reportedStart, false);
    }

    private static boolean splitsSurrogatePair(String value, int offset) {
        return offset > 0 && offset < value.length()
                && Character.isHighSurrogate(value.charAt(offset - 1))
                && Character.isLowSurrogate(value.charAt(offset));
    }
}
