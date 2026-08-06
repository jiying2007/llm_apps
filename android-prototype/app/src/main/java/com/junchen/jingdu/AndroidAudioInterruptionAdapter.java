package com.junchen.jingdu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/** Owns audio focus and the dynamic noisy-output receiver for one foreground TTS session. */
public final class AndroidAudioInterruptionAdapter {
    public enum Reason {
        FOCUS_LOSS,
        FOCUS_TRANSIENT,
        FOCUS_DUCK,
        OUTPUT_DISCONNECTED
    }

    public interface Listener {
        void onPauseRequested(Reason reason);
    }

    private final Context context;
    private final AudioManager audioManager;
    private final AudioFocusRequest focusRequest;
    private final Listener listener;
    private final BroadcastReceiver noisyReceiver;
    private boolean active;
    private boolean receiverRegistered;

    public AndroidAudioInterruptionAdapter(Context context, Listener listener) {
        if (context == null || listener == null) {
            throw new IllegalArgumentException("audio interruption context and listener required");
        }
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            throw new IllegalStateException("system audio manager is unavailable");
        }
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(this::onAudioFocusChange,
                        new Handler(Looper.getMainLooper()))
                .build();
        noisyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ignored, Intent intent) {
                if (active && AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(
                        intent == null ? null : intent.getAction())) {
                    listener.onPauseRequested(Reason.OUTPUT_DISCONNECTED);
                }
            }
        };
    }

    public boolean start() {
        if (active) {
            return true;
        }
        active = true;
        int result = audioManager.requestAudioFocus(focusRequest);
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            active = false;
            return false;
        }
        if (!active) {
            audioManager.abandonAudioFocusRequest(focusRequest);
            return false;
        }
        try {
            IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(noisyReceiver, filter);
            }
            receiverRegistered = true;
            return true;
        } catch (RuntimeException registrationFailure) {
            active = false;
            audioManager.abandonAudioFocusRequest(focusRequest);
            return false;
        }
    }

    public void stop() {
        active = false;
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(noisyReceiver);
            } catch (IllegalArgumentException ignored) {
                // Registration state is already cleared by the platform.
            }
            receiverRegistered = false;
        }
        audioManager.abandonAudioFocusRequest(focusRequest);
    }

    public boolean isActive() {
        return active;
    }

    private void onAudioFocusChange(int focusChange) {
        if (!active) {
            return;
        }
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            listener.onPauseRequested(Reason.FOCUS_LOSS);
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            listener.onPauseRequested(Reason.FOCUS_TRANSIENT);
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            listener.onPauseRequested(Reason.FOCUS_DUCK);
        }
    }
}
