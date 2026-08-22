package com.jingdu.txt.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GeneratedArtifactPruner {
    private static final Pattern REPAIR_FILE = Pattern.compile(
            "^(repair-[0-9a-f]+)\\.(utf8\\.txt|projection\\.bin|candidates\\.bin)(?:\\.tmp)?$");
    private static final Pattern REVISION = Pattern.compile("^[0-9a-f]{64}$");

    public Result prune(Path bookDirectory, Set<Path> protectedPaths,
            Set<String> protectedIndexRevisions, long maximumBytes,
            int maximumInactiveGroups) throws IOException {
        if (maximumBytes < 0 || maximumInactiveGroups < 0) {
            throw new IllegalArgumentException("prune limits must not be negative");
        }
        Path root = bookDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return new Result(0, 0, 0, 0, 0, false);
        }
        Set<Path> keep = new HashSet<Path>();
        for (Path path : protectedPaths) {
            if (path != null) {
                Path normalized = path.toAbsolutePath().normalize();
                if (!normalized.startsWith(root)) {
                    throw new IllegalArgumentException("protected path leaves book directory");
                }
                keep.add(normalized);
            }
        }

        Map<String, Group> repairGroups = new HashMap<String, Group>();
        try (java.util.stream.Stream<Path> children = Files.list(root)) {
            children.forEach(path -> {
                Matcher matcher = REPAIR_FILE.matcher(path.getFileName().toString());
                if (matcher.matches() && Files.isRegularFile(path)) {
                    repairGroups.computeIfAbsent(matcher.group(1), Group::new).paths.add(path);
                }
            });
        }
        List<Group> groups = new ArrayList<Group>(repairGroups.values());
        Path indexes = root.resolve("indexes");
        if (Files.isDirectory(indexes)) {
            try (java.util.stream.Stream<Path> revisions = Files.list(indexes)) {
                revisions.forEach(path -> {
                    String name = path.getFileName().toString();
                    if (REVISION.matcher(name).matches() && Files.isDirectory(path)) {
                        Group group = new Group("index-" + name);
                        group.paths.add(path);
                        group.protectedGroup = protectedIndexRevisions.contains(name);
                        groups.add(group);
                    }
                });
            }
        }

        long protectedBytes = 0;
        List<Group> candidates = new ArrayList<Group>();
        for (Group group : groups) {
            group.inspect(keep);
            if (group.protectedGroup) {
                protectedBytes += group.bytes;
            } else {
                candidates.add(group);
            }
        }
        Collections.sort(candidates, Comparator.comparingLong((Group group) -> group.modified)
                .reversed().thenComparing(group -> group.id));

        long retainedBytes = protectedBytes;
        int retainedGroups = 0;
        int deletedGroups = 0;
        long reclaimedBytes = 0;
        for (Group group : candidates) {
            if (retainedGroups < maximumInactiveGroups
                    && retainedBytes + group.bytes <= maximumBytes) {
                retainedGroups++;
                retainedBytes += group.bytes;
                continue;
            }
            for (Path path : group.paths) {
                deleteGeneratedPath(root, path);
            }
            deletedGroups++;
            reclaimedBytes += group.bytes;
        }
        return new Result(deletedGroups, retainedGroups, reclaimedBytes,
                protectedBytes, retainedBytes, protectedBytes > maximumBytes);
    }

    private static void deleteGeneratedPath(Path root, Path target) throws IOException {
        Path normalized = target.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || normalized.equals(root)) {
            throw new IOException("refusing generated artifact deletion outside book directory");
        }
        if (Files.isDirectory(normalized)) {
            try (java.util.stream.Stream<Path> paths = Files.walk(normalized)) {
                List<Path> ordered = new ArrayList<Path>();
                paths.forEach(ordered::add);
                Collections.sort(ordered, Comparator.reverseOrder());
                for (Path path : ordered) {
                    Files.deleteIfExists(path);
                }
            }
        } else {
            Files.deleteIfExists(normalized);
        }
    }

    private static long pathSize(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            return Files.size(path);
        }
        final long[] bytes = new long[] {0};
        try (java.util.stream.Stream<Path> paths = Files.walk(path)) {
            paths.filter(Files::isRegularFile).forEach(file -> {
                try {
                    bytes[0] += Files.size(file);
                } catch (IOException error) {
                    throw new SizeFailure(error);
                }
            });
        } catch (SizeFailure failure) {
            throw failure.cause;
        }
        return bytes[0];
    }

    private static final class Group {
        final String id;
        final List<Path> paths = new ArrayList<Path>();
        long bytes;
        long modified;
        boolean protectedGroup;

        Group(String id) {
            this.id = id;
        }

        void inspect(Set<Path> keep) throws IOException {
            for (Path path : paths) {
                Path normalized = path.toAbsolutePath().normalize();
                protectedGroup |= keep.contains(normalized);
                bytes += pathSize(path);
                modified = Math.max(modified, Files.getLastModifiedTime(path).toMillis());
            }
        }
    }

    private static final class SizeFailure extends RuntimeException {
        final IOException cause;

        SizeFailure(IOException cause) {
            super(cause);
            this.cause = cause;
        }
    }

    public static final class Result {
        public final int deletedGroups;
        public final int retainedInactiveGroups;
        public final long reclaimedBytes;
        public final long protectedBytes;
        public final long retainedBytes;
        public final boolean protectedDataExceedsLimit;

        Result(int deletedGroups, int retainedInactiveGroups, long reclaimedBytes,
                long protectedBytes, long retainedBytes, boolean protectedDataExceedsLimit) {
            this.deletedGroups = deletedGroups;
            this.retainedInactiveGroups = retainedInactiveGroups;
            this.reclaimedBytes = reclaimedBytes;
            this.protectedBytes = protectedBytes;
            this.retainedBytes = retainedBytes;
            this.protectedDataExceedsLimit = protectedDataExceedsLimit;
        }
    }
}
