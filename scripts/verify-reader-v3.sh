#!/usr/bin/env bash
set -euo pipefail

require_file() {
  local path="$1"
  test -f "$path" || { echo "Reader V3 asset missing: $path" >&2; exit 1; }
}

require_literal() {
  local path="$1" literal="$2" label="${3:-$2}"
  grep -F -q -- "$literal" "$path" || { echo "Reader V3 contract missing [$label] in $path" >&2; exit 1; }
}

forbid_literal() {
  local path="$1" literal="$2" label="${3:-$2}"
  if grep -F -q -- "$literal" "$path"; then
    echo "Reader V3 forbidden contract [$label] remains in $path" >&2
    exit 1
  fi
}

required=(
  docs/READER_V3_PRELAUNCH_FINAL.md
  apps/android/readerproto/src/main/proto/reader_settings.proto
  apps/android/app/src/main/java/com/junchen/jingdu/BookRepository.java
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
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderPanelSurface.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderSmartChaptersPanel.kt
  apps/android/app/src/main/java/com/junchen/jingdu/SmartTocCacheStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderHotControls.kt
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
  core/native/src/index_cache.h
  core/native/src/index_cache.cpp
  core/native/src/core_api_cached.cpp
  core/native/tests/core_api_test.cpp
  core/native/tests/core_performance_gate_test.cpp
)
for path in "${required[@]}"; do require_file "$path"; done

prefs=apps/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt
screen=apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreenV3.kt
engine=apps/android/app/src/main/java/com/junchen/jingdu/ReaderViewportEngine.kt
controller=apps/android/app/src/main/java/com/junchen/jingdu/ReaderController.java
book_repository=apps/android/app/src/main/java/com/junchen/jingdu/BookRepository.java
pipeline=apps/android/app/src/main/java/com/junchen/jingdu/ReaderPresentationPipeline.kt
annotations=apps/android/app/src/main/java/com/junchen/jingdu/ReaderAnnotationStore.kt
stats=apps/android/app/src/main/java/com/junchen/jingdu/ReaderStatsStore.kt
settings=apps/android/app/src/main/java/com/junchen/jingdu/ReaderSettingsScreen.kt
app=apps/android/app/src/main/java/com/junchen/jingdu/JingduApp.kt
activity=apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
quick_panel=apps/android/app/src/main/java/com/junchen/jingdu/ReaderQuickPanels.kt
panel_surface=apps/android/app/src/main/java/com/junchen/jingdu/ReaderPanelSurface.kt
smart_panel=apps/android/app/src/main/java/com/junchen/jingdu/ReaderSmartChaptersPanel.kt
smart_toc_cache=apps/android/app/src/main/java/com/junchen/jingdu/SmartTocCacheStore.kt
hot_controls=apps/android/app/src/main/java/com/junchen/jingdu/ReaderHotControls.kt
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
macrobenchmark_gradle=apps/android/macrobenchmark/build.gradle
benchmark_runner=scripts/run-android-macrobenchmark-ci.sh
smart_toc=apps/android/app/src/main/java/com/junchen/jingdu/SmartToc.kt
index_cache=core/native/src/index_cache.cpp
cached_core=core/native/src/core_api_cached.cpp
core_test=core/native/tests/core_api_test.cpp

require_literal "$prefs" 'DataStore<ReaderSettingsProto>' 'typed settings datastore'
require_literal "$prefs" 'reader-v3-settings.pb' 'V3 settings store'
require_literal "$prefs" 'pending.debounce(350L)' 'settings write debounce'
require_literal "$prefs" 'ReaderPreset.LOW_VISION' 'low vision preset'
require_literal "$prefs" 'namedThemes' 'named themes'
require_literal "$prefs" 'extraDim' 'extra dim'
require_literal "$prefs" 'ReaderGestureAction' 'gesture actions'
require_literal "$proto" 'center_tap_action' 'center tap proto'
require_literal "$proto" 'double_tap_action' 'double tap proto'
forbid_literal "$prefs" 'preferencesDataStore' 'legacy preferencesDataStore'
forbid_literal "$prefs" 'jingdu_reader_v2' 'legacy reader v2 settings'

