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
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderFastText.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderHotPanelHost.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderDatabase.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderAnnotationStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderStatsStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreenV3.kt
  apps/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderTtsPlayer.kt
  apps/android/app/src/main/java/com/junchen/jingdu/TtsSemanticNavigator.kt
  apps/android/app/src/benchmark/AndroidManifest.xml
  apps/android/app/src/benchmark/java/com/junchen/jingdu/ReaderBenchmarkFixtureProvider.kt
  apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt
  apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt
  scripts/run-android-macrobenchmark-ci.sh
  scripts/check-android-performance-slo.py
  core/native/src/index_cache.cpp
  core/native/src/core_api_cached.cpp
  core/native/tests/core_api_test.cpp
)
for path in "${required[@]}"; do test -f "$path" || { echo "Reader V3 asset missing: $path" >&2; exit 1; }; done

prefs=apps/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt
screen=apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreenV3.kt
fast_text=apps/android/app/src/main/java/com/junchen/jingdu/ReaderFastText.kt
engine=apps/android/app/src/main/java/com/junchen/jingdu/ReaderViewportEngine.kt
controller=apps/android/app/src/main/java/com/junchen/jingdu/ReaderController.java
pipeline=apps/android/app/src/main/java/com/junchen/jingdu/ReaderPresentationPipeline.kt
app=apps/android/app/src/main/java/com/junchen/jingdu/JingduApp.kt
host=apps/android/app/src/main/java/com/junchen/jingdu/ReaderHotPanelHost.kt
annotations=apps/android/app/src/main/java/com/junchen/jingdu/ReaderAnnotationStore.kt
stats=apps/android/app/src/main/java/com/junchen/jingdu/ReaderStatsStore.kt
service=apps/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.kt
player=apps/android/app/src/main/java/com/junchen/jingdu/ReaderTtsPlayer.kt
navigator=apps/android/app/src/main/java/com/junchen/jingdu/TtsSemanticNavigator.kt
proto=apps/android/readerproto/src/main/proto/reader_settings.proto
journey=apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt
baseline=apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt
fixture=apps/android/app/src/benchmark/java/com/junchen/jingdu/ReaderBenchmarkFixtureProvider.kt
runner=scripts/run-android-macrobenchmark-ci.sh
index_cache=core/native/src/index_cache.cpp
cached_core=core/native/src/core_api_cached.cpp
core_test=core/native/tests/core_api_test.cpp

grep -q 'DataStore<ReaderSettingsProto>' "$prefs"
grep -q 'reader-v3-settings.pb' "$prefs"
grep -q 'pending.debounce(350L)' "$prefs"
grep -q 'ReaderPreset.LOW_VISION' "$prefs"
grep -q 'ReaderGestureAction' "$prefs"
grep -q 'center_tap_action' "$proto"
grep -q 'double_tap_action' "$proto"
! grep -q 'preferencesDataStore' "$prefs"
! grep -q 'jingdu_reader_v2' "$prefs"

grep -q 'class TextProjection' apps/android/app/src/main/java/com/junchen/jingdu/TextProjection.kt
grep -q 'ReaderPresentationPipeline.present' "$engine"
grep -q 'SourceDisplayMap.compose' "$pipeline"
grep -q 'typographyFingerprint = spec.fingerprint' "$engine"
grep -q 'WINDOW_CHARS = 1536' "$controller"
grep -q 'PAGE_CACHE_CHARS = 64 \* 1024L' "$controller"
grep -q 'PAGE_CACHE_PREFETCH_MARGIN_CHARS = 8192L' "$controller"
grep -q 'CONTINUOUS_WINDOW_CHARS = 4096L' "$engine"
grep -q 'CONTINUOUS_ALIGN_CHARS = 1024L' "$engine"
grep -q 'val visibleText = remember(displayText, visibleEnd)' "$screen"
grep -q 'readerAnnotatedTextV3(sourceStart, visibleText' "$screen"
grep -q 'scrollState.isScrollInProgress' "$screen"
grep -q '!scrolling && absolute != lastCommitted' "$screen"
grep -q 'AUTO_SCROLL_COMMIT_CHARS = 512L' "$screen"

