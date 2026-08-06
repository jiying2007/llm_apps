package com.jingdu.txt.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

/** Atomic, bounded catalog for bookmarks anchored to normalized base text. */
public final class BookBookmarkStore {
    private static final int MAGIC = 0x4A44424D;
    private static final int VERSION = 1;
    private static final int MAXIMUM_BOOKMARKS = 5000;
    private static final int MAXIMUM_BOOKMARKS_PER_BOOK = 200;
    private static final int MAXIMUM_PAYLOAD_BYTES = 2 * 1024 * 1024;

    public List<BookBookmark> load(Path target) throws IOException {
        if (!Files.isRegularFile(target)) {
            return new ArrayList<BookBookmark>();
        }
        long size = Files.size(target);
        if (size <= 0 || size > MAXIMUM_PAYLOAD_BYTES + 32L) {
            throw new IOException("invalid bookmark catalog size");
        }
        try (DataInputStream input = new DataInputStream(Files.newInputStream(target))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("unsupported bookmark catalog");
            }
            int payloadLength = input.readInt();
            if (payloadLength <= 0 || payloadLength > MAXIMUM_PAYLOAD_BYTES) {
                throw new IOException("invalid bookmark payload size");
            }
            byte[] payload = new byte[payloadLength];
            input.readFully(payload);
            long expectedCrc = input.readLong();
            if (input.read() != -1) {
                throw new IOException("trailing bytes in bookmark catalog");
            }
            CRC32 crc = new CRC32();
            crc.update(payload);
            if (crc.getValue() != expectedCrc) {
                throw new IOException("bookmark catalog checksum mismatch");
            }
            return decode(payload);
        }
    }

    public void save(Path target, List<BookBookmark> bookmarks) throws IOException {
        List<BookBookmark> normalized = normalize(bookmarks);
        byte[] payload = encode(normalized);
        Path parent = requireParent(target);
        Files.createDirectories(parent);
        CRC32 crc = new CRC32();
        crc.update(payload);
        Path temporary = target.resolveSibling(target.getFileName().toString() + ".tmp");
        Files.deleteIfExists(temporary);
        try {
            try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(temporary))) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                output.writeInt(payload.length);
                output.write(payload);
                output.writeLong(crc.getValue());
            }
            atomicReplace(temporary, target);
        } catch (IOException error) {
            Files.deleteIfExists(temporary);
            throw error;
        }
    }

    public List<BookBookmark> upsert(List<BookBookmark> bookmarks, BookBookmark replacement) {
        if (replacement == null) {
            throw new IllegalArgumentException("bookmark is required");
        }
        List<BookBookmark> updated = new ArrayList<BookBookmark>();
        for (BookBookmark bookmark : normalize(bookmarks)) {
            if (!bookmark.getBookmarkId().equals(replacement.getBookmarkId())) {
                updated.add(bookmark);
            }
        }
        updated.add(replacement);
        return normalize(updated);
    }

    public List<BookBookmark> remove(List<BookBookmark> bookmarks, String bookmarkId) {
        if (bookmarkId == null) {
            throw new IllegalArgumentException("bookmark id is required");
        }
        List<BookBookmark> remaining = new ArrayList<BookBookmark>();
        for (BookBookmark bookmark : normalize(bookmarks)) {
            if (!bookmark.getBookmarkId().equals(bookmarkId)) {
                remaining.add(bookmark);
            }
        }
        return normalize(remaining);
    }

    public List<BookBookmark> removeBook(List<BookBookmark> bookmarks, String bookId) {
        if (bookId == null) {
            throw new IllegalArgumentException("book id is required");
        }
        List<BookBookmark> remaining = new ArrayList<BookBookmark>();
        for (BookBookmark bookmark : normalize(bookmarks)) {
            if (!bookmark.getBookId().equals(bookId)) {
                remaining.add(bookmark);
            }
        }
        return normalize(remaining);
    }

    public List<BookBookmark> forBook(List<BookBookmark> bookmarks, String bookId) {
        requireBookId(bookId);
        List<BookBookmark> selected = new ArrayList<BookBookmark>();
        for (BookBookmark bookmark : normalize(bookmarks)) {
            if (bookmark.getBookId().equals(bookId)) {
                selected.add(bookmark);
            }
        }
        Collections.sort(selected, Comparator
                .comparingInt(BookBookmark::getOriginalAnchorOffset)
                .thenComparingLong(BookBookmark::getCreatedAtEpochMillis)
                .thenComparing(BookBookmark::getBookmarkId));
        return selected;
    }

    public List<BookBookmark> requireBookProfile(List<BookBookmark> bookmarks,
            String bookId) {
        requireBookId(bookId);
        List<BookBookmark> normalized = normalize(bookmarks);
        for (BookBookmark bookmark : normalized) {
            if (!bookmark.getBookId().equals(bookId)) {
                throw new IllegalArgumentException("bookmark profile contains another book");
            }
        }
        return normalized;
    }

    private static List<BookBookmark> normalize(List<BookBookmark> bookmarks) {
        if (bookmarks == null || bookmarks.size() > MAXIMUM_BOOKMARKS) {
            throw new IllegalArgumentException("bookmark catalog exceeds " + MAXIMUM_BOOKMARKS);
        }
        List<BookBookmark> normalized = new ArrayList<BookBookmark>(bookmarks);
        Set<String> ids = new HashSet<String>();
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (BookBookmark bookmark : normalized) {
            if (bookmark == null || !ids.add(bookmark.getBookmarkId())) {
                throw new IllegalArgumentException("duplicate or missing bookmark");
            }
            int count = counts.containsKey(bookmark.getBookId())
                    ? counts.get(bookmark.getBookId()) + 1 : 1;
            if (count > MAXIMUM_BOOKMARKS_PER_BOOK) {
                throw new IllegalArgumentException("book has too many bookmarks");
            }
            counts.put(bookmark.getBookId(), count);
        }
        Collections.sort(normalized, Comparator
                .comparing(BookBookmark::getBookId)
                .thenComparingInt(BookBookmark::getOriginalAnchorOffset)
                .thenComparingLong(BookBookmark::getCreatedAtEpochMillis)
                .thenComparing(BookBookmark::getBookmarkId));
        return normalized;
    }

    private static byte[] encode(List<BookBookmark> bookmarks) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(bookmarks.size());
            for (BookBookmark bookmark : bookmarks) {
                output.writeUTF(bookmark.getBookmarkId());
                output.writeUTF(bookmark.getBookId());
                output.writeInt(bookmark.getOriginalAnchorOffset());
                output.writeUTF(bookmark.getLabel());
                output.writeLong(bookmark.getCreatedAtEpochMillis());
            }
        }
        byte[] payload = bytes.toByteArray();
        if (payload.length <= 0 || payload.length > MAXIMUM_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("bookmark payload is too large");
        }
        return payload;
    }

    private static List<BookBookmark> decode(byte[] payload) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            int count = input.readInt();
            if (count < 0 || count > MAXIMUM_BOOKMARKS) {
                throw new IOException("invalid bookmark count");
            }
            List<BookBookmark> bookmarks = new ArrayList<BookBookmark>(count);
            try {
                for (int index = 0; index < count; index++) {
                    bookmarks.add(new BookBookmark(input.readUTF(), input.readUTF(),
                            input.readInt(), input.readUTF(), input.readLong()));
                }
                if (input.read() != -1) {
                    throw new IOException("trailing bookmark payload bytes");
                }
                return normalize(bookmarks);
            } catch (IllegalArgumentException invalid) {
                throw new IOException("invalid bookmark content", invalid);
            }
        }
    }

    private static Path requireParent(Path target) {
        if (target == null || target.getFileName() == null) {
            throw new IllegalArgumentException("bookmark path is required");
        }
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IllegalArgumentException("bookmark catalog needs a parent");
        }
        return parent;
    }

    private static void requireBookId(String bookId) {
        if (bookId == null || !bookId.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid book id");
        }
    }

    private static void atomicReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
