package com.jingdu.txt.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Platform-neutral, bounded reading typography and color contract. */
public final class ReaderAppearance {
    public enum FontFamily {
        SYSTEM_SANS,
        SERIF,
        MONOSPACE
    }

    public enum Theme {
        DAY(0xFF202124, 0xFFFFFBF3),
        EYE(0xFF203229, 0xFFE7EEDB),
        NIGHT(0xFFE8E6E1, 0xFF17191C);

        private final int foregroundArgb;
        private final int backgroundArgb;

        Theme(int foregroundArgb, int backgroundArgb) {
            this.foregroundArgb = foregroundArgb;
            this.backgroundArgb = backgroundArgb;
        }

        public int getForegroundArgb() {
            return foregroundArgb;
        }

        public int getBackgroundArgb() {
            return backgroundArgb;
        }
    }

    private static final int[] TEXT_SIZE_OPTIONS_SP = {16, 18, 20, 22, 24, 28, 32};
    private static final int[] LINE_HEIGHT_OPTIONS_PERCENT = {120, 145, 170, 200};
    private static final int[] PARAGRAPH_SPACING_OPTIONS_DP = {0, 4, 8, 12};
    private static final int[] HORIZONTAL_MARGIN_OPTIONS_DP = {8, 12, 16, 24, 32};
    private static final Pattern CONTRACT_V2 = Pattern.compile(
            "\\{\\\"theme\\\":\\\"(DAY|EYE|NIGHT)\\\","
                    + "\\\"fontFamily\\\":\\\"(SYSTEM_SANS|SERIF|MONOSPACE)\\\","
                    + "\\\"textSizeSp\\\":([0-9]+),"
                    + "\\\"lineHeightPercent\\\":([0-9]+),"
                    + "\\\"paragraphSpacingDp\\\":([0-9]+),"
                    + "\\\"horizontalMarginDp\\\":([0-9]+)\\}");
    private static final Pattern LEGACY_CONTRACT = Pattern.compile(
            "\\{\\\"theme\\\":\\\"(DAY|EYE|NIGHT)\\\","
                    + "\\\"textSizeSp\\\":([0-9]+),"
                    + "\\\"lineHeightPercent\\\":([0-9]+),"
                    + "\\\"horizontalMarginDp\\\":([0-9]+)\\}");

    private final Theme theme;
    private final FontFamily fontFamily;
    private final int textSizeSp;
    private final int lineHeightPercent;
    private final int paragraphSpacingDp;
    private final int horizontalMarginDp;

    public ReaderAppearance(Theme theme, int textSizeSp,
            int lineHeightPercent, int horizontalMarginDp) {
        this(theme, FontFamily.SYSTEM_SANS, textSizeSp,
                lineHeightPercent, 0, horizontalMarginDp);
    }

    public ReaderAppearance(Theme theme, FontFamily fontFamily, int textSizeSp,
            int lineHeightPercent, int paragraphSpacingDp, int horizontalMarginDp) {
        if (theme == null) {
            throw new IllegalArgumentException("reader theme is required");
        }
        if (fontFamily == null) {
            throw new IllegalArgumentException("reader font family is required");
        }
        requireOption(textSizeSp, TEXT_SIZE_OPTIONS_SP, "text size");
        requireOption(lineHeightPercent, LINE_HEIGHT_OPTIONS_PERCENT, "line height");
        requireOption(paragraphSpacingDp, PARAGRAPH_SPACING_OPTIONS_DP,
                "paragraph spacing");
        requireOption(horizontalMarginDp, HORIZONTAL_MARGIN_OPTIONS_DP,
                "horizontal margin");
        if (contrastRatio(theme.getForegroundArgb(), theme.getBackgroundArgb()) < 4.5) {
            throw new IllegalArgumentException("reader theme contrast is too low");
        }
        this.theme = theme;
        this.fontFamily = fontFamily;
        this.textSizeSp = textSizeSp;
        this.lineHeightPercent = lineHeightPercent;
        this.paragraphSpacingDp = paragraphSpacingDp;
        this.horizontalMarginDp = horizontalMarginDp;
    }

