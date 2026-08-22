package com.jingdu.txt.core;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DiskDocumentIndex {
    private static final int CONTEXT_CHARACTERS = 24;

    private final Path revisionDirectory;
    private final String revision;
    private final String sourceSha256;
    private final long sourceBytes;
    private final long characterCount;
    private final int segmentCount;
    private final List<ChapterEntry> chapters;
    private final boolean chapterListTruncated;

    private DiskDocumentIndex(Path revisionDirectory, String revision, String sourceSha256,
            long sourceBytes, long characterCount, int segmentCount,
            List<ChapterEntry> chapters, boolean chapterListTruncated) {
        this.revisionDirectory = revisionDirectory;
        this.revision = revision;
        this.sourceSha256 = sourceSha256;
        this.sourceBytes = sourceBytes;
        this.characterCount = characterCount;
        this.segmentCount = segmentCount;
        this.chapters = Collections.unmodifiableList(chapters);
        this.chapterListTruncated = chapterListTruncated;
    }

    public static DiskDocumentIndex open(Path revisionDirectory, String requiredRevision)
            throws IOException {
        Path manifest = revisionDirectory.resolve("manifest.bin");
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(manifest)))) {
            if (input.readInt() != DiskDocumentIndexBuilder.MAGIC) {
                throw new IOException("invalid disk index magic");
            }
            int version = input.readInt();
            if (version != DiskDocumentIndexBuilder.FORMAT_VERSION) {
                throw new IOException("unsupported disk index format: " + version);
            }
            String revision = input.readUTF();
            if (!revision.equals(requiredRevision)) {
                throw new IOException("index revision is stale");
            }
            String sha256 = input.readUTF();
            long sourceBytes = input.readLong();
            long characterCount = input.readLong();
            int segmentCharacters = input.readInt();
            int segmentCount = input.readInt();
            int chapterCount = input.readInt();
            if (segmentCharacters != DiskDocumentIndexBuilder.SEGMENT_CHARACTERS
                    || segmentCount < 0 || chapterCount < 0 || characterCount < 0) {
                throw new IOException("invalid disk index manifest values");
            }
            List<ChapterEntry> chapters = readChapters(
                    revisionDirectory.resolve("chapters.bin"), chapterCount);
            int truncationMarker = input.read();
            boolean chapterListTruncated;
            if (truncationMarker < 0) {
                chapterListTruncated = false;
            } else if (truncationMarker == 0 || truncationMarker == 1) {
                chapterListTruncated = truncationMarker == 1;
                if (input.read() != -1) {
                    throw new IOException("disk index manifest has trailing bytes");
                }
            } else {
                throw new IOException("invalid chapter truncation marker");
            }
            validateFiles(revisionDirectory, segmentCount);
            return new DiskDocumentIndex(revisionDirectory, revision, sha256, sourceBytes,
                    characterCount, segmentCount, chapters, chapterListTruncated);
        } catch (EOFException truncated) {
            throw new IOException("truncated disk index manifest", truncated);
        }
    }

    public static DiskDocumentIndex openActive(Path indexRoot) throws IOException {
        String revision = new String(Files.readAllBytes(indexRoot.resolve("ACTIVE")),
                StandardCharsets.UTF_8).trim();
        if (revision.isEmpty()) {
            throw new IOException("ACTIVE revision is empty");
        }
        return open(indexRoot.resolve("revisions").resolve(revision), revision);
    }

    public List<SearchHit> search(String query, int limit, String requiredRevision)
            throws IOException {
        if (!revision.equals(requiredRevision)) {
            throw new IllegalStateException("index revision is stale");
        }
        if (query == null || query.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        if (query.length() > DiskDocumentIndexBuilder.MAX_QUERY_CHARACTERS) {
            throw new IllegalArgumentException("query exceeds 256 UTF-16 characters");
        }
        int first = Character.codePointAt(query, 0);
        int nextOffset = Character.charCount(first);
        if (nextOffset >= query.length()) {
            return scanAllSegments(query, limit);
        }
        int second = Character.codePointAt(query, nextOffset);
        int hash = DiskDocumentIndexBuilder.bigramHash(first, second);
        Path bucket = DiskDocumentIndexBuilder.bucketPath(
                revisionDirectory.resolve("buckets"), DiskDocumentIndexBuilder.bucketForHash(hash));
        Set<Integer> candidates = new HashSet<Integer>();
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(bucket)))) {
            while (true) {
                try {
                    int storedHash = input.readInt();
                    int segmentId = input.readInt();
                    if (storedHash == hash) {
                        candidates.add(segmentId);
                    }
                } catch (EOFException end) {
                    break;
                }
            }
        }
        List<SearchHit> result = new ArrayList<SearchHit>();
        for (int segmentId = 0; segmentId < segmentCount && result.size() < limit; segmentId++) {
            if (candidates.contains(segmentId)) {
                searchSegment(segmentId, query, limit, result);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public String getRevision() { return revision; }
    public String getSourceSha256() { return sourceSha256; }
    public long getSourceBytes() { return sourceBytes; }
    public long getCharacterCount() { return characterCount; }
    public int getSegmentCount() { return segmentCount; }
    public List<ChapterEntry> getChapters() { return chapters; }
    public boolean isChapterListTruncated() { return chapterListTruncated; }

    public IndexedTextWindow readWindowAround(int characterOffset, int maximumCharacters)
            throws IOException {
        if (maximumCharacters <= 0) {
            throw new IllegalArgumentException("maximumCharacters must be positive");
        }
        int clamped = (int) Math.max(0, Math.min((long) characterOffset, characterCount));
        int start = Math.max(0, clamped - maximumCharacters / 4);
        int end = (int) Math.min(characterCount, (long) start + maximumCharacters);
        if (end - start < maximumCharacters) {
            start = Math.max(0, end - maximumCharacters);
        }
        if (start > 0 && start < characterCount
                && Character.isLowSurrogate(readCharacterAt(start))
                && Character.isHighSurrogate(readCharacterAt(start - 1))) {
            start--;
        }
        if (end > start && end < characterCount
                && Character.isHighSurrogate(readCharacterAt(end - 1))
                && Character.isLowSurrogate(readCharacterAt(end))) {
            end++;
        }
        StringBuilder result = new StringBuilder(end - start);
        int firstSegment = start / DiskDocumentIndexBuilder.SEGMENT_CHARACTERS;
        int lastSegment = end == 0 ? 0
                : (end - 1) / DiskDocumentIndexBuilder.SEGMENT_CHARACTERS;
        for (int segmentId = firstSegment; segmentId <= lastSegment
                && segmentId < segmentCount; segmentId++) {
            char[] segment = DiskDocumentIndexBuilder.readSegment(
                    revisionDirectory.resolve("segments"), segmentId);
            int segmentStart = segmentId * DiskDocumentIndexBuilder.SEGMENT_CHARACTERS;
            int copyStart = Math.max(0, start - segmentStart);
            int copyEnd = Math.min(segment.length, end - segmentStart);
            if (copyEnd > copyStart) {
                result.append(segment, copyStart, copyEnd - copyStart);
            }
        }
        return new IndexedTextWindow(start, result.toString());
    }

    public String readTextRange(int startOffset, int endOffset, int maximumCharacters)
            throws IOException {
        if (startOffset < 0 || endOffset <= startOffset
                || (long) endOffset > characterCount || maximumCharacters <= 0
                || endOffset - startOffset > maximumCharacters) {
            throw new IllegalArgumentException("text range is invalid or exceeds its limit");
        }
        if (startOffset > 0
                && Character.isLowSurrogate(readCharacterAt(startOffset))
                && Character.isHighSurrogate(readCharacterAt(startOffset - 1))) {
            throw new IllegalArgumentException("text range starts inside a surrogate pair");
        }
        if ((long) endOffset < characterCount
                && Character.isHighSurrogate(readCharacterAt(endOffset - 1))
                && Character.isLowSurrogate(readCharacterAt(endOffset))) {
            throw new IllegalArgumentException("text range ends inside a surrogate pair");
        }
        StringBuilder result = new StringBuilder(endOffset - startOffset);
        int firstSegment = startOffset / DiskDocumentIndexBuilder.SEGMENT_CHARACTERS;
        int lastSegment = (endOffset - 1) / DiskDocumentIndexBuilder.SEGMENT_CHARACTERS;
        for (int segmentId = firstSegment; segmentId <= lastSegment; segmentId++) {
            char[] segment = DiskDocumentIndexBuilder.readSegment(
                    revisionDirectory.resolve("segments"), segmentId);
            int segmentStart = segmentId * DiskDocumentIndexBuilder.SEGMENT_CHARACTERS;
            int copyStart = Math.max(0, startOffset - segmentStart);
            int copyEnd = Math.min(segment.length, endOffset - segmentStart);
            result.append(segment, copyStart, copyEnd - copyStart);
        }
        return result.toString();
    }

    private char readCharacterAt(int characterOffset) throws IOException {
        int segmentId = characterOffset / DiskDocumentIndexBuilder.SEGMENT_CHARACTERS;
        int localOffset = characterOffset % DiskDocumentIndexBuilder.SEGMENT_CHARACTERS;
        Path path = DiskDocumentIndexBuilder.segmentPath(
                revisionDirectory.resolve("segments"), segmentId);
        try (RandomAccessFile input = new RandomAccessFile(path.toFile(), "r")) {
            int length = input.readInt();
            if (length < 0 || length > DiskDocumentIndexBuilder.SEGMENT_CHARACTERS
                    || input.length() != 4L + 2L * length
                    || localOffset < 0 || localOffset >= length) {
                throw new IOException("invalid indexed character offset");
            }
            input.seek(4L + 2L * localOffset);
            return input.readChar();
        }
    }

    private List<SearchHit> scanAllSegments(String query, int limit) throws IOException {
        List<SearchHit> result = new ArrayList<SearchHit>();
        for (int segmentId = 0; segmentId < segmentCount && result.size() < limit; segmentId++) {
            searchSegment(segmentId, query, limit, result);
        }
        return Collections.unmodifiableList(result);
    }

    private void searchSegment(int segmentId, String query, int limit, List<SearchHit> result)
            throws IOException {
        char[] current = DiskDocumentIndexBuilder.readSegment(
                revisionDirectory.resolve("segments"), segmentId);
        char[] next = segmentId + 1 < segmentCount
                ? DiskDocumentIndexBuilder.readSegment(revisionDirectory.resolve("segments"),
                        segmentId + 1)
                : new char[0];
        int extra = Math.min(next.length,
                DiskDocumentIndexBuilder.MAX_QUERY_CHARACTERS + CONTEXT_CHARACTERS);
        StringBuilder combined = new StringBuilder(current.length + extra);
        combined.append(current);
        combined.append(next, 0, extra);
        int from = 0;
        while (from < current.length && result.size() < limit) {
            int match = combined.indexOf(query, from);
            if (match < 0 || match >= current.length) {
                break;
            }
            int globalStart = segmentId * DiskDocumentIndexBuilder.SEGMENT_CHARACTERS + match;
            int contextStart = safeBoundary(combined, Math.max(0, match - CONTEXT_CHARACTERS), true);
            int contextEnd = safeBoundary(combined,
                    Math.min(combined.length(), match + query.length() + CONTEXT_CHARACTERS), false);
            result.add(new SearchHit(globalStart, globalStart + query.length(),
                    combined.substring(contextStart, contextEnd)));
            from = match + Math.max(1, query.length());
        }
    }

    private static int safeBoundary(CharSequence text, int offset, boolean backward) {
        if (offset > 0 && offset < text.length() && Character.isLowSurrogate(text.charAt(offset))
                && Character.isHighSurrogate(text.charAt(offset - 1))) {
            return backward ? offset - 1 : offset + 1;
        }
        return offset;
    }

    private static List<ChapterEntry> readChapters(Path path, int expectedCount)
            throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(path)))) {
            int count = input.readInt();
            if (count != expectedCount) {
                throw new IOException("chapter count differs from manifest");
            }
            List<ChapterEntry> result = new ArrayList<ChapterEntry>(count);
            for (int i = 0; i < count; i++) {
                result.add(new ChapterEntry(input.readUTF(), input.readInt(), input.readInt()));
            }
            if (input.read() != -1) {
                throw new IOException("chapters file has trailing bytes");
            }
            return result;
        } catch (EOFException truncated) {
            throw new IOException("truncated chapters file", truncated);
        }
    }

    private static void validateFiles(Path revisionDirectory, int segmentCount) throws IOException {
        Path segments = revisionDirectory.resolve("segments");
        for (int i = 0; i < segmentCount; i++) {
            if (!Files.isRegularFile(DiskDocumentIndexBuilder.segmentPath(segments, i))) {
                throw new IOException("missing segment: " + i);
            }
        }
        Path buckets = revisionDirectory.resolve("buckets");
        for (int bucket = 0; bucket < DiskDocumentIndexBuilder.BUCKET_COUNT; bucket++) {
            Path path = DiskDocumentIndexBuilder.bucketPath(buckets, bucket);
            if (!Files.isRegularFile(path) || Files.size(path) % 8 != 0) {
                throw new IOException("missing or truncated postings bucket: " + bucket);
            }
        }
    }
}
