package com.jingdu.txt.core;

import java.nio.charset.Charset;

public final class DetectedEncoding {
    public enum SelectionMode {
        AUTO_DETECTED,
        MANUAL_OVERRIDE
    }

    public enum Advisory {
        NONE,
        BIG5_HEURISTIC,
        LEGACY_AMBIGUOUS,
        MALFORMED_FALLBACK
    }

    private final Charset charset;
    private final int bomLength;
    private final double confidence;
    private final String reason;
    private final SelectionMode selectionMode;
    private final Advisory advisory;

    public DetectedEncoding(Charset charset, int bomLength, double confidence, String reason) {
        this(charset, bomLength, confidence, reason, SelectionMode.AUTO_DETECTED,
                Advisory.NONE);
    }

    public DetectedEncoding(Charset charset, int bomLength, double confidence, String reason,
            SelectionMode selectionMode) {
        this(charset, bomLength, confidence, reason, selectionMode, Advisory.NONE);
    }

    public DetectedEncoding(Charset charset, int bomLength, double confidence, String reason,
            SelectionMode selectionMode, Advisory advisory) {
        if (charset == null || bomLength < 0 || !Double.isFinite(confidence)
                || confidence < 0 || confidence > 1
                || reason == null || reason.isEmpty() || selectionMode == null
                || advisory == null
                || (selectionMode == SelectionMode.MANUAL_OVERRIDE
                        && advisory != Advisory.NONE)) {
            throw new IllegalArgumentException("invalid detected encoding");
        }
        this.charset = charset;
        this.bomLength = bomLength;
        this.confidence = confidence;
        this.reason = reason;
        this.selectionMode = selectionMode;
        this.advisory = advisory;
    }

    public Charset getCharset() {
        return charset;
    }

    public int getBomLength() {
        return bomLength;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getReason() {
        return reason;
    }

    public SelectionMode getSelectionMode() {
        return selectionMode;
    }

    public Advisory getAdvisory() {
        return advisory;
    }
}
