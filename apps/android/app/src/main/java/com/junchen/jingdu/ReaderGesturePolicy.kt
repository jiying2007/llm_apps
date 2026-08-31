package com.junchen.jingdu

import kotlin.math.abs

/**
 * Pure gesture arbitration shared by the pointer runtime and regression tests. Interaction
 * correctness owns this boundary: one Reader pointer owner observes the complete stream, including
 * multi-touch pinch, while SelectionContainer may still consume slow selection drags. A parallel
 * transform detector is intentionally forbidden because it can steal real single-finger taps.
 * Performance optimizations must not make paging/taps unreachable or reinterpret selection.
 */
internal object ReaderGesturePolicy {
    private const val CONSUMED_SWIPE_MAX_MS = 450L
    private const val DOUBLE_TAP_MIN_MS = 40L
    private const val DOUBLE_TAP_MAX_MS = 320L

    /**
     * SelectionContainer is allowed to consume slow/ambiguous drags. A short, strongly horizontal
     * swipe still counts as paging even when the selection layer observed it first.
     */
    fun allowsPageSwipe(
        consumedByChild: Boolean,
        durationMs: Long,
        deltaX: Float,
        deltaY: Float,
        thresholdPx: Float,
    ): Boolean {
        val horizontal = abs(deltaX) >= thresholdPx && abs(deltaX) > abs(deltaY) * 1.25f
        if (!horizontal) return false
        if (!consumedByChild) return true
        return durationMs <= CONSUMED_SWIPE_MAX_MS &&
            abs(deltaX) >= thresholdPx * 1.25f &&
            abs(deltaX) > abs(deltaY) * 1.75f
    }

    fun isDoubleTap(previousTapAt: Long, tapAt: Long): Boolean =
        previousTapAt > 0L && tapAt - previousTapAt in DOUBLE_TAP_MIN_MS..DOUBLE_TAP_MAX_MS
}
