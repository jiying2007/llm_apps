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
  apps/android/app/src/test/java/com/junchen/jingdu/ReaderMotionControllerTest.kt
  apps/android/app/src/benchmark/AndroidManifest.xml
  apps/android/app/src/benchmark/java/com/junchen/jingdu/ReaderBenchmarkFixtureProvider.kt
  apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt
  apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt
  scripts/check-android-performance-slo.py
  scripts/test-android-performance-slo.py
  scripts/run-android-macrobenchmark-ci.sh
  core/native/tests/core_performance_gate_test.cpp
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
foundations=apps/android/app/src/test/java/com/junchen/jingdu/ReaderV3FoundationsTest.kt
motion=apps/android/app/src/test/java/com/junchen/jingdu/ReaderMotionControllerTest.kt
journey=apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt
baseline=apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt
fixture=apps/android/app/src/benchmark/java/com/junchen/jingdu/ReaderBenchmarkFixtureProvider.kt
benchmark_manifest=apps/android/app/src/benchmark/AndroidManifest.xml

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
grep -q 'bestCost == Int.MAX_VALUE' apps/android/app/src/main/java/com/junchen/jingdu/TextProjection.kt
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

# Correctness and long-run soak contracts are mandatory, deterministic and executable in hosted CI.
grep -q 'localizedDeletionDoesNotScaleUnchangedSuffix' "$foundations"
grep -q 'randomizedProjectionSoakRemainsBoundedAndMonotonic' "$foundations"
grep -q 'map.sourceForDisplay(display.indexOf("world").toLong())' "$foundations"
grep -q 'typographyFingerprintCoversPaginationInputs' "$foundations"
grep -q 'semanticTtsNavigationPureCoreSoakIsBounded' "$foundations"
grep -q 'repeat(100_000)' "$motion"
grep -q 'ReaderMotionState.AUTO_SCROLL' "$motion"
grep -q 'ReaderMotionState.AUTO_PAGE' "$motion"
grep -q 'ReaderMotionState.TTS' "$motion"

# Real Android performance contract: benchmark-only fixture/profileability, 10/100MiB journeys,
# hosted execution, explicit target installation, machine-readable P95/P99 SLO and a real
# Baseline Profile critical-user journey.
grep -q '<profileable android:shell="true"' "$benchmark_manifest"
grep -q 'Benchmark-build only' "$fixture"
grep -q 'Reader V3' "$fixture"
! grep -q 'Reader V2' "$fixture"
grep -q 'open10MiBTxt' "$journey"
grep -q 'open100MiBTxt' "$journey"
grep -q 'StartupTimingMetric' "$journey"
grep -q 'FrameTimingMetric' "$journey"
grep -q 'BaselineProfileRule' "$baseline"
grep -q 'readerV3CriticalJourneys' "$baseline"
! grep -q 'reader-v2' "$baseline"
grep -q ':app:assembleBenchmark' scripts/run-android-macrobenchmark-ci.sh
grep -q 'pm path.*TARGET_PACKAGE' scripts/run-android-macrobenchmark-ci.sh
grep -q 'connectedCheck' scripts/run-android-macrobenchmark-ci.sh
grep -q 'enabledRules=Macrobenchmark' scripts/run-android-macrobenchmark-ci.sh
grep -q 'enabledRules=BaselineProfile' scripts/run-android-macrobenchmark-ci.sh
grep -q 'test-android-performance-slo.py' scripts/run-android-macrobenchmark-ci.sh
grep -q 'frameDurationCpuMs' scripts/check-android-performance-slo.py
grep -q 'JINGDU_FRAME_P95_MS' scripts/check-android-performance-slo.py
grep -q 'JINGDU_FRAME_P99_MS' scripts/check-android-performance-slo.py
python3 -m py_compile scripts/check-android-performance-slo.py scripts/test-android-performance-slo.py
python3 scripts/test-android-performance-slo.py
bash -n scripts/run-android-macrobenchmark-ci.sh

# Near-1GiB native RSS must remain a real CTest, not only prose.
grep -q 'JINGDU_PERF_FIXTURE_MIB=960' core/native/CMakeLists.txt
grep -q 'jingdu_core_near_1gib_rss_gate_test' core/native/CMakeLists.txt
grep -q 'rssMiB < 640L' core/native/tests/core_performance_gate_test.cpp

# Hard cut: no old runtime/service/gate implementation may survive.
for legacy in \
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt \
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderV2Panels.kt \
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderAdvancedSettingsSheet.kt \
  apps/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.java \
  scripts/verify-reader-v2.sh; do
  test ! -e "$legacy" || { echo "Reader V3 hard cut left legacy asset: $legacy" >&2; exit 1; }
done
if find apps/android/app/src/main/res -name 'strings_reader_v2.xml' -print -quit | grep -q .; then
  echo 'Reader V3 hard cut left legacy reader_v2 resource container' >&2
  exit 1
fi

if grep -q 'android.permission.INTERNET' apps/android/app/src/main/AndroidManifest.xml; then echo 'Reader V3 forbids INTERNET' >&2; exit 1; fi
if git grep -n -E 'readAt\([^,]+,[[:space:]]*(Long\.MAX_VALUE|[0-9]+[[:space:]]*\*[[:space:]]*1024[[:space:]]*\*[[:space:]]*1024)' -- "$engine" "$screen"; then
  echo 'Reader V3 must keep bounded document windows' >&2; exit 1
fi

echo 'Reader V3 prelaunch contract OK: correctness/Media3/Room/Proto/UDF/soak/Macrobenchmark/BaselineProfile/RSS gates aligned'
