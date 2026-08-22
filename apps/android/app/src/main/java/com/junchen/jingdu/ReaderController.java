package com.junchen.jingdu;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class ReaderController implements Closeable {
    static final long PAGE_CHARS = 1800;
    static final long WINDOW_CHARS = 6000;

    record Hit(long offset, String context) {}
    record Chapter(long offset, String title) {}
    record Speech(long nextOffset, String text) {}

    private long handle;
    private long length;
    private long position;

    void open(File file, long restoredPosition) throws IOException {
        close(); handle = NativeCore.open(file); length = NativeCore.nativeCharCount(handle); position = Math.min(Math.max(0, restoredPosition), Math.max(0, length - 1));
    }

    String page() throws IOException { ensureOpen(); return NativeCore.read(handle, position, WINDOW_CHARS); }
    long position() { return position; }
    long length() { return length; }
    void jump(long value) { position = Math.min(Math.max(0, value), Math.max(0, length - 1)); }
    void next() { jump(position + PAGE_CHARS); }
    void previous() { jump(position - PAGE_CHARS); }

    List<Hit> search(String query) throws IOException {
        ensureOpen(); ArrayList<Hit> hits = new ArrayList<>();
        for (String line : NativeCore.search(handle, query, 500).split("\n")) {
            int tab = line.indexOf('\t'); if (tab <= 0) continue;
            try { hits.add(new Hit(Long.parseLong(line.substring(0, tab)), line.substring(tab + 1))); } catch (NumberFormatException ignored) { }
        }
        return hits;
    }

    List<Chapter> chapters() throws IOException {
        ensureOpen(); ArrayList<Chapter> chapters = new ArrayList<>();
        for (String line : NativeCore.chapters(handle, 20000).split("\n")) {
            int tab = line.indexOf('\t'); if (tab <= 0) continue;
            try { chapters.add(new Chapter(Long.parseLong(line.substring(0, tab)), line.substring(tab + 1))); } catch (NumberFormatException ignored) { }
        }
        return chapters;
    }

    Speech speech(long from) throws IOException {
        ensureOpen(); String packed = NativeCore.speechChunk(handle, from, 900); int tab = packed.indexOf('\t');
        if (tab < 0) return new Speech(from, "");
        try { return new Speech(Long.parseLong(packed.substring(0, tab)), packed.substring(tab + 1)); } catch (NumberFormatException error) { throw new IOException("invalid speech core response", error); }
    }

    void exportRules(String packedRules, File output) throws IOException { ensureOpen(); NativeCore.exportRules(handle, packedRules == null ? "" : packedRules, output); }

    @Override public void close() { if (handle != 0) { NativeCore.nativeClose(handle); handle = 0; } length = 0; position = 0; }
    private void ensureOpen() throws IOException { if (handle == 0) throw new IOException("no document open"); }
}
