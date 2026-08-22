package com.jingdu.txt.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Platform-neutral screen-orientation preference for the reader. */
public final class ReaderDisplayPolicy {
    public enum Orientation {
        FOLLOW_SYSTEM,
        PORTRAIT,
        LANDSCAPE
    }

    private static final Pattern CONTRACT = Pattern.compile(
            "\\{\\\"orientation\\\":\\\"(FOLLOW_SYSTEM|PORTRAIT|LANDSCAPE)\\\"\\}");
    private final Orientation orientation;

    public ReaderDisplayPolicy(Orientation orientation) {
        if (orientation == null) {
            throw new IllegalArgumentException("reader orientation is required");
        }
        this.orientation = orientation;
    }

    public static ReaderDisplayPolicy defaults() {
        return new ReaderDisplayPolicy(Orientation.FOLLOW_SYSTEM);
    }

    public Orientation getOrientation() {
        return orientation;
    }

    public String toJson() {
        return "{\"orientation\":\"" + orientation.name() + "\"}";
    }

    public static ReaderDisplayPolicy fromJson(String value) {
        if (value == null || value.length() > 64) {
            throw new IllegalArgumentException("invalid reader display policy");
        }
        Matcher matcher = CONTRACT.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid reader display policy");
        }
        ReaderDisplayPolicy decoded;
        try {
            decoded = new ReaderDisplayPolicy(Orientation.valueOf(matcher.group(1)));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("invalid reader display policy", invalid);
        }
        if (!decoded.toJson().equals(value)) {
            throw new IllegalArgumentException("non-canonical reader display policy");
        }
        return decoded;
    }
}
