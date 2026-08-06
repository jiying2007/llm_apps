package com.jingdu.txt.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

public final class DiskRepairProjection implements AutoCloseable {
    static final int MAGIC = 0x4A445052;
    static final int LEGACY_VERSION = 1;
    static final int VERSION = 2;
    static final int LEGACY_HEADER_BYTES = 16;
    static final int LEGACY_RECORD_BYTES = 32;
    static final int HEADER_BYTES = 32;
    static final int BLOCK_RECORDS = 256;
    static final int BLOCK_INDEX_BYTES = 36;
    static final int INDEX_CHECKSUM_BYTES = 4;

    private final RandomAccessFile file;
    private final int version;
    private final long count;
    private final List<Block> blocks;

    private DiskRepairProjection(RandomAccessFile file, int version, long count,
            List<Block> blocks) {
        this.file = file;
        this.version = version;
        this.count = count;
        this.blocks = blocks;
    }

    public static DiskRepairProjection open(Path path) throws IOException {
        RandomAccessFile file = new RandomAccessFile(path.toFile(), "r");
        try {
            if (file.readInt() != MAGIC) {
                throw new IOException("unsupported repair projection format");
            }
            int version = file.readInt();
            if (version == LEGACY_VERSION) {
                long count = file.readLong();
                if (count < 0 || count > (Long.MAX_VALUE - LEGACY_HEADER_BYTES)
                        / LEGACY_RECORD_BYTES
                        || file.length() != LEGACY_HEADER_BYTES
                                + count * LEGACY_RECORD_BYTES) {
                    throw new IOException("truncated or malformed repair projection");
                }
                return new DiskRepairProjection(file, version, count, null);
            }
            if (version != VERSION) {
                throw new IOException("unsupported repair projection format");
            }
            long count = file.readLong();
            int blockSize = file.readInt();
            int blockCount = file.readInt();
            long indexOffset = file.readLong();
            long expectedBlocks = count == 0 ? 0 : (count - 1) / BLOCK_RECORDS + 1;
            if (count < 0 || blockSize != BLOCK_RECORDS || blockCount < 0
                    || expectedBlocks != blockCount || indexOffset < HEADER_BYTES
                    || indexOffset > file.length()
                    || file.length() - indexOffset
                            != (long) blockCount * BLOCK_INDEX_BYTES + INDEX_CHECKSUM_BYTES) {
                throw new IOException("truncated or malformed repair projection");
            }
            long indexBytes = (long) blockCount * BLOCK_INDEX_BYTES;
            int expectedIndexChecksum = checksumRange(file, indexOffset, indexBytes);
            file.seek(indexOffset + indexBytes);
            if (file.readInt() != expectedIndexChecksum) {
                throw new IOException("repair projection block index checksum mismatch");
            }
            List<Block> blocks = readBlockIndex(file, blockCount, indexOffset, count);
            return new DiskRepairProjection(file, version, count, blocks);
        } catch (IOException error) {
            file.close();
            throw error;
        }
    }

    static Writer create(Path path) throws IOException {
        return new Writer(path);
    }

    public long getMatchCount() { return count; }

    public long mapOriginalToDerived(long originalOffset) throws IOException {
        Record record = lastRecordAtOrBefore(originalOffset, true);
        if (record == null) {
            return originalOffset;
        }
        if (originalOffset <= record.originalEnd) {
            return record.derivedStart + Math.min(
                    originalOffset - record.originalStart,
                    record.derivedEnd - record.derivedStart);
        }
        return originalOffset + record.derivedEnd - record.originalEnd;
    }

    public long mapDerivedToOriginal(long derivedOffset) throws IOException {
        Record record = lastRecordAtOrBefore(derivedOffset, false);
        if (record == null) {
            return derivedOffset;
        }
        if (derivedOffset <= record.derivedEnd) {
            return record.originalStart + Math.min(
                    derivedOffset - record.derivedStart,
                    record.originalEnd - record.originalStart);
        }
        return derivedOffset + record.originalEnd - record.derivedEnd;
    }

