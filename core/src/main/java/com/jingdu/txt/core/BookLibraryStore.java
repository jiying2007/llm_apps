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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;

/** Atomic, bounded metadata store for the private TXT library. */
public final class BookLibraryStore {
    private static final int MAGIC = 0x4A44424C;
    private static final int VERSION = 1;
    private static final int MAXIMUM_ENTRIES = 1000;
    private static final int MAXIMUM_PAYLOAD_BYTES = 8 * 1024 * 1024;
    private static final int MAXIMUM_FILE_BYTES = MAXIMUM_PAYLOAD_BYTES + 32;

    public List<BookLibraryEntry> load(Path target) throws IOException {
        if (!Files.isRegularFile(target)) {
            return new ArrayList<BookLibraryEntry>();
        }
        long size = Files.size(target);
        if (size <= 0 || size > MAXIMUM_FILE_BYTES) {
            throw new IOException("invalid book library size");
        }
        try (DataInputStream input = new DataInputStream(Files.newInputStream(target))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("unsupported book library");
            }
            int payloadLength = input.readInt();
            if (payloadLength <= 0 || payloadLength > MAXIMUM_PAYLOAD_BYTES) {
                throw new IOException("invalid book library payload size");
            }
            byte[] payload = new byte[payloadLength];
            input.readFully(payload);
            long expectedCrc = input.readLong();
            if (input.read() != -1) {
                throw new IOException("trailing bytes in book library");
            }
            CRC32 crc = new CRC32();
            crc.update(payload);
            if (crc.getValue() != expectedCrc) {
                throw new IOException("book library checksum mismatch");
            }
            return decode(payload);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("invalid book library content", invalid);
        }
    }

    public void save(Path target, List<BookLibraryEntry> entries) throws IOException {
        List<BookLibraryEntry> normalized = normalize(entries);
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

    public List<BookLibraryEntry> upsert(List<BookLibraryEntry> entries,
            BookLibraryEntry replacement) {
        if (replacement == null) {
            throw new IllegalArgumentException("book library entry is required");
        }
        List<BookLibraryEntry> existing = normalize(entries);
        List<BookLibraryEntry> merged = new ArrayList<BookLibraryEntry>();
        for (BookLibraryEntry entry : existing) {
            if (!entry.getBookId().equals(replacement.getBookId())) {
                merged.add(entry);
            }
        }
        merged.add(replacement);
        return normalize(merged);
    }

    public BookLibraryEntry find(List<BookLibraryEntry> entries, String bookId) {
        if (entries == null || bookId == null) {
            throw new IllegalArgumentException("book library lookup is required");
        }
        for (BookLibraryEntry entry : entries) {
            if (entry == null) {
                throw new IllegalArgumentException("missing book library entry");
            }
            if (entry.getBookId().equals(bookId)) {
                return entry;
            }
        }
        return null;
    }

    public List<BookLibraryEntry> remove(List<BookLibraryEntry> entries, String bookId) {
        if (bookId == null) {
            throw new IllegalArgumentException("book id is required");
        }
        List<BookLibraryEntry> existing = normalize(entries);
        List<BookLibraryEntry> remaining = new ArrayList<BookLibraryEntry>();
        for (BookLibraryEntry entry : existing) {
            if (!entry.getBookId().equals(bookId)) {
                remaining.add(entry);
            }
        }
        return normalize(remaining);
    }

    public List<BookLibraryEntry> shelved(List<BookLibraryEntry> entries) {
        List<BookLibraryEntry> visible = new ArrayList<BookLibraryEntry>();
        for (BookLibraryEntry entry : normalize(entries)) {
            if (entry.isShelved()) {
                visible.add(entry);
            }
        }
        return visible;
    }

    private static List<BookLibraryEntry> normalize(List<BookLibraryEntry> entries) {
        if (entries == null || entries.size() > MAXIMUM_ENTRIES) {
            throw new IllegalArgumentException("book library exceeds " + MAXIMUM_ENTRIES);
        }
        List<BookLibraryEntry> normalized = new ArrayList<BookLibraryEntry>(entries);
        Set<String> ids = new HashSet<String>();
        for (BookLibraryEntry entry : normalized) {
            if (entry == null || !ids.add(entry.getBookId())) {
                throw new IllegalArgumentException("duplicate or missing book library entry");
            }
        }
        Collections.sort(normalized, Comparator
                .comparingLong(BookLibraryEntry::getLastOpenedAtEpochMillis).reversed()
                .thenComparing(BookLibraryEntry::getBookId));
        return normalized;
    }

    private static byte[] encode(List<BookLibraryEntry> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(entries.size());
            for (BookLibraryEntry entry : entries) {
                output.writeUTF(entry.getBookId());
                output.writeUTF(entry.getDisplayName());
                output.writeUTF(entry.getBaseFileName());
                output.writeUTF(entry.getBaseRevision());
                output.writeUTF(entry.getActiveFileName());
                output.writeUTF(entry.getActiveRevision());
                output.writeUTF(entry.getProjectionFileName());
                output.writeUTF(entry.getEncodingName());
                output.writeLong(entry.getSourceBytes());
                output.writeLong(entry.getImportedAtEpochMillis());
                output.writeLong(entry.getLastOpenedAtEpochMillis());
                output.writeInt(entry.getAnchorOffset());
            }
        }
        byte[] payload = bytes.toByteArray();
        if (payload.length <= 0 || payload.length > MAXIMUM_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("book library payload is too large");
        }
        return payload;
    }

    private static List<BookLibraryEntry> decode(byte[] payload) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            int count = input.readInt();
            if (count < 0 || count > MAXIMUM_ENTRIES) {
                throw new IOException("invalid book library entry count");
            }
            List<BookLibraryEntry> entries = new ArrayList<BookLibraryEntry>(count);
            for (int index = 0; index < count; index++) {
                String bookId = input.readUTF();
                String displayName = input.readUTF();
                String baseFileName = input.readUTF();
                String baseRevision = input.readUTF();
                String activeFileName = input.readUTF();
                String activeRevision = input.readUTF();
                String projectionFileName = input.readUTF();
                String encodingName = input.readUTF();
                long sourceBytes = input.readLong();
                long importedAt = input.readLong();
                long lastOpenedAt = input.readLong();
                int anchor = input.readInt();
                entries.add(new BookLibraryEntry(bookId, displayName,
                        baseFileName, baseRevision, activeFileName, activeRevision,
                        projectionFileName, encodingName, sourceBytes, importedAt,
                        lastOpenedAt, anchor));
            }
            if (input.read() != -1) {
                throw new IOException("trailing book library payload bytes");
            }
            return normalize(entries);
        }
    }

    private static Path requireParent(Path target) {
        if (target == null || target.getFileName() == null) {
            throw new IllegalArgumentException("book library path is required");
        }
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IllegalArgumentException("book library needs a parent directory");
        }
        return parent;
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
