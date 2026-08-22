package com.jingdu.txt.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DiskDocumentIndexBuilder {
    static final int MAGIC = 0x4A445831;
    static final int FORMAT_VERSION = 1;
    static final int SEGMENT_CHARACTERS = 64 * 1024;
    static final int BUCKET_COUNT = 16;
    public static final int MAX_QUERY_CHARACTERS = 256;
    public static final int MAXIMUM_CHAPTERS = 20000;
    private static final int CHECKPOINT_MAGIC = 0x4A444350;

    public DiskIndexBuildResult build(Path normalizedUtf8, Path indexRoot, String revision,
            String expectedSourceSha256, DiskIndexBuildListener listener) throws IOException {
        long started = System.nanoTime();
        validateRevision(revision);
        validateSha256(expectedSourceSha256);
        long sourceBytes = Files.size(normalizedUtf8);
        String actualSha256 = computeSha256(normalizedUtf8);
        if (!actualSha256.equals(expectedSourceSha256)) {
            throw new IOException("source SHA-256 does not match expected revision input");
        }

        Path revisions = indexRoot.resolve("revisions");
        Path workRoot = indexRoot.resolve("work");
        Path finalDirectory = revisions.resolve(revision);
        Files.createDirectories(revisions);
        Files.createDirectories(workRoot);
        if (Files.isDirectory(finalDirectory)) {
            DiskDocumentIndex existing = DiskDocumentIndex.open(finalDirectory, revision);
            if (!existing.getSourceSha256().equals(expectedSourceSha256)
                    || existing.getSourceBytes() != sourceBytes) {
                throw new IOException("published revision belongs to different source content");
            }
            return new DiskIndexBuildResult(finalDirectory, existing.getSegmentCount(),
                    existing.getChapters().size(), sourceBytes, System.nanoTime() - started,
                    false, existing.isChapterListTruncated());
        }

        Path workDirectory = workRoot.resolve(revision);
        Path segmentsDirectory = workDirectory.resolve("segments");
        Files.createDirectories(segmentsDirectory);
        Checkpoint checkpoint = readOrCreateCheckpoint(workDirectory, revision,
                expectedSourceSha256, sourceBytes);
        boolean resumed = checkpoint.completedSegments > 0;
        deleteIncompleteSegment(segmentsDirectory, checkpoint.completedSegments);
        writeSegments(normalizedUtf8, workDirectory, segmentsDirectory, checkpoint, listener);

        Checkpoint completed = readCheckpoint(workDirectory.resolve("checkpoint.bin"));
        ChapterScanResult chapterScan = writeChapters(workDirectory, segmentsDirectory,
                completed.completedSegments);
        writePostings(workDirectory, segmentsDirectory, completed.completedSegments);
        writeManifest(workDirectory, revision, expectedSourceSha256, sourceBytes,
                completed.completedSegments, chapterScan.chapters.size(),
                completed.processedCharacters, chapterScan.truncated);

        atomicDirectoryMove(workDirectory, finalDirectory);
        publishActive(indexRoot, revision);
        return new DiskIndexBuildResult(finalDirectory, completed.completedSegments,
                chapterScan.chapters.size(), sourceBytes, System.nanoTime() - started, resumed,
                chapterScan.truncated);
    }

    private static void writeSegments(Path source, Path workDirectory, Path segmentsDirectory,
            Checkpoint checkpoint, DiskIndexBuildListener listener) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Files.newInputStream(source), StandardCharsets.UTF_8), 128 * 1024)) {
            skipFully(reader, checkpoint.processedCharacters);
            char[] buffer = new char[SEGMENT_CHARACTERS];
            int segmentId = checkpoint.completedSegments;
            long processed = checkpoint.processedCharacters;
            while (true) {
                int length = readFullyOrEof(reader, buffer);
                if (length == 0) {
                    break;
                }
                Path target = segmentPath(segmentsDirectory, segmentId);
                Path temporary = segmentsDirectory.resolve(segmentName(segmentId) + ".tmp");
                Files.deleteIfExists(temporary);
                try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                        Files.newOutputStream(temporary)))) {
                    output.writeInt(length);
                    for (int i = 0; i < length; i++) {
                        output.writeChar(buffer[i]);
                    }
                }
                atomicFileMove(temporary, target);
                segmentId++;
                processed += length;
                writeCheckpoint(workDirectory, new Checkpoint(checkpoint.revision,
                        checkpoint.sourceSha256, checkpoint.sourceBytes, segmentId, processed));
                if (listener != null) {
                    listener.onSegmentCommitted(segmentId, processed);
                }
            }
        }
    }

    private static ChapterScanResult writeChapters(Path workDirectory, Path segmentsDirectory,
            int segmentCount) throws IOException {
        List<ChapterEntry> chapters = new ArrayList<ChapterEntry>();
        boolean truncated = false;
        StringBuilder line = new StringBuilder();
        int lineStart = 0;
        int globalOffset = 0;
        for (int segmentId = 0; segmentId < segmentCount; segmentId++) {
            char[] segment = readSegment(segmentsDirectory, segmentId);
            for (char value : segment) {
                if (value == '\n') {
                    truncated |= addChapterIfMatched(chapters, line, lineStart);
                    line.setLength(0);
                    lineStart = globalOffset + 1;
                } else if (line.length() <= 80) {
                    line.append(value);
                }
                globalOffset++;
            }
        }
        truncated |= addChapterIfMatched(chapters, line, lineStart);
        Path temporary = workDirectory.resolve("chapters.bin.tmp");
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                Files.newOutputStream(temporary)))) {
            output.writeInt(chapters.size());
            for (ChapterEntry chapter : chapters) {
                output.writeUTF(chapter.getTitle());
                output.writeInt(chapter.getCharacterOffset());
                output.writeInt(chapter.getConfidencePercent());
            }
        }
        atomicFileMove(temporary, workDirectory.resolve("chapters.bin"));
        return new ChapterScanResult(chapters, truncated);
    }

    private static boolean addChapterIfMatched(List<ChapterEntry> chapters, StringBuilder line,
            int lineStart) {
        String title = line.toString().trim();
        if (!title.isEmpty() && title.length() <= 80 && DocumentIndex.isChapterTitle(title)) {
            if (chapters.size() >= MAXIMUM_CHAPTERS) {
                return true;
            }
            int leading = 0;
            while (leading < line.length() && Character.isWhitespace(line.charAt(leading))) {
                leading++;
            }
            chapters.add(new ChapterEntry(title, lineStart + leading,
                    DocumentIndex.chapterConfidencePercent(title)));
        }
        return false;
    }

    private static void writePostings(Path workDirectory, Path segmentsDirectory,
            int segmentCount) throws IOException {
        Path bucketsDirectory = workDirectory.resolve("buckets");
        Files.createDirectories(bucketsDirectory);
        DataOutputStream[] outputs = new DataOutputStream[BUCKET_COUNT];
        try {
            for (int bucket = 0; bucket < BUCKET_COUNT; bucket++) {
                outputs[bucket] = new DataOutputStream(new BufferedOutputStream(
                        Files.newOutputStream(bucketPath(bucketsDirectory, bucket))));
            }
            for (int segmentId = 0; segmentId < segmentCount; segmentId++) {
                char[] current = readSegment(segmentsDirectory, segmentId);
                char[] next = segmentId + 1 < segmentCount
                        ? readSegment(segmentsDirectory, segmentId + 1) : new char[0];
                Set<Integer> hashes = collectBigramHashes(current, next);
                for (Integer hash : hashes) {
                    DataOutputStream output = outputs[bucketForHash(hash)];
                    output.writeInt(hash);
                    output.writeInt(segmentId);
                }
            }
        } finally {
            IOException failure = null;
            for (DataOutputStream output : outputs) {
                if (output != null) {
                    try {
                        output.close();
                    } catch (IOException error) {
                        failure = error;
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static Set<Integer> collectBigramHashes(char[] current, char[] next) {
        int nextLength = Math.min(next.length, MAX_QUERY_CHARACTERS);
        StringBuilder combined = new StringBuilder(current.length + nextLength);
        combined.append(current);
        combined.append(next, 0, nextLength);
        Set<Integer> hashes = new HashSet<Integer>();
        int offset = 0;
        while (offset < combined.length()) {
            int first = Character.codePointAt(combined, offset);
            int secondOffset = offset + Character.charCount(first);
            if (secondOffset >= combined.length()) {
                break;
            }
            int second = Character.codePointAt(combined, secondOffset);
            hashes.add(bigramHash(first, second));
            offset = secondOffset;
        }
        return hashes;
    }

    static char[] readSegment(Path segmentsDirectory, int segmentId) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(segmentPath(segmentsDirectory, segmentId))))) {
            int length = input.readInt();
            if (length < 0 || length > SEGMENT_CHARACTERS) {
                throw new IOException("invalid segment length: " + length);
            }
            char[] result = new char[length];
            for (int i = 0; i < length; i++) {
                result[i] = input.readChar();
            }
            if (input.read() != -1) {
                throw new IOException("segment has trailing bytes: " + segmentId);
            }
            return result;
        } catch (EOFException truncated) {
            throw new IOException("truncated segment: " + segmentId, truncated);
        }
    }

    private static void writeManifest(Path workDirectory, String revision, String sha256,
            long sourceBytes, int segmentCount, int chapterCount, long characterCount,
            boolean chapterListTruncated)
            throws IOException {
        Path temporary = workDirectory.resolve("manifest.bin.tmp");
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                Files.newOutputStream(temporary)))) {
            output.writeInt(MAGIC);
            output.writeInt(FORMAT_VERSION);
            output.writeUTF(revision);
            output.writeUTF(sha256);
            output.writeLong(sourceBytes);
            output.writeLong(characterCount);
            output.writeInt(SEGMENT_CHARACTERS);
            output.writeInt(segmentCount);
            output.writeInt(chapterCount);
            output.writeBoolean(chapterListTruncated);
        }
        atomicFileMove(temporary, workDirectory.resolve("manifest.bin"));
    }

    private static Checkpoint readOrCreateCheckpoint(Path workDirectory, String revision,
            String sha256, long sourceBytes) throws IOException {
        Path path = workDirectory.resolve("checkpoint.bin");
        if (!Files.exists(path)) {
            Checkpoint created = new Checkpoint(revision, sha256, sourceBytes, 0, 0);
            writeCheckpoint(workDirectory, created);
            return created;
        }
        Checkpoint existing = readCheckpoint(path);
        if (!existing.revision.equals(revision) || !existing.sourceSha256.equals(sha256)
                || existing.sourceBytes != sourceBytes) {
            throw new IOException("incomplete index belongs to different source or revision");
        }
        return existing;
    }

    private static Checkpoint readCheckpoint(Path path) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(path)))) {
            if (input.readInt() != CHECKPOINT_MAGIC || input.readInt() != FORMAT_VERSION) {
                throw new IOException("unsupported checkpoint format");
            }
            return new Checkpoint(input.readUTF(), input.readUTF(), input.readLong(),
                    input.readInt(), input.readLong());
        }
    }

    private static void writeCheckpoint(Path workDirectory, Checkpoint checkpoint)
            throws IOException {
        Path temporary = workDirectory.resolve("checkpoint.bin.tmp");
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                Files.newOutputStream(temporary)))) {
            output.writeInt(CHECKPOINT_MAGIC);
            output.writeInt(FORMAT_VERSION);
            output.writeUTF(checkpoint.revision);
            output.writeUTF(checkpoint.sourceSha256);
            output.writeLong(checkpoint.sourceBytes);
            output.writeInt(checkpoint.completedSegments);
            output.writeLong(checkpoint.processedCharacters);
        }
        atomicFileMove(temporary, workDirectory.resolve("checkpoint.bin"));
    }

    private static void publishActive(Path indexRoot, String revision) throws IOException {
        Path temporary = indexRoot.resolve("ACTIVE.tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            output.write((revision + "\n").getBytes(StandardCharsets.UTF_8));
        }
        atomicFileMoveStrict(temporary, indexRoot.resolve("ACTIVE"));
    }

    private static void deleteIncompleteSegment(Path segmentsDirectory, int segmentId)
            throws IOException {
        Files.deleteIfExists(segmentPath(segmentsDirectory, segmentId));
        Files.deleteIfExists(segmentsDirectory.resolve(segmentName(segmentId) + ".tmp"));
    }

    private static void skipFully(BufferedReader reader, long characters) throws IOException {
        long remaining = characters;
        while (remaining > 0) {
            long skipped = reader.skip(remaining);
            if (skipped <= 0) {
                if (reader.read() < 0) {
                    throw new IOException("source ended before checkpoint offset");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private static int readFullyOrEof(BufferedReader reader, char[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int read = reader.read(buffer, total, buffer.length - total);
            if (read < 0) {
                break;
            }
            total += read;
        }
        return total;
    }

    static int bigramHash(int first, int second) {
        int result = 0x811C9DC5;
        result = (result ^ first) * 0x01000193;
        return (result ^ second) * 0x01000193;
    }

    static int bucketForHash(int hash) {
        return (hash >>> 28) & (BUCKET_COUNT - 1);
    }

    static Path bucketPath(Path bucketsDirectory, int bucket) {
        return bucketsDirectory.resolve(String.format("%02x.bin", bucket));
    }

    static Path segmentPath(Path segmentsDirectory, int segmentId) {
        return segmentsDirectory.resolve(segmentName(segmentId));
    }

    private static String segmentName(int segmentId) {
        return String.format("%06d.utf16", segmentId);
    }

    private static void validateRevision(String revision) {
        if (revision == null || !revision.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException("revision contains unsupported characters");
        }
    }

    private static void validateSha256(String sha256) {
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("expectedSourceSha256 must be lowercase hex");
        }
    }

    public static String computeSha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[128 * 1024];
            try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(path))) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) {
                result.append(String.format("%02x", value & 0xFF));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void atomicFileMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void atomicDirectoryMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            throw new IOException("filesystem does not support atomic revision publication", unsupported);
        }
    }

    private static void atomicFileMoveStrict(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            throw new IOException("filesystem does not support atomic ACTIVE publication", unsupported);
        }
    }

    private static final class ChapterScanResult {
        final List<ChapterEntry> chapters;
        final boolean truncated;

        ChapterScanResult(List<ChapterEntry> chapters, boolean truncated) {
            this.chapters = chapters;
            this.truncated = truncated;
        }
    }

    private static final class Checkpoint {
        final String revision;
        final String sourceSha256;
        final long sourceBytes;
        final int completedSegments;
        final long processedCharacters;

        Checkpoint(String revision, String sourceSha256, long sourceBytes, int completedSegments,
                long processedCharacters) {
            this.revision = revision;
            this.sourceSha256 = sourceSha256;
            this.sourceBytes = sourceBytes;
            this.completedSegments = completedSegments;
            this.processedCharacters = processedCharacters;
        }
    }
}
