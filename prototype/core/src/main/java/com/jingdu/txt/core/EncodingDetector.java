package com.jingdu.txt.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EncodingDetector {
    private static final int SAMPLE_SIZE = 64 * 1024;
    private static final String COMMON_CHINESE =
            "的一是在不了有人我他這这个们們來来為为與与說说時时會会國国學学書书讀读"
                    + "體体臺台灣湾裡里後后發发现現聲声網网章第文中小大上下年月日天"
                    + "地生子你她到也就出道得著着過过家去能對对多然於于心好成事只"
                    + "想看用面起還还進进前開开手而本次此長长已無无明三二最行高";

    public DetectedEncoding detect(Path source) throws IOException {
        Sample sourceSample = readSample(source);
        byte[] sample = sourceSample.bytes;
        if (startsWith(sample, new int[] {0xEF, 0xBB, 0xBF})) {
            return new DetectedEncoding(StandardCharsets.UTF_8, 3, 1.0, "UTF-8 BOM");
        }
        if (startsWith(sample, new int[] {0xFF, 0xFE})) {
            return new DetectedEncoding(StandardCharsets.UTF_16LE, 2, 1.0, "UTF-16LE BOM");
        }
        if (startsWith(sample, new int[] {0xFE, 0xFF})) {
            return new DetectedEncoding(StandardCharsets.UTF_16BE, 2, 1.0, "UTF-16BE BOM");
        }

        DetectedEncoding utf16 = detectUtf16WithoutBom(sample);
        if (utf16 != null) {
            return utf16;
        }
        if (strictlyDecodes(sample, StandardCharsets.UTF_8, sourceSample.truncated)) {
            double confidence = containsNonAscii(sample) ? 0.98 : 0.65;
            return new DetectedEncoding(StandardCharsets.UTF_8, 0, confidence,
                    containsNonAscii(sample) ? "valid UTF-8 multibyte sequence" : "ASCII-compatible content");
        }

        Charset gb18030 = Charset.forName("GB18030");
        Charset big5 = Charset.forName("Big5");
        String gbText = decodeStrictly(sample, gb18030, sourceSample.truncated);
        String big5Text = decodeStrictly(sample, big5, sourceSample.truncated);
        if (gbText != null && big5Text != null) {
            long gbScore = chinesePlausibilityScore(gbText);
            long big5Score = chinesePlausibilityScore(big5Text);
            long margin = Math.max(8L,
                    Math.min(gbText.length(), big5Text.length()) / 12L);
            if (big5Score >= gbScore + margin
                    && commonChineseHits(big5Text) >= 2) {
                return new DetectedEncoding(big5, 0, 0.78,
                        "invalid UTF-8; Big5 language plausibility exceeds GB18030; "
                                + "manual override remains available",
                        DetectedEncoding.SelectionMode.AUTO_DETECTED,
                        DetectedEncoding.Advisory.BIG5_HEURISTIC);
            }
            return new DetectedEncoding(gb18030, 0, 0.60,
                    "invalid UTF-8 and valid as both GB18030 and Big5; "
                            + "conservative GB18030 fallback, manual Big5 remains available",
                    DetectedEncoding.SelectionMode.AUTO_DETECTED,
                    DetectedEncoding.Advisory.LEGACY_AMBIGUOUS);
        }
        if (gbText != null) {
            return new DetectedEncoding(gb18030, 0, 0.72,
                    "invalid UTF-8 and valid GB18030; manual override remains available");
        }
        if (big5Text != null) {
            return new DetectedEncoding(big5, 0, 0.72,
                    "invalid UTF-8 and valid Big5; manual override remains available");
        }
        return new DetectedEncoding(gb18030, 0, 0.30,
                "fallback GB18030 with malformed-input replacement expected; "
                        + "manual Big5 remains available",
                DetectedEncoding.SelectionMode.AUTO_DETECTED,
                DetectedEncoding.Advisory.MALFORMED_FALLBACK);
    }

    public DetectedEncoding manual(Path source, String charsetName) throws IOException {
        Charset charset = Charset.forName(charsetName);
        byte[] sample = readSample(source).bytes;
        return new DetectedEncoding(charset, bomLength(sample, charset), 1.0,
                "manual charset override: " + charset.name(),
                DetectedEncoding.SelectionMode.MANUAL_OVERRIDE);
    }

    private static Sample readSample(Path source) throws IOException {
        byte[] buffer = new byte[SAMPLE_SIZE + 1];
        int total = 0;
        try (InputStream input = Files.newInputStream(source)) {
            while (total < buffer.length) {
                int read = input.read(buffer, total, buffer.length - total);
                if (read < 0) {
                    break;
                }
                total += read;
            }
        }
        boolean truncated = total > SAMPLE_SIZE;
        int sampleLength = Math.min(total, SAMPLE_SIZE);
        byte[] sample = new byte[sampleLength];
        System.arraycopy(buffer, 0, sample, 0, sampleLength);
        return new Sample(sample, truncated);
    }

    private static DetectedEncoding detectUtf16WithoutBom(byte[] sample) {
        if (sample.length < 8) {
            return null;
        }
        int pairs = sample.length / 2;
        int evenZeros = 0;
        int oddZeros = 0;
        for (int i = 0; i + 1 < sample.length; i += 2) {
            if (sample[i] == 0) {
                evenZeros++;
            }
            if (sample[i + 1] == 0) {
                oddZeros++;
            }
        }
        double evenRatio = (double) evenZeros / pairs;
        double oddRatio = (double) oddZeros / pairs;
        if (oddRatio > 0.20 && evenRatio < 0.05) {
            return new DetectedEncoding(StandardCharsets.UTF_16LE, 0, 0.82,
                    "UTF-16LE zero-byte pattern without BOM");
        }
        if (evenRatio > 0.20 && oddRatio < 0.05) {
            return new DetectedEncoding(StandardCharsets.UTF_16BE, 0, 0.82,
                    "UTF-16BE zero-byte pattern without BOM");
        }
        return null;
    }

    private static int bomLength(byte[] sample, Charset charset) {
        if (charset.equals(StandardCharsets.UTF_8) && startsWith(sample, new int[] {0xEF, 0xBB, 0xBF})) {
            return 3;
        }
        if (charset.equals(StandardCharsets.UTF_16LE) && startsWith(sample, new int[] {0xFF, 0xFE})) {
            return 2;
        }
        if (charset.equals(StandardCharsets.UTF_16BE) && startsWith(sample, new int[] {0xFE, 0xFF})) {
            return 2;
        }
        return 0;
    }

    private static boolean strictlyDecodes(byte[] sample, Charset charset,
            boolean sampleTruncated) {
        return decodeStrictly(sample, charset, sampleTruncated) != null;
    }

    private static String decodeStrictly(byte[] sample, Charset charset,
            boolean sampleTruncated) {
        try {
            CharsetDecoder decoder = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            ByteBuffer input = ByteBuffer.wrap(sample);
            int outputSize = Math.max(1,
                    (int) Math.ceil(sample.length * decoder.maxCharsPerByte()) + 1);
            CharBuffer output = CharBuffer.allocate(outputSize);
            CoderResult result = decoder.decode(input, output, !sampleTruncated);
            if (result.isError()) {
                result.throwException();
            }
            if (result.isOverflow() || (sampleTruncated && input.remaining() > 3)) {
                return null;
            }
            if (!sampleTruncated) {
                result = decoder.flush(output);
                if (result.isError()) {
                    result.throwException();
                }
                if (result.isOverflow()) {
                    return null;
                }
            }
            output.flip();
            return output.toString();
        } catch (CharacterCodingException error) {
            return null;
        }
    }

    private static long chinesePlausibilityScore(String text) {
        long score = 0;
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            if (COMMON_CHINESE.indexOf(codePoint) >= 0) {
                score += 6;
            } else if (isCjk(codePoint)) {
                score += 1;
            } else if (Character.isWhitespace(codePoint)
                    || isCommonPunctuation(codePoint)) {
                score += 1;
            } else if (Character.isISOControl(codePoint)
                    || Character.getType(codePoint) == Character.UNASSIGNED
                    || Character.getType(codePoint) == Character.PRIVATE_USE) {
                score -= 8;
            }
        }
        return score;
    }

    private static int commonChineseHits(String text) {
        int hits = 0;
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            if (COMMON_CHINESE.indexOf(codePoint) >= 0) {
                hits++;
            }
        }
        return hits;
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    private static boolean isCommonPunctuation(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
    }

    private static boolean containsNonAscii(byte[] sample) {
        for (byte value : sample) {
            if ((value & 0x80) != 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWith(byte[] sample, int[] prefix) {
        if (sample.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((sample[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static final class Sample {
        private final byte[] bytes;
        private final boolean truncated;

        private Sample(byte[] bytes, boolean truncated) {
            this.bytes = bytes;
            this.truncated = truncated;
        }
    }
}
