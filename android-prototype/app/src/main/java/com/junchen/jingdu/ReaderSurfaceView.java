package com.junchen.jingdu;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Choreographer;
import android.view.ActionMode;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.jingdu.txt.core.port.ReaderSurface;
import com.jingdu.txt.core.AutoScrollPolicy;
import com.jingdu.txt.core.ReaderAppearance;
import com.jingdu.txt.core.ReaderTextSelection;

import java.util.ArrayList;
import java.util.List;

public final class ReaderSurfaceView extends View implements ReaderSurface, Choreographer.FrameCallback {
    public enum SelectionAction { COPY, SEARCH, CREATE_RULE }

    public interface SelectionActionListener {
        void onSelectionAction(SelectionAction action, ReaderTextSelection selection);
    }

    public interface AutoScrollListener {
        void onAutoScrollChanged(boolean running);
    }

    public interface ReaderTouchListener {
        void onReaderTouch(boolean finished, boolean pausedAutoScroll,
                int anchorOffset);
    }

    public interface ViewportBoundaryListener {
        void onViewportBoundary(boolean forward, int anchorOffset,
                boolean resumeAutoScroll);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint();
    private final Paint speechRangePaint = new Paint();
    private final Paint speechParagraphPaint = new Paint();
    private final List<String> lines = new ArrayList<String>();
    private final List<Integer> lineOffsets = new ArrayList<Integer>();
    private final List<Boolean> paragraphEndLines = new ArrayList<Boolean>();
    private final List<Float> lineTopOffsets = new ArrayList<Float>();
    private String text = "";
    private String revisionId = "";
    private int windowStartOffset;
    private float verticalOffset;
    private boolean autoScrolling;
    private long previousFrameNanos;
    private final float displayDensity;
    private AutoScrollPolicy autoScrollPolicy = AutoScrollPolicy.defaults();
    private float lineHeightMultiplier = 1.45f;
    private float paragraphSpacingPixels;
    private ReaderAppearance appearance = ReaderAppearance.defaults();
    private AutoScrollListener autoScrollListener;
    private ReaderTouchListener readerTouchListener;
    private ViewportBoundaryListener viewportBoundaryListener;
    private Mode mode = Mode.CONTINUOUS_SCROLL;
    private final int touchSlop;
    private float touchDownY;
    private float touchStartVerticalOffset;
    private boolean touchMoved;
    private boolean touchPausedAutoScroll;
    private float touchDownX;
    private boolean selectingText;
    private int selectionAnchorStartLocal;
    private int selectionAnchorEndLocal;
    private ReaderTextSelection textSelection;
    private SelectionActionListener selectionActionListener;
    private ActionMode selectionActionMode;
    private int speechHighlightStart = -1;
    private int speechHighlightEnd = -1;
    private boolean speechParagraphFallback;
    private static final int ACTION_COPY = 1;
    private static final int ACTION_SEARCH = 2;
    private static final int ACTION_CREATE_RULE = 3;
    private final Runnable longPressRunnable = this::performLongClick;

    public ReaderSurfaceView(Context context) {
        super(context);
        displayDensity = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setFocusable(true);
        setLongClickable(true);
        applyAppearance(appearance);
    }

    public void setDocumentText(String text, String revisionId) {
        setDocumentWindow(text, revisionId, 0);
    }

    public void setDocumentWindow(String text, String revisionId, int windowStartOffset) {
        stopAutoScroll();
        clearTextSelection();
        clearSpeechHighlight();
        this.text = text == null ? "" : text;
        this.revisionId = revisionId == null ? "" : revisionId;
        this.windowStartOffset = Math.max(0, windowStartOffset);
        verticalOffset = 0;
        rebuildLines();
        updateAccessibilityState();
        invalidate();
    }

    public void setAutoScrollListener(AutoScrollListener listener) {
        this.autoScrollListener = listener;
    }

    public void setReaderTouchListener(ReaderTouchListener listener) {
        this.readerTouchListener = listener;
    }

    public void setViewportBoundaryListener(ViewportBoundaryListener listener) {
        viewportBoundaryListener = listener;
    }

    public void setSelectionActionListener(SelectionActionListener listener) {
        selectionActionListener = listener;
    }

