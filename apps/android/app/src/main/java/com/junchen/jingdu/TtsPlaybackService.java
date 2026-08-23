package com.junchen.jingdu;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.io.File;

/**
 * Background/lock-screen/headset TTS host. Text stays local: the service opens only the private
 * normalized/clean file path supplied by MainActivity and delegates speech chunking to Core.
 */
public final class TtsPlaybackService extends Service {
    static final String ACTION_START = "com.junchen.jingdu.tts.START";
    static final String ACTION_TOGGLE = "com.junchen.jingdu.tts.TOGGLE";
    static final String ACTION_STOP = "com.junchen.jingdu.tts.STOP";
    static final String ACTION_NEXT = "com.junchen.jingdu.tts.NEXT";
    static final String ACTION_PREVIOUS = "com.junchen.jingdu.tts.PREVIOUS";
    static final String ACTION_SLEEP = "com.junchen.jingdu.tts.SLEEP";
    static final String ACTION_STATE = "com.junchen.jingdu.tts.STATE";

    static final String EXTRA_PATH = "path";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_OFFSET = "offset";
    static final String EXTRA_RATE = "rate";
    static final String EXTRA_PITCH = "pitch";
    static final String EXTRA_VOICE = "voice";
    static final String EXTRA_MINUTES = "minutes";
    static final String EXTRA_PLAYING = "playing";
    static final String EXTRA_NEXT_OFFSET = "nextOffset";
    static final String EXTRA_REASON = "reason";

    private static final String CHANNEL_ID = "jingdu_tts_playback";
    private static final int NOTIFICATION_ID = 2201;
    private static final long PLAYBACK_ACTIONS = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE |
            PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP |
            PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS;

