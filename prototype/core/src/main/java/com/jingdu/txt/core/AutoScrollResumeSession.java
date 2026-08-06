package com.jingdu.txt.core;

/** Monotonic, context-bound touch-resume countdown. */
public final class AutoScrollResumeSession {
    private AutoScrollCompanionSettings settings;
    private String contextToken = "";
    private int anchor = -1;
    private long deadlineElapsedMillis = -1;

    public AutoScrollResumeSession(AutoScrollCompanionSettings settings) {
        setSettings(settings);
    }

    public void setSettings(AutoScrollCompanionSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("auto-scroll companion settings are required");
        }
        this.settings = settings;
        cancel();
    }

    public boolean arm(String contextToken, int anchor, long nowElapsedMillis) {
        requireContext(contextToken, anchor, nowElapsedMillis);
        cancel();
        if (settings.getResumeDelaySeconds() == 0) {
            return false;
        }
        long delayMillis = settings.getResumeDelaySeconds() * 1000L;
        if (nowElapsedMillis > Long.MAX_VALUE - delayMillis) {
            throw new IllegalArgumentException("auto-scroll resume deadline overflows");
        }
        this.contextToken = contextToken;
        this.anchor = anchor;
        this.deadlineElapsedMillis = nowElapsedMillis + delayMillis;
        return true;
    }

    public int remainingSeconds(long nowElapsedMillis) {
        if (nowElapsedMillis < 0) {
            throw new IllegalArgumentException("elapsed time must not be negative");
        }
        if (!isArmed() || nowElapsedMillis >= deadlineElapsedMillis) {
            return 0;
        }
        return (int) ((deadlineElapsedMillis - nowElapsedMillis + 999L) / 1000L);
    }

    public boolean consumeIfDue(String currentContextToken, int currentAnchor,
            long nowElapsedMillis) {
        requireContext(currentContextToken, currentAnchor, nowElapsedMillis);
        if (!isArmed()) {
            return false;
        }
        if (!contextToken.equals(currentContextToken) || anchor != currentAnchor) {
            cancel();
            return false;
        }
        if (nowElapsedMillis < deadlineElapsedMillis) {
            return false;
        }
        cancel();
        return true;
    }

    public void cancel() {
        contextToken = "";
        anchor = -1;
        deadlineElapsedMillis = -1;
    }

    public boolean isArmed() {
        return deadlineElapsedMillis >= 0;
    }

    private static void requireContext(
            String contextToken, int anchor, long nowElapsedMillis) {
        if (contextToken == null || contextToken.isEmpty() || contextToken.length() > 128
                || anchor < 0 || nowElapsedMillis < 0) {
            throw new IllegalArgumentException("invalid auto-scroll resume context");
        }
    }
}
