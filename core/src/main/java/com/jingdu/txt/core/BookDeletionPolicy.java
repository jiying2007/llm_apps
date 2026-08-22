package com.jingdu.txt.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resolves an authorized deletion journal against references that must remain live. */
public final class BookDeletionPolicy {
    public static final class Plan {
        private final List<String> fileNames;
        private final List<String> indexRevisions;

        Plan(List<String> fileNames, List<String> indexRevisions) {
            this.fileNames = Collections.unmodifiableList(fileNames);
            this.indexRevisions = Collections.unmodifiableList(indexRevisions);
        }

        public List<String> getFileNames() {
            return fileNames;
        }

        public List<String> getIndexRevisions() {
            return indexRevisions;
        }
    }

    public Plan resolve(BookDeletionJournal.Entry pending,
            List<BookLibraryEntry> remainingEntries) {
        if (pending == null || remainingEntries == null) {
            throw new IllegalArgumentException("deletion policy inputs are required");
        }
        for (BookLibraryEntry entry : remainingEntries) {
            if (entry == null) {
                throw new IllegalArgumentException("remaining book entry is required");
            }
            if (pending.getBookId().equals(entry.getBookId())) {
                throw new IllegalArgumentException("deleted book still exists in library");
            }
        }
        List<String> files = new ArrayList<String>();
        for (String fileName : pending.getFileNames()) {
            if (!isFileReferenced(remainingEntries, fileName)) {
                files.add(fileName);
            }
        }
        List<String> revisions = new ArrayList<String>();
        for (String revision : pending.getIndexRevisions()) {
            if (!isRevisionReferenced(remainingEntries, revision)) {
                revisions.add(revision);
            }
        }
        return new Plan(files, revisions);
    }

    private static boolean isFileReferenced(
            List<BookLibraryEntry> entries, String fileName) {
        for (BookLibraryEntry entry : entries) {
            if (fileName.equals(entry.getBaseFileName())
                    || fileName.equals(entry.getActiveFileName())
                    || fileName.equals(entry.getProjectionFileName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRevisionReferenced(
            List<BookLibraryEntry> entries, String revision) {
        for (BookLibraryEntry entry : entries) {
            if (revision.equals(entry.getBaseRevision())
                    || revision.equals(entry.getActiveRevision())) {
                return true;
            }
        }
        return false;
    }
}
