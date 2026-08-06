package com.jingdu.txt.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cross-platform reading mode and volume-key navigation contract. */
public final class ReaderNavigationSettings {
    public enum ReadingMode {
        CONTINUOUS_SCROLL,
        PAGED
    }

    public enum VolumeKeyMode {
        OFF,
        DOWN_FORWARD,
        UP_FORWARD
    }

    public enum VolumeKey {
        UP,
        DOWN
    }

    public enum Direction {
        NONE,
        FORWARD,
        BACKWARD
    }

    private static final Pattern CONTRACT = Pattern.compile(
            "\\{\\\"version\\\":1,\\\"readingMode\\\":\\\""
                    + "(CONTINUOUS_SCROLL|PAGED)\\\",\\\"volumeKeyMode\\\":\\\""
                    + "(OFF|DOWN_FORWARD|UP_FORWARD)\\\"\\}");

    private final ReadingMode readingMode;
    private final VolumeKeyMode volumeKeyMode;

    public ReaderNavigationSettings(ReadingMode readingMode,
            VolumeKeyMode volumeKeyMode) {
        if (readingMode == null || volumeKeyMode == null) {
            throw new IllegalArgumentException("reader navigation settings are required");
        }
        this.readingMode = readingMode;
        this.volumeKeyMode = volumeKeyMode;
    }

    public static ReaderNavigationSettings defaults() {
        return new ReaderNavigationSettings(
                ReadingMode.CONTINUOUS_SCROLL, VolumeKeyMode.OFF);
    }

    public ReadingMode getReadingMode() {
        return readingMode;
    }

    public VolumeKeyMode getVolumeKeyMode() {
        return volumeKeyMode;
    }

    public Direction directionFor(VolumeKey key) {
        if (key == null) {
            throw new IllegalArgumentException("volume key is required");
        }
        if (volumeKeyMode == VolumeKeyMode.OFF) {
            return Direction.NONE;
        }
        boolean upIsForward = volumeKeyMode == VolumeKeyMode.UP_FORWARD;
        boolean forward = key == VolumeKey.UP ? upIsForward : !upIsForward;
        return forward ? Direction.FORWARD : Direction.BACKWARD;
    }

    public String toJson() {
        return "{\"version\":1,\"readingMode\":\"" + readingMode.name()
                + "\",\"volumeKeyMode\":\"" + volumeKeyMode.name() + "\"}";
    }

    public static ReaderNavigationSettings fromJson(String value) {
        if (value == null || value.length() > 128) {
            throw new IllegalArgumentException("invalid reader navigation settings");
        }
        Matcher matcher = CONTRACT.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid reader navigation settings");
        }
        ReaderNavigationSettings decoded;
        try {
            decoded = new ReaderNavigationSettings(
                    ReadingMode.valueOf(matcher.group(1)),
                    VolumeKeyMode.valueOf(matcher.group(2)));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(
                    "invalid reader navigation settings", invalid);
        }
        if (!decoded.toJson().equals(value)) {
            throw new IllegalArgumentException(
                    "non-canonical reader navigation settings");
        }
        return decoded;
    }
}