grep -q 'produceState<StaticLayout?>' "$fast_text"
grep -q 'produceState<TextLayoutResult?>' "$fast_text"
grep -q 'withContext(Dispatchers.Default)' "$fast_text"

# One full-size hot host remains mounted; its remembered state excludes pageText/position.
grep -q 'val hotPanelState = remember' "$app"
grep -q 'ReaderHotPanelHost(hotPanelState, trackedActions, currentReaderPosition)' "$app"
grep -q 'ReaderPanel.QUICK_SETTINGS, ReaderPanel.CHAPTERS -> Unit' "$app"
! grep -q 'ReaderPanel.QUICK_SETTINGS -> ReaderQuickSettingsSheet' "$app"
! grep -q 'ReaderPanel.CHAPTERS -> ReaderSmartChaptersPanel' "$app"
grep -q 'Layout-stable Reader V3 hot overlay' "$host"
grep -q 'if (!hotActive) return@Canvas' "$host"
grep -q 'HOT_CHAPTER_ROWS = 8' "$host"
grep -q 'SmartToc.evaluate(state.chapters.map' "$host"
grep -q 'contentDescription = continuousLabel' "$host"

grep -q 'kMagicV1 = "JDX1"' "$index_cache"
grep -q 'kMagicV2 = "JDX2"' "$index_cache"
grep -q 'load_chapter_cache' "$cached_core"
grep -q 'save_index_cache_with_chapters' "$cached_core"
grep -q 'first chapter scan upgrades cache to JDX2' "$core_test"

grep -q 'rememberSelectionState' "$screen"
grep -q 'SelectionContainer(state = selectionState)' "$screen"
grep -q 'ReaderSelectionController.fromSelectedTexts' "$screen"
grep -q 'extendAcrossBoundary' "$screen"

grep -q '@Database' apps/android/app/src/main/java/com/junchen/jingdu/ReaderDatabase.kt
grep -q 'ReaderAnnotationEntity' "$annotations"
grep -q 'ReaderSessionEntity' "$stats"
grep -q 'class TtsPlaybackService : MediaSessionService' "$service"
grep -q 'MediaSession.Builder' "$service"
grep -q 'SimpleBasePlayer' "$player"
grep -q 'previousSentence' "$navigator"
grep -q 'nextSentence' "$navigator"
! grep -q 'android.media.session.MediaSession' "$service"

grep -q 'FrameTimingMetric' "$journey"
grep -q 'StartupTimingMetric' "$journey"
grep -q 'KEYCODE_VOLUME_DOWN' "$journey"
grep -q 'BaselineProfileRule' "$baseline"
grep -q 'readerV3CriticalJourneys' "$baseline"
grep -q 'readerV3Startup' "$baseline"
grep -q 'Benchmark-build only' "$fixture"
grep -q 'BODY_LINES_PER_CHAPTER = 256' "$fixture"
grep -q 'run_instrumentation Macrobenchmark' "$runner"
grep -q 'run_instrumentation BaselineProfile' "$runner"
grep -q 'os.environ.get("JINGDU_FRAME_P95_MS", "40")' scripts/check-android-performance-slo.py
grep -q 'os.environ.get("JINGDU_FRAME_P99_MS", "80")' scripts/check-android-performance-slo.py

for removed in \
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt \
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderV2Panels.kt \
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderAdvancedSettingsSheet.kt; do
  test ! -e "$removed" || { echo "Reader V3 residual exists: $removed" >&2; exit 1; }
done

echo 'Reader V3 performance-candidate contract OK: stable isolated hot panel host, exact Source/Core offsets, bounded windows, 40/80 unchanged'
