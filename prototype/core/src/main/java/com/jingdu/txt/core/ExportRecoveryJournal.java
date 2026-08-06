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
import java.util.zip.CRC32;

/** App-private evidence that an external export started but has not been verified. */
public final class ExportRecoveryJournal {
    private static final int MAGIC = 0x4A444558;
    private static final int VERSION = 1;
    private static final int MAXIMUM_FILE_BYTES = 8 * 1024;
    private static final int MAXIMUM_PAYLOAD_BYTES = 4 * 1024;
    private static final int MAXIMUM_TOKEN_CHARACTERS = 256;
    private static final int MAXIMUM_NAME_CHARACTERS = 255;

    public enum Kind {
        RULE_PACK,
        CLEAN_TEXT
    }

    public static final class Entry {
        private final Kind kind;
        private final String sourceToken;
        private final String displayName;
        private final long expectedBytes;
        private final long startedAtEpochMillis;

        public Entry(Kind kind, String sourceToken, String displayName,
                long expectedBytes, long startedAtEpochMillis) {
            if (kind == null) {
                throw new IllegalArgumentException("export kind is required");
            }
            validateText(sourceToken, MAXIMUM_TOKEN_CHARACTERS, "source token");
            validateText(displayName, MAXIMUM_NAME_CHARACTERS, "display name");
            if (expectedBytes < 0) {
                throw new IllegalArgumentException("expected bytes must not be negative");
            }
            if (startedAtEpochMillis <= 0) {
                throw new IllegalArgumentException("start time must be positive");
            }
            this.kind = kind;
            this.sourceToken = sourceToken;
            this.displayName = displayName;
            this.expectedBytes = expectedBytes;
            this.startedAtEpochMillis = startedAtEpochMillis;
        }

        public Kind getKind() {
            return kind;
        }

        public String getSourceToken() {
            return sourceToken;
        }

        public String getDisplayName() {
            return displayName;
        }

        public long getExpectedBytes() {
            return expectedBytes;
        }

        public long getStartedAtEpochMillis() {
            return startedAtEpochMillis;
        }
    }

    public void writePending(Path target, Entry entry) throws IOException {
        if (entry == null) {
            throw new IllegalArgumentException("export entry is required");
        }
        Path parent = requireParent(target);
        Files.createDirectories(parent);
        byte[] payload = encodePayload(entry);
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
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            Files.deleteIfExists(temporary);
            throw error;
        }
    }

    public Entry readPending(Path target) throws IOException {
        if (!Files.isRegularFile(target)) {
            return null;
        }
        long fileBytes = Files.size(target);
        if (fileBytes <= 0 || fileBytes > MAXIMUM_FILE_BYTES) {
            throw new IOException("invalid export recovery journal size");
        }
        try (DataInputStream input = new DataInputStream(Files.newInputStream(target))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("unsupported export recovery journal");
            }
            int payloadLength = input.readInt();
            if (payloadLength <= 0 || payloadLength > MAXIMUM_PAYLOAD_BYTES) {
                throw new IOException("invalid export recovery payload size");
            }
            byte[] payload = new byte[payloadLength];
            input.readFully(payload);
            long expectedCrc = input.readLong();
            if (input.read() != -1) {
                throw new IOException("trailing bytes in export recovery journal");
            }
            CRC32 crc = new CRC32();
            crc.update(payload);
            if (expectedCrc != crc.getValue()) {
                throw new IOException("export recovery journal checksum mismatch");
            }
            return decodePayload(payload);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("invalid export recovery journal content", invalid);
        }
    }

    public void clear(Path target) throws IOException {
        requireParent(target);
        Files.deleteIfExists(target);
    }

    private static byte[] encodePayload(Entry entry) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(entry.kind.name());
            output.writeUTF(entry.sourceToken);
            output.writeUTF(entry.displayName);
            output.writeLong(entry.expectedBytes);
            output.writeLong(entry.startedAtEpochMillis);
        }
        byte[] payload = bytes.toByteArray();
        if (payload.length > MAXIMUM_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("export recovery payload is too large");
        }
        return payload;
    }

    private static Entry decodePayload(byte[] payload) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            Kind kind = Kind.valueOf(input.readUTF());
            Entry entry = new Entry(kind, input.readUTF(), input.readUTF(),
                    input.readLong(), input.readLong());
            if (input.read() != -1) {
                throw new IOException("trailing export recovery payload bytes");
            }
            return entry;
        }
    }

    private static Path requireParent(Path target) {
        if (target == null || target.getFileName() == null) {
            throw new IllegalArgumentException("export journal path is required");
        }
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IllegalArgumentException("export journal needs a parent directory");
        }
        return parent;
    }

    private static void validateText(String value, int maximumCharacters, String label) {
        if (value == null || value.isEmpty() || value.length() > maximumCharacters
                || containsControlCharacter(value)) {
            throw new IllegalArgumentException("invalid " + label);
        }
    }

    private static boolean containsControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }
}