    public TextOffsetRange mapDerivedRangeToOriginal(long derivedStart, long derivedEnd)
            throws IOException {
        if (derivedStart < 0 || derivedEnd < derivedStart) {
            throw new IllegalArgumentException("derived range must be non-negative and ordered");
        }
        long originalStart = mapDerivedBoundaryToOriginal(derivedStart, true);
        long originalEnd = mapDerivedBoundaryToOriginal(derivedEnd, false);
        if (originalEnd < originalStart) {
            throw new IOException("derived range maps to an invalid original range");
        }
        return new TextOffsetRange(originalStart, originalEnd);
    }

    private long mapDerivedBoundaryToOriginal(long derivedOffset, boolean rangeStart)
            throws IOException {
        if (!rangeStart) {
            Record starting = firstRecordStartingAtDerivedOffset(derivedOffset);
            if (starting != null) {
                return starting.originalStart;
            }
        }
        Record record = lastRecordAtOrBefore(derivedOffset, false);
        if (record == null) {
            return derivedOffset;
        }
        if (derivedOffset > record.derivedEnd) {
            return derivedOffset + record.originalEnd - record.derivedEnd;
        }
        if (record.derivedStart == record.derivedEnd) {
            return rangeStart ? record.originalEnd : record.originalStart;
        }
        if (rangeStart) {
            return derivedOffset == record.derivedEnd
                    ? record.originalEnd : record.originalStart;
        }
        return derivedOffset == record.derivedStart
                ? record.originalStart : record.originalEnd;
    }

    private Record firstRecordStartingAtDerivedOffset(long derivedOffset)
            throws IOException {
        if (version == LEGACY_VERSION) {
            long low = 0;
            long high = count;
            while (low < high) {
                long middle = (low + high) >>> 1;
                if (readLegacy(middle).derivedStart < derivedOffset) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }
            if (low < count) {
                Record record = readLegacy(low);
                return record.derivedStart == derivedOffset ? record : null;
            }
            return null;
        }
        if (blocks.isEmpty()) {
            return null;
        }
        int low = 0;
        int high = blocks.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (blocks.get(middle).derivedStart < derivedOffset) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        int blockIndex = Math.max(0, low - 1);
        while (blockIndex < blocks.size()
                && blocks.get(blockIndex).derivedStart <= derivedOffset) {
            for (Record record : readBlock(blocks.get(blockIndex))) {
                if (record.derivedStart == derivedOffset) {
                    return record;
                }
                if (record.derivedStart > derivedOffset) {
                    return null;
                }
            }
            blockIndex++;
        }
        return null;
    }

