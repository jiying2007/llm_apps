package com.junchen.jingdu;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import com.jingdu.txt.core.port.TextToSpeechPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AndroidTextToSpeechAdapter implements TextToSpeechPort {
    public interface InitializationListener {
        void onInitialized(boolean available);
    }

    private TextToSpeech engine;
    private volatile Voice systemDefaultVoice;
    private volatile boolean initialized;
    private volatile Listener listener;

    public AndroidTextToSpeechAdapter(Context context) {
        this(context, null);
    }

    public AndroidTextToSpeechAdapter(Context context,
            InitializationListener initializationListener) {
        engine = new TextToSpeech(context.getApplicationContext(), status -> {
            initialized = status == TextToSpeech.SUCCESS;
            if (initialized) {
                initialized = engine.setLanguage(Locale.CHINA) >= TextToSpeech.LANG_AVAILABLE;
                if (initialized) {
                    systemDefaultVoice = engine.getDefaultVoice();
                }
            }
            if (initializationListener != null) {
                initializationListener.onInitialized(initialized);
            }
        });
        engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Listener current = listener;
                if (current != null) current.onStart(utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                Listener current = listener;
                if (current != null) current.onDone(utteranceId);
            }

            @Override
            public void onError(String utteranceId) {
                Listener current = listener;
                if (current != null) current.onError(utteranceId, "tts-error");
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
                Listener current = listener;
                if (current != null) current.onError(utteranceId, Integer.toString(errorCode));
            }

            @Override
            public void onRangeStart(String utteranceId, int start, int end, int frame) {
                Listener current = listener;
                if (current != null) current.onRange(utteranceId, start, end);
            }

            @Override
            public void onStop(String utteranceId, boolean interrupted) {
                Listener current = listener;
                if (current != null) current.onStopped(utteranceId, interrupted);
            }
        });
    }

    @Override
    public Capabilities capabilities() {
        if (!initialized) {
            return unavailableCapabilities();
        }
        try {
            List<VoiceInfo> voices = new ArrayList<VoiceInfo>();
            Set<String> seenVoiceIds = new HashSet<String>();
            boolean offline = false;
            Set<Voice> engineVoices = engine.getVoices();
            if (engineVoices != null) {
                for (Voice voice : engineVoices) {
                if (voice.getName() == null || voice.getName().isEmpty()) {
                    continue;
                }
                if (!seenVoiceIds.add(voice.getName())) {
                    continue;
                }
                boolean networkRequired = voice.isNetworkConnectionRequired();
                String localeTag = voice.getLocale() == null
                        ? "" : voice.getLocale().toLanguageTag();
                voices.add(new VoiceInfo(voice.getName(), localeTag, networkRequired));
                offline = offline || !networkRequired;
                }
            }
            Collections.sort(voices, Comparator
                    .comparing((VoiceInfo voice) -> voice.networkRequired)
                    .thenComparing(voice -> voice.localeTag)
                    .thenComparing(voice -> voice.id));
            Voice defaultVoice = systemDefaultVoice;
            VoiceInfo defaultVoiceInfo = defaultVoice == null || defaultVoice.getName() == null
                    || defaultVoice.getName().isEmpty() ? null : new VoiceInfo(
                            defaultVoice.getName(), defaultVoice.getLocale() == null
                                    ? "" : defaultVoice.getLocale().toLanguageTag(),
                            defaultVoice.isNetworkConnectionRequired());
            return new Capabilities(true, true, false, offline,
                    TextToSpeech.getMaxSpeechInputLength(), defaultVoiceInfo, voices);
        } catch (RuntimeException capabilityFailure) {
            return unavailableCapabilities();
        }
    }

    private static Capabilities unavailableCapabilities() {
        return new Capabilities(false, false, false, false,
                TextToSpeech.getMaxSpeechInputLength(),
                null, Collections.<VoiceInfo>emptyList());
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void speak(String utteranceId, String text, String voiceId, float rate, float pitch) {
        if (!initialized) {
            reportError(utteranceId, "tts-not-initialized");
            return;
        }
        if (utteranceId == null || utteranceId.isEmpty() || text == null || text.isEmpty()
                || text.length() > TextToSpeech.getMaxSpeechInputLength()) {
            reportError(utteranceId, "speech-input-invalid");
            return;
        }
        Voice selectedVoice = null;
        if (voiceId == null) {
            selectedVoice = systemDefaultVoice;
        } else if (engine.getVoices() != null) {
            for (Voice voice : engine.getVoices()) {
                if (voiceId.equals(voice.getName())) {
                    selectedVoice = voice;
                    break;
                }
            }
        }
        if (selectedVoice == null) {
            reportError(utteranceId, "voice-unavailable");
            return;
        }
        if (!Float.isFinite(rate) || !Float.isFinite(pitch) || rate <= 0 || pitch <= 0
                || engine.setVoice(selectedVoice) == TextToSpeech.ERROR) {
            reportError(utteranceId, "speech-settings-rejected");
            return;
        }
        if (engine.setSpeechRate(rate) == TextToSpeech.ERROR
                || engine.setPitch(pitch) == TextToSpeech.ERROR) {
            reportError(utteranceId, "speech-settings-rejected");
            return;
        }
        int result = engine.speak(text, TextToSpeech.QUEUE_FLUSH,
                new Bundle(), utteranceId);
        if (result == TextToSpeech.ERROR) {
            reportError(utteranceId, "speak-rejected");
        }
    }

    private void reportError(String utteranceId, String errorCode) {
        Listener current = listener;
        if (current != null) {
            current.onError(utteranceId == null ? "" : utteranceId, errorCode);
        }
    }

    @Override
    public void pause() {
        engine.stop();
    }

    @Override
    public void resume() {
        throw new UnsupportedOperationException("resume requires the segmented queue controller planned after W0");
    }

    @Override
    public void stop() {
        engine.stop();
    }

    @Override
    public void shutdown() {
        engine.stop();
        engine.shutdown();
    }
}
