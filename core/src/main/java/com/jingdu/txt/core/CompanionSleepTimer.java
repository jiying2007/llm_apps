package com.jingdu.txt.core;

/** Stateful, platform-neutral sleep timer using a caller-provided monotonic clock. */
public final class CompanionSleepTimer {
    public static final long MAXIMUM_DURATION_MILLIS = 24L * 60L * 60L * 1000L;

    public enum Mode {
        OFF,
        DEADLINE,
        CHAPTER_END
    }

    private Mode mode = Mode.OFF;
    private long target;

    public void armForDuration(long nowMillis, long durationMillis) {
        if (nowMillis < 0) {
            throw new IllegalArgumentException("monotonic time must not be negative");
        }
        if (durationMillis <= 0 || durationMillis > MAXIMUM_DURATION_MILLIS) {
            throw new IllegalArgumentException("sleep duration is outside the supported range");
        }
        if (nowMillis > Long.MAX_VALUE - durationMillis) {
            throw new IllegalArgumentException("sleep deadline overflows");
        }
        mode = Mode.DEADLINE;
        target = nowMillis + durationMillis;
    }

    public void armForChapterEnd(long currentAnchor, long chapterEndAnchor) {
        if (currentAnchor < 0 || chapterEndAnchor <= currentAnchor) {
            throw new IllegalArgumentException("chapter end must be after the current anchor");
        }
        mode = Mode.CHAPTER_END;
        target = chapterEndAnchor;
    }

    /** Returns true once at expiry and atomically disarms the timer. */
    public boolean consumeIfExpired(long nowMillis, long currentAnchor) {
        if (nowMillis < 0 || currentAnchor < 0) {
            throw new IllegalArgumentException("timer inputs must not be negative");
        }
        boolean expired = (mode == Mode.DEADLINE && nowMillis >= target)
                || (mode == Mode.CHAPTER_END && currentAnchor >= target);
        if (expired) {
            cancel();
        }
        return expired;
    }

    public void cancel() {
        mode = Mode.OFF;
        target = 0;
    }

    public Mode getMode() {
        return mode;
    }

    public long getTarget() {
        return target;
    }

    public long remainingMillis(long nowMillis) {
        if (nowMillis < 0) {
            throw new IllegalArgumentException("monotonic time must not be negative");
        }
        if (mode != Mode.DEADLINE) {
            return 0;
        }
        return Math.max(0, target - nowMillis);
    }
}
