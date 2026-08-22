package com.jingdu.txt.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/** Atomic, CRC-protected storage for one book's editable chapter outline. */
public final class ChapterOutlineStore {
    private static final int MAGIC = 0x4A44434F;
    private static final int VERSION = 1;
    private static final int MAXIMUM_PAYLOAD_BYTES = 4 * 1024 * 1024;

    public ChapterOutline load(Path target) throws IOException {
        if (!Files.isRegularFile(target)) {
            return null;
        }
        long size = Files.size(target);
        if (size <= 0 || size > MAXIMUM_PAYLOAD_BYTES + 32L) {
            throw new IOException("invalid chapter outline size");
        }
        try (DataInputStream input = new DataInputStream(Files.newInputStream(target))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("unsupported chapter outline");
            }
            int payloadLength = input.readInt();
            if (payloadLength <= 0 || payloadLength > MAXIMUM_PAYLOAD_BYTES) {
                throw new IOException("invalid chapter outline payload size");
            }
            byte[] payload = new byte[payloadLength];
            input.readFully(payload);
            long expectedCrc = input.readLong();
            if (input.read() != -1) {
                throw new IOException("trailing bytes in chapter outline");
            }
            CRC32 crc = new CRC32();
            crc.update(payload);
            if (crc.getValue() != expectedCrc) {
                throw new IOException("chapter outline checksum mismatch");
            }
            return decode(payload);
        } catch (EOFException truncated) {
            throw new IOException("truncated chapter outline", truncated);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("invalid chapter outline content", invalid);
        }
    }

    public void save(Path target, ChapterOutline outline) throws IOException {
        if (outline == null) {
            throw new IllegalArgumentException("chapter outline is required");
        }
        byte[] payload = encode(outline);
        Path parent = requireParent(target);
        Files.createDirectories(parent);
        CRC32 crc = new CRC32();
        crc.update(payload);
        Path temporary = target.resolveSibling(target.getFileName().toString() + ".tmp");
        Files.deleteIfExists(temporary);
        try {
            try (DataOutputStream output = new DataOutputStream(
                    Files.newOutputStream(temporary))) {
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

    private static byte[] encode(ChapterOutline outline) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(outline.getBaseRevision());
            output.writeInt(outline.getEntries().size());
            for (ChapterOutlineEntry entry : outline.getEntries()) {
                output.writeUTF(entry.getTitle());
                output.writeInt(entry.getOriginalCharacterOffset());
                output.writeInt(entry.getConfidencePercent());
                output.writeByte(entry.getOrigin().ordinal());
            }
        }
        byte[] payload = bytes.toByteArray();
        if (payload.length <= 0 || payload.length > MAXIMUM_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("chapter outline payload is too large");
        }
        return payload;
    }

    private static ChapterOutline decode(byte[] payload) throws IOException {
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(payload))) {
            String revision = input.readUTF();
            int count = input.readInt();
            if (count < 0 || count > ChapterOutline.MAXIMUM_ENTRIES) {
                throw new IOException("invalid chapter outline count");
            }
            List<ChapterOutlineEntry> entries =
                    new ArrayList<ChapterOutlineEntry>(count);
            ChapterOutlineEntry.Origin[] origins = ChapterOutlineEntry.Origin.values();
            for (int index = 0; index < count; index++) {
                String title = input.readUTF();
                int offset = input.readInt();
                int confidence = input.readInt();
                int origin = input.readUnsignedByte();
                if (origin >= origins.length) {
                    throw new IOException("invalid chapter outline origin");
                }
                entries.add(new ChapterOutlineEntry(title, offset, confidence,
                        origins[origin]));
            }
            if (input.read() != -1) {
                throw new IOException("trailing chapter outline payload bytes");
            }
            return new ChapterOutline(revision, entries);
        }
    }

    private static Path requireParent(Path target) {
        if (target == null || target.getFileName() == null) {
            throw new IllegalArgumentException("chapter outline path is required");
        }
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IllegalArgumentException("chapter outline needs a parent directory");
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
