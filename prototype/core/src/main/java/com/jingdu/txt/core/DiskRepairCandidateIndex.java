package com.jingdu.txt.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

public final class DiskRepairCandidateIndex implements AutoCloseable {
    static final int MAGIC = 0x4A444349;
    static final int LEGACY_VERSION = 1;
    static final int VERSION = 2;
    static final int LEGACY_HEADER_BYTES = 144;
    static final int LEGACY_RECORD_BYTES = 20;
    static final int HEADER_BYTES = 160;
    static final int BLOCK_RECORDS = 256;
    static final int BLOCK_INDEX_BYTES = 28;
    static final int INDEX_CHECKSUM_BYTES = 4;
    static final int MAXIMUM_BLOCKS = 200_000;
    private static final int CONTEXT_CHARACTERS = 16;
    private static final int CONTEXT_WINDOW_CHARACTERS = 4096;

    private final RandomAccessFile file;
    private final int version;
    private final long count;
    private final String sourceSha256;
    private final List<RepairRule> rules;
    private final List<Block> blocks;
    private int cachedBlockIndex = -1;
    private List<Record> cachedRecords;

    private DiskRepairCandidateIndex(RandomAccessFile file, int version, long count,
            String sourceSha256, List<RepairRule> rules, List<Block> blocks) {
        this.file = file;
        this.version = version;
        this.count = count;
        this.sourceSha256 = sourceSha256;
        this.rules = rules;
        this.blocks = blocks;
    }

    public static DiskRepairCandidateIndex open(Path path, String expectedSourceSha256,
            List<RepairRule> inputRules) throws IOException {
        List<RepairRule> rules = RepairRules.enabled(inputRules);
        String expectedRuleSignature = rulesSignatureEnabled(rules);
        RandomAccessFile file = new RandomAccessFile(path.toFile(), "r");
        try {
            if (file.readInt() != MAGIC) {
                throw new IOException("unsupported repair candidate index format");
            }
            int version = file.readInt();
            long count = file.readLong();
            String sourceSha256 = readHex(file);
            String ruleSignature = readHex(file);
            if (!sourceSha256.equals(expectedSourceSha256)) {
                throw new IOException("repair candidate source revision mismatch");
            }
            if (!ruleSignature.equals(expectedRuleSignature)) {
                throw new IOException("repair candidate rule signature mismatch");
            }
            if (version == LEGACY_VERSION) {
                if (count < 0 || count > (Long.MAX_VALUE - LEGACY_HEADER_BYTES)
                        / LEGACY_RECORD_BYTES
                        || file.length() != LEGACY_HEADER_BYTES
                                + count * LEGACY_RECORD_BYTES) {
                    throw new IOException("truncated or malformed repair candidate index");
                }
                return new DiskRepairCandidateIndex(
                        file, version, count, sourceSha256, rules, null);
            }
            if (version != VERSION) {
                throw new IOException("unsupported repair candidate index format");
            }
            int blockSize = file.readInt();
            int blockCount = file.readInt();
            long indexOffset = file.readLong();
            long expectedBlocks = count == 0 ? 0 : (count - 1) / BLOCK_RECORDS + 1;
            if (count < 0 || blockSize != BLOCK_RECORDS || blockCount < 0
                    || blockCount > MAXIMUM_BLOCKS
                    || expectedBlocks != blockCount || indexOffset < HEADER_BYTES
                    || indexOffset > file.length()
                    || file.length() - indexOffset
                            != (long) blockCount * BLOCK_INDEX_BYTES + INDEX_CHECKSUM_BYTES) {
                throw new IOException("truncated or malformed repair candidate index");
            }
            long indexBytes = (long) blockCount * BLOCK_INDEX_BYTES;
            if (indexBytes > Integer.MAX_VALUE) {
                throw new IOException("repair candidate block index is too large");
            }
            byte[] indexData = new byte[(int) indexBytes];
            file.seek(indexOffset);
            file.readFully(indexData);
            CRC32 indexCrc = new CRC32();
            indexCrc.update(indexData);
            file.seek(indexOffset + indexBytes);
            if (file.readInt() != (int) indexCrc.getValue()) {
                throw new IOException("repair candidate block index checksum mismatch");
            }
            List<Block> blocks = readBlockIndex(indexData, blockCount, indexOffset, count);
            return new DiskRepairCandidateIndex(
                    file, version, count, sourceSha256, rules, blocks);
        } catch (IOException error) {
            file.close();
            throw error;
        }
    }