    public boolean toggleAutoScroll() {
        if (mode == Mode.PAGED) {
            return false;
        }
        if (autoScrolling) {
            stopAutoScroll();
        } else {
            clearTextSelection();
            autoScrolling = true;
            previousFrameNanos = 0;
            Choreographer.getInstance().postFrameCallback(this);
            updateAccessibilityState();
            notifyAutoScrollChanged();
        }
        return autoScrolling;
    }

    public boolean isAutoScrolling() {
        return autoScrolling;
    }

    public void setAutoScrollSpeedDpPerSecond(int speedDpPerSecond) {
        autoScrollPolicy = new AutoScrollPolicy(speedDpPerSecond);
        updateAccessibilityState();
    }

    public int getAutoScrollSpeedDpPerSecond() {
        return autoScrollPolicy.getSpeedDpPerSecond();
    }

    public void stopAutoScroll() {
        boolean changed = autoScrolling;
        autoScrolling = false;
        previousFrameNanos = 0;
        Choreographer.getInstance().removeFrameCallback(this);
        updateAccessibilityState();
        if (changed) {
            notifyAutoScrollChanged();
        }
    }

    public int visibleCharacterOffset() {
        if (lineOffsets.isEmpty()) {
            return 0;
        }
        int line = lineIndexForVerticalOffset(verticalOffset);
        return windowStartOffset + lineOffsets.get(line);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        int anchor = visibleCharacterOffset();
        rebuildLines();
        scrollToAnchor(Integer.toString(anchor));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float lineHeight = lineHeight();
        int firstLine = lineIndexForVerticalOffset(verticalOffset);
        for (int i = firstLine; i < lines.size(); i++) {
            float y = getPaddingTop() + lineTopOffsets.get(i)
                    - verticalOffset + lineHeight;
            drawSpeechHighlightForLine(canvas, i, y, lineHeight);
            drawSelectionForLine(canvas, i, y, lineHeight);
            if (y >= getPaddingTop() - lineHeight) {
                canvas.drawText(lines.get(i), getPaddingLeft(), y, paint);
            }
            if (y >= getHeight()) {
                break;
            }
        }
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!autoScrolling) {
            return;
        }
        if (previousFrameNanos != 0) {
            long elapsedNanos = Math.max(0, frameTimeNanos - previousFrameNanos);
            verticalOffset += (float) (autoScrollPolicy.distanceDp(elapsedNanos)
                    * displayDensity);
            float maximum = Math.max(0, contentHeight() - getHeight());
            if (verticalOffset >= maximum) {
                verticalOffset = maximum;
                stopAutoScroll();
                if (viewportBoundaryListener != null) {
                    viewportBoundaryListener.onViewportBoundary(
                            true, visibleCharacterOffset(), true);
                }
            }
            invalidate();
            updateAccessibilityState();
        }
        previousFrameNanos = frameTimeNanos;
        if (autoScrolling) {
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                requestFocus();
                if (textSelection != null) {
                    clearTextSelection();
                }
                boolean pausedAutoScroll = autoScrolling;
                touchPausedAutoScroll = pausedAutoScroll;
                stopAutoScroll();
                touchDownY = event.getY();
                touchDownX = event.getX();
                touchStartVerticalOffset = verticalOffset;
                touchMoved = false;
                selectingText = false;
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                if (readerTouchListener != null) {
                    readerTouchListener.onReaderTouch(false,
                            pausedAutoScroll, visibleCharacterOffset());
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (selectingText) {
                    updateSelectionAt(event.getX(), event.getY());
                    return true;
                }
                float distance = touchDownY - event.getY();
                if (Math.hypot(event.getX() - touchDownX, distance) >= touchSlop) {
                    touchMoved = true;
                    removeCallbacks(longPressRunnable);
                }
                if (touchMoved && mode == Mode.CONTINUOUS_SCROLL) {
                    verticalOffset = clampVerticalOffset(
                            touchStartVerticalOffset + distance);
                    updateAccessibilityState();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPressRunnable);
                if (selectingText) {
                    updateSelectionAt(event.getX(), event.getY());
                    selectingText = false;
                    notifyTouchFinished(false);
                    return true;
                }
                float finalDistance = touchDownY - event.getY();
                if (touchMoved) {
                    if (mode == Mode.PAGED) {
                        verticalOffset = touchStartVerticalOffset;
                        if (Math.abs(finalDistance) >= touchSlop) {
                            navigateViewport(finalDistance > 0);
                        } else {
                            performClick();
                        }
                    } else {
                        notifyBoundaryAfterDrag(finalDistance);
                    }
                    updateAccessibilityState();
                    invalidate();
                } else {
                    performClick();
                }
                notifyTouchFinished(true);
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPressRunnable);
                if (mode == Mode.PAGED) {
                    verticalOffset = touchStartVerticalOffset;
                }
                updateAccessibilityState();
                invalidate();
                touchPausedAutoScroll = false;
                selectingText = false;
                notifyTouchFinished(false);
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public boolean performLongClick() {
        if (text.isEmpty() || touchMoved) {
            return false;
        }
        super.performLongClick();
        stopAutoScroll();
        int lineIndex = lineIndexForPointY(touchDownY);
        int localOffset = localOffsetForPoint(touchDownX, touchDownY);
        String selectedLine = lines.get(lineIndex);
        if (selectedLine.isEmpty()) {
            return false;
        }
        int selectedLineEnd = lineOffsets.get(lineIndex) + selectedLine.length();
        if (!selectedLine.isEmpty() && localOffset == selectedLineEnd) {
            localOffset -= Character.charCount(
                    Character.codePointBefore(text, localOffset));
        }
        textSelection = ReaderTextSelection.selectWord(
                text, windowStartOffset, localOffset);
        selectionAnchorStartLocal = textSelection.getStartOffset() - windowStartOffset;
        selectionAnchorEndLocal = textSelection.getEndOffset() - windowStartOffset;
        selectingText = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        startSelectionActionMode();
        updateAccessibilityState();
        invalidate();
        return true;
    }

    private void drawSelectionForLine(Canvas canvas, int lineIndex, float baseline,
            float lineHeight) {
        if (textSelection == null) {
            return;
        }
        int lineStart = windowStartOffset + lineOffsets.get(lineIndex);
        String lineText = lines.get(lineIndex);
        int lineEnd = lineStart + lineText.length();
        int selectedStart = Math.max(lineStart, textSelection.getStartOffset());
        int selectedEnd = Math.min(lineEnd, textSelection.getEndOffset());
        if (selectedStart >= selectedEnd) {
            return;
        }
        int localStart = selectedStart - lineStart;
        int localEnd = selectedEnd - lineStart;
        float left = getPaddingLeft() + paint.measureText(
                lineText, 0, localStart);
        float right = getPaddingLeft() + paint.measureText(
                lineText, 0, localEnd);
        canvas.drawRect(left, baseline - lineHeight, right,
                baseline + paint.getFontMetrics().descent, selectionPaint);
    }

    private void drawSpeechHighlightForLine(Canvas canvas, int lineIndex,
            float baseline, float lineHeight) {
        if (speechHighlightStart < 0 || speechHighlightEnd <= speechHighlightStart) {
            return;
        }
        int lineStart = windowStartOffset + lineOffsets.get(lineIndex);
        String lineText = lines.get(lineIndex);
        int lineEnd = lineStart + lineText.length();
        int highlightedStart = Math.max(lineStart, speechHighlightStart);
        int highlightedEnd = Math.min(lineEnd, speechHighlightEnd);
        if (highlightedStart >= highlightedEnd) {
            return;
        }
        float left = getPaddingLeft() + paint.measureText(
                lineText, 0, highlightedStart - lineStart);
        float right = getPaddingLeft() + paint.measureText(
                lineText, 0, highlightedEnd - lineStart);
        canvas.drawRect(left, baseline - lineHeight, right,
                baseline + paint.getFontMetrics().descent,
                speechParagraphFallback ? speechParagraphPaint : speechRangePaint);
    }

    private int localOffsetForPoint(float x, float y) {
        if (lines.isEmpty()) {
            return 0;
        }
        int lineIndex = lineIndexForPointY(y);
        String line = lines.get(lineIndex);
        float requestedX = Math.max(0, x - getPaddingLeft());
        int offset = 0;
        float previousWidth = 0;
        while (offset < line.length()) {
            int next = offset + Character.charCount(Character.codePointAt(line, offset));
            float nextWidth = paint.measureText(line, 0, next);
            if (requestedX < (previousWidth + nextWidth) / 2f) {
                break;
            }
            offset = next;
            previousWidth = nextWidth;
        }
        return lineOffsets.get(lineIndex) + offset;
    }

    private int lineIndexForPointY(float y) {
        float contentY = Math.max(0, verticalOffset + y - getPaddingTop());
        return lineIndexForVerticalOffset(contentY);
    }

    private void updateSelectionAt(float x, float y) {
        int localOffset = localOffsetForPoint(x, y);
        int start = selectionAnchorStartLocal;
        int end = selectionAnchorEndLocal;
        if (localOffset < start) {
            start = localOffset;
        } else if (localOffset > end) {
            end = localOffset;
        }
        textSelection = ReaderTextSelection.selectRange(
                text, windowStartOffset, start, end);
        updateAccessibilityState();
        invalidate();
    }

    private void startSelectionActionMode() {
        if (selectionActionMode != null) {
            selectionActionMode.invalidate();
            return;
        }
        selectionActionMode = startActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                menu.add(Menu.NONE, ACTION_COPY, 0, R.string.reader_selection_copy);
                menu.add(Menu.NONE, ACTION_SEARCH, 1, R.string.reader_selection_search);
                menu.add(Menu.NONE, ACTION_CREATE_RULE, 2,
                        R.string.reader_selection_create_rule);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                ReaderTextSelection snapshot = textSelection;
                if (snapshot == null || selectionActionListener == null) {
                    return false;
                }
                SelectionAction action;
                if (item.getItemId() == ACTION_COPY) {
                    action = SelectionAction.COPY;
                } else if (item.getItemId() == ACTION_SEARCH) {
                    action = SelectionAction.SEARCH;
                } else if (item.getItemId() == ACTION_CREATE_RULE) {
                    action = SelectionAction.CREATE_RULE;
                } else {
                    return false;
                }
                selectionActionListener.onSelectionAction(action, snapshot);
                mode.finish();
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                selectionActionMode = null;
                clearSelectionInternal();
            }
        }, ActionMode.TYPE_FLOATING);
    }

    private void clearSelectionInternal() {
        textSelection = null;
        selectingText = false;
        updateAccessibilityState();
        invalidate();
    }

    private void rebuildLines() {
        lines.clear();
        lineOffsets.clear();
        paragraphEndLines.clear();
        lineTopOffsets.clear();
        float usableWidth = Math.max(1, getWidth() - getPaddingLeft() - getPaddingRight());
        int offset = 0;
        while (offset < text.length()) {
            int newline = text.indexOf('\n', offset);
            int paragraphEnd = newline < 0 ? text.length() : newline;
            if (paragraphEnd == offset) {
                addReaderLine("", offset, true);
            } else {
                int cursor = offset;
                while (cursor < paragraphEnd) {
                    int fitting = Math.max(1, paint.breakText(text, cursor,
                            paragraphEnd, true, usableWidth, null));
                    int end = Math.min(paragraphEnd, cursor + fitting);
                    end = preserveCodePointBoundary(end, paragraphEnd);
                    end = preferNaturalLineBreak(cursor, end, paragraphEnd);
                    addReaderLine(text.substring(cursor, end), cursor,
                            end >= paragraphEnd);
                    cursor = end;
                }
            }
            if (newline < 0) {
                break;
            }
            offset = newline + 1;
        }
        if (lines.isEmpty()) {
            addReaderLine("", 0, true);
        }
        float top = 0;
        for (int index = 0; index < lines.size(); index++) {
            lineTopOffsets.add(top);
            top += lineHeight();
            if (paragraphEndLines.get(index) && index + 1 < lines.size()) {
                top += paragraphSpacingPixels;
            }
        }
    }

    private int preserveCodePointBoundary(int end, int paragraphEnd) {
        if (end < paragraphEnd && end > 0 && Character.isLowSurrogate(text.charAt(end))) {
            return end + 1;
        }
        return end;
    }

    private int preferNaturalLineBreak(int start, int measuredEnd,
            int paragraphEnd) {
        if (measuredEnd >= paragraphEnd) {
            return measuredEnd;
        }
        for (int index = measuredEnd; index > start; index--) {
            int codePoint = Character.codePointBefore(text, index);
            if (Character.isWhitespace(codePoint) || isLineBreakPunctuation(codePoint)) {
                return index;
            }
            if (!isLatinWordCodePoint(codePoint)) {
                break;
            }
        }
        return measuredEnd;
    }

    private static boolean isLatinWordCodePoint(int codePoint) {
        return Character.isLetterOrDigit(codePoint)
                || codePoint == '\'' || codePoint == 0x2019
                || codePoint == '-' || codePoint == 0x2010;
    }

    private static boolean isLineBreakPunctuation(int codePoint) {
        return codePoint == ',' || codePoint == '.' || codePoint == ';'
                || codePoint == ':' || codePoint == '!' || codePoint == '?'
                || codePoint == 0x3001 || codePoint == 0x3002
                || codePoint == 0xFF0C || codePoint == 0xFF01
                || codePoint == 0xFF1F || codePoint == 0xFF1B
                || codePoint == 0xFF1A;
    }

    private void addReaderLine(String line, int offset, boolean paragraphEnd) {
        lines.add(line);
        lineOffsets.add(offset);
        paragraphEndLines.add(paragraphEnd);
    }

    private float lineHeight() {
        return paint.getFontSpacing() * lineHeightMultiplier;
    }

    private float contentHeight() {
        if (lineTopOffsets.isEmpty()) {
            return 0;
        }
        return lineTopOffsets.get(lineTopOffsets.size() - 1) + lineHeight();
    }

    private float maximumVerticalOffset() {
        return Math.max(0, contentHeight() - getHeight());
    }

    private float clampVerticalOffset(float requested) {
        return Math.max(0, Math.min(requested, maximumVerticalOffset()));
    }

    private float viewportStepPixels() {
        float available = Math.max(lineHeight(), getHeight()
                - getPaddingTop() - getPaddingBottom());
        float overlap = mode == Mode.CONTINUOUS_SCROLL ? lineHeight() : 0;
        return Math.max(lineHeight(), available - overlap);
    }

    @Override
    public boolean navigateViewport(boolean forward) {
        stopAutoScroll();
        clearTextSelection();
        float requested = verticalOffset
                + (forward ? viewportStepPixels() : -viewportStepPixels());
        float target = clampVerticalOffset(requested);
        int targetLine = lineIndexForVerticalOffset(target);
        target = lineTopOffsets.isEmpty() ? 0 : lineTopOffsets.get(targetLine);
        boolean moved = Math.abs(target - verticalOffset) >= 0.5f;
        verticalOffset = target;
        updateAccessibilityState();
        invalidate();
        if (!moved && viewportBoundaryListener != null) {
            viewportBoundaryListener.onViewportBoundary(
                    forward, visibleCharacterOffset(), false);
        }
        return moved;
    }

    private void notifyBoundaryAfterDrag(float distance) {
        if (viewportBoundaryListener == null) {
            return;
        }
        if (distance > 0 && verticalOffset >= maximumVerticalOffset() - 0.5f) {
            viewportBoundaryListener.onViewportBoundary(
                    true, visibleCharacterOffset(), false);
        } else if (distance < 0 && verticalOffset <= 0.5f) {
            viewportBoundaryListener.onViewportBoundary(
                    false, visibleCharacterOffset(), false);
        }
    }

    private void notifyTouchFinished(boolean allowAutoScrollResume) {
        if (readerTouchListener != null) {
            readerTouchListener.onReaderTouch(true,
                    allowAutoScrollResume && touchPausedAutoScroll,
                    visibleCharacterOffset());
        }
        touchPausedAutoScroll = false;
    }

    private int lineIndexForVerticalOffset(float requestedOffset) {
        if (lineTopOffsets.isEmpty()) {
            return 0;
        }
        int low = 0;
        int high = lineTopOffsets.size() - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (lineTopOffsets.get(middle) <= requestedOffset) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    private void updateAccessibilityState() {
        int visibleOffset = visibleCharacterOffset();
        String excerpt = visibleLineExcerpt();
        String selection = textSelection == null ? "" : getContext().getString(
                R.string.reader_accessibility_selected, textSelection.length());
        String content = excerpt.isEmpty() ? "" : getContext().getString(
                R.string.reader_accessibility_excerpt, excerpt);
        setContentDescription(getContext().getString(
                R.string.reader_accessibility_summary, visibleOffset,
                getContext().getString(mode == Mode.PAGED
                        ? R.string.reader_mode_paged : R.string.reader_mode_continuous),
                getContext().getString(autoScrolling
                        ? R.string.reader_accessibility_running
                        : R.string.reader_accessibility_stopped),
                autoScrollPolicy.getSpeedDpPerSecond(), selection, content));
        setTag(diagnosticState());
    }

    public String diagnosticState() {
        return "reader-offset:" + visibleCharacterOffset()
                + ";auto-scroll:" + autoScrolling
                + ";auto-scroll-speed-dp-s:"
                + autoScrollPolicy.getSpeedDpPerSecond()
                + ";mode:" + mode.name()
                + ";selection:" + (textSelection == null ? "none"
                        : textSelection.getStartOffset() + "-"
                                + textSelection.getEndOffset())
                + ";speech-highlight:" + (speechHighlightStart < 0 ? "none"
                        : speechHighlightStart + "-" + speechHighlightEnd + "-"
                                + (speechParagraphFallback ? "paragraph" : "range"))
                + ";revision:" + revisionId;
    }

    private String visibleLineExcerpt() {
        if (lines.isEmpty()) {
            return "";
        }
        int line = lineIndexForVerticalOffset(verticalOffset);
        String excerpt = lines.get(line).replaceAll("[\\p{Cc}\\s]+", " ").trim();
        if (excerpt.length() <= 60) {
            return excerpt;
        }
        int end = 60;
        if (Character.isHighSurrogate(excerpt.charAt(end - 1))) {
            end--;
        }
        return excerpt.substring(0, end);
    }

    private void notifyAutoScrollChanged() {
        if (autoScrollListener != null) {
            autoScrollListener.onAutoScrollChanged(autoScrolling);
        }
    }

    @Override
    public void loadWindow(String bookId, String anchorId, int viewportCharacters) {
        scrollToAnchor(anchorId);
    }

    @Override
    public void applyTypography(String typographyJson) {
        applyAppearance(ReaderAppearance.fromTypographyJson(typographyJson));
    }

    public void applyAppearance(ReaderAppearance newAppearance) {
        if (newAppearance == null) {
            throw new IllegalArgumentException("reader appearance is required");
        }
        int anchor = visibleCharacterOffset();
        appearance = newAppearance;
        lineHeightMultiplier = newAppearance.getLineHeightMultiplier();
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        float density = getResources().getDisplayMetrics().density;
        paragraphSpacingPixels = newAppearance.getParagraphSpacingDp() * density;
        paint.setTypeface(typefaceFor(newAppearance.getFontFamily()));
        paint.setTextSize(newAppearance.getTextSizeSp() * scaledDensity);
        paint.setColor(newAppearance.getTheme().getForegroundArgb());
        selectionPaint.setColor(Color.argb(64,
                Color.red(paint.getColor()), Color.green(paint.getColor()),
                Color.blue(paint.getColor())));
        boolean night = newAppearance.getTheme() == ReaderAppearance.Theme.NIGHT;
        speechRangePaint.setColor(night
                ? Color.argb(112, 79, 195, 247)
                : Color.argb(112, 255, 179, 0));
        speechParagraphPaint.setColor(night
                ? Color.argb(72, 79, 195, 247)
                : Color.argb(72, 255, 213, 79));
        int horizontalPadding = Math.round(
                newAppearance.getHorizontalMarginDp() * density);
        setPadding(horizontalPadding, getPaddingTop(),
                horizontalPadding, getPaddingBottom());
        setBackgroundColor(newAppearance.getTheme().getBackgroundArgb());
        rebuildLines();
        scrollToAnchor(Integer.toString(anchor));
        requestLayout();
        invalidate();
    }

    public ReaderAppearance getAppearance() {
        return appearance;
    }

    private static Typeface typefaceFor(ReaderAppearance.FontFamily fontFamily) {
        switch (fontFamily) {
            case SERIF:
                return Typeface.SERIF;
            case MONOSPACE:
                return Typeface.MONOSPACE;
            case SYSTEM_SANS:
            default:
                return Typeface.DEFAULT;
        }
    }

    @Override
    public void applyViewRevision(String revisionId) {
        String normalizedRevision = revisionId == null ? "" : revisionId;
        if (!this.revisionId.equals(normalizedRevision)) {
            clearTextSelection();
            clearSpeechHighlight();
        }
        this.revisionId = normalizedRevision;
        updateAccessibilityState();
    }

    @Override
    public void setMode(Mode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("reader mode is required");
        }
        int anchor = visibleCharacterOffset();
        stopAutoScroll();
        clearTextSelection();
        this.mode = mode;
        scrollToAnchor(Integer.toString(anchor));
        updateAccessibilityState();
    }

    public Mode getMode() {
        return mode;
    }

    @Override
    public void selectText(int startOffset, int endOffset) {
        int windowEnd = windowStartOffset + text.length();
        if (startOffset < windowStartOffset || endOffset > windowEnd) {
            throw new IllegalArgumentException("selection must stay inside the loaded window");
        }
        textSelection = ReaderTextSelection.selectRange(text, windowStartOffset,
                startOffset - windowStartOffset, endOffset - windowStartOffset);
        selectionAnchorStartLocal = textSelection.getStartOffset() - windowStartOffset;
        selectionAnchorEndLocal = textSelection.getEndOffset() - windowStartOffset;
        startSelectionActionMode();
        updateAccessibilityState();
        invalidate();
    }

    @Override
    public void clearTextSelection() {
        removeCallbacks(longPressRunnable);
        if (selectionActionMode != null) {
            ActionMode previous = selectionActionMode;
            selectionActionMode = null;
            previous.finish();
        }
        clearSelectionInternal();
    }

    @Override
    public ReaderTextSelection currentTextSelection() {
        return textSelection;
    }

    @Override
    public void scrollToAnchor(String anchorId) {
        try {
            int requestedOffset = Math.max(0, Integer.parseInt(anchorId) - windowStartOffset);
            int line = 0;
            while (line + 1 < lineOffsets.size() && lineOffsets.get(line + 1) <= requestedOffset) {
                line++;
            }
            verticalOffset = lineTopOffsets.isEmpty() ? 0 : lineTopOffsets.get(line);
            updateAccessibilityState();
            invalidate();
        } catch (NumberFormatException ignored) {
            verticalOffset = 0;
            updateAccessibilityState();
        }
    }

    @Override
    public String currentAnchor() {
        return Integer.toString(visibleCharacterOffset());
    }

    @Override
    public void highlightSpeechRange(int start, int end, int followOffset,
            boolean paragraphFallback) {
        int windowEnd = windowStartOffset + text.length();
        if (start < windowStartOffset || end <= start || end > windowEnd
                || followOffset < start || followOffset >= end) {
            throw new IllegalArgumentException(
                    "speech highlight must stay inside the loaded window");
        }
        int localStart = start - windowStartOffset;
        int localEnd = end - windowStartOffset;
        if (splitsSurrogatePair(localStart) || splitsSurrogatePair(localEnd)) {
            throw new IllegalArgumentException("speech highlight splits a surrogate pair");
        }
        clearTextSelection();
        speechHighlightStart = start;
        speechHighlightEnd = end;
        speechParagraphFallback = paragraphFallback;
        followSpeechHighlight(followOffset);
        updateAccessibilityState();
        invalidate();
    }

    @Override
    public void clearSpeechHighlight() {
        speechHighlightStart = -1;
        speechHighlightEnd = -1;
        speechParagraphFallback = false;
        updateAccessibilityState();
        invalidate();
    }

    private void followSpeechHighlight(int followOffset) {
        int followLine = lineIndexForGlobalOffset(followOffset);
        float highlightedTop = lineTopOffsets.isEmpty() ? 0
                : lineTopOffsets.get(followLine);
        float highlightedBottom = highlightedTop + lineHeight();
        float viewportHeight = Math.max(lineHeight(), getHeight()
                - getPaddingTop() - getPaddingBottom());
        float viewportBottom = verticalOffset + viewportHeight;
        if (highlightedTop < verticalOffset
                || highlightedBottom > viewportBottom) {
            verticalOffset = clampVerticalOffset(mode == Mode.PAGED
                    ? highlightedTop
                    : highlightedTop - viewportHeight * 0.25f);
        }
    }

    private int lineIndexForGlobalOffset(int globalOffset) {
        int localOffset = Math.max(0, Math.min(text.length(),
                globalOffset - windowStartOffset));
        int line = 0;
        while (line + 1 < lineOffsets.size()
                && lineOffsets.get(line + 1) <= localOffset) {
            line++;
        }
        return line;
    }

    private boolean splitsSurrogatePair(int localOffset) {
        return localOffset > 0 && localOffset < text.length()
                && Character.isHighSurrogate(text.charAt(localOffset - 1))
                && Character.isLowSurrogate(text.charAt(localOffset));
    }
}
