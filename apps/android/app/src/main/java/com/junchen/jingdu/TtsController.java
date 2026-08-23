package com.junchen.jingdu;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

final class TtsController implements AutoCloseable {
    interface Listener {
        void onPosition(long offset);
        void onStopped(String reason);
        default void onPaused() { }
        default void onResumed() { }
    }
    record VoiceOption(String name, String label) {}

    private static final String HANS_MARKERS = "这为后发国书读时会里还进对从个们来说现学与体门见风东语网无龙边开长";
    private static final String HANT_MARKERS = "這為後發國書讀時會裡還進對從個們來說現學與體門見風東語網無龍邊開長";
    private static final String HK_MARKERS = "係嘅唔嗰佢哋冇喺咁啲嚟咗";

    private final AudioManager audio;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final TextToSpeech tts;
    private final AudioFocusRequest focus;
    private final AtomicLong generation = new AtomicLong();
    private boolean ready;
    private boolean pausedForFocus;
    private boolean resumeOnFocusGain;
    private String desiredVoiceName = "";
    private Locale preferredLocale = Locale.getDefault();
    private ReaderController reader;
    private Listener listener;
    private long offset;
    private long pendingNextOffset;

    TtsController(Context context) {
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(change -> {
                    if (change == AudioManager.AUDIOFOCUS_GAIN) {
                        main.post(this::resumeAfterFocus);
                    } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                            || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                        main.post(this::pauseForFocus);
                    } else if (change == AudioManager.AUDIOFOCUS_LOSS) {
                        main.post(() -> stop("audio focus"));
                    }
                }, main)
                .build();
        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            ready = status == TextToSpeech.SUCCESS;
            if (ready) applyDesiredVoice();
        });
        tts.setAudioAttributes(attributes);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }

            @Override public void onDone(String utteranceId) {
                long token = parseToken(utteranceId);
                main.post(() -> {
                    if (token != generation.get() || pausedForFocus || reader == null) return;
                    if (pendingNextOffset > offset) {
                        offset = pendingNextOffset;
                        if (listener != null) listener.onPosition(offset);
                    }
                    speakNext(token);
                });
            }

            @Override public void onError(String utteranceId, int errorCode) {
                main.post(() -> stop("tts error: " + errorCode));
            }

            @SuppressWarnings("deprecation")
            @Override public void onError(String utteranceId) {
                main.post(() -> stop("tts error"));
            }
        });
    }

    void start(ReaderController reader, long from, Listener listener) {
        stop(null);
        if (!ready) {
            if (listener != null) listener.onStopped("TTS engine not ready");
            return;
        }
        if (desiredVoiceName.isEmpty()) {
            try {
                preferredLocale = detectDocumentLocale(reader.page());
                tts.setLanguage(preferredLocale);
            } catch (Exception ignored) {
                preferredLocale = Locale.getDefault();
            }
        } else {
            applyDesiredVoice();
        }
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            if (listener != null) listener.onStopped("audio focus denied");
            return;
        }
        this.reader = reader;
        this.listener = listener;
        this.offset = Math.max(0, from);
        this.pendingNextOffset = this.offset;
        this.pausedForFocus = false;
        this.resumeOnFocusGain = false;
        long token = generation.incrementAndGet();
        speakNext(token);
    }

    void stop(String reason) {
        generation.incrementAndGet();
        resumeOnFocusGain = false;
        pausedForFocus = false;
        pendingNextOffset = offset;
        tts.stop();
        audio.abandonAudioFocusRequest(focus);
        Listener old = listener;
        listener = null;
        reader = null;
        if (reason != null && old != null) old.onStopped(reason);
    }

    boolean isSpeaking() { return reader != null && !pausedForFocus; }
    void setRate(float rate) { tts.setSpeechRate(Math.max(0.5f, Math.min(2f, rate))); }
    void setPitch(float pitch) { tts.setPitch(Math.max(0.5f, Math.min(2f, pitch))); }
    void setLanguage(Locale locale) { preferredLocale = locale == null ? Locale.getDefault() : locale; tts.setLanguage(preferredLocale); }

    void setVoiceName(String voiceName) {
        desiredVoiceName = voiceName == null ? "" : voiceName;
        if (ready) applyDesiredVoice();
    }

    List<VoiceOption> offlineVoices() {
        ArrayList<VoiceOption> output = new ArrayList<>();
        if (!ready || tts.getVoices() == null) return output;
        for (Voice voice : tts.getVoices()) {
            if (voice == null || voice.isNetworkConnectionRequired()) continue;
            Locale locale = voice.getLocale();
            String language = locale == null ? "" : locale.toLanguageTag();
            output.add(new VoiceOption(voice.getName(), language.isEmpty() ? voice.getName() : language + " · " + voice.getName()));
        }
        String preferredLanguage = preferredLocale == null ? "" : preferredLocale.getLanguage();
        output.sort(Comparator
                .comparingInt((VoiceOption option) -> option.label().toLowerCase(Locale.ROOT).startsWith(preferredLanguage.toLowerCase(Locale.ROOT)) ? 0 : 1)
                .thenComparing(VoiceOption::label, String.CASE_INSENSITIVE_ORDER));
        return output;
    }

    private void applyDesiredVoice() {
        if (!ready || desiredVoiceName.isEmpty() || tts.getVoices() == null) return;
        for (Voice voice : tts.getVoices()) {
            if (voice != null && !voice.isNetworkConnectionRequired() && desiredVoiceName.equals(voice.getName())) {
                preferredLocale = voice.getLocale() == null ? Locale.getDefault() : voice.getLocale();
                tts.setVoice(voice);
                return;
            }
        }
    }

    private void pauseForFocus() {
        if (reader == null || pausedForFocus) return;
        resumeOnFocusGain = true;
        pausedForFocus = true;
        generation.incrementAndGet();
        tts.stop();
        if (listener != null) listener.onPaused();
    }

    private void resumeAfterFocus() {
        if (reader == null || !pausedForFocus || !resumeOnFocusGain) return;
        pausedForFocus = false;
        resumeOnFocusGain = false;
        pendingNextOffset = offset;
        long token = generation.incrementAndGet();
        if (listener != null) listener.onResumed();
        speakNext(token);
    }

    private void speakNext(long token) {
        if (reader == null || pausedForFocus || token != generation.get()) return;
        try {
            ReaderController.Speech chunk = reader.speech(offset);
            if (chunk.text().trim().isEmpty() || chunk.nextOffset() <= offset) {
                stop("end");
                return;
            }
            pendingNextOffset = chunk.nextOffset();
            if (listener != null) listener.onPosition(offset);
            tts.speak(chunk.text(), TextToSpeech.QUEUE_FLUSH, new Bundle(), Long.toString(token));
        } catch (Exception error) {
            stop(error.getMessage() == null ? "tts failure" : error.getMessage());
        }
    }

    private static Locale detectDocumentLocale(String text) {
        int hans = 0;
        int hant = 0;
        int hk = 0;
        int latin = 0;
        int cjk = 0;
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset);
            offset += Character.charCount(cp);
            if (cp >= 0x4E00 && cp <= 0x9FFF) cjk++;
            if ((cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z')) latin++;
            if (HANS_MARKERS.indexOf(cp) >= 0) hans++;
            if (HANT_MARKERS.indexOf(cp) >= 0) hant++;
            if (HK_MARKERS.indexOf(cp) >= 0) hk++;
        }
        if (hk >= 2 && hk >= hant / 3) return Locale.forLanguageTag("zh-HK");
        if (hant > hans) return Locale.forLanguageTag("zh-TW");
        if (hans > 0 || cjk >= latin) return Locale.forLanguageTag("zh-CN");
        if (latin > cjk * 2) return Locale.ENGLISH;
        return Locale.getDefault();
    }

    private static long parseToken(String id) {
        try { return Long.parseLong(id); } catch (Exception ignored) { return -1; }
    }

    @Override public void close() {
        stop(null);
        tts.shutdown();
    }
}