    static Writer create(Path path, String sourceSha256, List<RepairRule> enabledRules)
            throws IOException {
        return new Writer(path, sourceSha256, enabledRules);
    }

    public long getCandidateCount() { return count; }

    public List<RepairOccurrence> readOccurrences(long matchOffset, int limit)
            throws IOException {
        if (matchOffset < 0) {
            throw new IllegalArgumentException("matchOffset must not be negative");
        }
        if (limit < 1 || limit > RepairSelection.MAXIMUM_EXCLUSIONS) {
            throw new IllegalArgumentException("limit must be between 1 and "
                    + RepairSelection.MAXIMUM_EXCLUSIONS);
        }
        long endIndex = matchOffset >= count ? count
                : matchOffset + Math.min((long) limit, count - matchOffset);
        List<RepairOccurrence> occurrences =
                new ArrayList<RepairOccurrence>((int) (endIndex - matchOffset));
        for (long index = matchOffset; index < endIndex; index++) {
            Record record = read(index);
            RepairRule rule = validatedRule(record);
            occurrences.add(new RepairOccurrence(rule.getId(), record.originalStart));
        }
        return occurrences;
    }

    public RepairPreviewPage readPage(DiskDocumentIndex sourceIndex,
            RepairSelection selection, long matchOffset, int pageSize) throws IOException {
        if (sourceIndex == null || selection == null) {
            throw new IllegalArgumentException("source index and selection must not be null");
        }
        if (!sourceSha256.equals(sourceIndex.getSourceSha256())) {
            throw new IOException("repair candidate text index revision mismatch");
        }
        if (matchOffset < 0) {
            throw new IllegalArgumentException("matchOffset must not be negative");
        }
        if (pageSize < 1 || pageSize > 1000) {
            throw new IllegalArgumentException("pageSize must be between 1 and 1000");
        }
        List<RepairMatch> matches = new ArrayList<RepairMatch>(pageSize);
        long endIndex = matchOffset >= count ? count
                : matchOffset + Math.min((long) pageSize, count - matchOffset);
        IndexedTextWindow window = null;
        for (long index = matchOffset; index < endIndex; index++) {
            Record record = read(index);
            RepairRule rule = validatedRule(record);
            if (record.originalStart > Integer.MAX_VALUE) {
                throw new IOException("repair candidate range is invalid");
            }
            int originalStart = (int) record.originalStart;
            long originalEndLong = safeAdd(record.originalStart, record.matchLength);
            if (originalEndLong > sourceIndex.getCharacterCount()
                    || originalEndLong > Integer.MAX_VALUE) {
                throw new IOException("repair candidate leaves source text");
            }
            int originalEnd = (int) originalEndLong;
            int requiredStart = Math.max(0, originalStart - CONTEXT_CHARACTERS);
            int requiredEnd = (int) Math.min(sourceIndex.getCharacterCount(),
                    originalEndLong + CONTEXT_CHARACTERS);
            if (!contains(window, requiredStart, requiredEnd)) {
                window = sourceIndex.readWindowAround(originalStart,
                        Math.max(CONTEXT_WINDOW_CHARACTERS,
                                (record.matchLength + CONTEXT_CHARACTERS * 2) * 2));
            }
            int localStart = originalStart - window.getStartOffset();
            int localEnd = originalEnd - window.getStartOffset();
            String text = window.getText();
            if (localStart < 0 || localEnd > text.length()
                    || !rule.getMatchText().equals(text.substring(localStart, localEnd))) {
                throw new IOException("repair candidate no longer matches source text");
            }
            int beforeStart = safeBoundary(text,
                    Math.max(0, localStart - CONTEXT_CHARACTERS), true);
            int afterEnd = safeBoundary(text,
                    Math.min(text.length(), localEnd + CONTEXT_CHARACTERS), false);
            matches.add(new RepairMatch(rule.getId(), originalStart, originalEnd,
                    text.substring(beforeStart, localStart), rule.getMatchText(),
                    rule.getReplacement(), text.substring(localEnd, afterEnd),
                    selection.isApplied(rule.getId(), originalStart)));
        }
        return new RepairPreviewPage(matchOffset, matches, endIndex < count);
    }

