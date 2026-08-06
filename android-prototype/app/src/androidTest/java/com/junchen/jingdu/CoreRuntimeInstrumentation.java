package com.junchen.jingdu;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.util.Log;

import com.jingdu.txt.core.DecodingReplacement;
import com.jingdu.txt.core.EncodingDetector;
import com.jingdu.txt.core.ImportEncodingPreference;
import com.jingdu.txt.core.ImportResult;
import com.jingdu.txt.core.TextImportPipeline;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Runs cross-runtime decoder recovery checks without an external test runner. */
public final class CoreRuntimeInstrumentation extends Instrumentation {
    private static final String TAG = "JingduCoreRuntime";

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            verifySingleByteEncoding(ImportEncodingPreference.Choice.UTF_8);
            verifySingleByteEncoding(ImportEncodingPreference.Choice.GB18030);
            verifySingleByteEncoding(ImportEncodingPreference.Choice.GBK);
            verifySingleByteEncoding(ImportEncodingPreference.Choice.GB2312);
            verifySingleByteEncoding(ImportEncodingPreference.Choice.BIG5);
            verifyUtf16(ImportEncodingPreference.Choice.UTF_16LE, true);
            verifyUtf16(ImportEncodingPreference.Choice.UTF_16BE, false);
            result.putString("stream",
                    "PASS decoder recovery: UTF-8, GB18030, GBK, GB2312, Big5, "
                            + "UTF-16LE, UTF-16BE\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            Log.e(TAG, "decoder recovery regression", failure);
            result.putString("shortMsg", failure.toString());
            result.putString("stream", "FAIL decoder recovery: " + failure + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void verifySingleByteEncoding(ImportEncodingPreference.Choice choice)
            throws Exception {
        verify(choice, new byte[] {'A', (byte) 0x81, ' ', '<', '=', 'B'},
                "A� <=B", 1L);
    }

    private void verifyUtf16(ImportEncodingPreference.Choice choice,
            boolean littleEndian) throws Exception {
        byte[] input = littleEndian
                ? new byte[] {0x41, 0x00, 0x00, (byte) 0xD8,
                        0x3C, 0x00, 0x3D, 0x00, 0x42, 0x00}
                : new byte[] {0x00, 0x41, (byte) 0xD8, 0x00,
                        0x00, 0x3C, 0x00, 0x3D, 0x00, 0x42};
        verify(choice, input, "A�<=B", 2L);
    }

    private void verify(ImportEncodingPreference.Choice choice, byte[] input,
            String expected, long expectedOffset) throws Exception {
        Path directory = getTargetContext().getCacheDir().toPath();
        Path source = Files.createTempFile(directory, "decoder-source-", ".txt");
        Path target = Files.createTempFile(directory, "decoder-target-", ".txt");
        Files.deleteIfExists(target);
        try {
            Files.write(source, input);
            ImportResult imported = new TextImportPipeline(new EncodingDetector())
                    .importFile(source, target,
                            new ImportEncodingPreference(choice), null);
            String actual = new String(Files.readAllBytes(target),
                    StandardCharsets.UTF_8);
            require(expected.equals(actual), choice + " output " + actual);
            require(imported.getDecodingReplacementCount() == 1L,
                    choice + " replacement count");
            require(imported.getFirstDecodingErrorByteOffset() == expectedOffset,
                    choice + " first offset "
                            + imported.getFirstDecodingErrorByteOffset());
            require(imported.getDecodingReplacements().size() == 1,
                    choice + " retained location count");
            DecodingReplacement replacement =
                    imported.getDecodingReplacements().get(0);
            require(replacement.getSourceByteOffset() == expectedOffset,
                    choice + " retained source offset");
            require(replacement.getNormalizedCharacterOffset() == 1L,
                    choice + " retained character offset");
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(target);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
