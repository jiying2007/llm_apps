package com.jingdu.txt.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cross-platform, bounded encoding choice made before TXT import. */
public final class ImportEncodingPreference {
    public enum Choice {
        AUTO,
        UTF_8,
        GB18030,
        GBK,
        GB2312,
        BIG5,
        UTF_16LE,
        UTF_16BE
    }

    private static final Pattern CONTRACT = Pattern.compile(
            "\\{\\\"choice\\\":\\\""
                    + "(AUTO|UTF_8|GB18030|GBK|GB2312|BIG5|UTF_16LE|UTF_16BE)\\\"\\}");
    private final Choice choice;

    public ImportEncodingPreference(Choice choice) {
        if (choice == null) {
            throw new IllegalArgumentException("import encoding choice is required");
        }
        this.choice = choice;
    }

    public static ImportEncodingPreference automatic() {
        return new ImportEncodingPreference(Choice.AUTO);
    }

    public Choice getChoice() {
        return choice;
    }

    public boolean isAutomatic() {
        return choice == Choice.AUTO;
    }

    public String getManualCharsetName() {
        switch (choice) {
            case UTF_8:
                return "UTF-8";
            case GB18030:
                return "GB18030";
            case GBK:
                return "GBK";
            case GB2312:
                return "GB2312";
            case BIG5:
                return "Big5";
            case UTF_16LE:
                return "UTF-16LE";
            case UTF_16BE:
                return "UTF-16BE";
            case AUTO:
            default:
                throw new IllegalArgumentException(
                        "automatic encoding choice has no manual charset");
        }
    }

    public String toJson() {
        return "{\"choice\":\"" + choice.name() + "\"}";
    }

    public static ImportEncodingPreference fromJson(String value) {
        if (value == null || value.length() > 48) {
            throw new IllegalArgumentException("invalid import encoding preference");
        }
        Matcher matcher = CONTRACT.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid import encoding preference");
        }
        ImportEncodingPreference decoded;
        try {
            decoded = new ImportEncodingPreference(Choice.valueOf(matcher.group(1)));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("invalid import encoding preference", invalid);
        }
        if (!decoded.toJson().equals(value)) {
            throw new IllegalArgumentException("non-canonical import encoding preference");
        }
        return decoded;
    }
}