require_literal apps/android/app/src/main/java/com/junchen/jingdu/TextProjection.kt 'class TextProjection' 'text projection'
require_literal apps/android/app/src/main/java/com/junchen/jingdu/TextProjection.kt 'bestCost == Int.MAX_VALUE' 'projection bounded fallback'
require_literal "$engine" 'ReaderPresentationPipeline.present' 'shared presentation pipeline'
require_literal "$pipeline" 'SourceDisplayMap.compose' 'projection composition'
require_literal "$engine" 'typographyFingerprint = spec.fingerprint' 'typography fingerprint'
require_literal "$engine" 'androidLayoutText' 'android pagination layout text'
require_literal "$engine" 'BREAK_STRATEGY_SIMPLE' 'bounded line breaking'
require_literal "$controller" 'WINDOW_CHARS = 1536' 'paged window'
require_literal "$controller" 'PAGE_CACHE_CHARS = 64 * 1024L' 'page cache size'
require_literal "$controller" 'PAGE_CACHE_PREFETCH_MARGIN_CHARS = 8192L' 'page prefetch margin'
require_literal "$controller" 'jingdu-page-prefetch' 'page prefetch worker'
require_literal "$engine" 'ReaderController(false)' 'continuous isolated controller'
require_literal "$engine" 'CONTINUOUS_WINDOW_CHARS = 2048L' '2K continuous window'
require_literal "$engine" 'CONTINUOUS_ALIGN_CHARS = 512L' 'continuous alignment'
require_literal "$engine" 'CONTINUOUS_BACK_BUFFER_CHARS = 512L' 'continuous back buffer'
require_literal "$book_repository" 'prewarmChapterIndex(book);' 'import-time chapter index prewarm'
require_literal "$book_repository" 'prewarmChapterIndex(updated);' 'redecode chapter index prewarm'
require_literal "$book_repository" 'source.chapters();' 'authoritative Core chapter prewarm'
require_literal "$smart_toc" 'MIN_CORE_CHAPTERS_FOR_COMPLETE_TOC = 20' 'sparse TOC threshold'
require_literal "$smart_toc" 'if (merged.size < MIN_CORE_CHAPTERS_FOR_COMPLETE_TOC)' 'sparse-only TOC enrichment'
require_literal apps/android/app/src/main/java/com/junchen/jingdu/ReaderTypographySpec.kt 'PARAGRAPH_SPACER' 'paragraph spacing sentinel'

require_literal "$screen" 'val slideAnimation = settings.pageAnimation == ReaderPageAnimation.SLIDE' 'conditional slide animation'
require_literal "$screen" 'pageDirection = if (settings.pageAnimation == ReaderPageAnimation.SLIDE' 'slide direction guard'
require_literal "$screen" 'state.position, state.pageText, state, adaptiveLayout' 'paged route source'
require_literal "$screen" 'val visibleText = remember(displayText, visibleEnd)' 'visible prefix slicing'
require_literal "$screen" 'readerAnnotatedTextV3(sourceStart, visibleText' 'annotation after pagination'
forbid_literal "$screen" 'annotated.subSequence(0, visibleEnd)' 'post-annotation pagination'
require_literal "$screen" 'scrollState.isScrollInProgress' 'continuous gesture settling'
require_literal "$screen" '!scrolling && absolute != lastCommitted' 'manual continuous commit'
require_literal "$screen" 'AUTO_SCROLL_COMMIT_CHARS = 512L' 'auto-scroll commit bound'
forbid_literal "$screen" 'abs(absolute - lastCommitted) >= 192' 'old noisy commit threshold'
require_literal "$screen" 'MAX_CHAPTER_TICKS = 96' 'chapter tick bound'
require_literal "$screen" 'take(MAX_CHAPTER_TICKS)' 'bounded chapter ticks'
require_literal "$hot_controls" 'Exact overload used by ReaderReadingStatusV3' 'Canvas reading status text'
require_literal "$hot_controls" 'Canvas(modifier.fillMaxWidth().height(22.dp))' 'fixed-cost status drawing'