    private RepairRule validatedRule(Record record) throws IOException {
        if (record.ruleIndex < 0 || record.ruleIndex >= rules.size()
                || record.originalStart < 0) {
            throw new IOException("repair candidate occurrence is invalid");
        }
        RepairRule rule = rules.get(record.ruleIndex);
        if (record.matchLength != rule.getMatchText().length()) {
            throw new IOException("repair candidate occurrence is invalid");
        }
        return rule;
    }

    private Record read(long index) throws IOException {
        if (index < 0 || index >= count) {
            throw new IOException("repair candidate index out of range");
        }
        if (version == LEGACY_VERSION) {
            return readLegacy(index);
        }
        int blockIndex = (int) (index / BLOCK_RECORDS);
        if (cachedBlockIndex != blockIndex) {
            cachedRecords = readBlock(blocks.get(blockIndex));
            cachedBlockIndex = blockIndex;
        }
        return cachedRecords.get((int) (index % BLOCK_RECORDS));
    }

    private Record readLegacy(long index) throws IOException {
        file.seek(LEGACY_HEADER_BYTES + index * LEGACY_RECORD_BYTES);
        long originalStart = file.readLong();
        int matchLength = file.readInt();
        int ruleIndex = file.readInt();
        int checksum = file.readInt();
        if (checksum != recordChecksum(index, originalStart, matchLength, ruleIndex)) {
            throw new IOException("repair candidate record checksum mismatch");
        }
        return new Record(originalStart, matchLength, ruleIndex);
    }

