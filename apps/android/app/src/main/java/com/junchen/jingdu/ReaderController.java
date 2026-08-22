package com.junchen.jingdu;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class ReaderController implements Closeable {
    static final long READ_AHEAD_CHARS = 5000;
    static final long MIN_PAGE_CHARS = 180;

    record Hit(long offset, String context) {}
    record Chapter(long offset, String title) {}
    record Speech(long nextOffset, String text) {}

    private long handle;
    private File documentFile;
    private long length;
    private long position;

    void open(File file, long restoredPosition) throws IOException {
        close();
        handle = NativeCore.open(file);
        documentFile = file;
        length = NativeCore.nativeCharCount(handle);
        position = Math.min(Math.max(0, restoredPosition), Math.max(0, length - 1));
    }

    String page() throws IOException {
        ensureOpen();
        return NativeCore.read(handle, position, READ_AHEAD_CHARS);
    }

    long position() { return position; }
    long length() { return length; }

    void jump(long value) {
        position = Math.min(Math.max(0, value), Math.max(0, length - 1));
    }

    void move(long delta) {
        jump(position + delta);
    }

    List<Hit> search(String query) throws IOException {
        File file = requireFile();
        long searchHandle = NativeCore.open(file);
        try {
            ArrayList<Hit> hits = new ArrayList<>();
            for (String line : NativeCore.search(searchHandle, query, 500).split("\n")) {
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                try {
                    hits.add(new Hit(Long.parseLong(line.substring(0, tab)), line.substring(tab + 1)));
                } catch (NumberFormatException ignored) {
                }
            }
            return hits;
        } finally {
            NativeCore.nativeClose(searchHandle);
        }
    }

    List<Chapter> chapters() throws IOException {
        File file = requireFile();
        long chapterHandle = NativeCore.open(file);
        try {
            ArrayList<Chapter> chapters = new ArrayList<>();
            for (String line : NativeCore.chapters(chapterHandle, 20000).split("\n")) {
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                try {
                    chapters.add(new Chapter(
                            Long.parseLong(line.substring(0, tab)), line.substring(tab + 1)));
                } catch (NumberFormatException ignored) {
                }
            }
            return chapters;
        } finally {
            NativeCore.nativeClose(chapterHandle);
        }
    }

    Speech speech(long from) throws IOException {
        ensureOpen();
        String packed = NativeCore.speechChunk(handle, from, 900);
        int tab = packed.indexOf('\t');
        if (tab < 0) return new Speech(from, "");
        try {
            return new Speech(Long.parseLong(packed.substring(0, tab)), packed.substring(tab + 1));
        } catch (NumberFormatException error) {
            throw new IOException("invalid speech core response", error);
        }
    }

    void exportRules(String packedRules, File output) throws IOException {
        ensureOpen();
        NativeCore.exportRules(handle, packedRules == null ? "" : packedRules, output);
    }

    @Override public void close() {
        if (handle != 0) {
            NativeCore.nativeClose(handle);
            handle = 0;
        }
        documentFile = null;
        length = 0;
        position = 0;
    }

    private File requireFile() throws IOException {
        File file = documentFile;
        if (file == null) throw new IOException("no document open");
        return file;
    }

    private void ensureOpen() throws IOException {
        if (handle == 0) throw new IOException("no document open");
    }
}
