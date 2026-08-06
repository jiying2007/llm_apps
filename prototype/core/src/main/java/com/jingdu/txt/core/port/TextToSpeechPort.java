package com.jingdu.txt.core.port;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public interface TextToSpeechPort {
    final class VoiceInfo {
        public final String id;
        public final String localeTag;
        public final boolean networkRequired;

        public VoiceInfo(String id, String localeTag, boolean networkRequired) {
            if (id == null || id.isEmpty() || localeTag == null) {
                throw new IllegalArgumentException("voice metadata is invalid");
            }
            this.id = id;
            this.localeTag = localeTag;
            this.networkRequired = networkRequired;
        }
    }

    final class Capabilities {
        public final boolean available;
        public final boolean rangeProgress;
        public final boolean backgroundPlayback;
        public final boolean offlineVoiceAvailable;
        public final int maxInputCharacters;
        public final List<String> voiceIds;
        public final List<VoiceInfo> voices;
        public final VoiceInfo defaultVoice;

        public Capabilities(boolean available, boolean rangeProgress, boolean backgroundPlayback,
                boolean offlineVoiceAvailable, int maxInputCharacters,
                VoiceInfo defaultVoice, List<VoiceInfo> voices) {
            if (maxInputCharacters < 0) {
                throw new IllegalArgumentException("maximum speech input is invalid");
            }
            this.available = available;
            this.rangeProgress = rangeProgress;
            this.backgroundPlayback = backgroundPlayback;
            this.offlineVoiceAvailable = offlineVoiceAvailable;
            this.maxInputCharacters = maxInputCharacters;
            this.defaultVoice = defaultVoice;
            List<VoiceInfo> copy = voices == null
                    ? Collections.<VoiceInfo>emptyList()
                    : new ArrayList<VoiceInfo>(voices);
            Set<String> uniqueIds = new HashSet<String>();
            for (VoiceInfo voice : copy) {
                if (voice == null || !uniqueIds.add(voice.id)) {
                    throw new IllegalArgumentException("voice metadata is invalid");
                }
            }
            this.voices = Collections.unmodifiableList(copy);
            List<String> ids = new ArrayList<String>(copy.size());
            for (VoiceInfo voice : copy) {
                ids.add(voice.id);
            }
            this.voiceIds = Collections.unmodifiableList(ids);
        }
    }

    interface Listener {
        void onStart(String utteranceId);
        void onRange(String utteranceId, int start, int end);
        void onDone(String utteranceId);
        void onStopped(String utteranceId, boolean interrupted);
        void onError(String utteranceId, String errorCode);
    }

    Capabilities capabilities();
    void setListener(Listener listener);
    void speak(String utteranceId, String text, String voiceId, float rate, float pitch);
    void pause();
    void resume();
    void stop();
    void shutdown();
}