    private List<Record> readBlock(Block block) throws IOException {
        byte[] bytes = new byte[block.dataLength];
        file.seek(block.dataOffset);
        file.readFully(bytes);
        CRC32 crc = new CRC32();
        crc.update(bytes);
        if ((int) crc.getValue() != block.checksum) {
            throw new IOException("repair candidate block checksum mismatch");
        }
        List<Record> records = new ArrayList<Record>(block.recordCount);
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            long originalStart = block.originalStart;
            long previousOriginalEnd = originalStart;
            for (int index = 0; index < block.recordCount; index++) {
                if (index > 0) {
                    originalStart = safeAdd(previousOriginalEnd, readUnsignedLong(input));
                }
                long matchLength = readUnsignedLong(input);
                long ruleIndex = readUnsignedLong(input);
                if (matchLength < 1 || matchLength > Integer.MAX_VALUE
                        || ruleIndex > Integer.MAX_VALUE) {
                    throw new IOException("repair candidate compressed record is invalid");
                }
                previousOriginalEnd = safeAdd(originalStart, matchLength);
                records.add(new Record(originalStart, (int) matchLength, (int) ruleIndex));
            }
            if (input.read() != -1) {
                throw new IOException("repair candidate block has trailing data");
            }
        }
        return records;
    }

    private static List<Block> readBlockIndex(byte[] indexData, int blockCount,
            long indexOffset, long totalCount) throws IOException {
        List<Block> blocks = new ArrayList<Block>(blockCount);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(indexData))) {
            long expectedDataOffset = HEADER_BYTES;
            long indexedRecords = 0;
            long previousOriginalStart = -1;
            for (int index = 0; index < blockCount; index++) {
                long originalStart = input.readLong();
                long dataOffset = input.readLong();
                int dataLength = input.readInt();
                int recordCount = input.readInt();
                int checksum = input.readInt();
                int expectedRecords = (int) Math.min(
                        BLOCK_RECORDS, totalCount - indexedRecords);
                if (originalStart < 0 || dataOffset != expectedDataOffset || dataLength < 1
                        || recordCount != expectedRecords || dataOffset > indexOffset
                        || dataLength > indexOffset - dataOffset
                        || (index > 0 && originalStart <= previousOriginalStart)) {
                    throw new IOException("repair candidate block index is invalid");
                }
                blocks.add(new Block(originalStart, dataOffset,
                        dataLength, recordCount, checksum));
                expectedDataOffset += dataLength;
                indexedRecords += recordCount;
                previousOriginalStart = originalStart;
            }
            if (expectedDataOffset != indexOffset || indexedRecords != totalCount
                    || input.read() != -1) {
                throw new IOException("repair candidate block index is incomplete");
            }
        }
        return blocks;
    }

    private static long safeAdd(long left, long right) throws IOException {
        if (left < 0 || right < 0 || left > Long.MAX_VALUE - right) {
            throw new IOException("repair candidate offset overflow");
        }
        return left + right;
    }

    private static void writeUnsignedLong(DataOutputStream output, long value)
            throws IOException {
        if (value < 0) {
            throw new IOException("repair candidate value must not be negative");
        }
        while ((value & ~0x7FL) != 0) {
            output.writeByte((int) (value & 0x7F) | 0x80);
            value >>>= 7;
        }
        output.writeByte((int) value);
    }

    private static long readUnsignedLong(DataInputStream input) throws IOException {
        long result = 0;
        for (int index = 0; index < 10; index++) {
            int value = input.read();
            if (value < 0) {
                throw new IOException("truncated repair candidate varint");
            }
            if (index == 9 && value != 0) {
                throw new IOException("repair candidate varint overflow");
            }
            result |= (long) (value & 0x7F) << (index * 7);
            if ((value & 0x80) == 0) {
                return result;
            }
        }
        throw new IOException("repair candidate varint overflow");
    }

    static int recordChecksum(long candidateOrdinal, long originalStart,
            int matchLength, int ruleIndex) {
        int hash = 0x811C9DC5;
        for (int shift = 56; shift >= 0; shift -= 8) {
            hash = (hash ^ (int) ((candidateOrdinal >>> shift) & 0xFF)) * 0x01000193;
        }
        for (int shift = 56; shift >= 0; shift -= 8) {
            hash = (hash ^ (int) ((originalStart >>> shift) & 0xFF)) * 0x01000193;
        }
        for (int shift = 24; shift >= 0; shift -= 8) {
            hash = (hash ^ ((matchLength >>> shift) & 0xFF)) * 0x01000193;
        }
        for (int shift = 24; shift >= 0; shift -= 8) {
            hash = (hash ^ ((ruleIndex >>> shift) & 0xFF)) * 0x01000193;
        }
        return hash;
    }

    private static boolean contains(IndexedTextWindow window, int start, int end) {
        return window != null && window.getStartOffset() <= start
                && (long) window.getStartOffset() + window.getText().length() >= end;
    }

    private static int safeBoundary(CharSequence text, int offset, boolean backward) {
        if (offset > 0 && offset < text.length() && Character.isLowSurrogate(text.charAt(offset))
                && Character.isHighSurrogate(text.charAt(offset - 1))) {
            return backward ? offset - 1 : offset + 1;
        }
        return offset;
    }

    static String rulesSignatureEnabled(List<RepairRule> rules) {
        MessageDigest digest = newSha256();
        update(digest, "jingdu-repair-candidates-v1");
        for (RepairRule rule : rules) {
            update(digest, rule.getId());
            update(digest, rule.getMatchText());
            update(digest, rule.getReplacement());
            update(digest, Integer.toString(rule.getOrder()));
        }
        return hex(digest.digest());
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void writeHex(RandomAccessFile output, String value) throws IOException {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("expected lowercase SHA-256 hex");
        }
        output.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static String readHex(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[64];
        file.readFully(bytes);
        String value = new String(bytes, StandardCharsets.US_ASCII);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IOException("repair candidate index hash is invalid");
        }
        return value;
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xFF));
        }
        return result.toString();
    }

    private static void updateLong(CRC32 crc, long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            crc.update((int) (value >>> shift) & 0xFF);
        }
    }

    private static void updateInt(CRC32 crc, int value) {
        for (int shift = 24; shift >= 0; shift -= 8) {
            crc.update(value >>> shift & 0xFF);
        }
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    static final class Writer implements AutoCloseable {
        private final RandomAccessFile file;
        private final List<Block> blocks = new ArrayList<Block>();
        private final List<RepairRule> rules;
        private ByteArrayOutputStream blockBytes = new ByteArrayOutputStream();
        private DataOutputStream blockOutput = new DataOutputStream(blockBytes);
        private int blockRecords;
        private long totalRecords;
        private long blockOriginalStart;
        private long previousOriginalEnd;
        private boolean closed;

        Writer(Path path, String sourceSha256, List<RepairRule> enabledRules)
                throws IOException {
            rules = RepairRules.enabled(enabledRules);
            RandomAccessFile opened = new RandomAccessFile(path.toFile(), "rw");
            try {
                opened.setLength(0);
                opened.writeInt(MAGIC);
                opened.writeInt(VERSION);
                opened.writeLong(0L);
                writeHex(opened, sourceSha256);
                writeHex(opened, rulesSignatureEnabled(rules));
                opened.writeInt(BLOCK_RECORDS);
                opened.writeInt(0);
                opened.writeLong(0L);
            } catch (IOException | RuntimeException error) {
                try {
                    opened.close();
                } catch (IOException closeError) {
                    error.addSuppressed(closeError);
                }
                throw error;
            }
            file = opened;
        }

        void writeRecord(long candidateOrdinal, long originalStart,
                int matchLength, int ruleIndex) throws IOException {
            if (closed) {
                throw new IOException("repair candidate writer is closed");
            }
            if (candidateOrdinal != totalRecords || originalStart < 0
                    || matchLength < 1 || ruleIndex < 0 || ruleIndex >= rules.size()
                    || matchLength != rules.get(ruleIndex).getMatchText().length()) {
                throw new IOException("repair candidate record is invalid");
            }
            if (blockRecords == BLOCK_RECORDS) {
                flushBlock();
            }
            if (blockRecords == 0) {
                blockOriginalStart = originalStart;
            } else {
                if (originalStart < previousOriginalEnd) {
                    throw new IOException("repair candidate records are not monotonic");
                }
                writeUnsignedLong(blockOutput, originalStart - previousOriginalEnd);
            }
            writeUnsignedLong(blockOutput, matchLength);
            writeUnsignedLong(blockOutput, ruleIndex);
            previousOriginalEnd = safeAdd(originalStart, matchLength);
            blockRecords++;
            totalRecords++;
        }

        private void flushBlock() throws IOException {
            if (blockRecords == 0) {
                return;
            }
            blockOutput.flush();
            if (blocks.size() >= MAXIMUM_BLOCKS) {
                throw new IOException("repair candidate index exceeds supported block count");
            }
            byte[] bytes = blockBytes.toByteArray();
            CRC32 crc = new CRC32();
            crc.update(bytes);
            long dataOffset = file.getFilePointer();
            file.write(bytes);
            blocks.add(new Block(blockOriginalStart, dataOffset,
                    bytes.length, blockRecords, (int) crc.getValue()));
            blockBytes = new ByteArrayOutputStream();
            blockOutput = new DataOutputStream(blockBytes);
            blockRecords = 0;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            IOException failure = null;
            try {
                flushBlock();
                long indexOffset = file.getFilePointer();
                CRC32 indexCrc = new CRC32();
                for (Block block : blocks) {
                    file.writeLong(block.originalStart);
                    file.writeLong(block.dataOffset);
                    file.writeInt(block.dataLength);
                    file.writeInt(block.recordCount);
                    file.writeInt(block.checksum);
                    updateLong(indexCrc, block.originalStart);
                    updateLong(indexCrc, block.dataOffset);
                    updateInt(indexCrc, block.dataLength);
                    updateInt(indexCrc, block.recordCount);
                    updateInt(indexCrc, block.checksum);
                }
                file.writeInt((int) indexCrc.getValue());
                file.seek(8);
                file.writeLong(totalRecords);
                file.seek(LEGACY_HEADER_BYTES);
                file.writeInt(BLOCK_RECORDS);
                file.writeInt(blocks.size());
                file.writeLong(indexOffset);
            } catch (IOException error) {
                failure = error;
            } finally {
                closed = true;
                try {
                    file.close();
                } catch (IOException closeError) {
                    if (failure == null) {
                        failure = closeError;
                    } else {
                        failure.addSuppressed(closeError);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static final class Block {
        final long originalStart;
        final long dataOffset;
        final int dataLength;
        final int recordCount;
        final int checksum;

        Block(long originalStart, long dataOffset,
                int dataLength, int recordCount, int checksum) {
            this.originalStart = originalStart;
            this.dataOffset = dataOffset;
            this.dataLength = dataLength;
            this.recordCount = recordCount;
            this.checksum = checksum;
        }
    }

    private static final class Record {
        final long originalStart;
        final int matchLength;
        final int ruleIndex;

        Record(long originalStart, int matchLength, int ruleIndex) {
            this.originalStart = originalStart;
            this.matchLength = matchLength;
            this.ruleIndex = ruleIndex;
        }
    }
}