    public static ReaderAppearance defaults() {
        return new ReaderAppearance(Theme.DAY, FontFamily.SYSTEM_SANS,
                20, 145, 8, 12);
    }

    public Theme getTheme() {
        return theme;
    }

    public FontFamily getFontFamily() {
        return fontFamily;
    }

    public int getTextSizeSp() {
        return textSizeSp;
    }

    public int getLineHeightPercent() {
        return lineHeightPercent;
    }

    public float getLineHeightMultiplier() {
        return lineHeightPercent / 100.0f;
    }

    public int getParagraphSpacingDp() {
        return paragraphSpacingDp;
    }

    public int getHorizontalMarginDp() {
        return horizontalMarginDp;
    }

    public String toTypographyJson() {
        return "{\"theme\":\"" + theme.name()
                + "\",\"fontFamily\":\"" + fontFamily.name()
                + "\",\"textSizeSp\":" + textSizeSp
                + ",\"lineHeightPercent\":" + lineHeightPercent
                + ",\"paragraphSpacingDp\":" + paragraphSpacingDp
                + ",\"horizontalMarginDp\":" + horizontalMarginDp + "}";
    }

    public static ReaderAppearance fromTypographyJson(String value) {
        if (value == null || value.length() > 240) {
            throw new IllegalArgumentException("invalid reader appearance contract");
        }
        Matcher legacy = LEGACY_CONTRACT.matcher(value);
        if (legacy.matches()) {
            try {
                ReaderAppearance decoded = new ReaderAppearance(
                        Theme.valueOf(legacy.group(1)),
                        FontFamily.SYSTEM_SANS,
                        Integer.parseInt(legacy.group(2)),
                        Integer.parseInt(legacy.group(3)), 0,
                        Integer.parseInt(legacy.group(4)));
                String canonicalLegacy = "{\"theme\":\"" + decoded.theme.name()
                        + "\",\"textSizeSp\":" + decoded.textSizeSp
                        + ",\"lineHeightPercent\":" + decoded.lineHeightPercent
                        + ",\"horizontalMarginDp\":"
                        + decoded.horizontalMarginDp + "}";
                if (!canonicalLegacy.equals(value)) {
                    throw new IllegalArgumentException(
                            "non-canonical legacy reader appearance contract");
                }
                return decoded;
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException(
                        "invalid legacy reader appearance contract", invalid);
            }
        }
        Matcher matcher = CONTRACT_V2.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid reader appearance contract");
        }
        try {
            ReaderAppearance decoded = new ReaderAppearance(
                    Theme.valueOf(matcher.group(1)),
                    FontFamily.valueOf(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    Integer.parseInt(matcher.group(4)),
                    Integer.parseInt(matcher.group(5)),
                    Integer.parseInt(matcher.group(6)));
            if (!decoded.toTypographyJson().equals(value)) {
                throw new IllegalArgumentException("non-canonical reader appearance contract");
            }
            return decoded;
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("invalid reader appearance contract", invalid);
        }
    }

    public static int[] textSizeOptionsSp() {
        return TEXT_SIZE_OPTIONS_SP.clone();
    }

    public static int[] lineHeightOptionsPercent() {
        return LINE_HEIGHT_OPTIONS_PERCENT.clone();
    }

    public static int[] paragraphSpacingOptionsDp() {
        return PARAGRAPH_SPACING_OPTIONS_DP.clone();
    }

    public static int[] horizontalMarginOptionsDp() {
        return HORIZONTAL_MARGIN_OPTIONS_DP.clone();
    }

    public static double contrastRatio(int firstArgb, int secondArgb) {
        double first = relativeLuminance(firstArgb);
        double second = relativeLuminance(secondArgb);
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    private static double relativeLuminance(int argb) {
        double red = linearChannel((argb >>> 16) & 0xFF);
        double green = linearChannel((argb >>> 8) & 0xFF);
        double blue = linearChannel(argb & 0xFF);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linearChannel(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92
                : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static void requireOption(int value, int[] options, String label) {
        for (int option : options) {
            if (value == option) {
                return;
            }
        }
        throw new IllegalArgumentException("unsupported reader " + label);
    }
}
