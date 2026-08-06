package com.jingdu.txt.core.port;

import com.jingdu.txt.core.ReaderTextSelection;

public interface ReaderSurface {
    enum Mode { CONTINUOUS_SCROLL, PAGED }

    void loadWindow(String bookId, String anchorId, int viewportCharacters);
    void applyTypography(String typographyJson);
    void applyViewRevision(String revisionId);
    void setMode(Mode mode);
    boolean navigateViewport(boolean forward);
    void selectText(int startOffset, int endOffset);
    void clearTextSelection();
    ReaderTextSelection currentTextSelection();
    void scrollToAnchor(String anchorId);
    String currentAnchor();
    void highlightSpeechRange(int start, int end, int followOffset,
            boolean paragraphFallback);
    void clearSpeechHighlight();
}
