package com.junchen.jingdu;

import openccjava.OpenCC;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Display-only Chinese conversion. Source/normalized files and Core offsets stay authoritative;
 * only bounded UI/TTS strings are transformed.
 */
final class ChineseDisplayConverter {
    private static final int MAX_OVERRIDES = 200;
    private static final int MAX_OVERRIDE_FIELD_CHARS = 64;
    private static volatile ChineseDisplayMode currentMode = ChineseDisplayMode.ORIGINAL;
    private static volatile String currentOverrides = "";

    private ChineseDisplayConverter() {}

    static void configure(ReaderSettings settings) {
        if (settings == null) {
            currentMode = ChineseDisplayMode.ORIGINAL;
            currentOverrides = "";
            return;
        }
        currentMode = settings.getChineseMode();
        currentOverrides = settings.getChineseOverrides();
    }

    static String convert(String text) {
        return convert(text, currentMode, currentOverrides);
    }

    static String convert(String text, ChineseDisplayMode mode, String overridesText) {
        if (text == null || text.isEmpty() || mode == null || mode == ChineseDisplayMode.ORIGINAL) return text == null ? "" : text;
        List<OverridePair> overrides = parseOverrides(overridesText);
        String protectedText = text;
        ArrayList<ProtectedOverride> protectedOverrides = new ArrayList<>();
        String nonce = "__JINGDU_" + UUID.randomUUID().toString().replace("-", "") + "_";
        for (int index = 0; index < overrides.size(); index++) {
            OverridePair pair = overrides.get(index);
            if (!protectedText.contains(pair.source)) continue;
            String token = nonce + index + "__";
            protectedText = protectedText.replace(pair.source, token);
            protectedOverrides.add(new ProtectedOverride(token, pair.target));
        }

        String converted = OpenCC.convert(protectedText, config(mode));
        for (ProtectedOverride pair : protectedOverrides) converted = converted.replace(pair.token, pair.target);
        return converted;
    }

    static List<String> searchVariants(String query) {
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        if (query == null || query.trim().isEmpty()) return new ArrayList<>();
        String value = query.trim();
        variants.add(value);
        try { variants.add(OpenCC.convert(value, "t2s")); } catch (RuntimeException ignored) { }
        try { variants.add(OpenCC.convert(value, "s2t")); } catch (RuntimeException ignored) { }
        try { variants.add(OpenCC.convert(value, "s2twp")); } catch (RuntimeException ignored) { }
        variants.removeIf(String::isEmpty);
        return new ArrayList<>(variants);
    }

    static long sourceCharsForDisplayed(String source, String displayed, long displayedCodePoints) {
        if (source == null || source.isEmpty()) return 0;
        long sourcePoints = source.codePointCount(0, source.length());
        long displayedPoints = displayed == null ? 0 : displayed.codePointCount(0, displayed.length());
        if (displayedPoints <= 0) return Math.min(sourcePoints, Math.max(0, displayedCodePoints));
        double fraction = Math.min(1.0, Math.max(0.0, (double) displayedCodePoints / (double) displayedPoints));
        long mapped = Math.round(sourcePoints * fraction);
        return Math.max(1, Math.min(sourcePoints, mapped));
    }

    static int overrideCount(String overridesText) {
        return parseOverrides(overridesText).size();
    }

    private static String config(ChineseDisplayMode mode) {
        return switch (mode) {
            case SIMPLIFIED -> "t2s";
            case TRADITIONAL -> "s2t";
            case TAIWAN -> "s2tw";
            case TAIWAN_PHRASES -> "s2twp";
            case HONG_KONG -> "s2hk";
            case ORIGINAL -> throw new IllegalArgumentException("ORIGINAL has no OpenCC config");
        };
    }

    private static List<OverridePair> parseOverrides(String raw) {
        ArrayList<OverridePair> output = new ArrayList<>();
        if (raw == null || raw.isBlank()) return output;
        for (String line : raw.split("\\R")) {
            if (output.size() >= MAX_OVERRIDES) break;
            String value = line.trim();
            if (value.isEmpty() || value.startsWith("#")) continue;
            int split = value.indexOf("=>");
            int step = 2;
            if (split < 1) {
                split = value.indexOf('=');
                step = 1;
            }
            if (split < 1) continue;
            String source = value.substring(0, split).trim();
            String target = value.substring(split + step).trim();
            if (source.isEmpty() || target.isEmpty() || source.length() > MAX_OVERRIDE_FIELD_CHARS || target.length() > MAX_OVERRIDE_FIELD_CHARS) continue;
            output.add(new OverridePair(source, target));
        }
        output.sort(Comparator.comparingInt((OverridePair pair) -> pair.source.codePointCount(0, pair.source.length())).reversed());
        ArrayList<OverridePair> deduped = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (OverridePair pair : output) if (seen.add(pair.source.toLowerCase(Locale.ROOT))) deduped.add(pair);
        return deduped;
    }

    private record OverridePair(String source, String target) {}
    private record ProtectedOverride(String token, String target) {}
}
