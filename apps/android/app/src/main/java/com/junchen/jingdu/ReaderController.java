package com.junchen.jingdu;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class ReaderController implements Closeable {
    static final long MIN_PAGE_CHARS = 120;
    static final long DEFAULT_PAGE_CHARS = 800;
    static final long WINDOW_CHARS = 6000;

    record Hit(long offset, String context) {}
    record Chapter(long offset, String title) {}
    record NoiseCandidate(int score, int count, String reason, String text) {}
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
        return NativeCore.read(handle, position, WINDOW_CHARS);
    }

    long position() { return position; }
    long length() { return length; }

    void jump(long value) {
        position = Math.min(Math.max(0, value), Math.max(0, length - 1));
    }

    void move(long delta) {
        long target;
        try {
            target = Math.addExact(position, delta);
        } catch (ArithmeticException overflow) {
            target = delta >= 0 ? Long.MAX_VALUE : 0;
        }
        jump(target);
    }

    List<Hit> search(String query) throws IOException {
        ensureOpen();
        ArrayList<Hit> hits = new ArrayList<>();
        for (String line : NativeCore.search(handle, query, 500).split("\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            try {
                hits.add(new Hit(Long.parseLong(line.substring(0, tab)), line.substring(tab + 1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return hits;
    }

    List<Chapter> chapters() throws IOException {
        ensureOpen();
        ArrayList<Chapter> chapters = new ArrayList<>();
        for (String line : NativeCore.chapters(handle, 20000).split("\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            try {
                chapters.add(new Chapter(
                        Long.parseLong(line.substring(0, tab)), line.substring(tab + 1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return chapters;
    }

    List<NoiseCandidate> noiseCandidates() throws IOException {
        ensureOpen();
        ArrayList<NoiseCandidate> candidates = new ArrayList<>();
        for (String line : NativeCore.noiseCandidates(handle, 80).split("\n")) {
            if (line.isEmpty()) continue;
            String[] fields = line.split("\t", 4);
            if (fields.length != 4) continue;
            try {
                candidates.add(new NoiseCandidate(
                        Integer.parseInt(fields[0]),
                        Integer.parseInt(fields[1]),
                        fields[2],
                        fields[3]));
            } catch (NumberFormatException ignored) {
            }
        }
        return candidates;
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

    private void ensureOpen() throws IOException {
        if (handle == 0 || documentFile == null) throw new IOException("no document open");
    }
}
