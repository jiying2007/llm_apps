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

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

final class TtsController implements AutoCloseable {
    interface Listener { void onPosition(long offset); void onStopped(String reason); }

    private final AudioManager audio;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final TextToSpeech tts;
    private final AudioFocusRequest focus;
    private final AtomicLong generation = new AtomicLong();
    private boolean ready;
    private ReaderController reader;
    private Listener listener;
    private long offset;

    TtsController(Context context) {
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change -> {
                    if (change == AudioManager.AUDIOFOCUS_LOSS
                            || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                            || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                        main.post(() -> stop("audio focus"));
                    }
                })
                .build();
        tts = new TextToSpeech(context.getApplicationContext(),
                status -> ready = status == TextToSpeech.SUCCESS);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }

            @Override public void onDone(String utteranceId) {
                long token = parseToken(utteranceId);
                main.post(() -> { if (token == generation.get()) speakNext(token); });
            }

            @Override public void onError(String utteranceId, int errorCode) {
                main.post(() -> stop("tts error: " + errorCode));
            }

            // Android still declares this API-15 method abstract even though API 21
            // deprecated it in favor of onError(String, int), so a minimal override
            // is required to keep the listener concrete for every supported API.
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
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            if (listener != null) listener.onStopped("audio focus denied");
            return;
        }
        this.reader = reader;
        this.listener = listener;
        this.offset = Math.max(0, from);
        long token = generation.incrementAndGet();
        speakNext(token);
    }

    void stop(String reason) {
        generation.incrementAndGet();
        tts.stop();
        audio.abandonAudioFocusRequest(focus);
        Listener old = listener;
        listener = null;
        reader = null;
        if (reason != null && old != null) old.onStopped(reason);
    }

    boolean isSpeaking() { return reader != null; }
    void setRate(float rate) { tts.setSpeechRate(Math.max(0.5f, Math.min(2f, rate))); }
    void setPitch(float pitch) { tts.setPitch(Math.max(0.5f, Math.min(2f, pitch))); }
    void setLanguage(Locale locale) { tts.setLanguage(locale); }

    private void speakNext(long token) {
        if (reader == null || token != generation.get()) return;
        try {
            ReaderController.Speech chunk = reader.speech(offset);
            if (chunk.text().trim().isEmpty() || chunk.nextOffset() <= offset) {
                stop("end");
                return;
            }
            offset = chunk.nextOffset();
            if (listener != null) listener.onPosition(offset);
            tts.speak(chunk.text(), TextToSpeech.QUEUE_FLUSH, new Bundle(), Long.toString(token));
        } catch (Exception error) {
            stop(error.getMessage() == null ? "tts failure" : error.getMessage());
        }
    }

    private static long parseToken(String id) {
        try { return Long.parseLong(id); } catch (Exception ignored) { return -1; }
    }

    @Override public void close() {
        stop(null);
        tts.shutdown();
    }
}
