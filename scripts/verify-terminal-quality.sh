#!/usr/bin/env bash
set -euo pipefail

required=(
  docs/PERFORMANCE_SLO.md
  core/native/tests/core_performance_gate_test.cpp
  apps/android/app/src/main/java/com/junchen/jingdu/CleanHistory.kt
  apps/android/app/src/main/java/com/junchen/jingdu/LibraryMetadataStore.kt
  apps/android/app/src/main/res/values/strings_terminal_quality.xml
  apps/android/app/src/main/res/values-b+zh+Hans/strings_terminal_quality.xml
  apps/android/app/src/main/res/values-b+zh+Hant/strings_terminal_quality.xml
  apps/android/macrobenchmark/build.gradle
  apps/android/macrobenchmark/src/main/AndroidManifest.xml
  apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/StartupBenchmark.kt
  apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt
)
for path in "${required[@]}"; do
  test -f "$path" || { echo "terminal-quality asset missing: $path" >&2; exit 1; }
done

grep -q 'jingdu_core_performance_gate_test' core/native/CMakeLists.txt
grep -q 'JINGDU_PERF_FIXTURE_MIB' core/native/tests/core_performance_gate_test.cpp
grep -q '1000' core/native/tests/core_performance_gate_test.cpp
grep -q 'peakRssMiB' core/native/tests/core_performance_gate_test.cpp

grep -q 'enum class NoiseRisk' apps/android/app/src/main/java/com/junchen/jingdu/UiModels.kt
grep -q 'defaultSafeSelection' apps/android/app/src/main/java/com/junchen/jingdu/UiModels.kt
grep -q 'impactChars' apps/android/app/src/main/java/com/junchen/jingdu/UiModels.kt
grep -q 'smartCleanUndoAvailable' apps/android/app/src/main/java/com/junchen/jingdu/UiModels.kt

grep -q 'CleanHistory' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'LibraryMetadataStore' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'PROGRESS_SAVE_INTERVAL_MS' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'PROGRESS_SAVE_CHAR_DELTA' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'cleanHistory.save' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'undoSmartClean' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'bookmarks.${book.id}' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'restoredOverride = preservedProgress' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt

grep -q 'existing != null ? existing.progress : 0' apps/android/app/src/main/java/com/junchen/jingdu/BookRepository.java
grep -q 'book.progress,' apps/android/app/src/main/java/com/junchen/jingdu/BookRepository.java
grep -q 'book.progress >= charCount' apps/android/app/src/main/java/com/junchen/jingdu/BookRepository.java

grep -q 'library_filter_favorites' apps/android/app/src/main/java/com/junchen/jingdu/LibraryScreen.kt
grep -q 'LibrarySort.PROGRESS' apps/android/app/src/main/java/com/junchen/jingdu/LibraryScreen.kt
grep -q 'onToggleFavorite' apps/android/app/src/main/java/com/junchen/jingdu/LibraryScreen.kt
grep -q 'onSetBookTags' apps/android/app/src/main/java/com/junchen/jingdu/JingduApp.kt

grep -q 'setWillPauseWhenDucked(true)' apps/android/app/src/main/java/com/junchen/jingdu/TtsController.java
grep -q 'pauseForFocus' apps/android/app/src/main/java/com/junchen/jingdu/TtsController.java
grep -q 'resumeAfterFocus' apps/android/app/src/main/java/com/junchen/jingdu/TtsController.java

grep -q 'smart_clean_explain_low' apps/android/app/src/main/java/com/junchen/jingdu/ReaderSheets.kt
grep -q 'smart_clean_impact' apps/android/app/src/main/java/com/junchen/jingdu/ReaderSheets.kt
grep -q 'onUndoSmartClean' apps/android/app/src/main/java/com/junchen/jingdu/ReaderSheets.kt

grep -q 'id "com.android.test" version "9.3.1" apply false' apps/android/build.gradle
grep -q 'include(":app", ":macrobenchmark")' apps/android/settings.gradle
grep -q 'benchmark-macro-junit4:1.4.1' apps/android/macrobenchmark/build.gradle
grep -q 'uiautomator:2.4.0' apps/android/macrobenchmark/build.gradle
grep -q 'StartupTimingMetric' apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/StartupBenchmark.kt
grep -q 'BaselineProfileRule' apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt
grep -q '<profileable android:shell="true"' apps/android/app/src/main/AndroidManifest.xml
grep -q 'profileinstaller:1.4.1' apps/android/app/build.gradle

python3 ./scripts/verify-android-i18n.py

echo 'Terminal long-form TXT quality contract OK'
