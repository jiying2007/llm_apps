#!/usr/bin/env bash
set -euo pipefail

required=(
  docs/PERFORMANCE_SLO.md
  docs/SMART_CLEAN_ARCHITECTURE.md
  THIRD_PARTY_NOTICES.md
  third_party/NOTICE.md
  third_party/licenses/OpenccJava-MIT.txt
  third_party/licenses/OpenCC-Apache-2.0.txt
  core/native/tests/core_performance_gate_test.cpp
  apps/android/app/src/main/java/com/junchen/jingdu/CleanHistory.kt
  apps/android/app/src/main/java/com/junchen/jingdu/LibraryMetadataStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/BuiltinCleanRules.kt
  apps/android/app/src/main/java/com/junchen/jingdu/SmartCleanRefiner.java
  apps/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ChineseDisplayConverter.java
  apps/android/app/src/main/res/values/strings_terminal_quality.xml
  apps/android/app/src/main/res/values-b+zh+Hans/strings_terminal_quality.xml
  apps/android/app/src/main/res/values-b+zh+Hant/strings_terminal_quality.xml
  apps/android/app/src/main/res/values/strings_smart_clean3.xml
  apps/android/app/src/main/res/values-b+zh+Hans/strings_smart_clean3.xml
  apps/android/app/src/main/res/values-b+zh+Hant/strings_smart_clean3.xml
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
grep -q '"inline_fragment", "garbled_line" -> false' apps/android/app/src/main/java/com/junchen/jingdu/UiModels.kt

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
grep -q 'BuiltinCleanRules.PACK_VERSION' apps/android/app/src/main/java/com/junchen/jingdu/ReaderSheets.kt
grep -q 'noise_reason_inline_fragment' apps/android/app/src/main/java/com/junchen/jingdu/ReaderSheets.kt
grep -q 'noise_reason_garbled_line' apps/android/app/src/main/java/com/junchen/jingdu/ReaderSheets.kt

grep -q 'const val PACK_VERSION = 3' apps/android/app/src/main/java/com/junchen/jingdu/BuiltinCleanRules.kt
grep -q 'confidence: Int' apps/android/app/src/main/java/com/junchen/jingdu/BuiltinCleanRules.kt
grep -q 'BufferedReader' apps/android/app/src/main/java/com/junchen/jingdu/SmartCleanRefiner.java
grep -q 'MAX_UNIQUE = 160' apps/android/app/src/main/java/com/junchen/jingdu/SmartCleanRefiner.java
grep -q 'inline_fragment' apps/android/app/src/main/java/com/junchen/jingdu/SmartCleanRefiner.java
grep -q 'garbled_line' apps/android/app/src/main/java/com/junchen/jingdu/SmartCleanRefiner.java
grep -q 'classifyCandidate(text: String)' apps/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt
grep -q 'DisabledSemanticCandidateClassifier' apps/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt

grep -q 'enum class ChineseDisplayMode' apps/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt
grep -q 'chineseOverrides' apps/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt
grep -q 'ChineseDisplayConverter.configure' apps/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt
grep -q 'OpenCC.convert' apps/android/app/src/main/java/com/junchen/jingdu/ChineseDisplayConverter.java
grep -q 'sourceCharsForDisplayed' apps/android/app/src/main/java/com/junchen/jingdu/ChineseDisplayConverter.java
grep -q 'ChineseDisplayConverter.convert(text, settings.chineseMode' apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt
grep -q 'sourceCharsForDisplayed(text, displayText' apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt
grep -q 'ChineseDisplayConverter.searchVariants' apps/android/app/src/main/java/com/junchen/jingdu/ReaderController.java
grep -q 'SmartCleanRefiner.scan' apps/android/app/src/main/java/com/junchen/jingdu/ReaderController.java
grep -q 'ChineseDisplayConverter.convert(packed.substring' apps/android/app/src/main/java/com/junchen/jingdu/ReaderController.java
grep -q 'chineseMode = chineseMode' apps/android/app/src/main/java/com/junchen/jingdu/UserBackup.kt
grep -q 'chineseOverrides = chineseOverrides' apps/android/app/src/main/java/com/junchen/jingdu/UserBackup.kt
grep -q 'else ChineseDisplayMode.ORIGINAL' apps/android/app/src/main/java/com/junchen/jingdu/UserBackup.kt
grep -q 'else ""' apps/android/app/src/main/java/com/junchen/jingdu/UserBackup.kt
grep -q 'io.github.laisuk:openccjava:1.4.2' apps/android/app/build.gradle
grep -q 'assets.srcDir rootProject.file("../../third_party")' apps/android/app/build.gradle

grep -q '^MIT License$' third_party/licenses/OpenccJava-MIT.txt
grep -q 'Copyright (c) 2025 https://github.com/laisuk' third_party/licenses/OpenccJava-MIT.txt
grep -q '^Apache License$' third_party/licenses/OpenCC-Apache-2.0.txt
grep -q '^Version 2.0, January 2004$' third_party/licenses/OpenCC-Apache-2.0.txt
grep -q 'Derivative Works that You distribute' third_party/licenses/OpenCC-Apache-2.0.txt
grep -q 'OpenccJava-MIT.txt' THIRD_PARTY_NOTICES.md
grep -q 'OpenCC-Apache-2.0.txt' THIRD_PARTY_NOTICES.md
grep -q 'repository root has no `NOTICE` file' THIRD_PARTY_NOTICES.md
grep -q 'This directory is packaged into the Android APK/AAB as application assets.' third_party/NOTICE.md

if grep -q 'android.permission.INTERNET' apps/android/app/src/main/AndroidManifest.xml; then
  echo 'Smart Clean/OpenCC must not add Android INTERNET permission' >&2
  exit 1
fi
if git grep -n -E 'converted(File|Path)|converted-[^ ]+\.txt|opencc.*\.txt' -- apps/android/app/src/main/java; then
  echo 'full-book converted artifact mechanism found; conversion must remain windowed' >&2
  exit 1
fi

grep -q 'id "com.android.test" version "9.3.1" apply false' apps/android/build.gradle
grep -q 'include(":app", ":macrobenchmark")' apps/android/settings.gradle
grep -q 'benchmark-macro-junit4:1.4.1' apps/android/macrobenchmark/build.gradle
grep -q 'uiautomator:2.4.0' apps/android/macrobenchmark/build.gradle
grep -q 'StartupTimingMetric' apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/StartupBenchmark.kt
grep -q 'BaselineProfileMode.UseIfAvailable' apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/StartupBenchmark.kt
grep -q 'BaselineProfileRule' apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt
grep -q 'includeInStartupProfile = true' apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt
grep -q 'stableIterations = 3' apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt
grep -q '<profileable android:shell="true"' apps/android/app/src/main/AndroidManifest.xml
grep -q 'profileinstaller:1.4.1' apps/android/app/build.gradle

python3 ./scripts/verify-android-i18n.py

echo 'Terminal long-form TXT / Smart Clean 3 / OpenCC quality contract OK'
