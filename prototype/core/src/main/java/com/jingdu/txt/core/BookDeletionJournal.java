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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/** App-private intent record for idempotently finishing deletion of one private book copy. */
public final class BookDeletionJournal {
    private static final int MAGIC = 0x4A444244;
    private static final int VERSION = 1;
    private static final int MAXIMUM_FILE_BYTES = 16 * 1024;
    private static final int MAXIMUM_PAYLOAD_BYTES = 8 * 1024;
    private static final int MAXIMUM_ITEMS = 8;
    private static final Pattern DELETABLE_FILE = Pattern.compile(
            "^(?:book-[0-9]+\\.utf8\\.txt|repair-[0-9a-f]+\\.(?:utf8\\.txt|projection\\.bin))$");

    public static final class Entry {
        private final String bookId;
        private final List<String> fileNames;
        private final List<String> indexRevisions;
        private final long startedAtEpochMillis;

        public Entry(String bookId, List<String> fileNames,
                List<String> indexRevisions, long startedAtEpochMillis) {
            requireRevision(bookId, "book id");
            this.fileNames = validateFileNames(fileNames);
            this.indexRevisions = validateRevisions(indexRevisions);
            if (startedAtEpochMillis <= 0) {
                throw new IllegalArgumentException("deletion start time must be positive");
            }
            this.bookId = bookId;
            this.startedAtEpochMillis = startedAtEpochMillis;
        }

        public String getBookId() {
            return bookId;
        }

        public List<String> getFileNames() {
            return fileNames;
        }

        public List<String> getIndexRevisions() {
            return indexRevisions;
        }

        public long getStartedAtEpochMillis() {
            return startedAtEpochMillis;
        }
    }

    public void writePending(Path target, Entry entry) throws IOException {
        if (entry == null) {
            throw new IllegalArgumentException("deletion entry is required");
        }
        byte[] payload = encode(entry);
        CRC32 crc = new CRC32();
        crc.update(payload);
        Path parent = requireParent(target);
        Files.createDirectories(parent);
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

    public Entry readPending(Path target) throws IOException {
        if (!Files.isRegularFile(target)) {
            return null;
        }
        long size = Files.size(target);
        if (size <= 0 || size > MAXIMUM_FILE_BYTES) {
            throw new IOException("invalid book deletion journal size");
        }
        try (DataInputStream input = new DataInputStream(Files.newInputStream(target))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("unsupported book deletion journal");
            }
            int payloadLength = input.readInt();
            if (payloadLength <= 0 || payloadLength > MAXIMUM_PAYLOAD_BYTES) {
                throw new IOException("invalid book deletion payload size");
            }
            byte[] payload = new byte[payloadLength];
            input.readFully(payload);
            long expectedCrc = input.readLong();
            if (input.read() != -1) {
                throw new IOException("trailing bytes in book deletion journal");
            }
            CRC32 crc = new CRC32();
            crc.update(payload);
            if (crc.getValue() != expectedCrc) {
                throw new IOException("book deletion journal checksum mismatch");
            }
            return decode(payload);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("invalid book deletion journal content", invalid);
        }
    }

    public void clear(Path target) throws IOException {
        requireParent(target);
        Files.deleteIfExists(target);
    }

    private static byte[] encode(Entry entry) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(entry.bookId);
            output.writeInt(entry.fileNames.size());
            for (String fileName : entry.fileNames) {
                output.writeUTF(fileName);
            }
            output.writeInt(entry.indexRevisions.size());
            for (String revision : entry.indexRevisions) {
                output.writeUTF(revision);
            }
            output.writeLong(entry.startedAtEpochMillis);
        }
        byte[] payload = bytes.toByteArray();
        if (payload.length <= 0 || payload.length > MAXIMUM_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("book deletion payload is too large");
        }
        return payload;
    }

    private static Entry decode(byte[] payload) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            String bookId = input.readUTF();
            int fileCount = readCount(input, "file");
            List<String> fileNames = new ArrayList<String>(fileCount);
            for (int index = 0; index < fileCount; index++) {
                fileNames.add(input.readUTF());
            }
            int revisionCount = readCount(input, "revision");
            List<String> revisions = new ArrayList<String>(revisionCount);
            for (int index = 0; index < revisionCount; index++) {
                revisions.add(input.readUTF());
            }
            Entry entry = new Entry(bookId, fileNames, revisions, input.readLong());
            if (input.read() != -1) {
                throw new IOException("trailing book deletion payload bytes");
            }
            return entry;
        }
    }

    private static int readCount(DataInputStream input, String label) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > MAXIMUM_ITEMS) {
            throw new IOException("invalid deletion " + label + " count");
        }
        return count;
    }

    private static List<String> validateFileNames(List<String> values) {
        if (values == null || values.isEmpty() || values.size() > MAXIMUM_ITEMS) {
            throw new IllegalArgumentException("invalid deletion file names");
        }
        List<String> result = new ArrayList<String>(values.size());
        Set<String> unique = new HashSet<String>();
        for (String value : values) {
            if (value == null || value.isEmpty() || value.length() > 255
                    || ".".equals(value) || "..".equals(value)
                    || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0
                    || containsControl(value) || !DELETABLE_FILE.matcher(value).matches()
                    || !unique.add(value)) {
                throw new IllegalArgumentException("invalid deletion file name");
            }
            result.add(value);
        }
        return Collections.unmodifiableList(result);
    }

    private static List<String> validateRevisions(List<String> values) {
        if (values == null || values.size() > MAXIMUM_ITEMS) {
            throw new IllegalArgumentException("invalid deletion revisions");
        }
        List<String> result = new ArrayList<String>(values.size());
        Set<String> unique = new HashSet<String>();
        for (String value : values) {
            requireRevision(value, "deletion revision");
            if (!unique.add(value)) {
                throw new IllegalArgumentException("duplicate deletion revision");
            }
            result.add(value);
        }
        return Collections.unmodifiableList(result);
    }

    private static void requireRevision(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid " + label);
        }
    }

    private static boolean containsControl(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static Path requireParent(Path target) {
        if (target == null || target.getFileName() == null) {
            throw new IllegalArgumentException("book deletion journal path is required");
        }
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IllegalArgumentException("book deletion journal needs a parent");
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