require_literal "$activity" 'progressWorkers: ExecutorService' 'progress IO worker'
require_literal "$activity" 'tocWorkers: ExecutorService' 'TOC worker'
require_literal "$activity" 'THREAD_PRIORITY_BACKGROUND' 'background TOC priority'
require_literal "$activity" 'publishPositionOnly(book, bounded)' 'lightweight continuous position commit'
require_literal "$activity" 'SmartTocCacheStore(this)' 'revision TOC cache'
require_literal "$activity" 'smartTocCache.load(book.id, revision, length)' 'TOC cache read'
require_literal "$activity" 'smartTocCache.save(book.id, revision, length, report)' 'TOC cache write'
require_literal "$smart_toc_cache" 'cacheFile(bookId, revision, sourceLength)' 'revision-keyed TOC cache key'

require_literal "$app" 'val latestPosition = rememberUpdatedState(state.position)' 'stable position closure'
require_literal "$app" 'val trackedActions = remember(actions)' 'stable reader actions'
require_literal "$app" 'ReaderPanel.QUICK_SETTINGS -> ReaderQuickSettingsSheet' 'quick settings route'
require_literal "$app" 'ReaderPanel.CHAPTERS -> ReaderSmartChaptersPanel' 'chapters route'
require_literal "$quick_panel" 'ReaderPanelSurface(onDismiss = actions.onClosePanel)' 'quick panel surface'
require_literal "$smart_panel" 'ReaderPanelSurface(onDismiss = actions.onClosePanel)' 'chapters panel surface'
require_literal "$smart_panel" 'TocPanelCache' 'bounded panel cache'
require_literal "$smart_panel" 'derivedCache.load(book.id, book.normalizedSha256, state.length)' 'derived TOC reuse'
require_literal "$smart_panel" 'SmartToc.evaluate(state.chapters.map' 'bounded cache-eviction fallback'
forbid_literal "$smart_panel" 'SmartToc.analyze(reader)' 'full scan inside panel'
require_literal "$panel_surface" 'same composition tree' 'single composition panel surface'
test ! -e apps/android/app/src/main/java/com/junchen/jingdu/ReaderHotPanels.kt || { echo 'Reader V3 superseded hot panels remain' >&2; exit 1; }

require_literal "$index_cache" 'kMagicV1 = "JDX1"' 'JDX1 detector'
require_literal "$index_cache" 'kMagicV2 = "JDX2"' 'JDX2 detector'
require_literal "$index_cache" 'load_chapter_cache' 'chapter cache load'
require_literal "$index_cache" 'save_index_cache_with_chapters' 'chapter cache save'
require_literal "$cached_core" 'jd_chapters_uncached_internal' 'authoritative uncached chapter scan'
require_literal "$cached_core" 'load_chapter_cache(document->path' 'cached chapter load path'
require_literal "$cached_core" 'save_index_cache_with_chapters' 'chapter cache upgrade path'
require_literal "$core_test" 'first chapter scan upgrades cache to JDX2' 'chapter cache upgrade test'
require_literal "$core_test" 'JDX2 chapters preserve authoritative output' 'chapter cache correctness test'

require_literal apps/android/app/src/main/java/com/junchen/jingdu/ReaderDatabase.kt '@Database' 'Room database'
require_literal "$annotations" 'ReaderAnnotationEntity' 'Room annotation entity'
require_literal "$annotations" 'reanchor(item' 'annotation reanchor'
require_literal "$stats" 'ReaderSessionEntity' 'Room session entity'
forbid_literal "$annotations" 'reader-v2-annotations.json' 'legacy annotation JSON'
forbid_literal "$stats" 'reader-v2-stats.json' 'legacy stats JSON'

require_literal "$screen" 'rememberSelectionState' 'selection state'
require_literal "$screen" 'SelectionContainer(state = selectionState)' 'selection container'
require_literal "$screen" 'ReaderSelectionController.fromSelectedTexts' 'selection projection'
require_literal "$screen" 'extendAcrossBoundary' 'two-stage selection'
require_literal "$screen" 'ReaderSkimController' 'skim controller'
require_literal "$screen" 'ReaderSkimPreviewCardV3' 'skim preview'
require_literal apps/android/app/src/main/java/com/junchen/jingdu/ReaderViewModel.kt 'MutableStateFlow' 'UDF state flow'
require_literal "$app" 'ReaderSettingsScreen' 'settings route'
require_literal "$app" 'ReaderAnnotationsV3Panel' 'annotations route'
require_literal "$app" 'ReaderReadingMapV3Panel' 'reading map route'
require_literal apps/android/app/src/main/java/com/junchen/jingdu/ReaderRoute.kt 'ReaderScreenV3' 'Reader V3 route'

