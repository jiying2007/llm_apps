package com.jingdu.txt.core;

import java.nio.file.Path;

public interface ImportProgressListener {
    void onFirstWindowReady(Path readableTemporaryFile, long normalizedCharacters, long elapsedNanos);
}
