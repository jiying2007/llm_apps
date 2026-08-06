package com.jingdu.txt.core;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class LargeFileBenchmark {
    public static void main(String[] args) throws Exception {
        int mebibytes = args.length == 0 ? 10 : Integer.parseInt(args[0]);
        if (mebibytes <= 0 || mebibytes > 512) {
            throw new IllegalArgumentException("size must be between 1 and 512 MiB");
        }
        Path temporary = Files.createTempDirectory("jingdu-benchmark-");
        try {
            Path source = temporary.resolve("source-" + mebibytes + "m.txt");
            Path target = temporary.resolve("normalized.txt");
            generate(source, mebibytes);
            final long[] firstWindowNanos = new long[] {-1L};
            long started = System.nanoTime();
            ImportResult result = new TextImportPipeline(new EncodingDetector())
                    .importFile(source, target, ImportEncodingPreference.automatic(),
                            new ImportProgressListener() {
                        @Override
                        public void onFirstWindowReady(Path file, long characters, long elapsedNanos) {
                            firstWindowNanos[0] = elapsedNanos;
                        }
                    });
            long elapsed = System.nanoTime() - started;
            long heapBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            System.out.println("{"
                    + "\"size_mib\":" + mebibytes + ","
                    + "\"source_bytes\":" + result.getSourceBytes() + ","
                    + "\"output_bytes\":" + result.getOutputBytes() + ","
                    + "\"first_window_ms\":" + nanosToMillis(firstWindowNanos[0]) + ","
                    + "\"total_ms\":" + nanosToMillis(elapsed) + ","
                    + "\"heap_after_mib\":" + (heapBytes / 1024.0 / 1024.0) + ","
                    + "\"encoding\":\"" + result.getEncoding().getCharset().name() + "\","
                    + "\"source_sha256\":\"" + result.getSourceSha256() + "\""
                    + "}");
        } finally {
            deleteTree(temporary);
        }
    }

    private static void generate(Path target, int mebibytes) throws IOException {
        byte[] line = "第0001章 测试章节\r\n祂遇到錯別字，这是一段用于大文件流式导入的原创合成文本。\r\n"
                .getBytes(StandardCharsets.UTF_8);
        long remaining = mebibytes * 1024L * 1024L;
        try (OutputStream output = Files.newOutputStream(target)) {
            while (remaining > 0) {
                int write = (int) Math.min(line.length, remaining);
                output.write(line, 0, write);
                remaining -= write;
            }
        }
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