require_literal "$settings" 'ReaderPreset.entries' 'preset selector'
require_literal "$prefs" 'ReaderPreset.LOW_VISION' 'low vision settings'
require_literal "$settings" 'NamedThemes' 'named theme controls'
require_literal "$settings" 'extraDim' 'extra dim controls'
require_literal "$settings" 'twoStageSelectionEnabled' 'two-stage selection setting'
require_literal "$settings" 'dictionaryProcessTextEnabled' 'dictionary process-text setting'
require_literal "$settings" 'advancedGestureCustomizationEnabled' 'advanced gesture setting'
require_literal "$settings" 'centerTapAction' 'center tap setting'
require_literal "$settings" 'doubleTapAction' 'double tap setting'
require_literal "$screen" 'ReaderGestureAction' 'gesture runtime'
require_literal "$screen" 'ACTION_PROCESS_TEXT' 'dictionary runtime'
require_literal "$screen" 'WindowInsets.systemGestures' 'system gesture arbitration'

require_literal "$service" 'class TtsPlaybackService : MediaSessionService' 'Media3 session service'
require_literal "$service" 'MediaSession.Builder' 'Media3 session'
require_literal "$player" 'class ReaderTtsPlayer' 'Reader TTS player'
require_literal "$player" 'SimpleBasePlayer' 'Media3 player base'
require_literal "$navigator" 'previousSentence' 'previous sentence'
require_literal "$navigator" 'nextSentence' 'next sentence'
require_literal "$navigator" 'previousParagraph' 'previous paragraph'
require_literal "$navigator" 'nextParagraph' 'next paragraph'
require_literal apps/android/app/src/main/AndroidManifest.xml 'androidx.media3.session.MediaSessionService' 'Media3 manifest service'
forbid_literal "$service" 'android.media.session.MediaSession' 'platform MediaSession authority'

require_literal "$foundations" 'localizedDeletionDoesNotScaleUnchangedSuffix' 'localized projection deletion test'
require_literal "$foundations" 'randomizedProjectionSoakRemainsBoundedAndMonotonic' 'projection soak'
require_literal "$foundations" 'map.sourceForDisplay(display.indexOf("world").toLong())' 'projection exactness'
require_literal "$foundations" 'typographyFingerprintCoversPaginationInputs' 'typography fingerprint test'
require_literal "$foundations" 'semanticTtsNavigationPureCoreSoakIsBounded' 'TTS semantic soak'
require_literal "$motion" 'repeat(100_000)' 'motion soak size'
require_literal "$motion" 'ReaderMotionState.AUTO_SCROLL' 'auto-scroll motion test'
require_literal "$motion" 'ReaderMotionState.AUTO_PAGE' 'auto-page motion test'
require_literal "$motion" 'ReaderMotionState.TTS' 'TTS motion test'