    private Record lastRecordAtOrBefore(long offset, boolean original) throws IOException {
        if (version == LEGACY_VERSION) {
            return legacyLastRecordAtOrBefore(offset, original);
        }
        int low = 0;
        int high = blocks.size() - 1;
        int found = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            Block block = blocks.get(middle);
            long start = original ? block.originalStart : block.derivedStart;
            if (start <= offset) {
                found = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        if (found < 0) {
            return null;
        }
        Record result = null;
        for (Record record : readBlock(blocks.get(found))) {
            long start = original ? record.originalStart : record.derivedStart;
            if (start > offset) {
                break;
            }
            result = record;
        }
        return result;
    }

    private Record legacyLastRecordAtOrBefore(long offset, boolean original)
            throws IOException {
        long low = 0;
        long high = count - 1;
        long found = -1;
        while (low <= high) {
            long middle = (low + high) >>> 1;
            Record record = readLegacy(middle);
            long start = original ? record.originalStart : record.derivedStart;
            if (start <= offset) {
                found = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return found < 0 ? null : readLegacy(found);
    }

    private Record readLegacy(long index) throws IOException {
        file.seek(LEGACY_HEADER_BYTES + index * LEGACY_RECORD_BYTES);
        return new Record(file.readLong(), file.readLong(), file.readLong(), file.readLong());
    }

    private List<Record> readBlock(Block block) throws IOException {
        byte[] bytes = new byte[block.dataLength];
        file.seek(block.dataOffset);
        file.readFully(bytes);
        CRC32 crc = new CRC32();
        crc.update(bytes);
        if ((int) crc.getValue() != block.checksum) {
            throw new IOException("repair projection block checksum mismatch");
        }
        List<Record> records = new ArrayList<Record>(block.recordCount);
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            long originalStart = block.originalStart;
            long derivedStart = block.derivedStart;
            long previousOriginalEnd = originalStart;
            long previousDerivedEnd = derivedStart;
            for (int index = 0; index < block.recordCount; index++) {
                if (index > 0) {
                    long gap = readUnsignedLong(input);
                    originalStart = safeAdd(previousOriginalEnd, gap);
                    derivedStart = safeAdd(previousDerivedEnd, gap);
                }
                long originalLength = readUnsignedLong(input);
                long derivedLength = readUnsignedLong(input);
                if (originalLength < 1 || originalLength > Integer.MAX_VALUE
                        || derivedLength > Integer.MAX_VALUE) {
                    throw new IOException("repair projection record length is invalid");
                }
                long originalEnd = safeAdd(originalStart, originalLength);
                long derivedEnd = safeAdd(derivedStart, derivedLength);
                records.add(new Record(originalStart, originalEnd, derivedStart, derivedEnd));
                previousOriginalEnd = originalEnd;
                previousDerivedEnd = derivedEnd;
            }
            if (input.read() != -1) {
                throw new IOException("repair projection block has trailing data");
            }
        }
        return records;
    }

    private static List<Block> readBlockIndex(RandomAccessFile file, int blockCount,
            long indexOffset, long totalCount) throws IOException {
        List<Block> blocks = new ArrayList<Block>(blockCount);
        file.seek(indexOffset);
        long expectedDataOffset = HEADER_BYTES;
        long indexedRecords = 0;
        long previousOriginalStart = -1;
        long previousDerivedStart = -1;
        for (int index = 0; index < blockCount; index++) {
            long originalStart = file.readLong();
            long derivedStart = file.readLong();
            long dataOffset = file.readLong();
            int dataLength = file.readInt();
            int recordCount = file.readInt();
            int checksum = file.readInt();
            int expectedRecords = (int) Math.min(BLOCK_RECORDS, totalCount - indexedRecords);
            if (originalStart < 0 || derivedStart < 0 || dataOffset != expectedDataOffset
                    || dataLength < 1 || recordCount != expectedRecords
                    || dataOffset > indexOffset || dataLength > indexOffset - dataOffset
                    || (index > 0 && (originalStart <= previousOriginalStart
                            || derivedStart < previousDerivedStart))) {
                throw new IOException("repair projection block index is invalid");
            }
            blocks.add(new Block(originalStart, derivedStart, dataOffset,
                    dataLength, recordCount, checksum));
            expectedDataOffset += dataLength;
            indexedRecords += recordCount;
            previousOriginalStart = originalStart;
            previousDerivedStart = derivedStart;
        }
        if (expectedDataOffset != indexOffset || indexedRecords != totalCount) {
            throw new IOException("repair projection block index is incomplete");
        }
        return blocks;
    }

    private static long safeAdd(long left, long right) throws IOException {
        if (right < 0 || left > Long.MAX_VALUE - right) {
            throw new IOException("repair projection offset overflow");
        }
        return left + right;
    }

    private static int checksumRange(RandomAccessFile file, long offset, long length)
            throws IOException {
        CRC32 crc = new CRC32();
        byte[] buffer = new byte[8192];
        file.seek(offset);
        long remaining = length;
        while (remaining > 0) {
            int read = file.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) {
                throw new IOException("truncated repair projection block index");
            }
            crc.update(buffer, 0, read);
            remaining -= read;
        }
        return (int) crc.getValue();
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

    private static void writeUnsignedLong(DataOutputStream output, long value)
            throws IOException {
        if (value < 0) {
            throw new IOException("repair projection value must not be negative");
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
                throw new IOException("truncated repair projection varint");
            }
            if (index == 9 && (value & 0xFE) != 0) {
                throw new IOException("repair projection varint overflow");
            }
            result |= (long) (value & 0x7F) << (index * 7);
            if ((value & 0x80) == 0) {
                return result;
            }
        }
        throw new IOException("repair projection varint overflow");
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    static final class Writer implements AutoCloseable {
        private final RandomAccessFile file;
        private final List<Block> blocks = new ArrayList<Block>();
        private ByteArrayOutputStream blockBytes = new ByteArrayOutputStream();
        private DataOutputStream blockOutput = new DataOutputStream(blockBytes);
        private int blockRecords;
        private long totalRecords;
        private long blockOriginalStart;
        private long blockDerivedStart;
        private long previousOriginalEnd;
        private long previousDerivedEnd;
        private boolean closed;

        Writer(Path path) throws IOException {
            RandomAccessFile opened = new RandomAccessFile(path.toFile(), "rw");
            try {
                opened.setLength(0);
                opened.writeInt(MAGIC);
                opened.writeInt(VERSION);
                opened.writeLong(0L);
                opened.writeInt(BLOCK_RECORDS);
                opened.writeInt(0);
                opened.writeLong(0L);
            } catch (IOException error) {
                try {
                    opened.close();
                } catch (IOException closeError) {
                    error.addSuppressed(closeError);
                }
                throw error;
            }
            file = opened;
        }

        void writeRecord(long originalStart, long originalEnd,
                long derivedStart, long derivedEnd) throws IOException {
            if (closed) {
                throw new IOException("repair projection writer is closed");
            }
            if (originalStart < 0 || originalEnd <= originalStart
                    || derivedStart < 0 || derivedEnd < derivedStart) {
                throw new IOException("repair projection record is invalid");
            }
            if (blockRecords == BLOCK_RECORDS) {
                flushBlock();
            }
            if (blockRecords == 0) {
                blockOriginalStart = originalStart;
                blockDerivedStart = derivedStart;
            } else {
                if (originalStart < previousOriginalEnd || derivedStart < previousDerivedEnd) {
                    throw new IOException("repair projection records are not monotonic");
                }
                long originalGap = originalStart - previousOriginalEnd;
                long derivedGap = derivedStart - previousDerivedEnd;
                if (originalGap != derivedGap) {
                    throw new IOException("repair projection unchanged gap mismatch");
                }
                writeUnsignedLong(blockOutput, originalGap);
            }
            writeUnsignedLong(blockOutput, originalEnd - originalStart);
            writeUnsignedLong(blockOutput, derivedEnd - derivedStart);
            previousOriginalEnd = originalEnd;
            previousDerivedEnd = derivedEnd;
            blockRecords++;
            totalRecords++;
        }

        private void flushBlock() throws IOException {
            if (blockRecords == 0) {
                return;
            }
            blockOutput.flush();
            byte[] bytes = blockBytes.toByteArray();
            CRC32 crc = new CRC32();
            crc.update(bytes);
            long dataOffset = file.getFilePointer();
            file.write(bytes);
            blocks.add(new Block(blockOriginalStart, blockDerivedStart, dataOffset,
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
                    file.writeLong(block.derivedStart);
                    file.writeLong(block.dataOffset);
                    file.writeInt(block.dataLength);
                    file.writeInt(block.recordCount);
                    file.writeInt(block.checksum);
                    updateLong(indexCrc, block.originalStart);
                    updateLong(indexCrc, block.derivedStart);
                    updateLong(indexCrc, block.dataOffset);
                    updateInt(indexCrc, block.dataLength);
                    updateInt(indexCrc, block.recordCount);
                    updateInt(indexCrc, block.checksum);
                }
                file.writeInt((int) indexCrc.getValue());
                file.seek(8);
                file.writeLong(totalRecords);
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
        final long derivedStart;
        final long dataOffset;
        final int dataLength;
        final int recordCount;
        final int checksum;

        Block(long originalStart, long derivedStart, long dataOffset,
                int dataLength, int recordCount, int checksum) {
            this.originalStart = originalStart;
            this.derivedStart = derivedStart;
            this.dataOffset = dataOffset;
            this.dataLength = dataLength;
            this.recordCount = recordCount;
            this.checksum = checksum;
        }
    }

    private static final class Record {
        final long originalStart;
        final long originalEnd;
        final long derivedStart;
        final long derivedEnd;

        Record(long originalStart, long originalEnd, long derivedStart, long derivedEnd) {
            this.originalStart = originalStart;
            this.originalEnd = originalEnd;
            this.derivedStart = derivedStart;
            this.derivedEnd = derivedEnd;
        }
    }
}