    private final Handler main = new Handler(Looper.getMainLooper());
    private ReaderController reader;
    private TtsController engine;
    private MediaSession mediaSession;
    private long offset;
    private long nextOffset;
    private boolean playing;
    private String title = "Jingdu";
    private float rate = 1f;
    private float pitch = 1f;
    private String voice = "";
    private int startRetries;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        reader = new ReaderController();
        engine = new TtsController(this);
        mediaSession = new MediaSession(this, "JingduTts");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resumeSpeech(); }
            @Override public void onPause() { pauseSpeech(); }
            @Override public void onStop() { stopPlayback("user"); }
            @Override public void onSkipToNext() { skipNext(); }
            @Override public void onSkipToPrevious() { skipPrevious(); }
        });
        mediaSession.setActive(true);
        updatePlaybackState();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            startBook(intent);
        } else if (ACTION_TOGGLE.equals(action)) {
            if (playing) pauseSpeech(); else resumeSpeech();
        } else if (ACTION_STOP.equals(action)) {
            stopPlayback("user");
        } else if (ACTION_NEXT.equals(action)) {
            skipNext();
        } else if (ACTION_PREVIOUS.equals(action)) {
            skipPrevious();
        } else if (ACTION_SLEEP.equals(action)) {
            setSleepTimer(intent.getIntExtra(EXTRA_MINUTES, 0));
        }
        return START_NOT_STICKY;
    }

    private void startBook(Intent intent) {
        String path = intent.getStringExtra(EXTRA_PATH);
        if (path == null || path.isEmpty()) { stopPlayback("missing document"); return; }
        try {
            engine.stop(null);
            reader.close();
            reader.open(new File(path), intent.getLongExtra(EXTRA_OFFSET, 0));
            offset = reader.position();
            nextOffset = offset;
            title = valueOr(intent.getStringExtra(EXTRA_TITLE), "Jingdu");
            rate = intent.getFloatExtra(EXTRA_RATE, 1f);
            pitch = intent.getFloatExtra(EXTRA_PITCH, 1f);
            voice = valueOr(intent.getStringExtra(EXTRA_VOICE), "");
            engine.setRate(rate);
            engine.setPitch(pitch);
            engine.setVoiceName(voice);
            mediaSession.setMetadata(new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "Jingdu · Local TXT")
                    .build());
            startRetries = 0;
            playing = true;
            startForeground(NOTIFICATION_ID, notification());
            updatePlaybackState();
            broadcast(null);
            startSpeechWithRetry();
        } catch (Exception error) {
            stopPlayback(error.getMessage() == null ? "tts open failure" : error.getMessage());
        }
    }

    private void startSpeechWithRetry() {
        if (!playing) return;
        engine.start(reader, offset, new TtsController.Listener() {
            @Override public void onPosition(long value) {
                offset = Math.max(0, value);
                try {
                    ReaderController.Speech chunk = reader.speech(offset);
                    nextOffset = chunk.nextOffset();
                } catch (Exception ignored) {
                    nextOffset = offset;
                }
                broadcast(null);
                updatePlaybackState();
            }
            @Override public void onPaused() {
                playing = false;
                updatePlaybackState();
                broadcast("focus-paused");
            }
            @Override public void onResumed() {
                playing = true;
                updatePlaybackState();
                broadcast(null);
            }
            @Override public void onStopped(String reason) {
                if ("TTS engine not ready".equals(reason) && startRetries < 12) {
                    startRetries++;
                    main.postDelayed(TtsPlaybackService.this::startSpeechWithRetry, 250L);
                    return;
                }
                if ("end".equals(reason)) {
                    playing = false;
                    updatePlaybackState();
                    broadcast("end");
                    stopForeground(false);
                } else if (reason != null) {
                    playing = false;
                    updatePlaybackState();
                    broadcast(reason);
                }
            }
        });
    }

    private void resumeSpeech() {
        if (playing || reader.length() <= 0) return;
        playing = true;
        startRetries = 0;
        startForeground(NOTIFICATION_ID, notification());
        updatePlaybackState();
        startSpeechWithRetry();
    }

    private void pauseSpeech() {
        if (!playing) return;
        engine.stop(null);
        playing = false;
        updatePlaybackState();
        broadcast("paused");
        stopForeground(false);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, notification());
    }

    private void skipNext() {
        if (reader.length() <= 0) return;
        try {
            ReaderController.Speech chunk = reader.speech(offset);
            offset = Math.max(offset + 1, chunk.nextOffset());
            reader.jump(offset);
            if (playing) { engine.stop(null); startSpeechWithRetry(); }
            broadcast(null);
        } catch (Exception ignored) { }
    }

    private void skipPrevious() {
        if (reader.length() <= 0) return;
        offset = Math.max(0, offset - 900);
        reader.jump(offset);
        if (playing) { engine.stop(null); startSpeechWithRetry(); }
        broadcast(null);
    }

    private void setSleepTimer(int minutes) {
        main.removeCallbacksAndMessages(SLEEP_TOKEN);
        if (minutes <= 0) return;
        main.postAtTime(() -> stopPlayback("sleep"), SLEEP_TOKEN,
                android.os.SystemClock.uptimeMillis() + minutes * 60_000L);
    }

    private void stopPlayback(String reason) {
        main.removeCallbacksAndMessages(null);
        playing = false;
        if (engine != null) engine.stop(null);
        updatePlaybackState();
        broadcast(reason);
        stopForeground(true);
        stopSelf();
    }

    private void updatePlaybackState() {
        if (mediaSession == null) return;
        mediaSession.setPlaybackState(new PlaybackState.Builder()
                .setActions(PLAYBACK_ACTIONS)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        Math.max(0, offset), 1f)
                .build());
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, notification());
    }

    private Notification notification() {
        PendingIntent content = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent previous = serviceIntent(ACTION_PREVIOUS, 1);
        PendingIntent toggle = serviceIntent(ACTION_TOGGLE, 2);
        PendingIntent next = serviceIntent(ACTION_NEXT, 3);
        PendingIntent stop = serviceIntent(ACTION_STOP, 4);
        Notification.Action previousAction = new Notification.Action.Builder(android.R.drawable.ic_media_previous, "Previous", previous).build();
        Notification.Action toggleAction = new Notification.Action.Builder(
                playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                playing ? "Pause" : "Play", toggle).build();
        Notification.Action nextAction = new Notification.Action.Builder(android.R.drawable.ic_media_next, "Next", next).build();
        Notification.Action stopAction = new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop).build();
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText("Local read aloud")
                .setContentIntent(content)
                .setOnlyAlertOnce(true)
                .setOngoing(playing)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(previousAction)
                .addAction(toggleAction)
                .addAction(nextAction)
                .addAction(stopAction)
                .setStyle(new Notification.MediaStyle().setMediaSession(mediaSession.getSessionToken()).setShowActionsInCompactView(0, 1, 2))
                .build();
    }

    private PendingIntent serviceIntent(String action, int requestCode) {
        return PendingIntent.getService(this, requestCode, new Intent(this, TtsPlaybackService.class).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void broadcast(String reason) {
        Intent intent = new Intent(ACTION_STATE).setPackage(getPackageName())
                .putExtra(EXTRA_PLAYING, playing)
                .putExtra(EXTRA_OFFSET, offset)
                .putExtra(EXTRA_NEXT_OFFSET, nextOffset);
        if (reason != null) intent.putExtra(EXTRA_REASON, reason);
        sendBroadcast(intent);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Read aloud", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Background local TXT read aloud controls");
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }

    private static String valueOr(String value, String fallback) { return value == null ? fallback : value; }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (engine != null) engine.close();
        if (reader != null) reader.close();
        if (mediaSession != null) { mediaSession.setActive(false); mediaSession.release(); }
        super.onDestroy();
    }

    private static final Object SLEEP_TOKEN = new Object();
}
