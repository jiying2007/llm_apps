package com.jingdu.txt.core;

import com.jingdu.txt.core.port.TextToSpeechPort;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

public final class CorePrototypeTest {
    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("jingdu-core-test-");
        try {
            testEncodingAndStreamingImport(temporary);
            testManualEncodingOverride(temporary);
            testBig5ImportCandidate(temporary);
            testEncodingAdvisoryContract(temporary);
            testDecodingReplacementDiagnostics(temporary);
            testMalformedNavigationFixture(temporary);
            testBookEncodingProfileStore(temporary);
            testUtf16Bom(temporary);
            testTargetOverwriteIsRejected(temporary);
            testRepairProjectionAndAnchor();
            testRepairConflictWarning();
            testChapterAndUnicodeSearchIndex();
            testIndexRevisionGuard();
            testSegmentedAnchorRecovery();
            testDiskIndexSearchAndAtomicRevision(temporary);
            testDiskIndexInterruptedBuildResumes(temporary);
            testDiskIndexDamageIsRejected(temporary);
            testDiskIndexChapterLimit(temporary);
            testStreamingRepairFileAndProjection(temporary);
            testCompressedProjectionCompatibilityAndDamage(temporary);
            testRepairRevisionIsDeterministic(temporary);
            testRepairTargetOverwriteIsRejected(temporary);
            testRepairRuleStoreRoundTripAndDamageGate(temporary);
            testGeneratedArtifactPrunerSafetyAndLimits(temporary);
            testSelectiveRepairOccurrences(temporary);
            testRepairPreviewPagination(temporary);
            testRepairOrdinalRange();
            testRepairRulePackAndMerge();
            testExportRecoveryJournal(temporary);
            testBookLibraryStore(temporary);
            testBookDeletionJournal(temporary);
            testBookBookmarkStore(temporary);
            testChapterOutlineStore(temporary);
            testReaderAppearanceContract();
            testReaderDisplayPolicyContract();
            testReaderNavigationSettingsContract();
            testReaderTextSelectionContract();
            testAutoScrollPolicyContract();
            testAutoScrollCompanionContract();
            testCompanionSleepTimer();
            testSpeechSettingsContract();
            testSpeechPlaybackQueue();
            System.out.println("PASS CorePrototypeTest: 42 scenarios");
        } finally {
            deleteTree(temporary);
        }
    }

    private static void testEncodingAndStreamingImport(Path temporary) throws Exception {
        Path source = temporary.resolve("gb18030.txt");
        Path target = temporary.resolve("gb18030.normalized.txt");
        String originalText = "第一章\r\n祂来了\r下一行\n";
        byte[] sourceBytes = originalText.getBytes(Charset.forName("GB18030"));
        Files.write(source, sourceBytes);
        byte[] before = Files.readAllBytes(source);

        final long[] firstWindow = new long[] {-1L};
        ImportResult result = new TextImportPipeline(new EncodingDetector())
                .importFile(source, target, ImportEncodingPreference.automatic(),
                        new ImportProgressListener() {
                    @Override
                    public void onFirstWindowReady(Path file, long characters, long elapsedNanos) {
                        firstWindow[0] = elapsedNanos;
                    }
                });

        assertEquals("GB18030", result.getEncoding().getCharset().name(), "encoding");
        assertEquals(DetectedEncoding.SelectionMode.AUTO_DETECTED,
                result.getEncoding().getSelectionMode(), "automatic encoding source");
        assertEquals("第一章\n祂来了\n下一行\n",
                new String(Files.readAllBytes(target), StandardCharsets.UTF_8), "line normalization");
        assertArrayEquals(before, Files.readAllBytes(source), "source bytes remain unchanged");
        assertEquals(sha256(before), result.getSourceSha256(), "source hash includes original bytes");
        assertEquals(sha256(Files.readAllBytes(target)), result.getOutputSha256(),
                "output hash includes normalized UTF-8 bytes");
        assertTrue(firstWindow[0] >= 0, "first-window callback");
        assertEquals(0L, result.getDecodingReplacementCount(),
                "valid input has no decoding replacements");
        assertEquals(-1L, result.getFirstDecodingErrorByteOffset(),
                "valid input has no decoding error offset");
        assertEquals(-1L, result.getFirstReplacementCharacterOffset(),
                "valid input has no replacement character anchor");
    }

    private static void testManualEncodingOverride(Path temporary) throws Exception {
        Path source = temporary.resolve("manual-gb2312.txt");
        Path target = temporary.resolve("manual-gb2312.normalized.txt");
        String text = "编码覆盖：中文测试\r\n";
        byte[] original = text.getBytes(Charset.forName("GB2312"));
        Files.write(source, original);
        ImportEncodingPreference preference = new ImportEncodingPreference(
                ImportEncodingPreference.Choice.GB2312);
        ImportResult result = new TextImportPipeline(new EncodingDetector())
                .importFile(source, target, preference, null);
        assertEquals("GB2312", result.getEncoding().getCharset().name(),
                "manual encoding charset");
        assertEquals(DetectedEncoding.SelectionMode.MANUAL_OVERRIDE,
                result.getEncoding().getSelectionMode(), "manual encoding source");
        assertEquals(1.0, result.getEncoding().getConfidence(),
                "manual override has explicit confidence");
        assertEquals("编码覆盖：中文测试\n",
                new String(Files.readAllBytes(target), StandardCharsets.UTF_8),
                "manual encoding preserves text and normalizes newlines");
        assertArrayEquals(original, Files.readAllBytes(source),
                "manual encoding leaves source bytes unchanged");
        Path alternateTarget = temporary.resolve("manual-utf8.normalized.txt");
        ImportResult alternate = new TextImportPipeline(new EncodingDetector())
                .importFile(source, alternateTarget,
                        new ImportEncodingPreference(
                                ImportEncodingPreference.Choice.UTF_8), null);
        assertEquals(result.getSourceSha256(), alternate.getSourceSha256(),
                "same source keeps stable book identity across encoding choices");
        assertTrue(!result.getOutputSha256().equals(alternate.getOutputSha256()),
                "different decoded content receives a different view revision");
        assertEquals(preference.toJson(),
                ImportEncodingPreference.fromJson(preference.toJson()).toJson(),
                "encoding preference canonical round trip");
        ImportEncodingPreference big5 = new ImportEncodingPreference(
                ImportEncodingPreference.Choice.BIG5);
        assertEquals("Big5", big5.getManualCharsetName(),
                "Big5 manual charset name");
        assertEquals(big5.toJson(),
                ImportEncodingPreference.fromJson(big5.toJson()).toJson(),
                "Big5 encoding preference canonical round trip");
        assertIllegalArgument(() -> ImportEncodingPreference.fromJson(
                "{\"choice\":\"BIG_5\"}"),
                "unknown Big5 alias is rejected");
        assertIllegalArgument(() -> ImportEncodingPreference.automatic()
                .getManualCharsetName(),
                "automatic choice cannot be used as a manual charset");
    }

    private static void testBig5ImportCandidate(Path temporary) throws Exception {
        String traditional = "第一章\r\n這是一本繁體中文書。\r\n"
                + "我們在臺灣閱讀與學習，後來發現這個故事很好。\r\n";
        Path source = temporary.resolve("traditional-big5.txt");
        Files.write(source, traditional.getBytes(Charset.forName("Big5")));

        Path automaticTarget = temporary.resolve("traditional-big5-auto.txt");
        ImportResult automatic = new TextImportPipeline(new EncodingDetector())
                .importFile(source, automaticTarget,
                        ImportEncodingPreference.automatic(), null);
        assertEquals("Big5", automatic.getEncoding().getCharset().name(),
                "strong Traditional Chinese evidence selects Big5 candidate");
        assertEquals(DetectedEncoding.SelectionMode.AUTO_DETECTED,
                automatic.getEncoding().getSelectionMode(),
                "Big5 candidate records automatic source");
        assertEquals(DetectedEncoding.Advisory.BIG5_HEURISTIC,
                automatic.getEncoding().getAdvisory(),
                "heuristic Big5 candidate requests user review");
        assertEquals(traditional.replace("\r\n", "\n"),
                new String(Files.readAllBytes(automaticTarget), StandardCharsets.UTF_8),
                "automatic Big5 import preserves Traditional Chinese text");

        Path manualTarget = temporary.resolve("traditional-big5-manual.txt");
        ImportResult manual = new TextImportPipeline(new EncodingDetector())
                .importFile(source, manualTarget,
                        new ImportEncodingPreference(
                                ImportEncodingPreference.Choice.BIG5), null);
        assertEquals(DetectedEncoding.SelectionMode.MANUAL_OVERRIDE,
                manual.getEncoding().getSelectionMode(),
                "manual Big5 override remains explicit");
        assertEquals(automatic.getOutputSha256(), manual.getOutputSha256(),
                "automatic candidate and manual Big5 produce the same revision");
        assertArrayEquals(traditional.getBytes(Charset.forName("Big5")),
                Files.readAllBytes(source), "Big5 source bytes remain unchanged");

        Path simplified = temporary.resolve("simplified-gb18030.txt");
        Files.write(simplified, ("第一章\n这是一本简体中文书。\n"
                + "我们在中国阅读和学习，后来发现这个故事很好。\n")
                .getBytes(Charset.forName("GB18030")));
        assertEquals("GB18030", new EncodingDetector().detect(simplified)
                        .getCharset().name(),
                "representative simplified text remains GB18030");

        Path ambiguous = temporary.resolve("ambiguous-big5.txt");
        Files.write(ambiguous, "龜龍".getBytes(Charset.forName("Big5")));
        DetectedEncoding ambiguousResult = new EncodingDetector().detect(ambiguous);
        assertEquals("GB18030", ambiguousResult.getCharset().name(),
                "short ambiguous legacy text conservatively remains GB18030");
        assertTrue(ambiguousResult.getReason().contains("manual Big5"),
                "ambiguous fallback exposes the manual Big5 path");

        byte[] big5Character = "這".getBytes(Charset.forName("Big5"));
        byte[] boundaryBytes = new byte[(64 * 1024) + 3];
        boundaryBytes[0] = 'A';
        for (int index = 1; index + 1 < boundaryBytes.length; index += 2) {
            boundaryBytes[index] = big5Character[0];
            boundaryBytes[index + 1] = big5Character[1];
        }
        Path boundary = temporary.resolve("big5-sample-boundary.txt");
        Files.write(boundary, boundaryBytes);
        DetectedEncoding boundaryResult = new EncodingDetector().detect(boundary);
        assertEquals("Big5", boundaryResult.getCharset().name(),
                "sample ending inside a Big5 character retains prior evidence");

        Path malformedAtEof = temporary.resolve("big5-malformed-eof.txt");
        Files.write(malformedAtEof, Arrays.copyOf(boundaryBytes, 64 * 1024));
        DetectedEncoding malformedResult = new EncodingDetector().detect(malformedAtEof);
        assertTrue(!"Big5".equals(malformedResult.getCharset().name())
                        || malformedResult.getConfidence() < 0.5,
                "the same incomplete character at true EOF is not high-confidence Big5");
    }

    private static void testEncodingAdvisoryContract(Path temporary) throws Exception {
        EncodingDetector detector = new EncodingDetector();

        Path ambiguous = temporary.resolve("advisory-ambiguous.txt");
        Files.write(ambiguous, "龜龍".getBytes(Charset.forName("Big5")));
        assertEquals(DetectedEncoding.Advisory.LEGACY_AMBIGUOUS,
                detector.detect(ambiguous).getAdvisory(),
                "ambiguous legacy encoding exposes a stable advisory");

        Path malformed = temporary.resolve("advisory-malformed.txt");
        Files.write(malformed, new byte[] {(byte) 0x81});
        assertEquals(DetectedEncoding.Advisory.MALFORMED_FALLBACK,
                detector.detect(malformed).getAdvisory(),
                "malformed bytes expose replacement-risk advisory");
        assertEquals(DetectedEncoding.Advisory.NONE,
                detector.manual(malformed, "Big5").getAdvisory(),
                "manual encoding does not inherit automatic detection advisory");
        assertIllegalArgument(() -> new DetectedEncoding(StandardCharsets.UTF_8,
                0, 1.0, "test", DetectedEncoding.SelectionMode.AUTO_DETECTED, null),
                "null advisory is rejected");
        assertIllegalArgument(() -> new DetectedEncoding(StandardCharsets.UTF_8,
                0, 1.0, "test", DetectedEncoding.SelectionMode.MANUAL_OVERRIDE,
                DetectedEncoding.Advisory.LEGACY_AMBIGUOUS),
                "manual encoding cannot carry an automatic detection advisory");
        assertIllegalArgument(() -> new DetectedEncoding(StandardCharsets.UTF_8,
                0, Double.NaN, "test"), "NaN confidence is rejected");
        assertIllegalArgument(() -> new DetectedEncoding(StandardCharsets.UTF_8,
                0, Double.POSITIVE_INFINITY, "test"),
                "infinite confidence is rejected");
    }

    private static void testBookEncodingProfileStore(Path temporary) throws Exception {
        String bookId = repeat('1', 64);
        String revision = repeat('2', 64);
        BookEncodingProfile profile = BookEncodingProfile.from(bookId, revision,
                new DetectedEncoding(Charset.forName("GB18030"), 0, 0.60,
                        "ambiguous", DetectedEncoding.SelectionMode.AUTO_DETECTED,
                        DetectedEncoding.Advisory.LEGACY_AMBIGUOUS), 2, 7, 5,
                Arrays.asList(new DecodingReplacement(7, 5),
                        new DecodingReplacement(9, 8)));
        BookEncodingProfileStore store = new BookEncodingProfileStore();
        Path path = temporary.resolve("encoding-profiles/")
                .resolve(bookId + "-" + revision + ".bin");
        assertEquals(null, store.load(path), "missing encoding profile is legacy-compatible");
        store.save(path, profile);
        byte[] firstEncoding = Files.readAllBytes(path);
        BookEncodingProfile loaded = store.load(path);
        assertEquals(bookId, loaded.getBookId(), "encoding profile book binding");
        assertEquals(revision, loaded.getBaseRevision(),
                "encoding profile revision binding");
        assertEquals("GB18030", loaded.getCharsetName(),
                "encoding profile charset");
        assertEquals(0.60, loaded.getConfidence(),
                "encoding profile confidence");
        assertEquals(DetectedEncoding.SelectionMode.AUTO_DETECTED,
                loaded.getSelectionMode(), "encoding profile selection mode");
        assertEquals(DetectedEncoding.Advisory.LEGACY_AMBIGUOUS,
                loaded.getAdvisory(), "encoding profile advisory");
        assertEquals(2L, loaded.getDecodingReplacementCount(),
                "encoding profile replacement count");
        assertEquals(7L, loaded.getFirstDecodingErrorByteOffset(),
                "encoding profile first error offset");
        assertEquals(5L, loaded.getFirstReplacementCharacterOffset(),
                "encoding profile replacement character anchor");
        assertEquals(2, loaded.getDecodingReplacements().size(),
                "v4 encoding profile retains bounded locations");
        assertEquals(8L, loaded.getDecodingReplacements().get(1)
                        .getNormalizedCharacterOffset(),
                "v4 encoding profile preserves ordered character anchors");
        store.save(path, profile);
        assertArrayEquals(firstEncoding, Files.readAllBytes(path),
                "encoding profile encoding is deterministic");

        byte[] damaged = Files.readAllBytes(path);
        damaged[damaged.length - 1] ^= 0x01;
        Files.write(path, damaged);
        boolean damageRejected = false;
        try {
            store.load(path);
        } catch (IOException expected) {
            damageRejected = true;
        }
        assertTrue(damageRejected, "encoding profile checksum rejects damage");

        Path legacyPath = temporary.resolve("encoding-profiles/legacy.bin");
        writeLegacyEncodingProfile(legacyPath, new BookEncodingProfile(
                bookId, revision, "UTF-8", 0.98,
                DetectedEncoding.SelectionMode.AUTO_DETECTED,
                DetectedEncoding.Advisory.NONE));
        BookEncodingProfile legacy = store.load(legacyPath);
        assertEquals(0L, legacy.getDecodingReplacementCount(),
                "v1 encoding profile defaults to no replacement diagnostics");
        assertEquals(-1L, legacy.getFirstDecodingErrorByteOffset(),
                "v1 encoding profile defaults to no error offset");
        assertEquals(-1L, legacy.getFirstReplacementCharacterOffset(),
                "v1 encoding profile has no replacement character anchor");

        Path version2Path = temporary.resolve("encoding-profiles/version2.bin");
        writeVersion2EncodingProfile(version2Path, profile);
        BookEncodingProfile version2 = store.load(version2Path);
        assertEquals(2L, version2.getDecodingReplacementCount(),
                "v2 encoding profile retains byte diagnostics");
        assertEquals(-1L, version2.getFirstReplacementCharacterOffset(),
                "v2 encoding profile safely lacks the v3 character anchor");
        assertEquals(0, version2.getDecodingReplacements().size(),
                "v2 encoding profile has no navigable locations");

        Path version3Path = temporary.resolve("encoding-profiles/version3.bin");
        writeVersion3EncodingProfile(version3Path, profile);
        BookEncodingProfile version3 = store.load(version3Path);
        assertEquals(5L, version3.getFirstReplacementCharacterOffset(),
                "v3 encoding profile retains first character anchor");
        assertEquals(1, version3.getDecodingReplacements().size(),
                "v3 encoding profile upgrades its first anchor to one location");
        assertEquals(7L, version3.getDecodingReplacements().get(0)
                        .getSourceByteOffset(),
                "v3 upgraded location retains first source byte offset");
        assertIllegalArgument(() -> new BookEncodingProfile(bookId, revision,
                "utf8", 1.0, DetectedEncoding.SelectionMode.MANUAL_OVERRIDE,
                DetectedEncoding.Advisory.NONE),
                "encoding profile requires canonical charset name");
        assertIllegalArgument(() -> BookEncodingProfile.from("invalid", revision,
                new DetectedEncoding(StandardCharsets.UTF_8, 0, 1.0, "test")),
                "encoding profile rejects invalid book identity");
        assertIllegalArgument(() -> new BookEncodingProfile(bookId, revision,
                "UTF-8", 1.0, DetectedEncoding.SelectionMode.AUTO_DETECTED,
                DetectedEncoding.Advisory.NONE, 0, 3),
                "zero replacements cannot carry an error offset");
        assertIllegalArgument(() -> new BookEncodingProfile(bookId, revision,
                "UTF-8", 1.0, DetectedEncoding.SelectionMode.AUTO_DETECTED,
                DetectedEncoding.Advisory.NONE, 1, 3, 2,
                Collections.emptyList()),
                "anchored encoding profile requires its first location");
    }

    private static void testDecodingReplacementDiagnostics(Path temporary)
            throws Exception {
        Path source = temporary.resolve("malformed-byte-diagnostics.txt");
        Path target = temporary.resolve("malformed-byte-diagnostics.normalized.txt");
        Files.write(source, new byte[] {'A', (byte) 0xFF, 'B', (byte) 0xFF});
        ImportResult automatic = new TextImportPipeline(new EncodingDetector())
                .importFile(source, target, ImportEncodingPreference.automatic(), null);
        assertEquals(2L, automatic.getDecodingReplacementCount(),
                "each malformed sequence produces one replacement event");
        assertEquals(1L, automatic.getFirstDecodingErrorByteOffset(),
                "first malformed offset is zero-based in the original source");
        assertEquals(1L, automatic.getFirstReplacementCharacterOffset(),
                "first replacement anchor is in normalized output characters");
        assertEquals("A�B�", new String(Files.readAllBytes(target),
                StandardCharsets.UTF_8), "malformed bytes are replaced while import continues");

        Path bomSource = temporary.resolve("malformed-after-bom.txt");
        Path bomTarget = temporary.resolve("malformed-after-bom.normalized.txt");
        Files.write(bomSource, new byte[] {(byte) 0xEF, (byte) 0xBB,
                (byte) 0xBF, 'A', (byte) 0xFF});
        ImportResult manualUtf8 = new TextImportPipeline(new EncodingDetector())
                .importFile(bomSource, bomTarget,
                        new ImportEncodingPreference(
                                ImportEncodingPreference.Choice.UTF_8), null);
        assertEquals(1L, manualUtf8.getDecodingReplacementCount(),
                "manual decoding also reports replacement events");
        assertEquals(4L, manualUtf8.getFirstDecodingErrorByteOffset(),
                "source offset includes discarded BOM bytes");
        assertEquals(1L, manualUtf8.getFirstReplacementCharacterOffset(),
                "discarded BOM does not affect normalized character anchor");
        assertEquals("A�", new String(Files.readAllBytes(bomTarget),
                StandardCharsets.UTF_8), "BOM is discarded before recovered decoding");

        byte[] boundaryBytes = new byte[(16 * 1024) + 10];
        Arrays.fill(boundaryBytes, 0, boundaryBytes.length, (byte) 'A');
        int malformedStart = (16 * 1024) + 7;
        boundaryBytes[malformedStart] = (byte) 0xE2;
        boundaryBytes[malformedStart + 1] = (byte) 0x82;
        boundaryBytes[malformedStart + 2] = (byte) 0xFF;
        Path boundarySource = temporary.resolve("malformed-buffer-boundary.txt");
        Path boundaryTarget = temporary.resolve(
                "malformed-buffer-boundary.normalized.txt");
        Files.write(boundarySource, boundaryBytes);
        ImportResult boundary = new TextImportPipeline(new EncodingDetector())
                .importFile(boundarySource, boundaryTarget,
                        new ImportEncodingPreference(
                                ImportEncodingPreference.Choice.UTF_8), null);
        assertEquals(2L, boundary.getDecodingReplacementCount(),
                "cross-buffer malformed prefix and following byte are distinct events");
        assertEquals((long) malformedStart,
                boundary.getFirstDecodingErrorByteOffset(),
                "cross-buffer malformed offset remains absolute");
        assertEquals((long) malformedStart,
                boundary.getFirstReplacementCharacterOffset(),
                "cross-buffer replacement anchor remains absolute");

        Path carriageSource = temporary.resolve("malformed-after-cr.txt");
        Path carriageTarget = temporary.resolve("malformed-after-cr.normalized.txt");
        Files.write(carriageSource, new byte[] {'\r', (byte) 0xFF});
        ImportResult afterCarriageReturn = new TextImportPipeline(
                new EncodingDetector()).importFile(carriageSource, carriageTarget,
                        new ImportEncodingPreference(
                                ImportEncodingPreference.Choice.UTF_8), null);
        assertEquals(1L, afterCarriageReturn.getFirstReplacementCharacterOffset(),
                "pending carriage return is counted before replacement anchor");
        assertEquals("\n�", new String(Files.readAllBytes(carriageTarget),
                StandardCharsets.UTF_8),
                "replacement follows normalized carriage return");

        byte[] manyErrors = new byte[300];
        Arrays.fill(manyErrors, (byte) 0xFF);
        Path manySource = temporary.resolve("many-malformed-bytes.txt");
        Path manyTarget = temporary.resolve("many-malformed-bytes.normalized.txt");
        Files.write(manySource, manyErrors);
        ImportResult bounded = new TextImportPipeline(new EncodingDetector())
                .importFile(manySource, manyTarget,
                        new ImportEncodingPreference(
                                ImportEncodingPreference.Choice.UTF_8), null);
        assertEquals(300L, bounded.getDecodingReplacementCount(),
                "total replacement count is not truncated");
        assertEquals(DecodingReplacement.MAXIMUM_RETAINED,
                bounded.getDecodingReplacements().size(),
                "navigable replacement locations are strictly bounded");
        assertEquals(127L, bounded.getDecodingReplacements().get(127)
                        .getNormalizedCharacterOffset(),
                "retained replacement anchors stay ordered");

        for (ImportEncodingPreference.Choice choice :
                ImportEncodingPreference.Choice.values()) {
            if (choice != ImportEncodingPreference.Choice.AUTO) {
                assertCrossRuntimeDecoderRecovery(temporary, choice);
            }
        }
    }

    private static void assertCrossRuntimeDecoderRecovery(Path temporary,
            ImportEncodingPreference.Choice choice) throws Exception {
        boolean littleEndian = choice == ImportEncodingPreference.Choice.UTF_16LE;
        boolean bigEndian = choice == ImportEncodingPreference.Choice.UTF_16BE;
        byte[] input;
        long expectedOffset;
        String expected;
        if (littleEndian) {
            input = new byte[] {0x41, 0x00, 0x00, (byte) 0xD8,
                    0x3C, 0x00, 0x3D, 0x00, 0x42, 0x00};
            expectedOffset = 2L;
            expected = "A�=B";
        } else if (bigEndian) {
            input = new byte[] {0x00, 0x41, (byte) 0xD8, 0x00,
                    0x00, 0x3C, 0x00, 0x3D, 0x00, 0x42};
            expectedOffset = 2L;
            expected = "A�=B";
        } else {
            input = new byte[] {'A', (byte) 0x81, ' ', '<', '=', 'B'};
            expectedOffset = 1L;
            expected = "A� <=B";
        }
        String name = choice.name().toLowerCase(java.util.Locale.ROOT);
        Path source = temporary.resolve("decoder-recovery-" + name + ".txt");
        Path target = temporary.resolve(
                "decoder-recovery-" + name + ".normalized.txt");
        Files.write(source, input);
        ImportResult result = new TextImportPipeline(new EncodingDetector())
                .importFile(source, target,
                        new ImportEncodingPreference(choice), null);
        assertEquals(expected, new String(Files.readAllBytes(target),
                StandardCharsets.UTF_8), choice + " preserves valid bytes after an error");
        assertEquals(1L, result.getDecodingReplacementCount(),
                choice + " replacement count");
        assertEquals(expectedOffset, result.getFirstDecodingErrorByteOffset(),
                choice + " zero-based source offset");
        assertEquals(expectedOffset,
                result.getDecodingReplacements().get(0).getSourceByteOffset(),
                choice + " retained source offset");
        assertEquals(1L,
                result.getDecodingReplacements().get(0)
                        .getNormalizedCharacterOffset(),
                choice + " normalized replacement anchor");
    }

    private static void testMalformedNavigationFixture(Path temporary)
            throws Exception {
        MalformedNavigationFixtureGenerator.Fixture fixture =
                MalformedNavigationFixtureGenerator.generate(
                        temporary.resolve("device-smoke-fixture"));
        assertTrue(Files.size(fixture.getSource()) > 256L * 1024L,
                "device fixture crosses two 128K reader windows");
        assertTrue(fixture.getExpectedLocations().get(63)
                        .getNormalizedCharacterOffset() > 128L * 1024L,
                "fixture navigation 64 requires a second reader window");
        assertTrue(fixture.getExpectedLocations().get(127)
                        .getNormalizedCharacterOffset() > 256L * 1024L,
                "fixture navigation 128 requires a third reader window");
        assertTrue(Files.size(fixture.getManifest()) > 0,
                "device fixture includes a non-empty expectation manifest");
        Path normalized = temporary.resolve("device-smoke.normalized.txt");
        ImportResult result = new TextImportPipeline(new EncodingDetector())
                .importFile(fixture.getSource(), normalized,
                        new ImportEncodingPreference(
                                ImportEncodingPreference.Choice.UTF_8), null);
        assertEquals(130L, result.getDecodingReplacementCount(),
                "device fixture reports every malformed byte");
        assertEquals(DecodingReplacement.MAXIMUM_RETAINED,
                result.getDecodingReplacements().size(),
                "device fixture retains exactly the navigation limit");
        String normalizedText = new String(Files.readAllBytes(normalized),
                StandardCharsets.UTF_8);
        assertTrue(normalizedText.contains("ERROR-001=>�<=ERROR-001"),
                "malformed recovery preserves the first valid byte after an error");
        assertTrue(normalizedText.contains("ERROR-130=>�<=ERROR-130"),
                "malformed recovery preserves valid bytes after every retained error");
        for (int index = 0; index < DecodingReplacement.MAXIMUM_RETAINED;
                index++) {
            DecodingReplacement expected = fixture.getExpectedLocations().get(index);
            DecodingReplacement actual = result.getDecodingReplacements().get(index);
            assertEquals(expected.getSourceByteOffset(),
                    actual.getSourceByteOffset(),
                    "device fixture source byte offset " + index);
            assertEquals(expected.getNormalizedCharacterOffset(),
                    actual.getNormalizedCharacterOffset(),
                    "device fixture normalized character offset " + index);
        }
    }

    private static void testUtf16Bom(Path temporary) throws Exception {
        Path source = temporary.resolve("utf16le.txt");
        Path target = temporary.resolve("utf16le.normalized.txt");
        byte[] text = "章节一\r\n正文".getBytes(StandardCharsets.UTF_16LE);
        byte[] bytes = new byte[text.length + 2];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xFE;
        System.arraycopy(text, 0, bytes, 2, text.length);
        Files.write(source, bytes);

        ImportResult result = new TextImportPipeline(new EncodingDetector())
                .importFile(source, target, ImportEncodingPreference.automatic(), null);
        assertEquals("UTF-16LE", result.getEncoding().getCharset().name(), "UTF-16 BOM detection");
        assertEquals("章节一\n正文", new String(Files.readAllBytes(target), StandardCharsets.UTF_8),
                "UTF-16 transcoding");
    }

    private static void testTargetOverwriteIsRejected(Path temporary) throws Exception {
        Path source = temporary.resolve("overwrite-source.txt");
        Path target = temporary.resolve("overwrite-target.txt");
        Files.write(source, "source".getBytes(StandardCharsets.UTF_8));
        Files.write(target, "keep".getBytes(StandardCharsets.UTF_8));
        boolean rejected = false;
        try {
            new TextImportPipeline(new EncodingDetector()).importFile(source, target,
                    ImportEncodingPreference.automatic(), null);
        } catch (FileAlreadyExistsException expected) {
            rejected = true;
        }
        assertTrue(rejected, "existing target must be rejected");
        assertEquals("keep", new String(Files.readAllBytes(target), StandardCharsets.UTF_8),
                "existing target remains unchanged");
    }

    private static void testRepairProjectionAndAnchor() {
        String original = "第一章\n祂遇到錯別字\n最后一段";
        int anchorOffset = original.indexOf("錯別字") + 1;
        AnchorResolver resolver = new AnchorResolver();
        TextAnchor anchor = resolver.create(original, anchorOffset);
        List<RepairRule> rules = Arrays.asList(
                new RepairRule("chapter", "第一章", "第1章", true, 10),
                new RepairRule("pronoun", "祂", "他", true, 20),
                new RepairRule("typo", "錯別字", "错别字", true, 30));

        RepairApplyResult result = new RepairEngine().apply(original, rules);
        assertEquals("第1章\n他遇到错别字\n最后一段", result.getDerivedText(), "derived view");
        assertEquals(3, result.getMatches().size(), "preview match count");
        assertEquals("第一章\n祂遇到錯別字\n最后一段", original, "original String remains immutable");

        int resolvedOriginal = resolver.resolveOriginalOffset(original, anchor);
        int derivedOffset = result.mapOriginalOffsetToDerived(resolvedOriginal);
        assertTrue(derivedOffset >= result.getDerivedText().indexOf("错别字"), "anchor maps into repaired word");
        assertTrue(derivedOffset <= result.getDerivedText().indexOf("错别字") + 3,
                "anchor drift remains inside repaired word");
    }

    private static void testRepairConflictWarning() {
        List<RepairRule> rules = new ArrayList<RepairRule>();
        rules.add(new RepairRule("first", "祂", "他", true, 10));
        rules.add(new RepairRule("second", "祂", "她", true, 20));
        RepairApplyResult result = new RepairEngine().apply("祂", rules);
        assertEquals("他", result.getDerivedText(), "lower order wins deterministically");
        assertTrue(!result.getWarnings().isEmpty(), "conflicting rules produce warning");

        RepairApplyResult overlap = new RepairEngine().apply("甲乙", Arrays.asList(
                new RepairRule("long", "甲乙", "乙", true, 10),
                new RepairRule("short", "甲", "丙甲乙", true, 20)));
        assertEquals("乙", overlap.getDerivedText(), "ordered overlapping match is deterministic");
        assertTrue(overlap.getWarnings().size() >= 2,
                "overlap and potential non-cascading loop produce warnings");

        boolean duplicateIdRejected = false;
        try {
            new RepairEngine().apply("甲", Arrays.asList(
                    new RepairRule("same-id", "甲", "乙", true, 10),
                    new RepairRule("same-id", "乙", "丙", true, 20)));
        } catch (IllegalArgumentException expected) {
            duplicateIdRejected = true;
        }
        assertTrue(duplicateIdRejected, "duplicate rule ids are rejected for exact counts");
    }

    private static void testChapterAndUnicodeSearchIndex() {
        String text = "前言\n说明\n第一章 初遇\n小明遇到猫😀。\n第二章 重逢\n猫😀再次出现。";
        DocumentIndex index = DocumentIndex.build(text, "view-v1");
        assertEquals(3, index.getChapters().size(), "chapter count");
        assertEquals("第一章 初遇", index.getChapters().get(1).getTitle(), "chapter title");
        assertEquals(text.indexOf("第一章"), index.getChapters().get(1).getCharacterOffset(),
                "chapter offset");
        assertEquals(90, index.getChapters().get(0).getConfidencePercent(),
                "special chapter confidence is heuristic and visible");
        assertEquals(96, index.getChapters().get(1).getConfidencePercent(),
                "numbered chapter has stronger structural confidence");

        List<SearchHit> chinese = index.search("猫😀", 10, "view-v1");
        assertEquals(2, chinese.size(), "Unicode bigram matches");
        assertEquals(text.indexOf("猫😀"), chinese.get(0).getStartOffset(), "first search offset");
        assertTrue(chinese.get(0).getContext().contains("小明遇到猫😀"), "search context");

        List<SearchHit> singleCodePoint = index.search("😀", 1, "view-v1");
        assertEquals(1, singleCodePoint.size(), "single supplementary code point search");
        assertEquals(text.indexOf("😀"), singleCodePoint.get(0).getStartOffset(),
                "supplementary code point offset uses UTF-16 character semantics");
    }

    private static void testIndexRevisionGuard() {
        DocumentIndex index = DocumentIndex.build("第一章\n祂来了", "rules-v1");
        boolean rejected = false;
        try {
            index.search("他", 10, "rules-v2");
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        assertTrue(rejected, "stale index result must not be published");

        String repaired = new RepairEngine().apply("第一章\n祂来了",
                Arrays.asList(new RepairRule("pronoun", "祂", "他", true, 10))).getDerivedText();
        DocumentIndex rebuilt = DocumentIndex.build(repaired, "rules-v2");
        assertEquals(1, rebuilt.search("他", 10, "rules-v2").size(),
                "rebuilt index sees repaired view");
    }

    private static void testSegmentedAnchorRecovery() {
        SegmentedText original = new SegmentedText(Arrays.asList(
                "第一章\n这是一段跨", "越文件边界的正文", "\n末段"));
        int expected = original.toString().indexOf("边界") + 1;
        AnchorResolver resolver = new AnchorResolver();
        TextAnchor anchor = resolver.create(original, expected);

        SegmentedText moved = new SegmentedText(Arrays.asList(
                "新增序言\n第一章\n这是一段", "跨越文件", "边界的正文\n末段"));
        int resolved = resolver.resolveOriginalOffset(moved, anchor);
        assertEquals(moved.toString().indexOf("边界") + 1, resolved,
                "paragraph anchor recovers across segment boundaries after prefix insertion");
    }

    private static void testDiskIndexSearchAndAtomicRevision(Path temporary) throws Exception {
        Path source = temporary.resolve("disk-index-source.txt");
        Path indexRoot = temporary.resolve("disk-index-root");
        StringBuilder text = new StringBuilder();
        text.append("第一章 起点\n");
        while (text.length() < 65534) {
            text.append('甲');
        }
        text.append("跨界命中");
        text.append("\n第二章 终点\n猫😀再次出现");
        Files.write(source, text.toString().getBytes(StandardCharsets.UTF_8));
        String firstSha = sha256(Files.readAllBytes(source));

        DiskIndexBuildResult first = new DiskDocumentIndexBuilder().build(
                source, indexRoot, "view-v1", firstSha, null);
        assertTrue(!first.isResumed(), "first disk index build is fresh");
        DiskDocumentIndex index = DiskDocumentIndex.openActive(indexRoot);
        assertEquals("view-v1", index.getRevision(), "active revision after first publish");
        assertEquals(2, index.getChapters().size(), "disk chapter count");
        List<SearchHit> boundary = index.search("跨界命中", 10, "view-v1");
        assertEquals(1, boundary.size(), "cross-segment disk search");
        assertEquals(text.indexOf("跨界命中"), boundary.get(0).getStartOffset(),
                "cross-segment global offset");
        IndexedTextWindow boundaryWindow = index.readWindowAround(
                boundary.get(0).getStartOffset(), 1024);
        assertTrue(boundaryWindow.getText().contains("跨界命中"),
                "disk window reload contains cross-segment hit");
        assertEquals(boundary.get(0).getStartOffset(),
                boundaryWindow.getStartOffset() + boundaryWindow.getText().indexOf("跨界命中"),
                "disk window maps local offset back to global anchor");
        assertEquals(1, index.search("猫😀", 10, "view-v1").size(),
                "disk Unicode search");
        int emojiOffset = text.indexOf("😀");
        IndexedTextWindow surrogateWindow = index.readWindowAround(emojiOffset + 2, 4);
        assertTrue(!Character.isLowSurrogate(surrogateWindow.getText().charAt(0)),
                "disk window never starts on a dangling low surrogate");
        assertTrue(!Character.isHighSurrogate(surrogateWindow.getText().charAt(
                        surrogateWindow.getText().length() - 1)),
                "disk window never ends on a dangling high surrogate");
        assertTrue(surrogateWindow.getText().contains("😀"),
                "surrogate-safe window preserves the complete code point");
        int catOffset = text.indexOf("猫😀");
        assertEquals("猫😀", index.readTextRange(catOffset, catOffset + 3, 4),
                "bounded disk range reads exact selected text");
        boolean splitRangeRejected = false;
        try {
            index.readTextRange(emojiOffset + 1, emojiOffset + 2, 4);
        } catch (IllegalArgumentException expected) {
            splitRangeRejected = true;
        }
        assertTrue(splitRangeRejected,
                "bounded disk range rejects surrogate-splitting endpoints");
        boolean oversizedRangeRejected = false;
        try {
            index.readTextRange(catOffset, catOffset + 3, 2);
        } catch (IllegalArgumentException expected) {
            oversizedRangeRejected = true;
        }
        assertTrue(oversizedRangeRejected,
                "bounded disk range enforces caller-provided size limit");

        String conflicting = text.toString().replaceFirst("起点", "异点");
        Files.write(source, conflicting.getBytes(StandardCharsets.UTF_8));
        String conflictingSha = sha256(Files.readAllBytes(source));
        boolean revisionConflictRejected = false;
        try {
            new DiskDocumentIndexBuilder().build(
                    source, indexRoot, "view-v1", conflictingSha, null);
        } catch (IOException expectedError) {
            revisionConflictRejected = true;
        }
        assertTrue(revisionConflictRejected,
                "published revision cannot be silently reused for different content");

        String revised = "序章\n新版关键词\n" + text;
        Files.write(source, revised.getBytes(StandardCharsets.UTF_8));
        String secondSha = sha256(Files.readAllBytes(source));
        new DiskDocumentIndexBuilder().build(source, indexRoot, "view-v2", secondSha, null);
        DiskDocumentIndex active = DiskDocumentIndex.openActive(indexRoot);
        assertEquals("view-v2", active.getRevision(), "ACTIVE switches atomically to new revision");
        assertEquals(1, active.search("新版关键词", 10, "view-v2").size(),
                "new revision search");
        assertEquals("view-v1", DiskDocumentIndex.open(first.getRevisionDirectory(), "view-v1")
                .getRevision(), "old revision remains readable for rollback");
    }

    private static void testDiskIndexInterruptedBuildResumes(Path temporary) throws Exception {
        Path source = temporary.resolve("resume-source.txt");
        Path indexRoot = temporary.resolve("resume-index-root");
        StringBuilder text = new StringBuilder();
        text.append("第一章 恢复测试\n");
        while (text.length() < 150000) {
            text.append("恢复段落和关键词\n");
        }
        Files.write(source, text.toString().getBytes(StandardCharsets.UTF_8));
        String sourceSha = sha256(Files.readAllBytes(source));
        boolean interrupted = false;
        try {
            new DiskDocumentIndexBuilder().build(source, indexRoot, "resume-v1", sourceSha,
                    new DiskIndexBuildListener() {
                        @Override
                        public void onSegmentCommitted(int completedSegments,
                                long processedCharacters) throws IOException {
                            if (completedSegments == 1) {
                                throw new IOException("injected interruption");
                            }
                        }
                    });
        } catch (IOException expected) {
            interrupted = true;
        }
        assertTrue(interrupted, "index build interruption is observable");
        assertTrue(!Files.exists(indexRoot.resolve("ACTIVE")),
                "interrupted revision is never published");

        DiskIndexBuildResult resumed = new DiskDocumentIndexBuilder().build(
                source, indexRoot, "resume-v1", sourceSha, null);
        assertTrue(resumed.isResumed(), "second build resumes committed segments");
        assertTrue(DiskDocumentIndex.openActive(indexRoot)
                .search("关键词", 1, "resume-v1").size() == 1,
                "resumed index is queryable");
    }

    private static void testDiskIndexDamageIsRejected(Path temporary) throws Exception {
        Path source = temporary.resolve("damage-source.txt");
        Path indexRoot = temporary.resolve("damage-index-root");
        Files.write(source, "第一章\n损坏检测".getBytes(StandardCharsets.UTF_8));
        String sourceSha = sha256(Files.readAllBytes(source));
        DiskIndexBuildResult result = new DiskDocumentIndexBuilder().build(
                source, indexRoot, "damage-v1", sourceSha, null);
        Path bucket = result.getRevisionDirectory().resolve("buckets").resolve("00.bin");
        Files.write(bucket, new byte[] {1});
        boolean rejected = false;
        try {
            DiskDocumentIndex.open(result.getRevisionDirectory(), "damage-v1");
        } catch (IOException expected) {
            rejected = true;
        }
        assertTrue(rejected, "truncated postings bucket is rejected");
    }

    private static void testDiskIndexChapterLimit(Path temporary) throws Exception {
        Path source = temporary.resolve("chapter-limit-source.txt");
        Path indexRoot = temporary.resolve("chapter-limit-index");
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < DiskDocumentIndexBuilder.MAXIMUM_CHAPTERS + 5; index++) {
            text.append('第').append(index).append("章\n");
        }
        Files.write(source, text.toString().getBytes(StandardCharsets.UTF_8));
        String sourceSha = sha256(Files.readAllBytes(source));
        DiskIndexBuildResult result = new DiskDocumentIndexBuilder().build(
                source, indexRoot, "chapter-cap", sourceSha, null);
        DiskDocumentIndex index = DiskDocumentIndex.openActive(indexRoot);
        assertEquals(DiskDocumentIndexBuilder.MAXIMUM_CHAPTERS,
                index.getChapters().size(), "chapter directory is bounded");
        assertTrue(result.isChapterListTruncated() && index.isChapterListTruncated(),
                "chapter truncation is explicit in result and manifest");
        assertEquals(1, index.search("第20004章", 1, "chapter-cap").size(),
                "chapter cap does not truncate full-text search");

        Path manifest = result.getRevisionDirectory().resolve("manifest.bin");
        byte[] manifestBytes = Files.readAllBytes(manifest);
        Files.write(manifest, Arrays.copyOf(manifestBytes, manifestBytes.length - 1));
        assertTrue(!DiskDocumentIndex.open(result.getRevisionDirectory(), "chapter-cap")
                        .isChapterListTruncated(),
                "legacy manifest without truncation marker remains readable");
    }

    private static void testStreamingRepairFileAndProjection(Path temporary) throws Exception {
        Path source = temporary.resolve("repair-stream-source.txt");
        Path derived = temporary.resolve("repair-stream-derived.txt");
        Path projection = temporary.resolve("repair-stream-projection.bin");
        StringBuilder original = new StringBuilder();
        while (original.length() < 16383) {
            original.append('甲');
        }
        original.append("和谐词\n祂遇到錯別字\n结尾");
        Files.write(source, original.toString().getBytes(StandardCharsets.UTF_8));
        byte[] before = Files.readAllBytes(source);
        List<RepairRule> rules = Arrays.asList(
                new RepairRule("harmonized", "和谐词", "原词", true, 10),
                new RepairRule("pronoun", "祂", "他", true, 20),
                new RepairRule("typo", "錯別字", "错字", true, 30));

        RepairFileResult result = new RepairFilePipeline().apply(
                source, derived, projection, rules, 2);
        String repaired = new String(Files.readAllBytes(derived), StandardCharsets.UTF_8);
        assertTrue(repaired.contains("原词\n他遇到错字"),
                "streaming repair handles a match crossing reader buffer boundary");
        assertEquals(3L, result.getMatchCount(), "file repair match count");
        assertEquals(1L, result.getRuleMatchCounts().get("harmonized"),
                "per-rule harmonized match count");
        assertEquals(1L, result.getRuleMatchCounts().get("pronoun"),
                "per-rule pronoun match count");
        assertEquals(1L, result.getRuleMatchCounts().get("typo"),
                "per-rule typo match count");
        assertEquals(2, result.getPreviews().size(), "file repair preview limit");
        assertArrayEquals(before, Files.readAllBytes(source), "file repair keeps source immutable");

        int originalTail = original.indexOf("结尾");
        int derivedTail = repaired.indexOf("结尾");
        BookBookmark bookmark = new BookBookmark(repeat('9', 32), repeat('8', 64),
                originalTail, "结尾书签", 1L);
        try (DiskRepairProjection mapping = DiskRepairProjection.open(projection)) {
            assertEquals(3L, mapping.getMatchCount(), "projection record count");
            assertEquals((long) derivedTail, mapping.mapOriginalToDerived(originalTail),
                    "projection maps original tail after shorter replacements");
            assertEquals((long) originalTail, mapping.mapDerivedToOriginal(derivedTail),
                    "projection maps derived tail back to original");
            assertEquals((long) derivedTail, mapping.mapOriginalToDerived(
                    bookmark.getOriginalAnchorOffset()),
                    "base-text bookmark resolves in the derived revision");
            int derivedReplacementStart = repaired.indexOf("原词");
            TextOffsetRange originalRange = mapping.mapDerivedRangeToOriginal(
                    derivedReplacementStart, derivedReplacementStart + "原词".length());
            assertEquals((long) original.indexOf("和谐词"), originalRange.getStartOffset(),
                    "visible replacement selection maps to original match start");
            assertEquals((long) original.indexOf("和谐词") + "和谐词".length(),
                    originalRange.getEndOffset(),
                    "visible replacement selection expands to the complete original match");
        }
    }

    private static void testCompressedProjectionCompatibilityAndDamage(Path temporary)
            throws Exception {
        Path source = temporary.resolve("repair-compressed-source.txt");
        Path derived = temporary.resolve("repair-compressed-derived.txt");
        Path projection = temporary.resolve("repair-compressed-projection.bin");
        Files.write(source, repeat('祂', 300).getBytes(StandardCharsets.UTF_8));
        RepairFileResult result = new RepairFilePipeline().apply(source, derived, projection,
                Collections.singletonList(new RepairRule(
                        "compressed", "祂", "他们", true, 0)), 1);
        assertEquals(300L, result.getMatchCount(), "compressed projection match count");
        assertTrue(Files.size(projection)
                        < DiskRepairProjection.LEGACY_HEADER_BYTES
                                + 300L * DiskRepairProjection.LEGACY_RECORD_BYTES / 4,
                "compressed projection is at least four times smaller than v1");
        try (DiskRepairProjection mapping = DiskRepairProjection.open(projection)) {
            assertEquals(300L, mapping.getMatchCount(), "compressed projection opens");
            assertEquals(512L, mapping.mapOriginalToDerived(256),
                    "compressed projection maps across block boundary");
            assertEquals(256L, mapping.mapDerivedToOriginal(512),
                    "compressed projection reverse maps across block boundary");
        }

        Path deletionDerived = temporary.resolve("repair-compressed-deletion.txt");
        Path deletionProjection = temporary.resolve("repair-compressed-deletion.bin");
        new RepairFilePipeline().apply(source, deletionDerived, deletionProjection,
                Collections.singletonList(new RepairRule(
                        "delete", "祂", "", true, 0)), 1);
        try (DiskRepairProjection mapping = DiskRepairProjection.open(deletionProjection)) {
            assertEquals(299L, mapping.mapDerivedToOriginal(0),
                    "compressed projection preserves last-record semantics for equal anchors");
        }

        Path deletionSource = temporary.resolve("repair-range-deletion-source.txt");
        Path deletionText = temporary.resolve("repair-range-deletion-derived.txt");
        Path deletionRangeProjection = temporary.resolve("repair-range-deletion.bin");
        Files.write(deletionSource, "祂乙".getBytes(StandardCharsets.UTF_8));
        new RepairFilePipeline().apply(deletionSource, deletionText,
                deletionRangeProjection, Collections.singletonList(new RepairRule(
                        "delete-one", "祂", "", true, 0)), 1);
        try (DiskRepairProjection mapping =
                DiskRepairProjection.open(deletionRangeProjection)) {
            TextOffsetRange visibleSecond = mapping.mapDerivedRangeToOriginal(0, 1);
            assertEquals(1L, visibleSecond.getStartOffset(),
                    "selection after a deletion starts after invisible original text");
            assertEquals(2L, visibleSecond.getEndOffset(),
                    "selection after a deletion maps to the visible original character");
        }

        Path adjacentDeletionSource = temporary.resolve("repair-adjacent-deletion-source.txt");
        Path adjacentDeletionText = temporary.resolve("repair-adjacent-deletion-derived.txt");
        Path adjacentDeletionProjection = temporary.resolve("repair-adjacent-deletion.bin");
        Files.write(adjacentDeletionSource, "甲删删乙".getBytes(StandardCharsets.UTF_8));
        new RepairFilePipeline().apply(adjacentDeletionSource, adjacentDeletionText,
                adjacentDeletionProjection, Collections.singletonList(new RepairRule(
                        "delete-adjacent", "删", "", true, 0)), 1);
        try (DiskRepairProjection mapping =
                DiskRepairProjection.open(adjacentDeletionProjection)) {
            TextOffsetRange beforeDeletion = mapping.mapDerivedRangeToOriginal(0, 1);
            assertEquals(0L, beforeDeletion.getStartOffset(),
                    "range before adjacent deletions keeps its original start");
            assertEquals(1L, beforeDeletion.getEndOffset(),
                    "range end excludes all invisible deletions at the same derived offset");
            TextOffsetRange afterDeletion = mapping.mapDerivedRangeToOriginal(1, 2);
            assertEquals(3L, afterDeletion.getStartOffset(),
                    "range start skips all adjacent invisible deletions");
            assertEquals(4L, afterDeletion.getEndOffset(),
                    "range after adjacent deletions maps to visible original text");
        }

        byte[] damagedBytes = Files.readAllBytes(projection);
        damagedBytes[DiskRepairProjection.HEADER_BYTES] ^= 0x01;
        Path damaged = temporary.resolve("repair-compressed-damaged.bin");
        Files.write(damaged, damagedBytes);
        boolean checksumRejected = false;
        try (DiskRepairProjection mapping = DiskRepairProjection.open(damaged)) {
            mapping.mapOriginalToDerived(0);
        } catch (IOException expected) {
            checksumRejected = true;
        }
        assertTrue(checksumRejected, "compressed projection rejects damaged block");

        byte[] damagedIndexBytes = Files.readAllBytes(projection);
        long indexOffset = ByteBuffer.wrap(damagedIndexBytes,
                24, Long.BYTES).getLong();
        damagedIndexBytes[(int) indexOffset] ^= 0x01;
        Path damagedIndex = temporary.resolve("repair-compressed-index-damaged.bin");
        Files.write(damagedIndex, damagedIndexBytes);
        boolean indexChecksumRejected = false;
        try (DiskRepairProjection ignored = DiskRepairProjection.open(damagedIndex)) {
            // Opening must validate the compact index before any mapping can use its anchors.
        } catch (IOException expected) {
            indexChecksumRejected = true;
        }
        assertTrue(indexChecksumRejected, "compressed projection rejects damaged block index");

        Path legacy = temporary.resolve("repair-legacy-projection.bin");
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(legacy))) {
            output.writeInt(DiskRepairProjection.MAGIC);
            output.writeInt(DiskRepairProjection.LEGACY_VERSION);
            output.writeLong(1L);
            output.writeLong(2L);
            output.writeLong(3L);
            output.writeLong(2L);
            output.writeLong(4L);
        }
        try (DiskRepairProjection mapping = DiskRepairProjection.open(legacy)) {
            assertEquals(1L, mapping.getMatchCount(), "legacy projection remains readable");
            assertEquals(6L, mapping.mapOriginalToDerived(5),
                    "legacy original mapping remains compatible");
            assertEquals(5L, mapping.mapDerivedToOriginal(6),
                    "legacy derived mapping remains compatible");
        }
    }

    private static void testRepairRevisionIsDeterministic(Path temporary) throws Exception {
        Path source = temporary.resolve("repair-revision-source.txt");
        Files.write(source, "祂和祂".getBytes(StandardCharsets.UTF_8));
        List<RepairRule> rules = Arrays.asList(
                new RepairRule("pronoun", "祂", "他", true, 10));
        RepairFileResult first = new RepairFilePipeline().apply(source,
                temporary.resolve("repair-revision-a.txt"),
                temporary.resolve("repair-revision-a.bin"), rules, 10);
        RepairFileResult second = new RepairFilePipeline().apply(source,
                temporary.resolve("repair-revision-b.txt"),
                temporary.resolve("repair-revision-b.bin"), rules, 10);
        assertEquals(first.getRevisionId(), second.getRevisionId(),
                "same source and ordered rules produce deterministic revision");
        assertEquals(first.getDerivedSha256(), second.getDerivedSha256(),
                "same repair produces identical derived bytes");
    }

    private static void testRepairTargetOverwriteIsRejected(Path temporary) throws Exception {
        Path source = temporary.resolve("repair-overwrite-source.txt");
        Path derived = temporary.resolve("repair-overwrite-derived.txt");
        Path projection = temporary.resolve("repair-overwrite.bin");
        Files.write(source, "祂".getBytes(StandardCharsets.UTF_8));
        Files.write(derived, "keep".getBytes(StandardCharsets.UTF_8));
        boolean rejected = false;
        try {
            new RepairFilePipeline().apply(source, derived, projection,
                    Arrays.asList(new RepairRule("pronoun", "祂", "他", true, 10)), 10);
        } catch (FileAlreadyExistsException expected) {
            rejected = true;
        }
        assertTrue(rejected, "repair refuses to overwrite a derived target");
        assertEquals("keep", new String(Files.readAllBytes(derived), StandardCharsets.UTF_8),
                "existing derived target remains unchanged");
        assertTrue(!Files.exists(projection), "projection is not created after overwrite rejection");
    }

    private static void testRepairRuleStoreRoundTripAndDamageGate(Path temporary) throws Exception {
        Path storePath = temporary.resolve("repair-rules.bin");
        List<RepairRule> rules = Arrays.asList(
                new RepairRule("second", "錯別字", "错别字", false, 20,
                        RepairScope.CURRENT_BOOK, "只在这本书停用"),
                new RepairRule("first", "祂", "他", true, 10,
                        RepairScope.ALL_BOOKS, "通用人称"));
        RepairRuleStore store = new RepairRuleStore();
        store.save(storePath, rules);
        List<RepairRule> restored = store.load(storePath);
        assertEquals(2, restored.size(), "rule store count");
        assertEquals("first", restored.get(0).getId(), "rule store preserves sorted order");
        assertEquals(RepairScope.ALL_BOOKS, restored.get(0).getScope(),
                "rule store preserves scope");
        assertEquals("通用人称", restored.get(0).getNote(), "rule store preserves note");
        assertTrue(!restored.get(1).isEnabled(), "rule store preserves enabled state");

        Files.write(storePath, new byte[] {0, 1, 2, 3});
        boolean damagedRejected = false;
        try {
            store.load(storePath);
        } catch (IOException expected) {
            damagedRejected = true;
        }
        assertTrue(damagedRejected, "damaged rule store is rejected");

        Path catalogPath = temporary.resolve("repair-rule-catalog.bin");
        String bookRevision = repeat('c', 64);
        Map<String, List<RepairRule>> catalog = new LinkedHashMap<>();
        catalog.put("*", Arrays.asList(rules.get(1)));
        catalog.put(bookRevision, Arrays.asList(rules.get(0)));
        store.saveCatalog(catalogPath, catalog);
        Map<String, List<RepairRule>> restoredCatalog = store.loadCatalog(catalogPath);
        assertEquals(2, restoredCatalog.size(), "rule catalog profile count");
        assertEquals(RepairScope.ALL_BOOKS, restoredCatalog.get("*").get(0).getScope(),
                "rule catalog keeps global profile");
        assertEquals(RepairScope.CURRENT_BOOK,
                restoredCatalog.get(bookRevision).get(0).getScope(),
                "rule catalog keeps book profile");
        Map<String, List<RepairRule>> invalidCatalog = new LinkedHashMap<>(catalog);
        invalidCatalog.put("../outside", Arrays.asList(rules.get(0)));
        boolean invalidProfileRejected = false;
        try {
            store.saveCatalog(catalogPath, invalidCatalog);
        } catch (IllegalArgumentException expected) {
            invalidProfileRejected = true;
        }
        assertTrue(invalidProfileRejected, "invalid catalog profile key is rejected");
        assertEquals(2, store.loadCatalog(catalogPath).size(),
                "invalid catalog save leaves published catalog unchanged");
    }

    private static void testGeneratedArtifactPrunerSafetyAndLimits(Path temporary)
            throws Exception {
        Path books = temporary.resolve("pruner-books");
        Files.createDirectories(books);
        Path activeText = books.resolve("repair-aa.utf8.txt");
        Path activeProjection = books.resolve("repair-aa.projection.bin");
        Path activeCandidates = books.resolve("repair-aa.candidates.bin");
        Path recentText = books.resolve("repair-bb.utf8.txt");
        Path recentProjection = books.resolve("repair-bb.projection.bin");
        Path recentCandidates = books.resolve("repair-bb.candidates.bin");
        Path oldText = books.resolve("repair-cc.utf8.txt");
        Path oldProjection = books.resolve("repair-cc.projection.bin");
        Path oldCandidates = books.resolve("repair-cc.candidates.bin");
        Path unknown = books.resolve("user-notes.txt");
        for (Path path : Arrays.asList(activeText, activeProjection, activeCandidates,
                recentText, recentProjection, recentCandidates, oldText, oldProjection,
                oldCandidates, unknown)) {
            Files.write(path, path.getFileName().toString().getBytes(StandardCharsets.UTF_8));
        }
        Files.setLastModifiedTime(recentText, FileTime.fromMillis(3000));
        Files.setLastModifiedTime(recentProjection, FileTime.fromMillis(3000));
        Files.setLastModifiedTime(recentCandidates, FileTime.fromMillis(3000));
        Files.setLastModifiedTime(oldText, FileTime.fromMillis(1000));
        Files.setLastModifiedTime(oldProjection, FileTime.fromMillis(1000));
        Files.setLastModifiedTime(oldCandidates, FileTime.fromMillis(1000));
        String activeRevision = repeat('a', 64);
        String oldRevision = repeat('b', 64);
        Path activeIndex = books.resolve("indexes").resolve(activeRevision);
        Path oldIndex = books.resolve("indexes").resolve(oldRevision);
        Files.createDirectories(activeIndex);
        Files.createDirectories(oldIndex);
        Files.write(activeIndex.resolve("data"), new byte[] {1});
        Files.write(oldIndex.resolve("data"), new byte[] {2});
        Files.setLastModifiedTime(oldIndex, FileTime.fromMillis(2000));

        Set<Path> protectedPaths = new HashSet<Path>(Arrays.asList(
                activeText, activeProjection, activeCandidates,
                recentText, recentProjection, recentCandidates));
        Set<String> protectedRevisions = new HashSet<String>(Arrays.asList(activeRevision));
        GeneratedArtifactPruner.Result result = new GeneratedArtifactPruner().prune(
                books, protectedPaths, protectedRevisions, 1024 * 1024, 0);
        assertTrue(Files.exists(activeText) && Files.exists(activeProjection)
                        && Files.exists(activeCandidates),
                "pruner preserves active repair group");
        assertTrue(Files.exists(activeIndex), "pruner preserves active index revision");
        assertTrue(Files.exists(recentText) && Files.exists(recentProjection)
                        && Files.exists(recentCandidates),
                "pruner preserves a second book's active repair group");
        assertTrue(!Files.exists(oldText) && !Files.exists(oldProjection)
                        && !Files.exists(oldCandidates),
                "pruner removes old inactive repair group");
        assertTrue(!Files.exists(oldIndex), "pruner removes old inactive index revision");
        assertTrue(Files.exists(unknown), "pruner never touches unknown files");
        assertEquals(2, result.deletedGroups, "pruner deleted group count");
        assertTrue(result.protectedBytes > 0 && result.retainedBytes >= result.protectedBytes,
                "pruner reports protected and retained bytes");

        GeneratedArtifactPruner.Result constrained = new GeneratedArtifactPruner().prune(
                books, protectedPaths, protectedRevisions, 0, 0);
        assertTrue(constrained.protectedDataExceedsLimit,
                "protected data over quota is reported but never deleted");
        assertTrue(Files.exists(activeText) && Files.exists(activeProjection)
                        && Files.exists(activeCandidates) && Files.exists(activeIndex),
                "zero quota still preserves every active artifact");
        assertTrue(Files.exists(recentText) && Files.exists(recentProjection)
                        && Files.exists(recentCandidates),
                "zero quota preserves every active group across books");
    }

    private static void testSelectiveRepairOccurrences(Path temporary) throws Exception {
        String original = "祂甲祂乙祂";
        List<RepairRule> rules = Arrays.asList(
                new RepairRule("primary", "祂", "他", true, 10),
                new RepairRule("fallback", "祂", "她", true, 20));
        RepairSelection selection = RepairSelection.excluding(Arrays.asList(
                new RepairOccurrence("primary", 2)));
        RepairApplyResult memory = new RepairEngine().apply(original, rules, selection);
        assertEquals("他甲祂乙他", memory.getDerivedText(),
                "excluded occurrence keeps original and does not fall through");
        assertEquals(3, memory.getMatches().size(), "selection keeps all preview candidates");
        assertTrue(!memory.getMatches().get(1).isApplied(),
                "excluded preview occurrence is marked not applied");

        Path source = temporary.resolve("repair-selection-source.txt");
        Files.write(source, original.getBytes(StandardCharsets.UTF_8));
        RepairFileResult selected = new RepairFilePipeline().apply(source,
                temporary.resolve("repair-selection-derived.txt"),
                temporary.resolve("repair-selection-projection.bin"), rules, selection, 10);
        assertEquals(3L, selected.getCandidateMatchCount(), "selection candidate count");
        assertEquals(2L, selected.getMatchCount(), "selection applied count");
        assertEquals(3L, selected.getRuleCandidateCounts().get("primary"),
                "selection per-rule candidate count");
        assertEquals(2L, selected.getRuleMatchCounts().get("primary"),
                "selection per-rule applied count");
        assertEquals("他甲祂乙他", new String(Files.readAllBytes(selected.getDerivedFile()),
                StandardCharsets.UTF_8), "selected file output");
        try (DiskRepairProjection projection = DiskRepairProjection.open(
                selected.getProjectionFile())) {
            assertEquals(2L, projection.getMatchCount(),
                    "selection projection only records applied changes");
        }

        RepairSelection sameSelectionDifferentOrder = RepairSelection.excluding(Arrays.asList(
                new RepairOccurrence("primary", 2), new RepairOccurrence("missing", 99)));
        RepairSelection reversed = RepairSelection.excluding(Arrays.asList(
                new RepairOccurrence("missing", 99), new RepairOccurrence("primary", 2)));
        RepairFileResult first = new RepairFilePipeline().apply(source,
                temporary.resolve("repair-selection-order-a.txt"),
                temporary.resolve("repair-selection-order-a.bin"), rules,
                sameSelectionDifferentOrder, 0);
        RepairFileResult second = new RepairFilePipeline().apply(source,
                temporary.resolve("repair-selection-order-b.txt"),
                temporary.resolve("repair-selection-order-b.bin"), rules, reversed, 0);
        assertEquals(first.getRevisionId(), second.getRevisionId(),
                "selection revision ignores exclusion insertion order");
        assertTrue(!first.getRevisionId().equals(selected.getRevisionId()),
                "selection revision includes complete exclusion set");
    }

    private static void testRepairPreviewPagination(Path temporary) throws Exception {
        StringBuilder text = new StringBuilder();
        List<Integer> offsets = new ArrayList<Integer>();
        for (int index = 0; index < 55; index++) {
            offsets.add(text.length());
            text.append("祂").append(index).append('|');
        }
        Path source = temporary.resolve("repair-preview-pages.txt");
        Files.write(source, text.toString().getBytes(StandardCharsets.UTF_8));
        List<RepairRule> rules = Arrays.asList(
                new RepairRule("pronoun", "祂", "他", true, 10));
        RepairSelection selection = RepairSelection.excluding(Arrays.asList(
                new RepairOccurrence("pronoun", offsets.get(22))));

        Path candidateIndexPath = temporary.resolve("repair-preview-pages-candidates.bin");
        RepairFileResult pipelinePage = new RepairFilePipeline().apply(source,
                temporary.resolve("repair-preview-pages-derived.txt"),
                temporary.resolve("repair-preview-pages-projection.bin"),
                candidateIndexPath, rules, selection, 20, 20);
        assertEquals(55L, pipelinePage.getCandidateMatchCount(),
                "pipeline page preserves complete candidate count");
        assertEquals(20, pipelinePage.getPreviews().size(), "pipeline middle page size");
        assertEquals(offsets.get(20), pipelinePage.getPreviews().get(0).getOriginalStart(),
                "pipeline page starts at candidate offset");
        assertTrue(!pipelinePage.getPreviews().get(2).isApplied(),
                "pipeline page preserves exclusion state");
        RepairFileResult firstPipelinePage = new RepairFilePipeline().apply(source,
                temporary.resolve("repair-preview-first-derived.txt"),
                temporary.resolve("repair-preview-first-projection.bin"),
                rules, selection, 0, 20);
        assertEquals(firstPipelinePage.getRevisionId(), pipelinePage.getRevisionId(),
                "preview page offset does not change revision");
        assertEquals(firstPipelinePage.getDerivedSha256(), pipelinePage.getDerivedSha256(),
                "preview page offset does not change derived bytes");

        RepairFilePreviewPager pager = new RepairFilePreviewPager();
        RepairPreviewPage middle = pager.readPage(source, rules, selection, 20, 20);
        assertTrue(middle.hasPrevious(), "middle preview page has previous page");
        assertTrue(middle.hasNext(), "middle preview page has next page");
        assertEquals(20, middle.getMatches().size(), "middle preview page size");
        assertEquals(pipelinePage.getPreviews().get(0).getOriginalStart(),
                middle.getMatches().get(0).getOriginalStart(),
                "read-only pager matches pipeline page start");
        assertEquals(pipelinePage.getPreviews().get(19).getAfterContext(),
                middle.getMatches().get(19).getAfterContext(),
                "read-only pager matches pipeline page context");
        assertTrue(!middle.getMatches().get(2).isApplied(),
                "read-only pager preserves exclusion state");

        Path textIndexRoot = temporary.resolve("repair-preview-text-index");
        new DiskDocumentIndexBuilder().build(source, textIndexRoot, "candidate-source",
                pipelinePage.getSourceSha256(), null);
        DiskDocumentIndex textIndex = DiskDocumentIndex.openActive(textIndexRoot);
        try (DiskRepairCandidateIndex candidateIndex = DiskRepairCandidateIndex.open(
                candidateIndexPath, pipelinePage.getSourceSha256(), rules)) {
            assertEquals(55L, candidateIndex.getCandidateCount(),
                    "candidate index stores complete count");
            RepairPreviewPage indexedMiddle = candidateIndex.readPage(
                    textIndex, selection, 20, 20);
            assertEquals(middle.getMatches().get(0).getOriginalStart(),
                    indexedMiddle.getMatches().get(0).getOriginalStart(),
                    "candidate index matches sequential page start");
            assertEquals(middle.getMatches().get(19).getAfterContext(),
                    indexedMiddle.getMatches().get(19).getAfterContext(),
                    "candidate index matches sequential page context");
            assertTrue(!indexedMiddle.getMatches().get(2).isApplied(),
                    "candidate index preserves exclusion state");
            List<RepairOccurrence> rangeOccurrences = candidateIndex.readOccurrences(19, 3);
            assertEquals(3, rangeOccurrences.size(), "candidate range occurrence count");
            assertEquals((long) indexedMiddle.getMatches().get(0).getOriginalStart(),
                    rangeOccurrences.get(1).getOriginalOffset(),
                    "candidate range occurrence uses global ordinal");
            RepairPreviewPage overflowPage = candidateIndex.readPage(
                    textIndex, selection, Long.MAX_VALUE, 20);
            assertEquals(0, overflowPage.getMatches().size(),
                    "candidate page offset overflow is safely empty");
            assertTrue(!overflowPage.hasNext(),
                    "candidate page offset overflow has no next page");
        }

        RepairPreviewPage last = pager.readPage(source, rules, selection, 40, 20);
        assertEquals(15, last.getMatches().size(), "last preview page size");
        assertTrue(last.hasPrevious(), "last preview page has previous page");
        assertTrue(!last.hasNext(), "last preview page has no next page");
        assertEquals(offsets.get(54), last.getMatches().get(14).getOriginalStart(),
                "last preview page reaches final candidate");
        RepairPreviewPage beyond = pager.readPage(source, rules, selection, 60, 20);
        assertEquals(0, beyond.getMatches().size(), "beyond-end page is empty");
        assertTrue(!beyond.hasNext(), "beyond-end page has no next page");

        boolean ruleMismatchRejected = false;
        try {
            DiskRepairCandidateIndex.open(candidateIndexPath,
                    pipelinePage.getSourceSha256(), Arrays.asList(
                            new RepairRule("pronoun", "祂", "她", true, 10))).close();
        } catch (IOException expected) {
            ruleMismatchRejected = true;
        }
        assertTrue(ruleMismatchRejected, "candidate index rejects rule signature mismatch");
        byte[] candidateBytes = Files.readAllBytes(candidateIndexPath);
        Path truncated = temporary.resolve("repair-preview-candidates-truncated.bin");
        Files.write(truncated, Arrays.copyOf(candidateBytes, candidateBytes.length - 1));
        boolean truncatedRejected = false;
        try {
            DiskRepairCandidateIndex.open(truncated,
                    pipelinePage.getSourceSha256(), rules).close();
        } catch (IOException expected) {
            truncatedRejected = true;
        }
        assertTrue(truncatedRejected, "candidate index rejects truncation");
        byte[] corruptedBytes = Arrays.copyOf(candidateBytes, candidateBytes.length);
        corruptedBytes[DiskRepairCandidateIndex.HEADER_BYTES] ^= 1;
        Path corrupted = temporary.resolve("repair-preview-candidates-corrupted.bin");
        Files.write(corrupted, corruptedBytes);
        boolean corruptionRejected = false;
        try (DiskRepairCandidateIndex candidateIndex = DiskRepairCandidateIndex.open(
                corrupted, pipelinePage.getSourceSha256(), rules)) {
            candidateIndex.readPage(textIndex, selection, 0, 1);
        } catch (IOException expected) {
            corruptionRejected = true;
        }
        assertTrue(corruptionRejected, "candidate index rejects record corruption");
        byte[] corruptedIndexBytes = Arrays.copyOf(candidateBytes, candidateBytes.length);
        long candidateIndexOffset = ByteBuffer.wrap(corruptedIndexBytes,
                DiskRepairCandidateIndex.LEGACY_HEADER_BYTES + 8, Long.BYTES).getLong();
        corruptedIndexBytes[(int) candidateIndexOffset] ^= 1;
        Path corruptedIndex = temporary.resolve("repair-preview-index-corrupted.bin");
        Files.write(corruptedIndex, corruptedIndexBytes);
        boolean indexCorruptionRejected = false;
        try {
            DiskRepairCandidateIndex.open(corruptedIndex,
                    pipelinePage.getSourceSha256(), rules).close();
        } catch (IOException expected) {
            indexCorruptionRejected = true;
        }
        assertTrue(indexCorruptionRejected, "candidate index rejects block index corruption");

        StringBuilder compactText = new StringBuilder();
        List<Integer> compactOffsets = new ArrayList<Integer>();
        for (int index = 0; index < 300; index++) {
            compactOffsets.add(compactText.length());
            compactText.append("祂|");
        }
        Path compactSource = temporary.resolve("repair-preview-compact.txt");
        Path compactCandidates = temporary.resolve("repair-preview-compact-candidates.bin");
        Files.write(compactSource, compactText.toString().getBytes(StandardCharsets.UTF_8));
        RepairFileResult compactResult = new RepairFilePipeline().apply(compactSource,
                temporary.resolve("repair-preview-compact-derived.txt"),
                temporary.resolve("repair-preview-compact-projection.bin"), compactCandidates,
                rules, RepairSelection.all(), 255, 3);
        assertTrue(Files.size(compactCandidates)
                        < DiskRepairCandidateIndex.LEGACY_HEADER_BYTES
                                + 300L * DiskRepairCandidateIndex.LEGACY_RECORD_BYTES / 4,
                "compressed candidate index is at least four times smaller than v1");
        Path compactTextIndexRoot = temporary.resolve("repair-preview-compact-text-index");
        new DiskDocumentIndexBuilder().build(compactSource, compactTextIndexRoot,
                "compact-source", compactResult.getSourceSha256(), null);
        try (DiskRepairCandidateIndex candidateIndex = DiskRepairCandidateIndex.open(
                compactCandidates, compactResult.getSourceSha256(), rules)) {
            RepairPreviewPage compactPage = candidateIndex.readPage(
                    DiskDocumentIndex.openActive(compactTextIndexRoot),
                    RepairSelection.all(), 255, 3);
            assertEquals(compactOffsets.get(255),
                    compactPage.getMatches().get(0).getOriginalStart(),
                    "compressed candidate index maps across block boundary");
            assertEquals(compactOffsets.get(257),
                    compactPage.getMatches().get(2).getOriginalStart(),
                    "compressed candidate index preserves global ordinal");
        }

        Path legacyCandidates = temporary.resolve("repair-preview-legacy-candidates.bin");
        try (DataOutputStream output = new DataOutputStream(
                Files.newOutputStream(legacyCandidates))) {
            output.writeInt(DiskRepairCandidateIndex.MAGIC);
            output.writeInt(DiskRepairCandidateIndex.LEGACY_VERSION);
            output.writeLong(1L);
            output.write(pipelinePage.getSourceSha256().getBytes(StandardCharsets.US_ASCII));
            output.write(DiskRepairCandidateIndex.rulesSignatureEnabled(rules)
                    .getBytes(StandardCharsets.US_ASCII));
            output.writeLong(offsets.get(0));
            output.writeInt(1);
            output.writeInt(0);
            output.writeInt(DiskRepairCandidateIndex.recordChecksum(
                    0, offsets.get(0), 1, 0));
        }
        try (DiskRepairCandidateIndex candidateIndex = DiskRepairCandidateIndex.open(
                legacyCandidates, pipelinePage.getSourceSha256(), rules)) {
            assertEquals(offsets.get(0).longValue(),
                    candidateIndex.readOccurrences(0, 1).get(0).getOriginalOffset(),
                    "legacy candidate index remains readable");
        }

        Path longSource = temporary.resolve("repair-preview-long-rule.txt");
        String longMatch = repeat('长', RepairRule.MAXIMUM_FIELD_CHARACTERS);
        Files.write(longSource, ("前" + longMatch + "后").getBytes(StandardCharsets.UTF_8));
        List<RepairRule> longRules = Arrays.asList(
                new RepairRule("long", longMatch, "短", true, 1));
        Path longCandidates = temporary.resolve("repair-preview-long-candidates.bin");
        RepairFileResult longResult = new RepairFilePipeline().apply(longSource,
                temporary.resolve("repair-preview-long-derived.txt"),
                temporary.resolve("repair-preview-long-projection.bin"), longCandidates,
                longRules, RepairSelection.all(), 0, 1);
        Path longTextIndexRoot = temporary.resolve("repair-preview-long-text-index");
        new DiskDocumentIndexBuilder().build(longSource, longTextIndexRoot, "long-source",
                longResult.getSourceSha256(), null);
        try (DiskRepairCandidateIndex candidateIndex = DiskRepairCandidateIndex.open(
                longCandidates, longResult.getSourceSha256(), longRules)) {
            RepairPreviewPage longPage = candidateIndex.readPage(
                    DiskDocumentIndex.openActive(longTextIndexRoot), RepairSelection.all(), 0, 1);
            assertEquals(longMatch, longPage.getMatches().get(0).getMatchedText(),
                    "candidate index supports maximum-length literal rules");
        }
    }

    private static void testRepairOrdinalRange() {
        RepairOrdinalRange range = RepairOrdinalRange.fromOneBased(2, 4, 5, 3);
        assertEquals(1L, range.getZeroBasedOffset(), "one-based range offset");
        assertEquals(3, range.getCount(), "one-based range count");
        assertEquals(2L, range.getFirstOneBased(), "one-based range first");
        assertEquals(4L, range.getLastOneBased(), "one-based range last");
        assertIllegalArgument(() -> RepairOrdinalRange.fromOneBased(0, 1, 5, 3),
                "range rejects zero start");
        assertIllegalArgument(() -> RepairOrdinalRange.fromOneBased(4, 3, 5, 3),
                "range rejects reversed endpoints");
        assertIllegalArgument(() -> RepairOrdinalRange.fromOneBased(1, 6, 5, 6),
                "range rejects endpoint after total");
        assertIllegalArgument(() -> RepairOrdinalRange.fromOneBased(1, 4, 5, 3),
                "range enforces operation limit");
    }

    private static void testRepairRulePackAndMerge() throws Exception {
        List<RepairRule> exported = Arrays.asList(
                new RepairRule("global/人称", "祂\t", "他\n", true, 20,
                        RepairScope.ALL_BOOKS, "特殊字符😀"),
                new RepairRule("book-delete", "广告", "", false, 10,
                        RepairScope.CURRENT_BOOK, ""));
        RepairRulePackCodec codec = new RepairRulePackCodec();
        byte[] pack = codec.encode(exported);
        List<RepairRule> decoded = codec.decode(pack);
        assertEquals(2, decoded.size(), "rule pack count");
        assertEquals("book-delete", decoded.get(0).getId(), "rule pack order");
        assertEquals("", decoded.get(0).getReplacement(), "rule pack empty replacement");
        assertTrue(!decoded.get(0).isEnabled(), "rule pack enabled state");
        assertEquals("祂\t", decoded.get(1).getMatchText(), "rule pack tab field");
        assertEquals("他\n", decoded.get(1).getReplacement(), "rule pack newline field");
        assertEquals("特殊字符😀", decoded.get(1).getNote(), "rule pack Unicode note");
        List<RepairRule> reversed = new ArrayList<RepairRule>(exported);
        Collections.reverse(reversed);
        assertArrayEquals(pack, codec.encode(reversed), "rule pack encoding is deterministic");

        byte[] tampered = Arrays.copyOf(pack, pack.length);
        tampered[25] ^= 1;
        boolean tamperRejected = false;
        try {
            codec.decode(tampered);
        } catch (IOException expected) {
            tamperRejected = true;
        }
        assertTrue(tamperRejected, "rule pack checksum rejects tampering");

        List<RepairRule> existing = Arrays.asList(
                new RepairRule("same", "旧", "原", true, 10),
                new RepairRule("keep", "甲", "乙", true, 20));
        List<RepairRule> imported = Arrays.asList(
                new RepairRule("same", "新", "替", false, 1,
                        RepairScope.ALL_BOOKS, "替换冲突"),
                new RepairRule("added", "丙", "丁", true, 2));
        RepairRuleMerger merger = new RepairRuleMerger();
        RepairRuleMergeResult kept = merger.merge(existing, imported,
                RepairRuleMergePolicy.KEEP_EXISTING);
        assertEquals(1, kept.getAdded(), "rule merge added count");
        assertEquals(0, kept.getReplaced(), "keep merge replaced count");
        assertEquals(1, kept.getSkipped(), "keep merge skipped count");
        assertEquals("旧", kept.getRules().get(0).getMatchText(),
                "keep merge preserves existing collision");
        RepairRuleMergeResult replaced = merger.merge(existing, imported,
                RepairRuleMergePolicy.REPLACE_EXISTING);
        assertEquals(1, replaced.getReplaced(), "replace merge collision count");
        assertEquals("新", replaced.getRules().get(0).getMatchText(),
                "replace merge uses imported collision");
        assertEquals(10, replaced.getRules().get(0).getOrder(),
                "merge normalizes deterministic order");
        assertEquals(30, replaced.getRules().get(2).getOrder(),
                "merge appends new rules deterministically");

        boolean oversizedFieldRejected = false;
        try {
            new RepairRule("oversized", repeat('甲', 4097), "", true, 1);
        } catch (IllegalArgumentException expected) {
            oversizedFieldRejected = true;
        }
        assertTrue(oversizedFieldRejected, "rule field limit is enforced at construction");

        List<RepairRule> tooMany = new ArrayList<RepairRule>();
        for (int index = 0; index <= RepairRuleMerger.MAXIMUM_RULES; index++) {
            tooMany.add(new RepairRule("limit-" + index, "词" + index, "值", true, index));
        }
        boolean mergedLimitRejected = false;
        try {
            merger.merge(Collections.emptyList(), tooMany,
                    RepairRuleMergePolicy.REPLACE_EXISTING);
        } catch (IllegalArgumentException expected) {
            mergedLimitRejected = true;
        }
        assertTrue(mergedLimitRejected, "merged applicable rule count is capped");
    }

    private static void testExportRecoveryJournal(Path temporary) throws Exception {
        Path journalPath = temporary.resolve("export-recovery/pending.bin");
        ExportRecoveryJournal journal = new ExportRecoveryJournal();
        ExportRecoveryJournal.Entry pending = new ExportRecoveryJournal.Entry(
                ExportRecoveryJournal.Kind.CLEAN_TEXT, repeat('a', 64),
                "jingdu-cleaned.txt", 123456L, 1720000000000L);
        journal.writePending(journalPath, pending);
        ExportRecoveryJournal.Entry restored = journal.readPending(journalPath);
        assertEquals(ExportRecoveryJournal.Kind.CLEAN_TEXT, restored.getKind(),
                "export recovery kind");
        assertEquals(repeat('a', 64), restored.getSourceToken(),
                "export recovery source token");
        assertEquals("jingdu-cleaned.txt", restored.getDisplayName(),
                "export recovery display name");
        assertEquals(123456L, restored.getExpectedBytes(), "export recovery expected bytes");
        assertEquals(1720000000000L, restored.getStartedAtEpochMillis(),
                "export recovery start time");

        byte[] damaged = Files.readAllBytes(journalPath);
        damaged[damaged.length - 9] ^= 1;
        Files.write(journalPath, damaged);
        boolean damageRejected = false;
        try {
            journal.readPending(journalPath);
        } catch (IOException expected) {
            damageRejected = true;
        }
        assertTrue(damageRejected, "export recovery checksum rejects damage");

        journal.writePending(journalPath, new ExportRecoveryJournal.Entry(
                ExportRecoveryJournal.Kind.RULE_PACK, "pack-token", "jingdu-rules.jdrp",
                42L, 1720000000001L));
        journal.clear(journalPath);
        assertEquals(null, journal.readPending(journalPath),
                "verified export clears recovery journal");

        assertIllegalArgument(() -> new ExportRecoveryJournal.Entry(
                ExportRecoveryJournal.Kind.CLEAN_TEXT, "", "x.txt", 1L, 1L),
                "empty export source token is rejected");
        assertIllegalArgument(() -> new ExportRecoveryJournal.Entry(
                ExportRecoveryJournal.Kind.CLEAN_TEXT, "token", "x.txt", -1L, 1L),
                "negative expected export bytes are rejected");
        assertIllegalArgument(() -> new ExportRecoveryJournal.Entry(
                ExportRecoveryJournal.Kind.CLEAN_TEXT, "token", "bad\nname.txt", 1L, 1L),
                "control characters in export display name are rejected");
    }

    private static void testCompanionSleepTimer() {
        CompanionSleepTimer timer = new CompanionSleepTimer();
        assertEquals(CompanionSleepTimer.Mode.OFF, timer.getMode(), "sleep timer starts off");

        timer.armForDuration(1_000L, 15L * 60L * 1000L);
        assertEquals(CompanionSleepTimer.Mode.DEADLINE, timer.getMode(),
                "duration timer mode");
        assertEquals(15L * 60L * 1000L, timer.remainingMillis(1_000L),
                "duration timer initial remainder");
        assertTrue(!timer.consumeIfExpired(900_999L, 0),
                "duration timer remains armed before deadline");
        assertTrue(timer.consumeIfExpired(901_000L, 0),
                "duration timer expires at deadline");
        assertEquals(CompanionSleepTimer.Mode.OFF, timer.getMode(),
                "duration timer disarms after expiry");
        assertTrue(!timer.consumeIfExpired(901_001L, 0),
                "duration timer expiry is consumed once");

        timer.armForChapterEnd(120L, 500L);
        assertEquals(CompanionSleepTimer.Mode.CHAPTER_END, timer.getMode(),
                "chapter timer mode");
        assertTrue(!timer.consumeIfExpired(0L, 499L),
                "chapter timer remains armed before boundary");
        assertTrue(timer.consumeIfExpired(0L, 500L),
                "chapter timer expires at boundary");

        timer.armForDuration(0L, 1L);
        timer.cancel();
        assertEquals(CompanionSleepTimer.Mode.OFF, timer.getMode(),
                "manual cancel disarms timer");
        assertIllegalArgument(() -> timer.armForDuration(0L, 0L),
                "zero duration is rejected");
        assertIllegalArgument(() -> timer.armForDuration(Long.MAX_VALUE, 1L),
                "deadline overflow is rejected");
        assertIllegalArgument(() -> timer.armForChapterEnd(20L, 20L),
                "chapter boundary at current anchor is rejected");
    }

    private static void testBookLibraryStore(Path temporary) throws Exception {
        Path libraryPath = temporary.resolve("library/books.bin");
        BookLibraryStore store = new BookLibraryStore();
        String firstId = repeat('a', 64);
        String firstViewRevision = repeat('1', 64);
        String secondId = repeat('b', 64);
        BookLibraryEntry first = new BookLibraryEntry(firstId, "第一本.txt",
                "book-a.txt", firstViewRevision, "book-a.txt", firstViewRevision,
                "", "UTF-8",
                1024L, 1000L, 2000L, 12);
        BookLibraryEntry second = new BookLibraryEntry(secondId, "第二本.txt",
                "book-b.txt", secondId, "book-b-clean.txt", repeat('c', 64),
                "book-b.projection", "GB18030", 2048L, 1100L, 3000L, 34);
        second = second.withShelved(false, 3001L);

        store.save(libraryPath, Arrays.asList(first, second));
        List<BookLibraryEntry> loaded = store.load(libraryPath);
        assertEquals(2, loaded.size(), "book library entry count");
        assertEquals(secondId, loaded.get(0).getBookId(),
                "book library is ordered by recent reading");
        assertEquals("GB18030", loaded.get(0).getEncodingName(),
                "book library preserves file metadata");
        assertEquals(34, loaded.get(0).getAnchorOffset(),
                "book library preserves reading anchor");
        assertEquals(firstViewRevision, store.find(loaded, firstId).getBaseRevision(),
                "stable source identity is independent from decoded view revision");
        assertTrue(loaded.get(0).isShelved(),
                "v1 book library remains downgrade-readable and defaults to shelved");

        Path shelfStatePath = temporary.resolve("library/shelf-state.bin");
        BookShelfStateStore shelfStateStore = new BookShelfStateStore();
        shelfStateStore.save(shelfStatePath,
                new java.util.LinkedHashSet<String>(Arrays.asList(secondId)));
        assertEquals(new java.util.LinkedHashSet<String>(Arrays.asList(secondId)),
                shelfStateStore.load(shelfStatePath),
                "removed-from-shelf state is stored separately from v1 metadata");

        BookLibraryEntry progressed = first.withProgress(88, 4000L);
        List<BookLibraryEntry> updated = store.upsert(loaded, progressed);
        assertEquals(firstId, updated.get(0).getBookId(),
                "progress update moves book to recent position");
        assertEquals(88, store.find(updated, firstId).getAnchorOffset(),
                "progress update replaces the matching book only");
        assertEquals(1, store.remove(updated, secondId).size(),
                "permanent removal only drops the selected entry");
        store.save(libraryPath, updated);

        Path legacyPath = temporary.resolve("library/books-v1.bin");
        writeLegacyBookLibrary(legacyPath, first);
        List<BookLibraryEntry> legacy = store.load(legacyPath);
        assertEquals(1, legacy.size(), "v1 book library remains readable");
        assertTrue(legacy.get(0).isShelved(),
                "v1 book library entries default to shelved");

        boolean duplicateRejected = false;
        try {
            store.save(libraryPath, Arrays.asList(first, first));
        } catch (IllegalArgumentException expected) {
            duplicateRejected = true;
        }
        assertTrue(duplicateRejected, "duplicate book ids are rejected");
        assertEquals(88, store.find(store.load(libraryPath), firstId).getAnchorOffset(),
                "invalid save does not overwrite the last valid library");

        boolean invalidShelfIdRejected = false;
        try {
            shelfStateStore.save(shelfStatePath,
                    new java.util.LinkedHashSet<String>(Arrays.asList("../bad")));
        } catch (IllegalArgumentException expected) {
            invalidShelfIdRejected = true;
        }
        assertTrue(invalidShelfIdRejected, "invalid hidden book ids are rejected");
        assertEquals(new java.util.LinkedHashSet<String>(Arrays.asList(secondId)),
                shelfStateStore.load(shelfStatePath),
                "invalid shelf-state save preserves the last valid state");

        assertIllegalArgument(() -> new BookLibraryEntry(firstId, "bad/path.txt",
                "../book.txt", firstId, "book.txt", firstId, "", "UTF-8",
                1L, 1L, 1L, 0), "path traversal in private file name is rejected");
        assertIllegalArgument(() -> first.withProgress(-1, 5000L),
                "negative reading anchor is rejected");
        assertIllegalArgument(() -> new BookLibraryEntry(firstId, "x.txt",
                "book.txt", firstId, "book.txt", firstId, null, "UTF-8",
                1L, 1L, 1L, 0), "null projection file name is rejected");

        byte[] damaged = Files.readAllBytes(libraryPath);
        damaged[damaged.length - 1] ^= 0x01;
        Files.write(libraryPath, damaged);
        boolean damageRejected = false;
        try {
            store.load(libraryPath);
        } catch (IOException expected) {
            damageRejected = true;
        }
        assertTrue(damageRejected, "book library checksum rejects damage");

        byte[] damagedShelfState = Files.readAllBytes(shelfStatePath);
        damagedShelfState[damagedShelfState.length - 1] ^= 0x01;
        Files.write(shelfStatePath, damagedShelfState);
        boolean shelfStateDamageRejected = false;
        try {
            shelfStateStore.load(shelfStatePath);
        } catch (IOException expected) {
            shelfStateDamageRejected = true;
        }
        assertTrue(shelfStateDamageRejected, "shelf-state checksum rejects damage");
    }

    private static void testBookDeletionJournal(Path temporary) throws Exception {
        Path journalPath = temporary.resolve("library/delete-pending.bin");
        String bookId = repeat('d', 64);
        String activeRevision = repeat('e', 64);
        BookDeletionJournal journal = new BookDeletionJournal();
        BookDeletionJournal.Entry pending = new BookDeletionJournal.Entry(
                bookId, Arrays.asList("book-1.utf8.txt", "repair-a.utf8.txt",
                        "repair-a.projection.bin"),
                Arrays.asList(bookId, activeRevision), 5000L);
        journal.writePending(journalPath, pending);
        BookDeletionJournal.Entry restored = journal.readPending(journalPath);
        assertEquals(bookId, restored.getBookId(), "deletion journal book id");
        assertEquals(3, restored.getFileNames().size(), "deletion journal file count");
        assertEquals(2, restored.getIndexRevisions().size(),
                "deletion journal revision count");
        String remainingId = repeat('f', 64);
        BookLibraryEntry remaining = new BookLibraryEntry(remainingId, "保留.txt",
                "book-2.utf8.txt", remainingId, "repair-a.utf8.txt", activeRevision,
                "repair-b.projection.bin", "UTF-8", 10L, 1L, 2L, 0);
        BookDeletionPolicy.Plan plan = new BookDeletionPolicy().resolve(
                restored, Arrays.asList(remaining));
        assertEquals(Arrays.asList("book-1.utf8.txt", "repair-a.projection.bin"),
                plan.getFileNames(), "deletion plan preserves shared active file");
        assertEquals(Arrays.asList(bookId), plan.getIndexRevisions(),
                "deletion plan preserves shared active index revision");
        BookLibraryEntry stillPresent = new BookLibraryEntry(bookId, "待删.txt",
                "book-1.utf8.txt", bookId, "book-1.utf8.txt", bookId, "", "UTF-8",
                1L, 1L, 1L, 0);
        assertIllegalArgument(() -> new BookDeletionPolicy().resolve(
                restored, Arrays.asList(stillPresent)),
                "deletion plan requires catalog removal first");

        assertIllegalArgument(() -> new BookDeletionJournal.Entry(bookId,
                Arrays.asList("../book.txt"), Arrays.asList(bookId), 1L),
                "deletion journal rejects path traversal");
        assertIllegalArgument(() -> new BookDeletionJournal.Entry(bookId,
                Arrays.asList("book-1.utf8.txt", "book-1.utf8.txt"),
                Arrays.asList(bookId), 1L),
                "deletion journal rejects duplicate files");
        assertIllegalArgument(() -> new BookDeletionJournal.Entry(bookId,
                Arrays.asList("library.bin"), Arrays.asList(bookId), 1L),
                "deletion journal rejects metadata targets");

        byte[] damaged = Files.readAllBytes(journalPath);
        damaged[damaged.length - 1] ^= 0x01;
        Files.write(journalPath, damaged);
        boolean rejected = false;
        try {
            journal.readPending(journalPath);
        } catch (IOException expected) {
            rejected = true;
        }
        assertTrue(rejected, "deletion journal checksum rejects damage");

        journal.writePending(journalPath, pending);
        journal.clear(journalPath);
        assertEquals(null, journal.readPending(journalPath),
                "completed deletion clears recovery journal");
    }

    private static void testBookBookmarkStore(Path temporary) throws Exception {
        Path bookmarkPath = temporary.resolve("library/bookmarks.bin");
        BookBookmarkStore store = new BookBookmarkStore();
        String firstBook = repeat('a', 64);
        String secondBook = repeat('b', 64);
        BookBookmark later = new BookBookmark(repeat('1', 32), firstBook,
                90, "第九十字 · 后一段", 2000L);
        BookBookmark earlier = new BookBookmark(repeat('2', 32), firstBook,
                10, "第十字 · 开头", 1000L);
        BookBookmark other = new BookBookmark(repeat('3', 32), secondBook,
                20, "另一本书", 3000L);

        store.save(bookmarkPath, Arrays.asList(later, other, earlier));
        List<BookBookmark> loaded = store.load(bookmarkPath);
        assertEquals(3, loaded.size(), "bookmark catalog round trip");
        List<BookBookmark> firstBookBookmarks = store.forBook(loaded, firstBook);
        assertEquals(2, firstBookBookmarks.size(), "book bookmark filtering");
        assertEquals(10, firstBookBookmarks.get(0).getOriginalAnchorOffset(),
                "bookmarks are ordered by immutable base anchor");
        assertEquals("第九十字 · 后一段", firstBookBookmarks.get(1).getLabel(),
                "bookmark label is preserved");
        assertEquals(2, store.remove(loaded, earlier.getBookmarkId()).size(),
                "single bookmark removal preserves other entries");
        assertEquals(1, store.removeBook(loaded, firstBook).size(),
                "book deletion removes only that book's bookmarks");
        assertIllegalArgument(() -> store.requireBookProfile(
                Arrays.asList(earlier, other), firstBook),
                "per-book bookmark profile rejects cross-book contamination");

        boolean duplicateRejected = false;
        try {
            store.save(bookmarkPath, Arrays.asList(earlier,
                    new BookBookmark(earlier.getBookmarkId(), secondBook,
                            1, "重复 ID", 4000L)));
        } catch (IllegalArgumentException expected) {
            duplicateRejected = true;
        }
        assertTrue(duplicateRejected, "duplicate bookmark ids are rejected");
        assertEquals(3, store.load(bookmarkPath).size(),
                "invalid bookmark save preserves last valid catalog");
        assertIllegalArgument(() -> new BookBookmark(repeat('4', 32), firstBook,
                -1, "非法锚点", 1L), "negative bookmark anchor is rejected");
        assertIllegalArgument(() -> new BookBookmark(repeat('4', 32), firstBook,
                1, "bad\nlabel", 1L), "bookmark control characters are rejected");
        assertIllegalArgument(() -> new BookBookmark(repeat('4', 32), firstBook,
                1, "bad\uD83D", 1L), "unpaired bookmark surrogate is rejected");

        List<BookBookmark> overLimit = new ArrayList<BookBookmark>();
        for (int index = 0; index <= 200; index++) {
            overLimit.add(new BookBookmark(String.format("%032x", index + 1000),
                    firstBook, index, "书签 " + index, index + 1L));
        }
        boolean perBookLimitRejected = false;
        try {
            store.save(bookmarkPath, overLimit);
        } catch (IllegalArgumentException expected) {
            perBookLimitRejected = true;
        }
        assertTrue(perBookLimitRejected, "per-book bookmark limit is enforced");

        byte[] damaged = Files.readAllBytes(bookmarkPath);
        damaged[damaged.length - 1] ^= 0x01;
        Files.write(bookmarkPath, damaged);
        boolean damageRejected = false;
        try {
            store.load(bookmarkPath);
        } catch (IOException expected) {
            damageRejected = true;
        }
        assertTrue(damageRejected, "bookmark checksum rejects damage");
    }

    private static void testChapterOutlineStore(Path temporary) throws Exception {
        String revision = repeat('7', 64);
        ChapterOutline detected = ChapterOutline.fromDetected(revision, Arrays.asList(
                new ChapterEntry("第一章", 0, 90),
                new ChapterEntry("第二章", 20, 90),
                new ChapterEntry("第三章", 40, 90)));
        assertEquals(ChapterOutlineEntry.Origin.AUTO_DETECTED,
                detected.getEntries().get(1).getOrigin(),
                "detected chapter records automatic origin");

        ChapterOutline renamed = detected.rename(1, "第二章 · 新标题");
        assertEquals("第二章 · 新标题", renamed.getEntries().get(1).getTitle(),
                "chapter rename changes only the title");
        assertEquals(20, renamed.getEntries().get(1).getOriginalCharacterOffset(),
                "chapter rename preserves original anchor");
        assertEquals(100, renamed.getEntries().get(1).getConfidencePercent(),
                "manual rename is explicitly confirmed");

        ChapterOutline split = renamed.split(30, "第二章下");
        assertEquals(4, split.getEntries().size(), "chapter split adds a boundary");
        assertEquals(30, split.getEntries().get(2).getOriginalCharacterOffset(),
                "chapter split keeps sorted original anchors");
        ChapterOutline merged = split.mergeWithNext(1);
        assertEquals(3, merged.getEntries().size(), "chapter merge removes next boundary");
        assertEquals(40, merged.getEntries().get(2).getOriginalCharacterOffset(),
                "chapter merge retains following chapters");

        Path outlinePath = temporary.resolve("chapters/book.bin");
        ChapterOutlineStore store = new ChapterOutlineStore();
        assertTrue(store.load(temporary.resolve("chapters/missing.bin")) == null,
                "missing chapter outline has no manual profile");
        store.save(outlinePath, merged);
        ChapterOutline restored = store.load(outlinePath);
        assertEquals(revision, restored.getBaseRevision(),
                "chapter outline remains bound to base revision");
        assertEquals("第二章 · 新标题", restored.getEntries().get(1).getTitle(),
                "chapter outline round trip");
        assertEquals(ChapterOutlineEntry.Origin.MANUAL,
                restored.getEntries().get(1).getOrigin(),
                "chapter outline preserves manual origin");
        ChapterOutlineMapper mapper = new ChapterOutlineMapper();
        List<ChapterEntry> shifted = mapper.map(restored, offset -> offset + 5L);
        assertEquals(25, shifted.get(1).getCharacterOffset(),
                "chapter outline maps original anchors into active view");
        boolean invalidMappingRejected = false;
        try {
            mapper.map(restored, offset -> 100L - offset);
        } catch (IOException expected) {
            invalidMappingRejected = true;
        }
        assertTrue(invalidMappingRejected,
                "chapter outline rejects non-monotonic active mapping");

        assertIllegalArgument(() -> detected.split(20, "重复边界"),
                "chapter split rejects duplicate boundary");
        assertIllegalArgument(() -> detected.mergeWithNext(2),
                "last chapter cannot merge with next");
        assertIllegalArgument(() -> detected.rename(0, "bad\nname"),
                "chapter title rejects controls");
        assertIllegalArgument(() -> detected.rename(0, " 前言"),
                "chapter title rejects non-canonical whitespace");
        assertIllegalArgument(() -> new ChapterOutlineEntry("手动", 1, 99,
                        ChapterOutlineEntry.Origin.MANUAL),
                "manual chapter confidence is deterministic");
        assertIllegalArgument(() -> new ChapterOutline(revision, Arrays.asList(
                        new ChapterOutlineEntry("后", 10, 90,
                                ChapterOutlineEntry.Origin.AUTO_DETECTED),
                        new ChapterOutlineEntry("前", 5, 90,
                                ChapterOutlineEntry.Origin.AUTO_DETECTED))),
                "chapter outline rejects unsorted anchors");

        byte[] damaged = Files.readAllBytes(outlinePath);
        damaged[damaged.length - 1] ^= 0x01;
        Files.write(outlinePath, damaged);
        boolean damageRejected = false;
        try {
            store.load(outlinePath);
        } catch (IOException expected) {
            damageRejected = true;
        }
        assertTrue(damageRejected, "chapter outline checksum rejects damage");
    }

    private static void testReaderAppearanceContract() {
        ReaderAppearance defaults = ReaderAppearance.defaults();
        String encoded = defaults.toTypographyJson();
        ReaderAppearance decoded = ReaderAppearance.fromTypographyJson(encoded);
        assertEquals(ReaderAppearance.Theme.DAY, decoded.getTheme(),
                "reader appearance theme round trip");
        assertEquals(ReaderAppearance.FontFamily.SYSTEM_SANS, decoded.getFontFamily(),
                "reader appearance font round trip");
        assertEquals(20, decoded.getTextSizeSp(),
                "reader appearance text size round trip");
        assertEquals(145, decoded.getLineHeightPercent(),
                "reader appearance line height round trip");
        assertEquals(8, decoded.getParagraphSpacingDp(),
                "reader appearance paragraph spacing round trip");
        assertEquals(12, decoded.getHorizontalMarginDp(),
                "reader appearance margin round trip");
        assertEquals(encoded, decoded.toTypographyJson(),
                "reader appearance encoding is deterministic");

        for (ReaderAppearance.Theme theme : ReaderAppearance.Theme.values()) {
            assertTrue(ReaderAppearance.contrastRatio(
                    theme.getForegroundArgb(), theme.getBackgroundArgb()) >= 4.5,
                    "reader theme meets WCAG normal-text contrast: " + theme);
        }
        ReaderAppearance large = new ReaderAppearance(
                ReaderAppearance.Theme.NIGHT, ReaderAppearance.FontFamily.SERIF,
                32, 200, 12, 32);
        assertEquals(2.0f, large.getLineHeightMultiplier(),
                "large-text accessibility option");
        assertEquals(ReaderAppearance.FontFamily.SERIF, large.getFontFamily(),
                "reader serif option");

        String legacy = "{\"theme\":\"EYE\",\"textSizeSp\":18,"
                + "\"lineHeightPercent\":120,\"horizontalMarginDp\":8}";
        ReaderAppearance migrated = ReaderAppearance.fromTypographyJson(legacy);
        assertEquals(ReaderAppearance.FontFamily.SYSTEM_SANS, migrated.getFontFamily(),
                "legacy appearance migrates to system sans");
        assertEquals(0, migrated.getParagraphSpacingDp(),
                "legacy appearance preserves zero paragraph spacing");
        assertTrue(!legacy.equals(migrated.toTypographyJson()),
                "legacy appearance is upgraded on next encoding");

        int[] sizes = ReaderAppearance.textSizeOptionsSp();
        sizes[0] = 999;
        assertEquals(16, ReaderAppearance.textSizeOptionsSp()[0],
                "reader appearance options are defensive copies");
        int[] paragraphSpacing = ReaderAppearance.paragraphSpacingOptionsDp();
        paragraphSpacing[0] = 999;
        assertEquals(0, ReaderAppearance.paragraphSpacingOptionsDp()[0],
                "paragraph spacing options are defensive copies");
        assertIllegalArgument(() -> new ReaderAppearance(
                ReaderAppearance.Theme.DAY, 15, 145, 12),
                "unsupported text size is rejected");
        assertIllegalArgument(() -> ReaderAppearance.fromTypographyJson(
                "{\"theme\":\"DAY\",\"textSizeSp\":20,\"horizontalMarginDp\":12,"
                        + "\"lineHeightPercent\":145}"),
                "reordered reader appearance contract is rejected");
        assertIllegalArgument(() -> ReaderAppearance.fromTypographyJson(
                "{\"theme\":\"SYSTEM\",\"textSizeSp\":20,"
                        + "\"lineHeightPercent\":145,\"horizontalMarginDp\":12}"),
                "unknown reader theme is rejected");
        assertIllegalArgument(() -> ReaderAppearance.fromTypographyJson(
                "{\"theme\":\"DAY\",\"textSizeSp\":020,"
                        + "\"lineHeightPercent\":145,\"horizontalMarginDp\":12}"),
                "non-canonical reader appearance numbers are rejected");
        assertIllegalArgument(() -> new ReaderAppearance(
                ReaderAppearance.Theme.DAY, ReaderAppearance.FontFamily.MONOSPACE,
                20, 145, 6, 12), "unsupported paragraph spacing is rejected");
        assertIllegalArgument(() -> ReaderAppearance.fromTypographyJson(
                "{\"theme\":\"DAY\",\"fontFamily\":\"CURSIVE\","
                        + "\"textSizeSp\":20,\"lineHeightPercent\":145,"
                        + "\"paragraphSpacingDp\":8,\"horizontalMarginDp\":12}"),
                "unknown font family is rejected");
    }

    private static void testReaderDisplayPolicyContract() {
        ReaderDisplayPolicy defaults = ReaderDisplayPolicy.defaults();
        assertEquals(ReaderDisplayPolicy.Orientation.FOLLOW_SYSTEM,
                defaults.getOrientation(), "reader orientation follows system by default");
        for (ReaderDisplayPolicy.Orientation orientation
                : ReaderDisplayPolicy.Orientation.values()) {
            ReaderDisplayPolicy policy = new ReaderDisplayPolicy(orientation);
            assertEquals(orientation,
                    ReaderDisplayPolicy.fromJson(policy.toJson()).getOrientation(),
                    "reader orientation canonical round trip: " + orientation);
        }
        assertIllegalArgument(() -> ReaderDisplayPolicy.fromJson(
                "{\"orientation\":\"SENSOR\"}"),
                "unknown reader orientation is rejected");
        assertIllegalArgument(() -> ReaderDisplayPolicy.fromJson(
                "{\"extra\":false,\"orientation\":\"PORTRAIT\"}"),
                "unknown reader display fields are rejected");
    }

    private static void testReaderNavigationSettingsContract() {
        ReaderNavigationSettings defaults = ReaderNavigationSettings.defaults();
        assertEquals(ReaderNavigationSettings.ReadingMode.CONTINUOUS_SCROLL,
                defaults.getReadingMode(), "reader navigation default mode");
        assertEquals(ReaderNavigationSettings.Direction.NONE,
                defaults.directionFor(ReaderNavigationSettings.VolumeKey.DOWN),
                "volume keys retain system behavior by default");

        ReaderNavigationSettings normal = new ReaderNavigationSettings(
                ReaderNavigationSettings.ReadingMode.PAGED,
                ReaderNavigationSettings.VolumeKeyMode.DOWN_FORWARD);
        assertEquals(ReaderNavigationSettings.Direction.FORWARD,
                normal.directionFor(ReaderNavigationSettings.VolumeKey.DOWN),
                "volume down moves forward in normal mode");
        assertEquals(ReaderNavigationSettings.Direction.BACKWARD,
                normal.directionFor(ReaderNavigationSettings.VolumeKey.UP),
                "volume up moves backward in normal mode");
        assertEquals(normal.toJson(),
                ReaderNavigationSettings.fromJson(normal.toJson()).toJson(),
                "reader navigation settings canonical round trip");

        ReaderNavigationSettings reversed = new ReaderNavigationSettings(
                ReaderNavigationSettings.ReadingMode.CONTINUOUS_SCROLL,
                ReaderNavigationSettings.VolumeKeyMode.UP_FORWARD);
        assertEquals(ReaderNavigationSettings.Direction.FORWARD,
                reversed.directionFor(ReaderNavigationSettings.VolumeKey.UP),
                "reversed volume direction is explicit");
        assertIllegalArgument(() -> ReaderNavigationSettings.fromJson(
                        "{\"version\":1,\"volumeKeyMode\":\"OFF\","
                                + "\"readingMode\":\"PAGED\"}"),
                "reader navigation settings reject reordered fields");
        assertIllegalArgument(() -> ReaderNavigationSettings.fromJson(
                        "{\"version\":2,\"readingMode\":\"PAGED\","
                                + "\"volumeKeyMode\":\"OFF\"}"),
                "reader navigation settings reject unknown version");
    }

    private static void testReaderTextSelectionContract() {
        ReaderTextSelection latin = ReaderTextSelection.selectWord(
                "前文 hello_world 后文", 100, 5);
        assertEquals("hello_world", latin.getSelectedText(),
                "Latin word selection expands to word boundaries");
        assertEquals(103, latin.getStartOffset(), "selection uses global start offset");
        assertEquals(114, latin.getEndOffset(), "selection uses global end offset");

        ReaderTextSelection chinese = ReaderTextSelection.selectWord("甲乙丙", 20, 1);
        assertEquals("乙", chinese.getSelectedText(),
                "CJK long press selects one character instead of a whole sentence");
        ReaderTextSelection emoji = ReaderTextSelection.selectRange("甲😀乙", 50, 2, 2);
        assertEquals("😀", emoji.getSelectedText(),
                "range normalization never splits a surrogate pair");
        assertEquals(51, emoji.getStartOffset(), "surrogate range moves start backward");
        assertEquals(53, emoji.getEndOffset(), "surrogate range moves end forward");

        ReaderTextSelection reversed = ReaderTextSelection.selectRange(
                "第一行\n第二行", 0, 7, 1);
        assertEquals("一行\n第二行", reversed.getSelectedText(),
                "drag selection accepts reversed endpoints and preserves newlines");
        assertIllegalArgument(() -> ReaderTextSelection.selectWord("", 0, 0),
                "empty window cannot create a selection");
        assertIllegalArgument(() -> ReaderTextSelection.selectRange("坏\uD83D", 0, 0, 2),
                "unpaired surrogate input is rejected");
        assertIllegalArgument(() -> ReaderTextSelection.selectRange("x", Integer.MAX_VALUE, 0, 1),
                "overflowing global selection is rejected");
        assertIllegalArgument(() -> ReaderTextSelection.selectRange("abc", 0, -1, 2),
                "negative selection endpoint is rejected instead of clamped");
        assertIllegalArgument(() -> ReaderTextSelection.selectRange("abc", 0, 1, 4),
                "selection beyond the window is rejected instead of clamped");
    }

    private static void testAutoScrollPolicyContract() {
        AutoScrollPolicy defaults = AutoScrollPolicy.defaults();
        assertEquals(28, defaults.getSpeedDpPerSecond(),
                "default auto-scroll speed");
        String encoded = defaults.toJson();
        assertEquals(encoded, AutoScrollPolicy.fromJson(encoded).toJson(),
                "auto-scroll policy canonical round trip");
        assertEquals(8, AutoScrollPolicy.minimumSpeedDpPerSecond(),
                "minimum auto-scroll speed");
        assertEquals(120, AutoScrollPolicy.maximumSpeedDpPerSecond(),
                "maximum auto-scroll speed");

        double at60Hz = integratedAutoScrollDistance(defaults, 60);
        double at90Hz = integratedAutoScrollDistance(defaults, 90);
        double at120Hz = integratedAutoScrollDistance(defaults, 120);
        assertTrue(Math.abs(at60Hz - 28.0) < 0.000001,
                "60Hz integrates to configured speed");
        assertTrue(Math.abs(at60Hz - at90Hz) < 0.000001
                        && Math.abs(at90Hz - at120Hz) < 0.000001,
                "auto-scroll distance is refresh-rate independent");
        assertTrue(Math.abs(defaults.distanceDp(500_000_000L) - 2.8) < 0.000001,
                "stalled frame is capped to avoid a jump");

        assertIllegalArgument(() -> new AutoScrollPolicy(7),
                "speed below minimum is rejected");
        assertIllegalArgument(() -> new AutoScrollPolicy(121),
                "speed above maximum is rejected");
        assertIllegalArgument(() -> defaults.distanceDp(-1),
                "negative frame delta is rejected");
        assertIllegalArgument(() -> AutoScrollPolicy.fromJson(
                "{\"speedDpPerSecond\":028}"),
                "non-canonical speed is rejected");
        assertIllegalArgument(() -> AutoScrollPolicy.fromJson(
                "{\"speedDpPerSecond\":28,\"extra\":true}"),
                "unknown policy fields are rejected");
    }

    private static double integratedAutoScrollDistance(
            AutoScrollPolicy policy, int refreshRate) {
        long baseFrameNanos = 1_000_000_000L / refreshRate;
        long remainder = 1_000_000_000L % refreshRate;
        double distance = 0;
        for (int frame = 0; frame < refreshRate; frame++) {
            distance += policy.distanceDp(baseFrameNanos + (frame < remainder ? 1 : 0));
        }
        return distance;
    }

    private static void testSpeechSettingsContract() {
        SpeechSettings defaults = SpeechSettings.defaults();
        assertEquals(null, defaults.getVoiceId(), "system voice is the default");
        assertEquals(100, defaults.getRatePercent(), "default speech rate");
        assertEquals(100, defaults.getPitchPercent(), "default speech pitch");
        assertTrue(Math.abs(defaults.getRateMultiplier() - 1.0f) < 0.000001f,
                "default rate multiplier");

        SpeechSettings selected = new SpeechSettings(
                "zh-CN-local-女声😀", 75, 125);
        String encoded = selected.toJson();
        SpeechSettings decoded = SpeechSettings.fromJson(encoded);
        assertEquals(selected.getVoiceId(), decoded.getVoiceId(),
                "Unicode voice ID canonical round trip");
        assertEquals(75, decoded.getRatePercent(), "speech rate round trip");
        assertEquals(125, decoded.getPitchPercent(), "speech pitch round trip");
        int[] options = SpeechSettings.percentOptions();
        options[0] = 999;
        assertEquals(50, SpeechSettings.percentOptions()[0],
                "speech percent options are defensively copied");

        assertIllegalArgument(() -> new SpeechSettings(null, 49, 100),
                "speech rate below minimum is rejected");
        assertIllegalArgument(() -> new SpeechSettings(null, 100, 201),
                "speech pitch above maximum is rejected");
        assertIllegalArgument(() -> new SpeechSettings("bad\uD83D", 100, 100),
                "speech voice ID rejects an unpaired surrogate");
        assertIllegalArgument(() -> new SpeechSettings("bad\nvoice", 100, 100),
                "speech voice ID rejects control characters");
        assertIllegalArgument(() -> new SpeechSettings(repeat('声', 513), 100, 100),
                "speech voice ID has a UTF-8 byte bound");
        assertIllegalArgument(() -> SpeechSettings.fromJson(
                "{\"version\":1,\"voiceIdBase64\":\"YQ==\","
                        + "\"ratePercent\":100,\"pitchPercent\":100}"),
                "padded non-canonical voice encoding is rejected");
        assertIllegalArgument(() -> SpeechSettings.fromJson(
                "{\"version\":1,\"voiceIdBase64\":\"\","
                        + "\"ratePercent\":0100,\"pitchPercent\":100}"),
                "non-canonical numeric speech setting is rejected");

        TextToSpeechPort.VoiceInfo offline = new TextToSpeechPort.VoiceInfo(
                "local-zh", "zh-CN", false);
        List<TextToSpeechPort.VoiceInfo> voices = new ArrayList<>();
        voices.add(offline);
        TextToSpeechPort.Capabilities capabilities = new TextToSpeechPort.Capabilities(
                true, true, false, true, 3000, offline, voices);
        voices.clear();
        assertEquals(1, capabilities.voices.size(),
                "speech capability voices are defensively copied");
        assertEquals("local-zh", capabilities.voiceIds.get(0),
                "legacy voice IDs derive from structured voice metadata");
        assertEquals(offline, capabilities.defaultVoice,
                "default voice metadata is explicit");
        assertIllegalArgument(() -> new TextToSpeechPort.Capabilities(
                        true, true, false, true, 3000, offline,
                        Arrays.asList(offline, offline)),
                "duplicate platform voice IDs are rejected");
        assertIllegalArgument(() -> new TextToSpeechPort.Capabilities(
                        true, true, false, true, -1, null,
                        Collections.<TextToSpeechPort.VoiceInfo>emptyList()),
                "negative platform speech limit is rejected");
    }

    private static void testAutoScrollCompanionContract() {
        AutoScrollCompanionSettings defaults = AutoScrollCompanionSettings.defaults();
        assertEquals(0, defaults.getResumeDelaySeconds(),
                "touch resume defaults to off");
        assertTrue(!defaults.isKeepScreenOn(), "screen awake defaults to off");
        String encoded = new AutoScrollCompanionSettings(5, true).toJson();
        AutoScrollCompanionSettings decoded =
                AutoScrollCompanionSettings.fromJson(encoded);
        assertEquals(encoded, decoded.toJson(),
                "auto-scroll companion settings canonical round trip");
        int[] options = AutoScrollCompanionSettings.resumeDelayOptionsSeconds();
        options[0] = 99;
        assertEquals(0, AutoScrollCompanionSettings.resumeDelayOptionsSeconds()[0],
                "resume delay options are defensive copies");

        AutoScrollResumeSession disabled = new AutoScrollResumeSession(defaults);
        assertTrue(!disabled.arm("revision-a", 40, 1000),
                "disabled touch resume does not arm");
        AutoScrollResumeSession session = new AutoScrollResumeSession(decoded);
        assertTrue(session.arm("revision-a", 40, 1000),
                "touch resume arms with a stable context");
        assertEquals(5, session.remainingSeconds(1000),
                "resume countdown begins at configured delay");
        assertEquals(1, session.remainingSeconds(5999),
                "resume countdown uses ceiling seconds");
        assertTrue(!session.consumeIfDue("revision-a", 40, 5999),
                "resume cannot fire early");
        assertTrue(session.consumeIfDue("revision-a", 40, 6000),
                "resume fires once when due and context is unchanged");
        assertTrue(!session.consumeIfDue("revision-a", 40, 6000),
                "resume deadline is consumed once");

        session.arm("revision-a", 40, 7000);
        assertTrue(!session.consumeIfDue("revision-b", 40, 12000)
                        && !session.isArmed(),
                "revision change invalidates pending resume");
        session.arm("revision-a", 40, 13000);
        assertTrue(!session.consumeIfDue("revision-a", 41, 18000)
                        && !session.isArmed(),
                "anchor change invalidates pending resume");
        session.arm("revision-a", 40, 19000);
        session.cancel();
        assertTrue(!session.consumeIfDue("revision-a", 40, 24000),
                "explicit cancellation prevents ghost resume");

        assertIllegalArgument(() -> new AutoScrollCompanionSettings(4, false),
                "unsupported resume delay is rejected");
        assertIllegalArgument(() -> AutoScrollCompanionSettings.fromJson(
                "{\"keepScreenOn\":true,\"resumeDelaySeconds\":5}"),
                "reordered companion settings are rejected");
        assertIllegalArgument(() -> AutoScrollCompanionSettings.fromJson(
                "{\"resumeDelaySeconds\":05,\"keepScreenOn\":true}"),
                "non-canonical companion settings are rejected");
        assertIllegalArgument(() -> session.arm("", 0, 0),
                "empty resume context is rejected");
        assertIllegalArgument(() -> session.arm("revision-a", 0,
                Long.MAX_VALUE), "overflowing resume deadline is rejected");
    }

    private static void writeLegacyBookLibrary(Path target, BookLibraryEntry entry)
            throws Exception {
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        try (DataOutputStream payload = new DataOutputStream(payloadBytes)) {
            payload.writeInt(1);
            payload.writeUTF(entry.getBookId());
            payload.writeUTF(entry.getDisplayName());
            payload.writeUTF(entry.getBaseFileName());
            payload.writeUTF(entry.getBaseRevision());
            payload.writeUTF(entry.getActiveFileName());
            payload.writeUTF(entry.getActiveRevision());
            payload.writeUTF(entry.getProjectionFileName());
            payload.writeUTF(entry.getEncodingName());
            payload.writeLong(entry.getSourceBytes());
            payload.writeLong(entry.getImportedAtEpochMillis());
            payload.writeLong(entry.getLastOpenedAtEpochMillis());
            payload.writeInt(entry.getAnchorOffset());
        }
        byte[] payload = payloadBytes.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(payload);
        Files.createDirectories(target.getParent());
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(target))) {
            output.writeInt(0x4A44424C);
            output.writeInt(1);
            output.writeInt(payload.length);
            output.write(payload);
            output.writeLong(crc.getValue());
        }
    }

    private static void writeLegacyEncodingProfile(Path target,
            BookEncodingProfile profile) throws Exception {
        writeOldEncodingProfile(target, profile, 1);
    }

    private static void writeVersion2EncodingProfile(Path target,
            BookEncodingProfile profile) throws Exception {
        writeOldEncodingProfile(target, profile, 2);
    }

    private static void writeVersion3EncodingProfile(Path target,
            BookEncodingProfile profile) throws Exception {
        writeOldEncodingProfile(target, profile, 3);
    }

    private static void writeOldEncodingProfile(Path target,
            BookEncodingProfile profile, int version) throws Exception {
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        try (DataOutputStream payload = new DataOutputStream(payloadBytes)) {
            payload.writeUTF(profile.getBookId());
            payload.writeUTF(profile.getBaseRevision());
            payload.writeUTF(profile.getCharsetName());
            payload.writeDouble(profile.getConfidence());
            payload.writeUTF(profile.getSelectionMode().name());
            payload.writeUTF(profile.getAdvisory().name());
            if (version >= 2) {
                payload.writeLong(profile.getDecodingReplacementCount());
                payload.writeLong(profile.getFirstDecodingErrorByteOffset());
            }
            if (version >= 3) {
                payload.writeLong(profile.getFirstReplacementCharacterOffset());
            }
        }
        byte[] payload = payloadBytes.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(payload);
        Files.createDirectories(target.getParent());
        try (DataOutputStream output = new DataOutputStream(
                Files.newOutputStream(target))) {
            output.writeInt(0x4A444550);
            output.writeInt(version);
            output.writeInt(payload.length);
            output.write(payload);
            output.writeLong(crc.getValue());
        }
    }

    private static void testSpeechPlaybackQueue() {
        String text = "第一句。第二句较长！第三段😀继续。\n最后一段";
        assertEquals(100, SpeechPlaybackQueue.paragraphStartAt(text, 100, 109),
                "initial speech anchor moves to the current paragraph start");
        assertEquals(119, SpeechPlaybackQueue.paragraphStartAt(text, 100, 121),
                "paragraph start resolves after a newline");
        assertEquals(100 + text.length(), SpeechPlaybackQueue.paragraphStartAt(
                        text, 100, 100 + text.length()),
                "document-end speech anchor does not repeat the final paragraph");
        assertIllegalArgument(() -> SpeechPlaybackQueue.paragraphStartAt(text, 100, 99),
                "paragraph anchor before the window is rejected");
        SpeechPlaybackQueue queue = new SpeechPlaybackQueue(
                repeat('b', 64), 100, text, 101, 8);
        assertTrue(queue.getSegmentCount() >= 4, "speech text is split into bounded segments");
        SpeechPlaybackQueue.Item first = queue.begin();
        assertEquals(101, first.getStartOffset(), "speech starts at requested global anchor");
        assertTrue(first.getText().length() <= 8, "speech item respects platform limit");
        SpeechPlaybackQueue.Highlight fallback = queue.fallbackHighlight(
                first.getUtteranceId());
        assertEquals(100, fallback.getStartOffset(),
                "engines without range callbacks highlight the current paragraph start");
        assertEquals(118, fallback.getEndOffset(),
                "fallback highlight covers the paragraph before its newline");
        assertEquals(first.getStartOffset(), fallback.getFollowOffset(),
                "fallback page following uses current chunk progress, not paragraph start");
        assertTrue(fallback.isParagraphFallback(),
                "fallback highlight is explicitly marked as paragraph precision");
        SpeechPlaybackQueue.Highlight precise = queue.onRangeHighlight(
                first.getUtteranceId(), 2, 3);
        assertTrue(precise != null,
                "current utterance range is accepted");
        assertEquals(100, precise.getStartOffset(),
                "word-level platform range expands to the current sentence start");
        assertEquals(104, precise.getEndOffset(),
                "word-level platform range expands through sentence punctuation");
        assertEquals(103, precise.getFollowOffset(),
                "exact highlight follows the platform progress anchor");
        assertTrue(!precise.isParagraphFallback(),
                "platform range highlight has exact precision");
        assertEquals(103, queue.getAnchor(), "speech range updates global anchor");
        assertTrue(!queue.onRange(first.getUtteranceId(), 1, 2),
                "out-of-order range cannot move the speech anchor backward");
        assertEquals(103, queue.getAnchor(), "rejected range preserves speech anchor");

        queue.pause();
        assertEquals(SpeechPlaybackQueue.State.PAUSED, queue.getState(),
                "speech queue pauses");
        assertTrue(queue.fallbackHighlight(first.getUtteranceId()) == null,
                "paused utterance cannot republish a stale fallback highlight");
        assertTrue(!queue.onRange(first.getUtteranceId(), 3, 4),
                "late range after pause is rejected");
        SpeechPlaybackQueue.Item resumed = queue.resume();
        assertEquals(103, resumed.getStartOffset(), "speech resumes from last range anchor");
        assertTrue(!first.getUtteranceId().equals(resumed.getUtteranceId()),
                "resume issues a fresh utterance id");
        assertTrue(queue.onDone(resumed.getUtteranceId()) != null,
                "completion advances to the next segment");

        SpeechPlaybackQueue.Item previous = queue.movePrevious();
        assertTrue(previous != null, "previous segment is available");
        SpeechPlaybackQueue.Item next = queue.moveNext();
        assertTrue(next != null, "next segment is available");
        assertTrue(!queue.onError(previous.getUtteranceId()),
                "stale error cannot stop the current utterance");
        assertTrue(queue.onError(next.getUtteranceId()),
                "current utterance error stops the queue");
        assertEquals(SpeechPlaybackQueue.State.STOPPED, queue.getState(),
                "speech queue stops on current error");

        SpeechPlaybackQueue emoji = new SpeechPlaybackQueue(
                "emoji", 0, "甲😀乙😀丙", 0, 2);
        StringBuilder rebuilt = new StringBuilder();
        SpeechPlaybackQueue.Item emojiItem = emoji.begin();
        while (emojiItem != null) {
            assertTrue(!Character.isHighSurrogate(
                    emojiItem.getText().charAt(emojiItem.getText().length() - 1)),
                    "speech split never ends on a dangling high surrogate");
            rebuilt.append(emojiItem.getText());
            emojiItem = emoji.onDone(emojiItem.getUtteranceId());
        }
        assertEquals("甲😀乙😀丙", rebuilt.toString(),
                "speech segmentation has no gaps or duplicates");
        assertEquals(SpeechPlaybackQueue.State.COMPLETE, emoji.getState(),
                "speech queue completes after final segment");
        assertIllegalArgument(() -> new SpeechPlaybackQueue("x", 10, "abc", 9, 2),
                "speech start before window is rejected");
        assertIllegalArgument(() -> new SpeechPlaybackQueue("x", 0, "abc", 0, 1),
                "speech limit too small for a surrogate pair is rejected");
        SpeechPlaybackQueue unlimited = new SpeechPlaybackQueue(
                "wide", 0, "短文本", 0, Integer.MAX_VALUE);
        assertEquals("短文本", unlimited.begin().getText(),
                "large platform limit cannot overflow segmentation arithmetic");
        SpeechPlaybackQueue surrogateRange = new SpeechPlaybackQueue(
                "range-emoji", 0, "甲😀乙", 0, 4);
        SpeechPlaybackQueue.Item surrogateItem = surrogateRange.begin();
        assertTrue(surrogateRange.onRangeHighlight(
                        surrogateItem.getUtteranceId(), 1, 2) == null,
                "speech highlight rejects a range ending inside a surrogate pair");
        assertTrue(surrogateRange.onRangeHighlight(
                        surrogateItem.getUtteranceId(), 2, 3) == null,
                "speech highlight rejects a range starting inside a surrogate pair");
        assertTrue(surrogateRange.onRangeHighlight(
                        surrogateItem.getUtteranceId(), 1, 3) != null,
                "speech highlight accepts a complete supplementary code point");
        SpeechPlaybackQueue multiSentence = new SpeechPlaybackQueue(
                "sentences", 40, "甲。乙！\n丙", 40, 100);
        SpeechPlaybackQueue.Item multiItem = multiSentence.begin();
        SpeechPlaybackQueue.Highlight multiFallback = multiSentence.fallbackHighlight(
                multiItem.getUtteranceId());
        assertEquals(40, multiFallback.getStartOffset(),
                "fallback starts at the current paragraph boundary");
        assertEquals(44, multiFallback.getEndOffset(),
                "fallback ends before the paragraph newline");
        SpeechPlaybackQueue.Highlight secondSentence = multiSentence.onRangeHighlight(
                multiItem.getUtteranceId(), 2, 3);
        assertEquals(42, secondSentence.getStartOffset(),
                "word callback in the second sentence excludes the prior sentence");
        assertEquals(44, secondSentence.getEndOffset(),
                "sentence highlight includes its terminal punctuation");
    }

    private static String repeat(char value, int count) {
        char[] values = new char[count];
        Arrays.fill(values, value);
        return new String(values);
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder();
        for (byte value : digest) {
            result.append(String.format("%02x", value & 0xFF));
        }
        return result.toString();
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walk(root)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException error) {
                        throw new RuntimeException(error);
                    }
                });
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertIllegalArgument(Runnable operation, String message) {
        boolean rejected = false;
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue(rejected, message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertArrayEquals(byte[] expected, byte[] actual, String message) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(message);
        }
    }
}
