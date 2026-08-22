package com.jingdu.txt.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Platform-neutral auto-scroll speed and frame-time contract. */
public final class AutoScrollPolicy {
    private static final int MIN_SPEED_DP_PER_SECOND = 8;
    private static final int MAX_SPEED_DP_PER_SECOND = 120;
    private static final int DEFAULT_SPEED_DP_PER_SECOND = 28;
    private static final long MAX_FRAME_ELAPSED_NANOS = 100_000_000L;
    private static final Pattern CONTRACT = Pattern.compile(
            "\\{\\\"speedDpPerSecond\\\":([0-9]+)\\}");

    private final int speedDpPerSecond;

    public AutoScrollPolicy(int speedDpPerSecond) {
        if (speedDpPerSecond < MIN_SPEED_DP_PER_SECOND
                || speedDpPerSecond > MAX_SPEED_DP_PER_SECOND) {
            throw new IllegalArgumentException("auto-scroll speed is out of range");
        }
        this.speedDpPerSecond = speedDpPerSecond;
    }

    public static AutoScrollPolicy defaults() {
        return new AutoScrollPolicy(DEFAULT_SPEED_DP_PER_SECOND);
    }

    public int getSpeedDpPerSecond() {
        return speedDpPerSecond;
    }

    public double distanceDp(long elapsedNanos) {
        if (elapsedNanos < 0) {
            throw new IllegalArgumentException("frame elapsed time must not be negative");
        }
        long boundedElapsed = Math.min(elapsedNanos, MAX_FRAME_ELAPSED_NANOS);
        return speedDpPerSecond * (boundedElapsed / 1_000_000_000.0);
    }

    public String toJson() {
        return "{\"speedDpPerSecond\":" + speedDpPerSecond + "}";
    }

    public static AutoScrollPolicy fromJson(String value) {
        if (value == null || value.length() > 64) {
            throw new IllegalArgumentException("invalid auto-scroll policy contract");
        }
        Matcher matcher = CONTRACT.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid auto-scroll policy contract");
        }
        try {
            AutoScrollPolicy decoded = new AutoScrollPolicy(
                    Integer.parseInt(matcher.group(1)));
            if (!decoded.toJson().equals(value)) {
                throw new IllegalArgumentException(
                        "non-canonical auto-scroll policy contract");
            }
            return decoded;
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("invalid auto-scroll policy contract", invalid);
        }
    }

    public static int minimumSpeedDpPerSecond() {
        return MIN_SPEED_DP_PER_SECOND;
    }

    public static int maximumSpeedDpPerSecond() {
        return MAX_SPEED_DP_PER_SECOND;
    }
}
