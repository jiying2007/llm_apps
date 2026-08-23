package com.junchen.jingdu;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
        LinkedHashMap<Long, Hit> merged = new LinkedHashMap<>();
        LinkedHashSet<String> variants = new LinkedHashSet<>(ChineseDisplayConverter.searchVariants(query));
        variants.addAll(ChineseScript.searchVariants(query));
        for (String variant : variants) {
            if (variant == null || variant.trim().isEmpty()) continue;
            for (String line : NativeCore.search(handle, variant, 500).split("\n")) {
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                try {
                    long offset = Long.parseLong(line.substring(0, tab));
                    merged.putIfAbsent(offset, new Hit(offset, ChineseDisplayConverter.convert(line.substring(tab + 1))));
                } catch (NumberFormatException ignored) {
                }
            }
            if (merged.size() >= 500) break;
        }
        ArrayList<Hit> results = new ArrayList<>(merged.values());
        results.sort((left, right) -> Long.compare(left.offset(), right.offset()));
        if (results.size() > 500) return new ArrayList<>(results.subList(0, 500));
        return results;
    }

    List<Chapter> chapters() throws IOException {
        ensureOpen();
        ArrayList<Chapter> chapters = new ArrayList<>();
        for (String line : NativeCore.chapters(handle, 20000).split("\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            try {
                chapters.add(new Chapter(Long.parseLong(line.substring(0, tab)), ChineseDisplayConverter.convert(line.substring(tab + 1))));
            } catch (NumberFormatException ignored) {
            }
        }
        return chapters;
    }

    List<NoiseCandidate> noiseCandidates() throws IOException {
        ensureOpen();
        LinkedHashMap<String, NoiseCandidate> merged = new LinkedHashMap<>();
        for (String line : NativeCore.noiseCandidates(handle, 80).split("\n")) {
            if (line.isEmpty()) continue;
            String[] fields = line.split("\t", 4);
            if (fields.length != 4) continue;
            try {
                NoiseCandidate candidate = new NoiseCandidate(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]), fields[2], fields[3]);
                merged.put(candidate.reason() + '\u001f' + candidate.text(), candidate);
            } catch (NumberFormatException ignored) {
            }
        }
        if (documentFile != null) {
            for (SmartCleanRefiner.Candidate refined : SmartCleanRefiner.scan(documentFile, 40)) {
                String key = refined.reason() + '\u001f' + refined.text();
                NoiseCandidate existing = merged.get(key);
                if (existing == null || refined.score() > existing.score()) {
                    merged.put(key, new NoiseCandidate(refined.score(), refined.count(), refined.reason(), refined.text()));
                }
            }
        }
        ArrayList<NoiseCandidate> candidates = new ArrayList<>(merged.values());
        candidates.sort(Comparator.comparingInt(NoiseCandidate::score).reversed().thenComparing(Comparator.comparingInt(NoiseCandidate::count).reversed()));
        if (candidates.size() > 100) return new ArrayList<>(candidates.subList(0, 100));
        return candidates;
    }

    Speech speech(long from) throws IOException {
        ensureOpen();
        String packed = NativeCore.speechChunk(handle, from, 900);
        int tab = packed.indexOf('\t');
        if (tab < 0) return new Speech(from, "");
        try {
            return new Speech(Long.parseLong(packed.substring(0, tab)), ChineseDisplayConverter.convert(packed.substring(tab + 1)));
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
