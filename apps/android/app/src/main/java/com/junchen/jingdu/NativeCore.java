package com.junchen.jingdu;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class NativeCore {
    static {
        System.loadLibrary("jingdu_native");
        if (nativeAbiVersion() != 2) {
            throw new ExceptionInInitializerError("unsupported Jingdu core ABI");
        }
    }

    private NativeCore() {}

    static native int nativeAbiVersion();
    static native String nativeDetectEncoding(byte[] sample, boolean truncated) throws IOException;
    static native String nativeFileSha256(byte[] path) throws IOException;
    static native String nativeRepairRevision(byte[] normalizedSha256, byte[] packedRules) throws IOException;
    static native long nativeOpen(byte[] path) throws IOException;
    static native void nativeClose(long handle);
    static native long nativeCharCount(long handle);
    static native byte[] nativeRead(long handle, long offset, long count) throws IOException;
    static native byte[] nativeSearch(long handle, byte[] query, int limit) throws IOException;
    static native byte[] nativeChapters(long handle, int limit) throws IOException;
    static native byte[] nativeSpeechChunk(long handle, long offset, long count) throws IOException;
    static native void nativeExportRules(long handle, byte[] packedRules, byte[] outputPath) throws IOException;

    static String detectEncoding(byte[] sample, boolean truncated) throws IOException {
        return nativeDetectEncoding(sample, truncated);
    }

    static String fileSha256(File file) throws IOException {
        return nativeFileSha256(file.getAbsolutePath().getBytes(StandardCharsets.UTF_8));
    }

    static String repairRevision(String normalizedSha256, String packedRules) throws IOException {
        return nativeRepairRevision(
                normalizedSha256.getBytes(StandardCharsets.UTF_8),
                packedRules.getBytes(StandardCharsets.UTF_8));
    }

    static long open(File file) throws IOException {
        return nativeOpen(file.getAbsolutePath().getBytes(StandardCharsets.UTF_8));
    }

    static String read(long handle, long offset, long count) throws IOException {
        return new String(nativeRead(handle, offset, count), StandardCharsets.UTF_8);
    }

    static String search(long handle, String query, int limit) throws IOException {
        return new String(nativeSearch(handle, query.getBytes(StandardCharsets.UTF_8), limit), StandardCharsets.UTF_8);
    }

    static String chapters(long handle, int limit) throws IOException {
        return new String(nativeChapters(handle, limit), StandardCharsets.UTF_8);
    }

    static String speechChunk(long handle, long offset, long count) throws IOException {
        return new String(nativeSpeechChunk(handle, offset, count), StandardCharsets.UTF_8);
    }

    static void exportRules(long handle, String packed, File output) throws IOException {
        nativeExportRules(handle, packed.getBytes(StandardCharsets.UTF_8), output.getAbsolutePath().getBytes(StandardCharsets.UTF_8));
    }
}
