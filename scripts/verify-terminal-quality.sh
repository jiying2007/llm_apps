#!/usr/bin/env bash
set -euo pipefail

required=(
  docs/PRODUCT.md docs/PERFORMANCE_SLO.md docs/SMART_CLEAN_ARCHITECTURE.md docs/COMPETITIVE_MOAT.md
  docs/GROWTH_MONETIZATION.md docs/RELEASE.md docs/READER_V3_PRELAUNCH_FINAL.md
  THIRD_PARTY_NOTICES.md third_party/NOTICE.md
  core/native/tests/core_performance_gate_test.cpp
  apps/android/app/src/main/java/com/junchen/jingdu/TxtDoctor.kt
  apps/android/app/src/main/java/com/junchen/jingdu/SmartToc.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ProgressiveImport.kt
  apps/android/app/src/main/java/com/junchen/jingdu/FolderLibraryStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/BatchAutomation.kt
  apps/android/app/src/main/java/com/junchen/jingdu/PrivacyAudit.kt
  apps/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt
  apps/android/app/src/main/java/com/junchen/jingdu/SmartCleanFeedbackStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderTtsPlayer.kt
  apps/android/app/src/main/java/com/junchen/jingdu/TtsSemanticNavigator.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderMotionController.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderViewportEngine.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderAnnotationStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderDatabase.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderFontStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderStatsStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderRoute.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreenV3.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderSettingsScreen.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderQuickPanels.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderV3Panels.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderPresentationPipeline.kt
  apps/android/app/src/main/java/com/junchen/jingdu/TextProjection.kt
  apps/android/app/src/test/java/com/junchen/jingdu/ReaderMotionControllerTest.kt
  apps/android/app/src/test/java/com/junchen/jingdu/ReaderV3FoundationsTest.kt
  apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt
  apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt
  scripts/check-android-performance-slo.py scripts/run-android-macrobenchmark-ci.sh
  scripts/train-smartclean-model.py scripts/verify-smartclean-model.py scripts/verify-reader-v3.sh
)
for path in "${required[@]}"; do test -f "$path" || { echo "terminal-quality asset missing: $path" >&2; exit 1; }; done

grep -q 'Long TXT · Smart Clean · Fully local' docs/PRODUCT.md
grep -q 'TXT Doctor' docs/PRODUCT.md
grep -q 'Smart TOC' docs/PRODUCT.md
grep -q 'Precision is deliberately more important than recall' docs/COMPETITIVE_MOAT.md

grep -q 'JINGDU_PERF_FIXTURE_MIB' core/native/tests/core_performance_gate_test.cpp
grep -q '1000' core/native/tests/core_performance_gate_test.cpp
grep -q 'peakRssMiB' core/native/tests/core_performance_gate_test.cpp
grep -q 'JINGDU_PERF_FIXTURE_MIB=960' core/native/CMakeLists.txt

grep -q 'SAMPLE_WINDOWS = 8' apps/android/app/src/main/java/com/junchen/jingdu/TxtDoctor.kt
grep -q 'MAX_PREVIEW_BYTES = 512 \* 1024' apps/android/app/src/main/java/com/junchen/jingdu/ProgressiveImport.kt
grep -q 'ActivityResultContracts.OpenDocumentTree' apps/android/app/src/main/java/com/junchen/jingdu/LibraryScreen.kt
grep -q 'MAX_BOOKS = 100' apps/android/app/src/main/java/com/junchen/jingdu/BatchAutomation.kt
grep -q 'Manifest.permission.INTERNET !in permissions' apps/android/app/src/main/java/com/junchen/jingdu/PrivacyAudit.kt
grep -q 'enum class SmartCleanFeedback { NONE, KEEP, DELETE, PROTECT }' apps/android/app/src/main/java/com/junchen/jingdu/SmartCleanFeedbackStore.kt
grep -q 'TinyLocalSemanticCandidateClassifier' apps/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt
python3 scripts/train-smartclean-model.py --verify-source apps/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt
python3 scripts/verify-smartclean-model.py

bash ./scripts/verify-reader-v3.sh
grep -q ':app:testDebugUnitTest' apps/android/build.gradle
grep -q 'repeat(100_000)' apps/android/app/src/test/java/com/junchen/jingdu/ReaderMotionControllerTest.kt
grep -q 'FrameTimingMetric' apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt
grep -q 'pageTurn' apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt
grep -q 'continuousScroll' apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt
grep -q 'open100MiBTxt' apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt
grep -q 'Benchmark Novel' apps/android/app/src/benchmark/java/com/junchen/jingdu/ReaderBenchmarkFixtureProvider.kt
grep -q 'device.executeShellCommand' apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt
grep -q 'jingdu-reader-v3' apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt
! grep -R -q 'Reader V2\|reader-v2' apps/android/app/src/benchmark apps/android/macrobenchmark

grep -q 'MediaSessionService' apps/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.kt
grep -q 'SimpleBasePlayer' apps/android/app/src/main/java/com/junchen/jingdu/ReaderTtsPlayer.kt
grep -q 'previousSentence' apps/android/app/src/main/java/com/junchen/jingdu/TtsSemanticNavigator.kt
grep -q 'previousParagraph' apps/android/app/src/main/java/com/junchen/jingdu/TtsSemanticNavigator.kt
! grep -q 'android.media.session.MediaSession' apps/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.kt

if grep -q 'android.permission.INTERNET' apps/android/app/src/main/AndroidManifest.xml; then echo 'offline/privacy contract forbids INTERNET' >&2; exit 1; fi
if git grep -n -E 'converted(File|Path)|converted-[^ ]+\.txt|opencc.*\.txt' -- apps/android/app/src/main/java; then echo 'full-book converted artifact found' >&2; exit 1; fi
if grep -qE 'normalizedFile|documentFile|ReaderController|File\(' apps/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt; then echo 'semantic classifier must remain file-blind' >&2; exit 1; fi

for legacy in apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt apps/android/app/src/main/java/com/junchen/jingdu/ReaderV2Panels.kt apps/android/app/src/main/java/com/junchen/jingdu/ReaderAdvancedSettingsSheet.kt apps/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.java scripts/verify-reader-v2.sh; do
  test ! -e "$legacy" || { echo "legacy Reader V2 asset remains: $legacy" >&2; exit 1; }
done

test ! -f .github/workflows/source-release.yml
python3 -m py_compile scripts/publish-source-release.py
grep -Fq 'needs: [native-core, android, android-performance, harmony-contract, play-store-contract, terminal-contract]' .github/workflows/ci.yml
grep -q 'if existing is not None and release_status != 404:' scripts/publish-source-release.py

echo 'Terminal long-form / moat / Reader V3 quality contract OK'
