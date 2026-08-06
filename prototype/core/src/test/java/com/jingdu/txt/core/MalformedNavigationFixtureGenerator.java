package com.jingdu.txt.core;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Generates a deterministic cross-window malformed UTF-8 device-smoke fixture. */
public final class MalformedNavigationFixtureGenerator {
    public static final int REPLACEMENT_COUNT = 130;
    private static final int FILLER_CHARACTERS = 2048;
    private static final String SOURCE_NAME = "jingdu-malformed-navigation.txt";
    private static final String MANIFEST_NAME =
            "jingdu-malformed-navigation.expected.txt";

    private MalformedNavigationFixtureGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: <output-directory>");
        }
        Fixture fixture = generate(Paths.get(args[0]));
        System.out.println("{\"source\":\"" + fixture.getSource()
                + "\",\"manifest\":\"" + fixture.getManifest()
                + "\",\"source_bytes\":" + Files.size(fixture.getSource())
                + ",\"replacement_count\":" + REPLACEMENT_COUNT
                + ",\"retained_count\":"
                + DecodingReplacement.MAXIMUM_RETAINED + "}");
    }

    static Fixture generate(Path outputDirectory) throws IOException {
        if (outputDirectory == null) {
            throw new IllegalArgumentException("output directory is required");
        }
        Files.createDirectories(outputDirectory);
        Path source = outputDirectory.resolve(SOURCE_NAME);
        Path manifest = outputDirectory.resolve(MANIFEST_NAME);
        List<DecodingReplacement> locations = new ArrayList<>();
        long sourceOffset = 0;
        long characterOffset = 0;
        String header = "净读乱码导航设备夹具\n";
        String filler = repeat('a', FILLER_CHARACTERS);
        try (OutputStream output = Files.newOutputStream(source)) {
            byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
            output.write(headerBytes);
            sourceOffset += headerBytes.length;
            characterOffset += header.length();
            for (int index = 1; index <= REPLACEMENT_COUNT; index++) {
                String prefix = filler + "\nERROR-" + threeDigits(index) + "=>";
                byte[] prefixBytes = prefix.getBytes(StandardCharsets.US_ASCII);
                output.write(prefixBytes);
                sourceOffset += prefixBytes.length;
                characterOffset += prefix.length();
                locations.add(new DecodingReplacement(sourceOffset,
                        characterOffset));
                output.write(0xFF);
                sourceOffset++;
                characterOffset++;
                String suffix = "<=ERROR-" + threeDigits(index) + "\n";
                byte[] suffixBytes = suffix.getBytes(StandardCharsets.US_ASCII);
                output.write(suffixBytes);
                sourceOffset += suffixBytes.length;
                characterOffset += suffix.length();
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("fixture=" + SOURCE_NAME);
        lines.add("encoding=UTF-8");
        lines.add("replacement_count=" + REPLACEMENT_COUNT);
        lines.add("retained_count=" + DecodingReplacement.MAXIMUM_RETAINED);
        lines.add("source_bytes=" + Files.size(source));
        lines.add("source_sha256=" + sha256(source));
        for (int index = 0; index < locations.size(); index++) {
            DecodingReplacement location = locations.get(index);
            String kind = index < DecodingReplacement.MAXIMUM_RETAINED
                    ? "navigation_" : "unretained_";
            lines.add(kind + threeDigits(index + 1) + "="
                    + location.getSourceByteOffset() + ","
                    + location.getNormalizedCharacterOffset());
        }
        Files.write(manifest, lines, StandardCharsets.UTF_8);
        return new Fixture(source, manifest, locations);
    }

    private static String repeat(char value, int count) {
        StringBuilder repeated = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            repeated.append(value);
        }
        return repeated.toString();
    }

    private static String threeDigits(int value) {
        if (value < 1 || value > 999) {
            throw new IllegalArgumentException("fixture index is out of range");
        }
        if (value < 10) {
            return "00" + value;
        }
        if (value < 100) {
            return "0" + value;
        }
        return Integer.toString(value);
    }

    private static String sha256(Path source) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
        byte[] buffer = new byte[16 * 1024];
        try (java.io.InputStream input = Files.newInputStream(source)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hex.append(String.format("%02x", value & 0xFF));
        }
        return hex.toString();
    }

    static final class Fixture {
        private final Path source;
        private final Path manifest;
        private final List<DecodingReplacement> expectedLocations;

        private Fixture(Path source, Path manifest,
                List<DecodingReplacement> expectedLocations) {
            this.source = source;
            this.manifest = manifest;
            this.expectedLocations = Collections.unmodifiableList(
                    new ArrayList<DecodingReplacement>(expectedLocations));
        }

        Path getSource() {
            return source;
        }

        Path getManifest() {
            return manifest;
        }

        List<DecodingReplacement> getExpectedLocations() {
            return expectedLocations;
        }
    }
}
