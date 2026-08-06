package com.jingdu.txt.core;

import java.nio.file.Path;

public final class DiskIndexBuildResult {
    private final Path revisionDirectory;
    private final int segmentCount;
    private final int chapterCount;
    private final long sourceBytes;
    private final long buildNanos;
    private final boolean resumed;
    private final boolean chapterListTruncated;

    public DiskIndexBuildResult(Path revisionDirectory, int segmentCount, int chapterCount,
            long sourceBytes, long buildNanos, boolean resumed) {
        this(revisionDirectory, segmentCount, chapterCount, sourceBytes, buildNanos,
                resumed, false);
    }

    public DiskIndexBuildResult(Path revisionDirectory, int segmentCount, int chapterCount,
            long sourceBytes, long buildNanos, boolean resumed, boolean chapterListTruncated) {
        this.revisionDirectory = revisionDirectory;
        this.segmentCount = segmentCount;
        this.chapterCount = chapterCount;
        this.sourceBytes = sourceBytes;
        this.buildNanos = buildNanos;
        this.resumed = resumed;
        this.chapterListTruncated = chapterListTruncated;
    }

    public Path getRevisionDirectory() { return revisionDirectory; }
    public int getSegmentCount() { return segmentCount; }
    public int getChapterCount() { return chapterCount; }
    public long getSourceBytes() { return sourceBytes; }
    public long getBuildNanos() { return buildNanos; }
    public boolean isResumed() { return resumed; }
    public boolean isChapterListTruncated() { return chapterListTruncated; }
}
