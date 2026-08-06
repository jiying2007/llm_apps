package com.jingdu.txt.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TextImportPipeline {
    public static final int FIRST_WINDOW_CHARACTERS = 64 * 1024;
    private static final int BUFFER_SIZE = 16 * 1024;

    private final EncodingDetector detector;

    public TextImportPipeline(EncodingDetector detector) {
        this.detector = detector;
    }

    public ImportResult importFile(Path source, Path target,
            ImportEncodingPreference encodingPreference,
            ImportProgressListener listener) throws IOException {
        if (Files.exists(target)) {
            throw new FileAlreadyExistsException(target.toString());
        }
        Path parent = target.toAbsolutePath().getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("target parent directory does not exist: " + target);
        }

        if (encodingPreference == null) {
            throw new IllegalArgumentException("import encoding preference is required");
        }
        DetectedEncoding encoding = encodingPreference.isAutomatic()
                ? detector.detect(source)
                : detector.manual(source, encodingPreference.getManualCharsetName());
        Path temporary = Files.createTempFile(parent, ".jingdu-import-", ".part");
        long started = System.nanoTime();
        long firstWindowNanos = -1L;
        long normalizedCharacters = 0L;
        boolean completed = false;
        MessageDigest sourceDigest = sha256Digest();
        MessageDigest outputDigest = sha256Digest();

        try (InputStream raw = Files.newInputStream(source);
             DigestInputStream digestInput = new DigestInputStream(raw, sourceDigest)) {
            discardBom(digestInput, encoding.getBomLength());
            CharsetDecoder decoder = encoding.getCharset().newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            DecodingStatistics decodingStatistics;
            try (DigestOutputStream digestOutput = new DigestOutputStream(
                         Files.newOutputStream(temporary), outputDigest);
                 BufferedWriter writer = new BufferedWriter(
                         new OutputStreamWriter(digestOutput, StandardCharsets.UTF_8), BUFFER_SIZE)) {
                NormalizedTextSink sink = new NormalizedTextSink(writer, listener,
                        temporary, started);
                decodingStatistics = decodeReplacingErrors(digestInput, decoder,
                        encoding.getBomLength(), sink);
                sink.finish();
                normalizedCharacters = sink.normalizedCharacters;
                firstWindowNanos = sink.firstWindowNanos;
            }

            moveAtomically(temporary, target);
            completed = true;
            long totalNanos = System.nanoTime() - started;
            return new ImportResult(target, encoding, Files.size(source), Files.size(target),
                    normalizedCharacters, firstWindowNanos, totalNanos,
                    toHex(sourceDigest.digest()), toHex(outputDigest.digest()),
                    decodingStatistics.replacementCount,
                    decodingStatistics.firstErrorByteOffset,
                    decodingStatistics.firstReplacementCharacterOffset,
                    decodingStatistics.replacements);
        } finally {
            if (!completed) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static DecodingStatistics decodeReplacingErrors(InputStream input,
            CharsetDecoder decoder, int bomLength, NormalizedTextSink sink)
            throws IOException {
        ByteBuffer bytes = ByteBuffer.allocate(BUFFER_SIZE + 8);
        bytes.limit(0);
        CharBuffer characters = CharBuffer.allocate(BUFFER_SIZE);
        boolean endOfInput = false;
        long bufferStartByteOffset = bomLength;
        long replacementCount = 0;
        long firstErrorByteOffset = -1;
        long firstReplacementCharacterOffset = -1;
        List<DecodingReplacement> replacements = new ArrayList<>();
        boolean decoderConsumesErroneousInput =
                decoderConsumesErroneousInput(decoder.charset());

        while (true) {
            if (!bytes.hasRemaining() && !endOfInput) {
                bytes.clear();
                int read = input.read(bytes.array(), 0, bytes.capacity());
                if (read < 0) {
                    endOfInput = true;
                    bytes.limit(0);
                } else {
                    bytes.position(0);
                    bytes.limit(read);
                }
            }

            CoderResult result = decoder.decode(bytes, characters, endOfInput);
            drainCharacters(characters, sink);
            if (result.isError()) {
                int errorLength = result.length();
                int reportedPosition = bytes.position();
                int errorPosition = decoderConsumesErroneousInput
                        ? reportedPosition - errorLength : reportedPosition;
                if (errorPosition < 0
                        || errorPosition + errorLength > bytes.limit()) {
                    throw new IOException("decoder returned an invalid error range");
                }
                long errorOffset = bufferStartByteOffset + errorPosition;
                if (firstErrorByteOffset < 0) {
                    firstErrorByteOffset = errorOffset;
                    firstReplacementCharacterOffset =
                            sink.nextNormalizedCharacterOffset();
                }
                replacementCount++;
                if (replacements.size() < DecodingReplacement.MAXIMUM_RETAINED) {
                    replacements.add(new DecodingReplacement(errorOffset,
                            sink.nextNormalizedCharacterOffset()));
                }
                bytes.position(errorPosition + errorLength);
                sink.accept(decoder.replacement());
                continue;
            }
            if (result.isOverflow()) {
                continue;
            }
            if (endOfInput) {
                break;
            }

            int consumed = bytes.position();
            bufferStartByteOffset += consumed;
            bytes.compact();
            int writePosition = bytes.position();
            int read = input.read(bytes.array(), writePosition,
                    bytes.remaining());
            if (read < 0) {
                endOfInput = true;
            } else {
                bytes.position(writePosition + read);
            }
            bytes.flip();
        }

        while (true) {
            CoderResult result = decoder.flush(characters);
            drainCharacters(characters, sink);
            if (result.isUnderflow()) {
                break;
            }
            if (result.isError()) {
                result.throwException();
            }
        }
        return new DecodingStatistics(replacementCount, firstErrorByteOffset,
                firstReplacementCharacterOffset, replacements);
    }

    /**
     * Android's ICU decoder reports an error after consuming the erroneous
     * bytes, while the JDK decoder leaves the input positioned at their start.
     * Calibrate the concrete runtime so the streaming recovery loop can use a
     * single error-range contract without relying on internal decoder classes.
     */
    private static boolean decoderConsumesErroneousInput(Charset charset) {
        byte[] malformed;
        int expectedErrorPosition;
        if (StandardCharsets.UTF_16LE.equals(charset)) {
            malformed = new byte[] {0x41, 0x00, 0x00, (byte) 0xD8,
                    0x42, 0x00};
            expectedErrorPosition = 2;
        } else if (StandardCharsets.UTF_16BE.equals(charset)) {
            malformed = new byte[] {0x00, 0x41, (byte) 0xD8, 0x00,
                    0x00, 0x42};
            expectedErrorPosition = 2;
        } else {
            malformed = new byte[] {0x41, (byte) 0x81, 0x20, 0x42};
            expectedErrorPosition = 1;
        }
        CharsetDecoder probe = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer input = ByteBuffer.wrap(malformed);
        CoderResult result = probe.decode(input, CharBuffer.allocate(8), false);
        if (!result.isError()) {
            throw new IllegalStateException(
                    "unable to calibrate decoder error position for " + charset.name());
        }
        if (input.position() == expectedErrorPosition) {
            return false;
        }
        if (input.position() == expectedErrorPosition + result.length()) {
            return true;
        }
        throw new IllegalStateException(
                "unsupported decoder error position for " + charset.name());
    }

    private static void drainCharacters(CharBuffer characters,
            NormalizedTextSink sink) throws IOException {
        characters.flip();
        sink.accept(characters);
        characters.clear();
    }

    private static final class DecodingStatistics {
        private final long replacementCount;
        private final long firstErrorByteOffset;
        private final long firstReplacementCharacterOffset;
        private final List<DecodingReplacement> replacements;

        private DecodingStatistics(long replacementCount,
                long firstErrorByteOffset,
                long firstReplacementCharacterOffset,
                List<DecodingReplacement> replacements) {
            this.replacementCount = replacementCount;
            this.firstErrorByteOffset = firstErrorByteOffset;
            this.firstReplacementCharacterOffset = firstReplacementCharacterOffset;
            this.replacements = Collections.unmodifiableList(
                    new ArrayList<DecodingReplacement>(replacements));
        }
    }

    private static final class NormalizedTextSink {
        private final BufferedWriter writer;
        private final ImportProgressListener listener;
        private final Path temporary;
        private final long started;
        private final StringBuilder normalized = new StringBuilder(BUFFER_SIZE + 1);
        private boolean pendingCarriageReturn;
        private long normalizedCharacters;
        private long firstWindowNanos = -1;

        private NormalizedTextSink(BufferedWriter writer,
                ImportProgressListener listener, Path temporary, long started) {
            this.writer = writer;
            this.listener = listener;
            this.temporary = temporary;
            this.started = started;
        }

        private void accept(CharSequence values) throws IOException {
            for (int index = 0; index < values.length(); index++) {
                accept(values.charAt(index));
            }
        }

        private void accept(char value) throws IOException {
            if (pendingCarriageReturn) {
                normalized.append('\n');
                pendingCarriageReturn = false;
                if (value == '\n') {
                    flushIfNeeded();
                    return;
                }
            }
            if (value == '\r') {
                pendingCarriageReturn = true;
            } else {
                normalized.append(value);
            }
            flushIfNeeded();
        }

        private long nextNormalizedCharacterOffset() {
            return normalizedCharacters + normalized.length()
                    + (pendingCarriageReturn ? 1L : 0L);
        }

        private void flushIfNeeded() throws IOException {
            if (normalized.length() >= BUFFER_SIZE) {
                flushNormalized();
            }
        }

        private void flushNormalized() throws IOException {
            if (normalized.length() == 0) {
                return;
            }
            writer.write(normalized.toString());
            normalizedCharacters += normalized.length();
            normalized.setLength(0);
            if (firstWindowNanos < 0
                    && normalizedCharacters >= FIRST_WINDOW_CHARACTERS) {
                writer.flush();
                firstWindowNanos = System.nanoTime() - started;
                if (listener != null) {
                    listener.onFirstWindowReady(temporary, normalizedCharacters,
                            firstWindowNanos);
                }
            }
        }

        private void finish() throws IOException {
            if (pendingCarriageReturn) {
                normalized.append('\n');
                pendingCarriageReturn = false;
            }
            flushNormalized();
            writer.flush();
            if (firstWindowNanos < 0) {
                firstWindowNanos = System.nanoTime() - started;
                if (listener != null) {
                    listener.onFirstWindowReady(temporary, normalizedCharacters,
                            firstWindowNanos);
                }
            }
        }
    }

    private static void discardBom(InputStream input, int bytes) throws IOException {
        for (int i = 0; i < bytes; i++) {
            if (input.read() < 0) {
                throw new IOException("truncated byte-order mark");
            }
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target);
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xFF));
        }
        return result.toString();
    }
}
