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
import java.util.zip.CRC32;

/** Atomic, CRC-protected storage for one revision-bound encoding profile. */
public final class BookEncodingProfileStore {
    private static final int MAGIC = 0x4A444550;
    private static final int LEGACY_VERSION = 1;
    private static final int DIAGNOSTICS_VERSION = 2;
    private static final int CHARACTER_ANCHOR_VERSION = 3;
    private static final int VERSION = 4;
    private static final int MAXIMUM_PAYLOAD_BYTES = 4096;

    public BookEncodingProfile load(Path target) throws IOException {
        if (!Files.isRegularFile(target)) {
            return null;
        }
        long size = Files.size(target);
        if (size <= 0 || size > MAXIMUM_PAYLOAD_BYTES + 32L) {
            throw new IOException("invalid encoding profile size");
        }
        try (DataInputStream input = new DataInputStream(Files.newInputStream(target))) {
            if (input.readInt() != MAGIC) {
                throw new IOException("unsupported encoding profile");
            }
            int version = input.readInt();
            if (version != LEGACY_VERSION && version != DIAGNOSTICS_VERSION
                    && version != CHARACTER_ANCHOR_VERSION
                    && version != VERSION) {
                throw new IOException("unsupported encoding profile");
            }
            int payloadLength = input.readInt();
            if (payloadLength <= 0 || payloadLength > MAXIMUM_PAYLOAD_BYTES) {
                throw new IOException("invalid encoding profile payload size");
            }
            byte[] payload = new byte[payloadLength];
            input.readFully(payload);
            long expectedCrc = input.readLong();
            if (input.read() != -1) {
                throw new IOException("trailing bytes in encoding profile");
            }
            CRC32 crc = new CRC32();
            crc.update(payload);
            if (crc.getValue() != expectedCrc) {
                throw new IOException("encoding profile checksum mismatch");
            }
            return decode(payload, version);
        } catch (EOFException truncated) {
            throw new IOException("truncated encoding profile", truncated);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("invalid encoding profile content", invalid);
        }
    }

    public void save(Path target, BookEncodingProfile profile) throws IOException {
        if (profile == null) {
            throw new IllegalArgumentException("encoding profile is required");
        }
        byte[] payload = encode(profile);
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

    private static byte[] encode(BookEncodingProfile profile) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(profile.getBookId());
            output.writeUTF(profile.getBaseRevision());
            output.writeUTF(profile.getCharsetName());
            output.writeDouble(profile.getConfidence());
            output.writeUTF(profile.getSelectionMode().name());
            output.writeUTF(profile.getAdvisory().name());
            output.writeLong(profile.getDecodingReplacementCount());
            output.writeLong(profile.getFirstDecodingErrorByteOffset());
            output.writeLong(profile.getFirstReplacementCharacterOffset());
            output.writeInt(profile.getDecodingReplacements().size());
            for (DecodingReplacement replacement :
                    profile.getDecodingReplacements()) {
                output.writeLong(replacement.getSourceByteOffset());
                output.writeLong(replacement.getNormalizedCharacterOffset());
            }
        }
        byte[] payload = bytes.toByteArray();
        if (payload.length <= 0 || payload.length > MAXIMUM_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("encoding profile payload is too large");
        }
        return payload;
    }

    private static BookEncodingProfile decode(byte[] payload, int version)
            throws IOException {
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(payload))) {
            String bookId = input.readUTF();
            String baseRevision = input.readUTF();
            String charsetName = input.readUTF();
            double confidence = input.readDouble();
            DetectedEncoding.SelectionMode selectionMode;
            DetectedEncoding.Advisory advisory;
            try {
                selectionMode = DetectedEncoding.SelectionMode.valueOf(input.readUTF());
                advisory = DetectedEncoding.Advisory.valueOf(input.readUTF());
            } catch (RuntimeException invalid) {
                throw new IOException("invalid encoding profile enum", invalid);
            }
            long replacementCount = 0;
            long firstErrorByteOffset = -1;
            if (version >= DIAGNOSTICS_VERSION) {
                replacementCount = input.readLong();
                firstErrorByteOffset = input.readLong();
            }
            long firstReplacementCharacterOffset = -1;
            if (version >= CHARACTER_ANCHOR_VERSION) {
                firstReplacementCharacterOffset = input.readLong();
            }
            java.util.List<DecodingReplacement> replacements =
                    new java.util.ArrayList<DecodingReplacement>();
            if (version >= VERSION) {
                int count = input.readInt();
                if (count < 0 || count > DecodingReplacement.MAXIMUM_RETAINED) {
                    throw new IOException("invalid encoding replacement location count");
                }
                for (int index = 0; index < count; index++) {
                    replacements.add(new DecodingReplacement(
                            input.readLong(), input.readLong()));
                }
            } else if (firstReplacementCharacterOffset >= 0) {
                replacements.add(new DecodingReplacement(firstErrorByteOffset,
                        firstReplacementCharacterOffset));
            }
            if (input.read() != -1) {
                throw new IOException("trailing encoding profile payload bytes");
            }
            return new BookEncodingProfile(bookId, baseRevision, charsetName,
                    confidence, selectionMode, advisory, replacementCount,
                    firstErrorByteOffset, firstReplacementCharacterOffset,
                    replacements);
        }
    }

    private static Path requireParent(Path target) {
        if (target == null || target.getFileName() == null) {
            throw new IllegalArgumentException("encoding profile path is required");
        }
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IllegalArgumentException("encoding profile needs a parent directory");
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
