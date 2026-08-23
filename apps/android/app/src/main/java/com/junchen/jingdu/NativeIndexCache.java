package com.junchen.jingdu;

import java.io.File;

final class NativeIndexCache {
    private NativeIndexCache() {}

    static void pruneOrphans(File directory) {
        if (directory == null) return;
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            if (name.endsWith(".jdx.tmp")) {
                delete(file);
                continue;
            }
            if (!name.endsWith(".jdx")) continue;
            String sourceName = name.substring(0, name.length() - 4);
            if (!new File(directory, sourceName).isFile()) delete(file);
        }
    }

    private static void delete(File file) {
        if (file.exists() && !file.delete()) file.deleteOnExit();
    }
}
