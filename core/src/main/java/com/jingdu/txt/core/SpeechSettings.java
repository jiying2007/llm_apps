package com.jingdu.txt.core;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Platform-neutral persisted TTS voice, rate and pitch selection. */
public final class SpeechSettings {
    private static final int MIN_PERCENT = 50;
    private static final int MAX_PERCENT = 200;
    private static final int DEFAULT_PERCENT = 100;
    private static final int MAX_VOICE_ID_BYTES = 512;
    private static final int[] PERCENT_OPTIONS = {50, 75, 100, 125, 150, 175, 200};
    private static final Pattern CONTRACT = Pattern.compile(
            "\\{\\\"version\\\":1,\\\"voiceIdBase64\\\":\\\""
                    + "([A-Za-z0-9_-]{0,683})\\\",\\\"ratePercent\\\":"
                    + "([0-9]+),\\\"pitchPercent\\\":([0-9]+)\\}");

    private final String voiceId;
    private final int ratePercent;
    private final int pitchPercent;

    public SpeechSettings(String voiceId, int ratePercent, int pitchPercent) {
        validateVoiceId(voiceId);
        validatePercent(ratePercent, "speech rate");
        validatePercent(pitchPercent, "speech pitch");
        this.voiceId = voiceId;
        this.ratePercent = ratePercent;
        this.pitchPercent = pitchPercent;
    }

    public static SpeechSettings defaults() {
        return new SpeechSettings(null, DEFAULT_PERCENT, DEFAULT_PERCENT);
    }

    public String getVoiceId() {
        return voiceId;
    }

    public int getRatePercent() {
        return ratePercent;
    }

    public int getPitchPercent() {
        return pitchPercent;
    }

    public float getRateMultiplier() {
        return ratePercent / 100.0f;
    }

    public float getPitchMultiplier() {
        return pitchPercent / 100.0f;
    }

    public String toJson() {
        String encodedVoice = voiceId == null ? "" : Base64.getUrlEncoder()
                .withoutPadding().encodeToString(voiceId.getBytes(StandardCharsets.UTF_8));
        return "{\"version\":1,\"voiceIdBase64\":\"" + encodedVoice
                + "\",\"ratePercent\":" + ratePercent
                + ",\"pitchPercent\":" + pitchPercent + "}";
    }

    public static SpeechSettings fromJson(String value) {
        if (value == null || value.length() > 800) {
            throw new IllegalArgumentException("invalid speech settings contract");
        }
        Matcher matcher = CONTRACT.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid speech settings contract");
        }
        try {
            String encodedVoice = matcher.group(1);
            String voiceId = encodedVoice.isEmpty() ? null : new String(
                    Base64.getUrlDecoder().decode(encodedVoice), StandardCharsets.UTF_8);
            SpeechSettings decoded = new SpeechSettings(voiceId,
                    Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
            if (!decoded.toJson().equals(value)) {
                throw new IllegalArgumentException("non-canonical speech settings contract");
            }
            return decoded;
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("invalid speech settings contract", invalid);
        }
    }

    public static int[] percentOptions() {
        return PERCENT_OPTIONS.clone();
    }

    private static void validatePercent(int value, String label) {
        if (value < MIN_PERCENT || value > MAX_PERCENT) {
            throw new IllegalArgumentException(label + " is out of range");
        }
    }

    private static void validateVoiceId(String voiceId) {
        if (voiceId == null) {
            return;
        }
        if (voiceId.isEmpty()
                || voiceId.getBytes(StandardCharsets.UTF_8).length > MAX_VOICE_ID_BYTES) {
            throw new IllegalArgumentException("invalid speech voice ID");
        }
        for (int index = 0; index < voiceId.length(); index++) {
            char current = voiceId.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= voiceId.length()
                        || !Character.isLowSurrogate(voiceId.charAt(index + 1))) {
                    throw new IllegalArgumentException("invalid speech voice ID");
                }
                index++;
            } else if (Character.isLowSurrogate(current) || Character.isISOControl(current)) {
                throw new IllegalArgumentException("invalid speech voice ID");
            }
        }
    }
}
