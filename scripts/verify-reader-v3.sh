#!/usr/bin/env bash
set -euo pipefail

required=(
  docs/READER_V3_PRELAUNCH_FINAL.md
  apps/android/readerproto/src/main/proto/reader_settings.proto
  apps/android/app/src/main/java/com/junchen/jingdu/TextProjection.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderPresentationPipeline.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderTypographySpec.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderViewportEngine.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderSelectionController.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderSkimController.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderDatabase.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderAnnotationStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderStatsStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderViewModel.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderSettingsScreen.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderQuickPanels.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderV3Panels.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreenV3.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderTtsPlayer.kt
  apps/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.kt
  apps/android/app/src/main/java/com/junchen/jingdu/TtsSemanticNavigator.kt
  apps/android/app/src/test/java/com/junchen/jingdu/ReaderV3FoundationsTest.kt
)
for path in "${required[@]}"; do test -f "$path" || { echo "Reader V3 asset missing: $path" >&2; exit 1; }; done

prefs=apps/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt
screen=apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreenV3.kt
engine=apps/android/app/src/main/java/com/junchen/jingdu/ReaderViewportEngine.kt
pipeline=apps/android/app/src/main/java/com/junchen/jingdu/ReaderPresentationPipeline.kt
annotations=apps/android/app/src/main/java/com/junchen/jingdu/ReaderAnnotationStore.kt
stats=apps/android/app/src/main/java/com/junchen/jingdu/ReaderStatsStore.kt
settings=apps/android/app/src/main/java/com/junchen/jingdu/ReaderSettingsScreen.kt
app=apps/android/app/src/main/java/com/junchen/jingdu/JingduApp.kt
service=apps/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.kt
player=apps/android/app/src/main/java/com/junchen/jingdu/ReaderTtsPlayer.kt
navigator=apps/android/app/src/main/java/com/junchen/jingdu/TtsSemanticNavigator.kt
proto=apps/android/readerproto/src/main/proto/reader_settings.proto

# Typed settings; key/value V2 schema is not retained.
grep -q 'DataStore<ReaderSettingsProto>' "$prefs"
grep -q 'reader-v3-settings.pb' "$prefs"
grep -q 'pending.debounce(350L)' "$prefs"
grep -q 'ReaderPreset.LOW_VISION' "$prefs"
grep -q 'namedThemes' "$prefs"
grep -q 'extraDim' "$prefs"
grep -q 'ReaderGestureAction' "$prefs"
grep -q 'center_tap_action' "$proto"
grep -q 'double_tap_action' "$proto"
! grep -q 'preferencesDataStore' "$prefs"
! grep -q 'jingdu_reader_v2' "$prefs"

# Exact source/display projection and one presentation/typography truth.
grep -q 'class TextProjection' apps/android/app/src/main/java/com/junchen/jingdu/TextProjection.kt
grep -q 'ReaderPresentationPipeline.present' "$engine"
grep -q 'SourceDisplayMap.compose' "$pipeline"
grep -q 'typographyFingerprint = spec.fingerprint' "$engine"
grep -q 'androidLayoutText' "$engine"
grep -q 'PARAGRAPH_SPACER' apps/android/app/src/main/java/com/junchen/jingdu/ReaderTypographySpec.kt

# Room is the retained annotation/stat persistence backend.
grep -q '@Database' apps/android/app/src/main/java/com/junchen/jingdu/ReaderDatabase.kt
grep -q 'ReaderAnnotationEntity' "$annotations"
grep -q 'reanchor(item' "$annotations"
grep -q 'ReaderSessionEntity' "$stats"
! grep -q 'reader-v2-annotations.json' "$annotations"
! grep -q 'reader-v2-stats.json' "$stats"

# Native selection/skim/UDF are on the actual runtime path.
grep -q 'rememberSelectionState' "$screen"
grep -q 'SelectionContainer(state = selectionState)' "$screen"
grep -q 'ReaderSelectionController.fromSelectedTexts' "$screen"
grep -q 'extendAcrossBoundary' "$screen"
grep -q 'ReaderSkimController' "$screen"
grep -q 'ReaderSkimPreviewCardV3' "$screen"
grep -q 'MutableStateFlow' apps/android/app/src/main/java/com/junchen/jingdu/ReaderViewModel.kt
grep -q 'ReaderSettingsScreen' "$app"
grep -q 'ReaderAnnotationsV3Panel' "$app"
grep -q 'ReaderReadingMapV3Panel' "$app"
grep -q 'ReaderScreenV3' apps/android/app/src/main/java/com/junchen/jingdu/ReaderRoute.kt

# All requested prelaunch controls are reachable and functional.
grep -q 'ReaderPreset.entries' "$settings"
grep -q 'ReaderPreset.LOW_VISION' "$prefs"
grep -q 'NamedThemes' "$settings"
grep -q 'extraDim' "$settings"
grep -q 'twoStageSelectionEnabled' "$settings"
grep -q 'dictionaryProcessTextEnabled' "$settings"
grep -q 'advancedGestureCustomizationEnabled' "$settings"
grep -q 'centerTapAction' "$settings"
grep -q 'doubleTapAction' "$settings"
grep -q 'ReaderGestureAction' "$screen"
grep -q 'ACTION_PROCESS_TEXT' "$screen"
grep -q 'WindowInsets.systemGestures' "$screen"

# Media3 is the only Android playback session authority.
grep -q 'class TtsPlaybackService : MediaSessionService' "$service"
grep -q 'MediaSession.Builder' "$service"
grep -q 'class ReaderTtsPlayer' "$player"
grep -q 'SimpleBasePlayer' "$player"
grep -q 'previousSentence' "$navigator"
grep -q 'nextSentence' "$navigator"
grep -q 'previousParagraph' "$navigator"
grep -q 'nextParagraph' "$navigator"
grep -q 'androidx.media3.session.MediaSessionService' apps/android/app/src/main/AndroidManifest.xml
! grep -q 'android.media.session.MediaSession' "$service"

# Exact projection/typography tests are mandatory.
grep -q 'localizedDeletionDoesNotScaleUnchangedSuffix' apps/android/app/src/test/java/com/junchen/jingdu/ReaderV3FoundationsTest.kt
grep -q 'typographyFingerprintCoversPaginationInputs' apps/android/app/src/test/java/com/junchen/jingdu/ReaderV3FoundationsTest.kt

# Hard cut: no old runtime/service/gate implementation may survive.
for legacy in \
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt \
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderV2Panels.kt \
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderAdvancedSettingsSheet.kt \
  apps/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.java \
  scripts/verify-reader-v2.sh; do
  test ! -e "$legacy" || { echo "Reader V3 hard cut left legacy asset: $legacy" >&2; exit 1; }
done

if grep -q 'android.permission.INTERNET' apps/android/app/src/main/AndroidManifest.xml; then echo 'Reader V3 forbids INTERNET' >&2; exit 1; fi
if git grep -n -E 'readAt\([^,]+,[[:space:]]*(Long\.MAX_VALUE|[0-9]+[[:space:]]*\*[[:space:]]*1024[[:space:]]*\*[[:space:]]*1024)' -- "$engine" "$screen"; then
  echo 'Reader V3 must keep bounded document windows' >&2; exit 1
fi

echo 'Reader V3 prelaunch contract OK: exact projection/typography/Room/Proto/UDF/selection/skim/gesture/Media3 invariants aligned'
