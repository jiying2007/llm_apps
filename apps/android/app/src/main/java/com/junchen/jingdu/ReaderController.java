package com.junchen.jingdu;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

final class ReaderController implements Closeable {
    static final long MIN_PAGE_CHARS = 120;
    static final long DEFAULT_PAGE_CHARS = 800;
    static final long WINDOW_CHARS = 1536;
    static final long PAGE_CACHE_CHARS = 64 * 1024L;

    record Hit(long offset, String context) {}
    record Chapter(long offset, String title) {}
    record NoiseCandidate(int score, int count, String reason, String text) {}
    record Speech(long nextOffset, String text) {}
    private record PageWindow(File file, long start, String text, long codePoints) {}

    private static final ExecutorService PAGE_PREFETCH = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "jingdu-page-prefetch");
        thread.setDaemon(true);
        return thread;
    });

    private final boolean pageCacheEnabled;
    private final AtomicLong pageCacheGeneration = new AtomicLong();
    private long handle;
    private volatile File documentFile;
    private volatile long length;
    private volatile long position;
    private volatile PageWindow pageWindow;

    ReaderController() { this(true); }

    ReaderController(boolean pageCacheEnabled) {
        this.pageCacheEnabled = pageCacheEnabled;
    }

    void open(File file, long restoredPosition) throws IOException {
        close();
        handle = NativeCore.open(file);
        documentFile = file;
        length = NativeCore.nativeCharCount(handle);
        position = Math.min(Math.max(0, restoredPosition), Math.max(0, length - 1));
        if (pageCacheEnabled && length > 0) primePageWindow(handle, file, length, position);
    }

    String page() throws IOException {
        ensureOpen();
        String cached = pageFromWindow(pageWindow, position);
        if (cached != null) return cached;
        return NativeCore.read(handle, position, WINDOW_CHARS);
    }

    String readAt(long offset, long maximum) throws IOException {
        ensureOpen();
        long safeOffset = Math.min(Math.max(0, offset), Math.max(0, length - 1));
        long safeMaximum = Math.min(Math.max(0, maximum), 64 * 1024);
        return NativeCore.read(handle, safeOffset, safeMaximum);
    }

    File documentFile() { return documentFile; }
    long position() { return position; }
    long length() { return length; }

    void jump(long value) {
        position = Math.min(Math.max(0, value), Math.max(0, length - 1));
        schedulePagePrefetchIfNeeded();
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
                    merged.putIfAbsent(offset, new Hit(offset, line.substring(tab + 1)));
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
                chapters.add(new Chapter(Long.parseLong(line.substring(0, tab)), line.substring(tab + 1)));
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
        pageCacheGeneration.incrementAndGet();
        pageWindow = null;
        if (handle != 0) {
            NativeCore.nativeClose(handle);
            handle = 0;
        }
        documentFile = null;
        length = 0;
        position = 0;
    }

    private void primePageWindow(long nativeHandle, File file, long documentLength, long target) throws IOException {
        long start = pageWindowStart(target, documentLength);
        String text = NativeCore.read(nativeHandle, start, PAGE_CACHE_CHARS);
        pageWindow = new PageWindow(file, start, text, text.codePointCount(0, text.length()));
    }

    private String pageFromWindow(PageWindow window, long target) {
        File file = documentFile;
        if (!pageCacheEnabled || window == null || file == null || !file.equals(window.file())) return null;
        long relative = target - window.start();
        if (relative < 0 || relative >= window.codePoints()) return null;
        long available = window.codePoints() - relative;
        int startUtf16 = window.text().offsetByCodePoints(0, (int) relative);
        int count = (int) Math.min(WINDOW_CHARS, available);
        int endUtf16 = window.text().offsetByCodePoints(startUtf16, count);
        return window.text().substring(startUtf16, endUtf16);
    }

    private void schedulePagePrefetchIfNeeded() {
        if (!pageCacheEnabled) return;
        PageWindow current = pageWindow;
        File file = documentFile;
        long documentLength = length;
        long target = position;
        if (current == null || file == null || documentLength <= 0 || !file.equals(current.file())) return;
        long relative = target - current.start();
        boolean nearStart = current.start() > 0 && relative < PAGE_CACHE_PREFETCH_MARGIN_CHARS;
        boolean nearEnd = current.start() + current.codePoints() < documentLength &&
                relative + WINDOW_CHARS + PAGE_CACHE_PREFETCH_MARGIN_CHARS > current.codePoints();
        if (!nearStart && !nearEnd) return;

        long generation = pageCacheGeneration.incrementAndGet();
        PAGE_PREFETCH.execute(() -> {
            if (generation != pageCacheGeneration.get()) return;
            long temporary = 0;
            try {
                temporary = NativeCore.open(file);
                long start = pageWindowStart(target, documentLength);
                String text = NativeCore.read(temporary, start, PAGE_CACHE_CHARS);
                PageWindow next = new PageWindow(file, start, text, text.codePointCount(0, text.length()));
                if (generation == pageCacheGeneration.get() && file.equals(documentFile) && length == documentLength) {
                    pageWindow = next;
                }
            } catch (IOException error) {
                // Cache refill is opportunistic. The authoritative native page read remains the fallback.
            } finally {
                if (temporary != 0) NativeCore.nativeClose(temporary);
            }
        });
    }

    private static long pageWindowStart(long target, long documentLength) {
        long preferred = Math.max(0, target - PAGE_CACHE_BACK_CHARS);
        long aligned = (preferred / PAGE_CACHE_ALIGN_CHARS) * PAGE_CACHE_ALIGN_CHARS;
        long maximum = Math.max(0, documentLength - PAGE_CACHE_CHARS);
        return Math.min(aligned, maximum);
    }

    private void ensureOpen() throws IOException {
        if (handle == 0 || documentFile == null) throw new IOException("no document open");
    }

    private static final long PAGE_CACHE_ALIGN_CHARS = 4096L;
    private static final long PAGE_CACHE_BACK_CHARS = 8192L;
    private static final long PAGE_CACHE_PREFETCH_MARGIN_CHARS = 8192L;
}
