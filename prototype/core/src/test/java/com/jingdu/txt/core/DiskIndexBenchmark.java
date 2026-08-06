package com.jingdu.txt.core;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;

public final class DiskIndexBenchmark {
    public static void main(String[] args) throws Exception {
        int mebibytes = args.length == 0 ? 10 : Integer.parseInt(args[0]);
        int chapterCount = args.length < 2 ? 1000 : Integer.parseInt(args[1]);
        if (mebibytes <= 0 || mebibytes > 512) {
            throw new IllegalArgumentException("size must be between 1 and 512 MiB");
        }
        if (chapterCount < 0 || chapterCount > 100000) {
            throw new IllegalArgumentException("chapterCount must be between 0 and 100000");
        }
        Path temporary = Files.createTempDirectory("jingdu-disk-index-benchmark-");
        try {
            Path source = temporary.resolve("normalized.txt");
            Path indexRoot = temporary.resolve("index");
            generate(source, mebibytes, chapterCount);
            String sha256 = sha256(source);
            long buildStarted = System.nanoTime();
            DiskIndexBuildResult build = new DiskDocumentIndexBuilder().build(
                    source, indexRoot, "benchmark-v1", sha256, null);
            long buildNanos = System.nanoTime() - buildStarted;

            DiskDocumentIndex index = DiskDocumentIndex.openActive(indexRoot);
            long queryStarted = System.nanoTime();
            int hits = index.search("磁盘索引关键词", 20, "benchmark-v1").size();
            long queryNanos = System.nanoTime() - queryStarted;
            long heapBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long indexBytes = directoryBytes(indexRoot);
            System.out.println("{"
                    + "\"requested_mib\":" + mebibytes + ","
                    + "\"source_bytes\":" + Files.size(source) + ","
                    + "\"index_bytes\":" + indexBytes + ","
                    + "\"segments\":" + build.getSegmentCount() + ","
                    + "\"chapters\":" + index.getChapters().size() + ","
                    + "\"build_ms\":" + nanosToMillis(buildNanos) + ","
                    + "\"query_ms\":" + nanosToMillis(queryNanos) + ","
                    + "\"query_hits_limited\":" + hits + ","
                    + "\"heap_after_mib\":" + (heapBytes / 1024.0 / 1024.0)
                    + "}");
        } finally {
            deleteTree(temporary);
        }
    }

    private static void generate(Path target, int mebibytes, int chapterCount) throws IOException {
        long minimumBytes = mebibytes * 1024L * 1024L;
        byte[] body = "磁盘索引关键词用于验证候选分段回读，哈希碰撞不能产生假命中。\n"
                .getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = Files.newOutputStream(target)) {
            for (int chapter = 1; chapter <= chapterCount; chapter++) {
                output.write(("第" + chapter + "章 合成章节\n").getBytes(StandardCharsets.UTF_8));
                output.write(body);
            }
            long written = Files.size(target);
            while (written < minimumBytes) {
                output.write(body);
                written += body.length;
            }
        }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[128 * 1024];
        try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) {
            result.append(String.format("%02x", value & 0xFF));
        }
        return result.toString();
    }

    private static long directoryBytes(Path root) throws IOException {
        final long[] total = new long[] {0};
        Files.walk(root).filter(Files::isRegularFile).forEach(path -> {
            try {
                total[0] += Files.size(path);
            } catch (IOException error) {
                throw new RuntimeException(error);
            }
        });
        return total[0];
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
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
}
