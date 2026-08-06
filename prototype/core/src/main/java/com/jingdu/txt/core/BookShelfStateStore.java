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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.CRC32;

/** Separate visibility state that keeps the v1 book metadata file downgrade-readable. */
public final class BookShelfStateStore {
    private static final int MAGIC = 0x4A445348;
    private static final int VERSION = 1;
    private static final int MAXIMUM_IDS = 1000;
    private static final int MAXIMUM_PAYLOAD_BYTES = 128 * 1024;

    public Set<String> load(Path target) throws IOException {
        if (!Files.isRegularFile(target)) {
            return new LinkedHashSet<String>();
        }
        long size = Files.size(target);
        if (size <= 0 || size > MAXIMUM_PAYLOAD_BYTES + 32L) {
            throw new IOException("invalid shelf state size");
        }
        try (DataInputStream input = new DataInputStream(Files.newInputStream(target))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("unsupported shelf state");
            }
            int payloadLength = input.readInt();
            if (payloadLength <= 0 || payloadLength > MAXIMUM_PAYLOAD_BYTES) {
                throw new IOException("invalid shelf state payload size");
            }
            byte[] payload = new byte[payloadLength];
            input.readFully(payload);
            long expectedCrc = input.readLong();
            if (input.read() != -1) {
                throw new IOException("trailing bytes in shelf state");
            }
            CRC32 crc = new CRC32();
            crc.update(payload);
            if (crc.getValue() != expectedCrc) {
                throw new IOException("shelf state checksum mismatch");
            }
            return decode(payload);
        }
    }

    public void save(Path target, Set<String> hiddenBookIds) throws IOException {
        if (hiddenBookIds == null || hiddenBookIds.size() > MAXIMUM_IDS) {
            throw new IllegalArgumentException("invalid hidden book ids");
        }
        TreeSet<String> normalized = new TreeSet<String>();
        for (String bookId : hiddenBookIds) {
            validateId(bookId);
            normalized.add(bookId);
        }
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

    private static byte[] encode(Set<String> ids) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(ids.size());
            for (String id : ids) {
                output.writeUTF(id);
            }
        }
        byte[] payload = bytes.toByteArray();
        if (payload.length <= 0 || payload.length > MAXIMUM_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("shelf state payload is too large");
        }
        return payload;
    }

    private static Set<String> decode(byte[] payload) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            int count = input.readInt();
            if (count < 0 || count > MAXIMUM_IDS) {
                throw new IOException("invalid hidden book id count");
            }
            Set<String> ids = new LinkedHashSet<String>();
            for (int index = 0; index < count; index++) {
                String id = input.readUTF();
                try {
                    validateId(id);
                } catch (IllegalArgumentException invalid) {
                    throw new IOException("invalid hidden book id", invalid);
                }
                if (!ids.add(id)) {
                    throw new IOException("duplicate hidden book id");
                }
            }
            if (input.read() != -1) {
                throw new IOException("trailing shelf state payload bytes");
            }
            return ids;
        }
    }

    private static void validateId(String id) {
        if (id == null || !id.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid hidden book id");
        }
    }

    private static Path requireParent(Path target) {
        if (target == null || target.getFileName() == null) {
            throw new IllegalArgumentException("shelf state path is required");
        }
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IllegalArgumentException("shelf state needs a parent");
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
