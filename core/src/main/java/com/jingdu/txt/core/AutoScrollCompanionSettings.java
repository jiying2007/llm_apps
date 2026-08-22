package com.jingdu.txt.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Platform-neutral touch-resume and screen-awake settings. */
public final class AutoScrollCompanionSettings {
    private static final int[] RESUME_DELAY_OPTIONS_SECONDS = {0, 3, 5, 10};
    private static final Pattern CONTRACT = Pattern.compile(
            "\\{\\\"resumeDelaySeconds\\\":([0-9]+),"
                    + "\\\"keepScreenOn\\\":(true|false)\\}");

    private final int resumeDelaySeconds;
    private final boolean keepScreenOn;

    public AutoScrollCompanionSettings(int resumeDelaySeconds, boolean keepScreenOn) {
        requireOption(resumeDelaySeconds);
        this.resumeDelaySeconds = resumeDelaySeconds;
        this.keepScreenOn = keepScreenOn;
    }

    public static AutoScrollCompanionSettings defaults() {
        return new AutoScrollCompanionSettings(0, false);
    }

    public int getResumeDelaySeconds() {
        return resumeDelaySeconds;
    }

    public boolean isKeepScreenOn() {
        return keepScreenOn;
    }

    public String toJson() {
        return "{\"resumeDelaySeconds\":" + resumeDelaySeconds
                + ",\"keepScreenOn\":" + keepScreenOn + "}";
    }

    public static AutoScrollCompanionSettings fromJson(String value) {
        if (value == null || value.length() > 96) {
            throw new IllegalArgumentException("invalid auto-scroll companion settings");
        }
        Matcher matcher = CONTRACT.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid auto-scroll companion settings");
        }
        try {
            AutoScrollCompanionSettings decoded = new AutoScrollCompanionSettings(
                    Integer.parseInt(matcher.group(1)),
                    Boolean.parseBoolean(matcher.group(2)));
            if (!decoded.toJson().equals(value)) {
                throw new IllegalArgumentException(
                        "non-canonical auto-scroll companion settings");
            }
            return decoded;
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(
                    "invalid auto-scroll companion settings", invalid);
        }
    }

    public static int[] resumeDelayOptionsSeconds() {
        return RESUME_DELAY_OPTIONS_SECONDS.clone();
    }

    private static void requireOption(int value) {
        for (int option : RESUME_DELAY_OPTIONS_SECONDS) {
            if (value == option) {
                return;
            }
        }
        throw new IllegalArgumentException("unsupported auto-scroll resume delay");
    }
}
