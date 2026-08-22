package com.jingdu.txt.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

public final class RepairFileBenchmark {
    private static final String LINE = "第000001章 净读大文件基准。祂遇到錯別字，也遇到不规则换行；原文保持不可变。\n";

    public static void main(String[] args) throws Exception {
        int sizeMiB = args.length == 0 ? 10 : Integer.parseInt(args[0]);
        int exclusionCount = args.length < 2 ? 0 : Integer.parseInt(args[1]);
        Path temporary = Files.createTempDirectory("jingdu-repair-benchmark-");
        Path source = temporary.resolve("source.txt");
        Path derived = temporary.resolve("derived.txt");
        Path projection = temporary.resolve("projection.bin");
        Path candidates = temporary.resolve("candidates.bin");
        try {
            createSource(source, sizeMiB);
            long sourceBytes = Files.size(source);
            long started = System.nanoTime();
            List<RepairOccurrence> exclusions = new ArrayList<RepairOccurrence>();
            int matchInLine = LINE.indexOf("祂");
            for (int index = 0; index < exclusionCount; index++) {
                exclusions.add(new RepairOccurrence("benchmark",
                        (long) index * LINE.length() + matchInLine));
            }
            RepairFileResult result = new RepairFilePipeline().apply(source, derived, projection,
                    candidates,
                    Collections.singletonList(new RepairRule("benchmark", "祂", "他", true, 0)),
                    RepairSelection.excluding(exclusions), 0, 10);
            long elapsedNanos = System.nanoTime() - started;
            System.out.printf("sizeMiB=%.1f matches=%d candidates=%d exclusions=%d "
                            + "elapsedMs=%.1f throughputMiBps=%.1f "
                            + "projectionMiB=%.1f candidatesMiB=%.1f "
                            + "derivedMiB=%.1f sourceUnchanged=%s%n",
                    sourceBytes / 1024.0 / 1024.0, result.getMatchCount(),
                    result.getCandidateMatchCount(), exclusionCount,
                    elapsedNanos / 1_000_000.0,
                    sourceBytes / 1024.0 / 1024.0 / (elapsedNanos / 1_000_000_000.0),
                    Files.size(projection) / 1024.0 / 1024.0,
                    Files.size(candidates) / 1024.0 / 1024.0,
                    Files.size(derived) / 1024.0 / 1024.0,
                    result.getSourceSha256().equals(DiskDocumentIndexBuilder.computeSha256(source)));
            long lastPageOffset = Math.max(0,
                    result.getCandidateMatchCount() - 20);
            long derivedBytes = Files.size(derived);
            FileTime derivedModified = Files.getLastModifiedTime(derived);
            long pageStarted = System.nanoTime();
            RepairPreviewPage lastPage = new RepairFilePreviewPager().readPage(source,
                    Collections.singletonList(new RepairRule(
                            "benchmark", "祂", "他", true, 0)),
                    RepairSelection.excluding(exclusions), lastPageOffset, 20);
            long pageElapsedNanos = System.nanoTime() - pageStarted;
            boolean derivedUnchanged = derivedBytes == Files.size(derived)
                    && derivedModified.equals(Files.getLastModifiedTime(derived));
            System.out.printf("previewPageOffset=%d pageMatches=%d hasNext=%s "
                            + "elapsedMs=%.1f derivedUnchanged=%s%n",
                    lastPageOffset, lastPage.getMatches().size(), lastPage.hasNext(),
                    pageElapsedNanos / 1_000_000.0, derivedUnchanged);

            Path textIndexRoot = temporary.resolve("text-index");
            new DiskDocumentIndexBuilder().build(source, textIndexRoot, "benchmark-source",
                    result.getSourceSha256(), null);
            DiskDocumentIndex textIndex = DiskDocumentIndex.openActive(textIndexRoot);
            long indexedStarted = System.nanoTime();
            RepairPreviewPage indexedPage;
            try (DiskRepairCandidateIndex candidateIndex = DiskRepairCandidateIndex.open(
                    candidates, result.getSourceSha256(), Collections.singletonList(
                            new RepairRule("benchmark", "祂", "他", true, 0)))) {
                indexedPage = candidateIndex.readPage(textIndex,
                        RepairSelection.excluding(exclusions), lastPageOffset, 20);
            }
            long indexedElapsedNanos = System.nanoTime() - indexedStarted;
            System.out.printf("indexedPreviewPageOffset=%d pageMatches=%d hasNext=%s "
                            + "elapsedMs=%.1f speedup=%.1fx%n",
                    lastPageOffset, indexedPage.getMatches().size(), indexedPage.hasNext(),
                    indexedElapsedNanos / 1_000_000.0,
                    (double) pageElapsedNanos / Math.max(1L, indexedElapsedNanos));
        } finally {
            deleteTree(temporary);
        }
    }

    private static void createSource(Path path, int sizeMiB) throws IOException {
        long targetBytes = sizeMiB * 1024L * 1024L;
        byte[] lineBytes = LINE.getBytes(StandardCharsets.UTF_8);
        long written = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            while (written < targetBytes) {
                writer.write(LINE);
                written += lineBytes.length;
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException error) {
                    throw new RuntimeException(error);
                }
            });
        }
    }
}
