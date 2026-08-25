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
  apps/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.java
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderMotionController.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderViewportEngine.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderAnnotationStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderDatabase.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderFontStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderStatsStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderRoute.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderSettingsScreen.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderV3Panels.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderPresentationPipeline.kt
  apps/android/app/src/main/java/com/junchen/jingdu/TextProjection.kt
  apps/android/app/src/test/java/com/junchen/jingdu/ReaderMotionControllerTest.kt
  apps/android/app/src/test/java/com/junchen/jingdu/ReaderV3FoundationsTest.kt
  apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt
  apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt
  scripts/train-smartclean-model.py scripts/verify-smartclean-model.py scripts/verify-reader-v3.sh
)
for path in "${required[@]}"; do test -f "$path" || { echo "terminal-quality asset missing: $path" >&2; exit 1; }; done

# Product scope / local moat.
grep -q 'Long TXT · Smart Clean · Fully local' docs/PRODUCT.md
grep -q 'TXT Doctor' docs/PRODUCT.md
grep -q 'Smart TOC' docs/PRODUCT.md
grep -q 'Precision is deliberately more important than recall' docs/COMPETITIVE_MOAT.md

# Long-form native gate includes the near-1GiB fixture and RSS observation.
grep -q 'JINGDU_PERF_FIXTURE_MIB' core/native/tests/core_performance_gate_test.cpp
grep -q '1000' core/native/tests/core_performance_gate_test.cpp
grep -q 'peakRssMiB' core/native/tests/core_performance_gate_test.cpp

# Existing differentiated product gates stay intact.
grep -q 'SAMPLE_WINDOWS = 8' apps/android/app/src/main/java/com/junchen/jingdu/TxtDoctor.kt
grep -q 'MAX_PREVIEW_BYTES = 512 \* 1024' apps/android/app/src/main/java/com/junchen/jingdu/ProgressiveImport.kt
grep -q 'ActivityResultContracts.OpenDocumentTree' apps/android/app/src/main/java/com/junchen/jingdu/LibraryScreen.kt
grep -q 'MAX_BOOKS = 100' apps/android/app/src/main/java/com/junchen/jingdu/BatchAutomation.kt
grep -q 'Manifest.permission.INTERNET !in permissions' apps/android/app/src/main/java/com/junchen/jingdu/PrivacyAudit.kt
grep -q 'enum class SmartCleanFeedback { NONE, KEEP, DELETE, PROTECT }' apps/android/app/src/main/java/com/junchen/jingdu/SmartCleanFeedbackStore.kt
grep -q 'TinyLocalSemanticCandidateClassifier' apps/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt
python3 scripts/train-smartclean-model.py --verify-source apps/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt
python3 scripts/verify-smartclean-model.py

# Reader V3 owns the final prelaunch correctness/performance contracts.
bash ./scripts/verify-reader-v3.sh
grep -q ':app:testDebugUnitTest' apps/android/build.gradle
grep -q 'repeat(100_000)' apps/android/app/src/test/java/com/junchen/jingdu/ReaderMotionControllerTest.kt
grep -q 'FrameTimingMetric' apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt
grep -q 'pageTurn' apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt
grep -q 'continuousScroll' apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt
grep -q 'Benchmark Novel' apps/android/app/src/benchmark/java/com/junchen/jingdu/ReaderBenchmarkFixtureProvider.kt
grep -q 'device.executeShellCommand' apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt

# Offline/privacy/no-whole-document regressions.
if grep -q 'android.permission.INTERNET' apps/android/app/src/main/AndroidManifest.xml; then echo 'offline/privacy contract forbids INTERNET' >&2; exit 1; fi
if git grep -n -E 'converted(File|Path)|converted-[^ ]+\.txt|opencc.*\.txt' -- apps/android/app/src/main/java; then echo 'full-book converted artifact found' >&2; exit 1; fi
if grep -qE 'normalizedFile|documentFile|ReaderController|File\(' apps/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt; then echo 'semantic classifier must remain file-blind' >&2; exit 1; fi

# Source-release governance remains single-path and immutable.
test ! -f .github/workflows/source-release.yml
python3 -m py_compile scripts/publish-source-release.py
grep -Fq 'needs: [native-core, android, harmony-contract, play-store-contract, terminal-contract]' .github/workflows/ci.yml
grep -q 'if existing is not None and release_status != 404:' scripts/publish-source-release.py

echo 'Terminal long-form / moat / Reader V3 quality contract OK'