require_literal "$benchmark_manifest" '<profileable android:shell="true"' 'profileable benchmark target'
require_literal "$macrobenchmark_gradle" 'signingConfig = signingConfigs.debug' 'benchmark debug signing'
require_literal "$fixture" 'Benchmark-build only' 'benchmark-only fixture'
require_literal "$fixture" 'Reader V3' 'V3 fixture'
forbid_literal "$fixture" 'Reader V2' 'V2 fixture residue'
require_literal "$fixture" 'advancedGestureCustomizationEnabled = false' 'deterministic fixture gestures'
require_literal "$fixture" 'centerTapAction = ReaderGestureAction.CONTROLS' 'deterministic fixture center tap'
require_literal "$fixture" 'volumeKeyMode = ReaderVolumeKeyMode.PAGE_WHEN_NOT_TTS' 'deterministic volume paging'
require_literal "$fixture" 'BODY_LINES_PER_CHAPTER = 256' 'representative chapter density'
require_literal "$journey" 'open10MiBTxt' '10MiB journey'
require_literal "$journey" 'open100MiBTxt' '100MiB journey'
require_literal "$journey" 'StartupTimingMetric' 'startup timing metric'
require_literal "$journey" 'FrameTimingMetric' 'frame timing metric'
require_literal "$journey" 'KEYCODE_VOLUME_DOWN' 'real page-turn input'
require_literal "$journey" 'ensureTopControlsVisible' 'real panel controls'
require_literal "$baseline" 'BaselineProfileRule' 'baseline profile rule'
require_literal "$baseline" 'prepareProfileTarget("paged")' 'profile target provisioning before collect'
require_literal "$baseline" 'readerV3CriticalJourneys' 'critical profile journeys'
require_literal "$baseline" 'KEYCODE_VOLUME_DOWN' 'profile page turns'
require_literal "$baseline" 'ensureTopControlsVisible' 'profile panel controls'
forbid_literal "$baseline" 'reader-v2' 'V2 baseline residue'
require_literal "$benchmark_runner" ':app:assembleBenchmark' 'benchmark app assembly'
require_literal "$benchmark_runner" ':macrobenchmark:assembleBenchmark' 'benchmark test assembly'
require_literal "$benchmark_runner" 'pm path "$TARGET_PACKAGE"' 'target install verification'
require_literal "$benchmark_runner" 'TEST_APK' 'test APK path'
require_literal "$benchmark_runner" 'pm list instrumentation' 'instrumentation discovery'
require_literal "$benchmark_runner" 'shell am instrument -w -r' 'direct instrumentation'
require_literal "$benchmark_runner" 'additionalTestOutputDir' 'benchmark evidence directory'
require_literal "$benchmark_runner" 'no-isolated-storage true' 'benchmark evidence access'
require_literal "$benchmark_runner" 'androidx.benchmark.enabledRules' 'benchmark rule filter'
require_literal "$benchmark_runner" 'run_instrumentation Macrobenchmark' 'Macrobenchmark instrumentation'
require_literal "$benchmark_runner" 'run_instrumentation BaselineProfile' 'BaselineProfile instrumentation'
require_literal "$benchmark_runner" 'Android guest is unavailable before ${label} target installation' 'target-swap guest readiness'
require_literal "$benchmark_runner" 'benchmarkData.json' 'benchmarkData evidence'
require_literal "$benchmark_runner" 'baseline-prof.txt' 'baseline profile evidence'
require_literal "$benchmark_runner" 'startup-prof.txt' 'startup profile evidence'
forbid_literal "$benchmark_runner" 'connectedBenchmarkAndroidTest' 'connected benchmark shortcut'
forbid_literal "$benchmark_runner" 'connectedCheck' 'connected check shortcut'
require_literal "$benchmark_runner" 'test-android-performance-slo.py' 'SLO self-test'
require_literal scripts/check-android-performance-slo.py 'frameDurationCpuMs' 'real frame metric'
require_literal scripts/check-android-performance-slo.py 'JINGDU_FRAME_P95_MS' 'P95 threshold'
require_literal scripts/check-android-performance-slo.py 'JINGDU_FRAME_P99_MS' 'P99 threshold'
python3 -m py_compile scripts/check-android-performance-slo.py scripts/test-android-performance-slo.py
python3 scripts/test-android-performance-slo.py
bash -n "$benchmark_runner"

require_literal core/native/CMakeLists.txt 'JINGDU_PERF_FIXTURE_MIB=960' 'near-1GiB fixture'
require_literal core/native/CMakeLists.txt 'jingdu_core_near_1gib_rss_gate_test' 'near-1GiB CTest'
require_literal core/native/tests/core_performance_gate_test.cpp 'rssMiB < 640L' 'native RSS SLO'

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

if grep -F -q 'android.permission.INTERNET' apps/android/app/src/main/AndroidManifest.xml; then
  echo 'Reader V3 forbids INTERNET' >&2
  exit 1
fi
if git grep -n -E 'readAt\([^,]+,[[:space:]]*(Long\.MAX_VALUE|[0-9]+[[:space:]]*\*[[:space:]]*1024[[:space:]]*\*[[:space:]]*1024)' -- "$engine" "$screen"; then
  echo 'Reader V3 must keep bounded document windows' >&2
  exit 1
fi

echo 'Reader V3 prelaunch contract OK: correctness/Media3/Room/Proto/UDF/cache/hot-path/soak/Macrobenchmark/BaselineProfile/RSS gates aligned'
