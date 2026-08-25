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
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderV3Panels.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt
  apps/android/app/src/test/java/com/junchen/jingdu/ReaderV3FoundationsTest.kt
)
for path in "${required[@]}"; do test -f "$path" || { echo "Reader V3 asset missing: $path" >&2; exit 1; }; done

prefs=apps/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt
screen=apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt
engine=apps/android/app/src/main/java/com/junchen/jingdu/ReaderViewportEngine.kt
annotations=apps/android/app/src/main/java/com/junchen/jingdu/ReaderAnnotationStore.kt
stats=apps/android/app/src/main/java/com/junchen/jingdu/ReaderStatsStore.kt
settings=apps/android/app/src/main/java/com/junchen/jingdu/ReaderSettingsScreen.kt
app=apps/android/app/src/main/java/com/junchen/jingdu/JingduApp.kt

# Typed settings; the V2 key/value schema must not survive the first-launch hard cut.
grep -q 'DataStore<ReaderSettingsProto>' "$prefs"
grep -q 'reader-v3-settings.pb' "$prefs"
grep -q 'pending.debounce(350L)' "$prefs"
grep -q 'ReaderPreset.LOW_VISION' "$prefs"
grep -q 'namedThemes' "$prefs"
grep -q 'extraDim' "$prefs"
! grep -q 'preferencesDataStore' "$prefs"
! grep -q 'jingdu_reader_v2' "$prefs"

# Exact source/display projection and one presentation/typography truth.
grep -q 'class TextProjection' apps/android/app/src/main/java/com/junchen/jingdu/TextProjection.kt
grep -q 'ReaderPresentationPipeline.present' "$engine"
grep -q 'SourceDisplayMap.compose' "$engine"
grep -q 'typographyFingerprint = spec.fingerprint' "$engine"
grep -q 'androidLayoutText' "$engine"
grep -q 'PARAGRAPH_SPACER' apps/android/app/src/main/java/com/junchen/jingdu/ReaderTypographySpec.kt

# Room is the only retained annotations/stats persistence backend.
grep -q '@Database' apps/android/app/src/main/java/com/junchen/jingdu/ReaderDatabase.kt
grep -q 'ReaderAnnotationEntity' "$annotations"
grep -q 'contextualRelocate' "$annotations"
grep -q 'ReaderSessionEntity' "$stats"
! grep -q 'reader-v2-annotations.json' "$annotations"
! grep -q 'reader-v2-stats.json' "$stats"

# Selection/skim/UDF foundations.
grep -q 'ReaderSelectionController' apps/android/app/src/main/java/com/junchen/jingdu/ReaderSelectionController.kt
grep -q 'extendAcrossBoundary' apps/android/app/src/main/java/com/junchen/jingdu/ReaderSelectionController.kt
grep -q 'ReaderSkimPreview' apps/android/app/src/main/java/com/junchen/jingdu/ReaderSkimController.kt
grep -q 'MutableStateFlow' apps/android/app/src/main/java/com/junchen/jingdu/ReaderViewModel.kt
grep -q 'ReaderSettingsScreen' "$app"
grep -q 'ReaderV3AnnotationsSheet' "$app"
grep -q 'ReaderV3ReadingMapSheet' "$app"

# All requested prelaunch controls must be reachable by users.
grep -q 'ReaderPreset.entries' "$settings"
grep -q 'ReaderPreset.LOW_VISION' "$prefs"
grep -q 'NamedThemes' "$settings"
grep -q 'extraDim' "$settings"
grep -q 'twoStageSelectionEnabled' "$settings"
grep -q 'dictionaryProcessTextEnabled' "$settings"
grep -q 'advancedGestureCustomizationEnabled' "$settings"

# Exact projection/typography tests are mandatory.
grep -q 'localizedDeletionDoesNotScaleUnchangedSuffix' apps/android/app/src/test/java/com/junchen/jingdu/ReaderV3FoundationsTest.kt
grep -q 'typographyFingerprintCoversPaginationInputs' apps/android/app/src/test/java/com/junchen/jingdu/ReaderV3FoundationsTest.kt

# Offline and bounded-memory invariants.
if grep -q 'android.permission.INTERNET' apps/android/app/src/main/AndroidManifest.xml; then echo 'Reader V3 forbids INTERNET' >&2; exit 1; fi
if git grep -n -E 'readAt\([^,]+,[[:space:]]*(Long\.MAX_VALUE|[0-9]+[[:space:]]*\*[[:space:]]*1024[[:space:]]*\*[[:space:]]*1024)' -- "$engine" "$screen"; then
  echo 'Reader V3 must keep bounded document windows' >&2; exit 1
fi

echo 'Reader V3 prelaunch foundation contract OK'
