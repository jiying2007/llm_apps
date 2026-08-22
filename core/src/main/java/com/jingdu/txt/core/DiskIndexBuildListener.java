package com.jingdu.txt.core;

import java.io.IOException;

public interface DiskIndexBuildListener {
    void onSegmentCommitted(int completedSegments, long processedCharacters) throws IOException;
}
