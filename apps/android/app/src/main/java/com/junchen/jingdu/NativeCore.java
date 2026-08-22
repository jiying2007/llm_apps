package com.junchen.jingdu;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class NativeCore {
    static {
        System.loadLibrary("jingdu_native");
        if (nativeAbiVersion() != 1) throw new ExceptionInInitializerError("unsupported Jingdu core ABI");
    }

    private NativeCore() {}

    static native int nativeAbiVersion();
    static native String nativeDetectEncoding(byte[] sample) throws IOException;
    static native long nativeOpen(byte[] path) throws IOException;
    static native void nativeClose(long handle);
    static native long nativeCharCount(long handle);
    static native byte[] nativeRead(long handle, long offset, long count) throws IOException;
    static native byte[] nativeSearch(long handle, byte[] query, int limit) throws IOException;
    static native byte[] nativeChapters(long handle, int limit) throws IOException;
    static native byte[] nativeSpeechChunk(long handle, long offset, long count) throws IOException;
    static native void nativeExportRules(long handle, byte[] packedRules, byte[] outputPath) throws IOException;

    static String detectEncoding(byte[] sample) throws IOException { return nativeDetectEncoding(sample); }
    static long open(File file) throws IOException { return nativeOpen(file.getAbsolutePath().getBytes(StandardCharsets.UTF_8)); }
    static String read(long h, long offset, long count) throws IOException { return new String(nativeRead(h, offset, count), StandardCharsets.UTF_8); }
    static String search(long h, String q, int limit) throws IOException { return new String(nativeSearch(h, q.getBytes(StandardCharsets.UTF_8), limit), StandardCharsets.UTF_8); }
    static String chapters(long h, int limit) throws IOException { return new String(nativeChapters(h, limit), StandardCharsets.UTF_8); }
    static String speechChunk(long h, long offset, long count) throws IOException { return new String(nativeSpeechChunk(h, offset, count), StandardCharsets.UTF_8); }
    static void exportRules(long h, String packed, File output) throws IOException { nativeExportRules(h, packed.getBytes(StandardCharsets.UTF_8), output.getAbsolutePath().getBytes(StandardCharsets.UTF_8)); }
}
