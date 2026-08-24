#!/usr/bin/env bash
set -euo pipefail

required=(
  docs/PRODUCT.md
  docs/PERFORMANCE_SLO.md
  docs/SMART_CLEAN_ARCHITECTURE.md
  docs/COMPETITIVE_MOAT.md
  docs/GROWTH_MONETIZATION.md
  docs/RELEASE.md
  THIRD_PARTY_NOTICES.md
  third_party/NOTICE.md
  third_party/licenses/OpenccJava-MIT.txt
  third_party/licenses/OpenCC-Apache-2.0.txt
  scripts/publish-source-release.py
  releases/source/v2.2.0.md
  core/native/tests/core_performance_gate_test.cpp
  apps/android/app/src/main/java/com/junchen/jingdu/CleanHistory.kt
  apps/android/app/src/main/java/com/junchen/jingdu/LibraryMetadataStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/BuiltinCleanRules.kt
  apps/android/app/src/main/java/com/junchen/jingdu/SmartCleanRefiner.java
  apps/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt
  apps/android/app/src/main/java/com/junchen/jingdu/SmartCleanFeedbackStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/SmartToc.kt
  apps/android/app/src/main/java/com/junchen/jingdu/TocOverrideStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/TxtDoctor.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ProgressiveImport.kt
  apps/android/app/src/main/java/com/junchen/jingdu/FolderLibraryStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/BatchAutomation.kt
  apps/android/app/src/main/java/com/junchen/jingdu/PrivacyAudit.kt
  apps/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.java
  apps/android/app/src/main/java/com/junchen/jingdu/CompetitiveSheets.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ChineseDisplayConverter.java
  apps/android/app/src/main/res/values/strings_competitive.xml
  apps/android/app/src/main/res/values-b+zh+Hans/strings_competitive.xml
  apps/android/app/src/main/res/values-b+zh+Hant/strings_competitive.xml
  quality/smartclean/train-v1.tsv
  quality/smartclean/eval-v1.tsv
  scripts/train-smartclean-model.py
  scripts/verify-smartclean-model.py
  apps/android/macrobenchmark/build.gradle
  apps/android/macrobenchmark/src/main/AndroidManifest.xml
  apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/StartupBenchmark.kt
  apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt
)
for path in "${required[@]}"; do
  test -f "$path" || { echo "terminal-quality asset missing: $path" >&2; exit 1; }
done

# Product scope and moat SSOT.
grep -q 'Long TXT · Smart Clean · Fully local' docs/PRODUCT.md
grep -q 'Smart Clean 4' docs/PRODUCT.md
grep -q 'TXT Doctor' docs/PRODUCT.md
grep -q 'Smart TOC' docs/PRODUCT.md
grep -q 'batch automation' docs/PRODUCT.md
grep -q 'Precision is deliberately more important than recall' docs/COMPETITIVE_MOAT.md
grep -q 'batch TXT automation' docs/GROWTH_MONETIZATION.md
grep -q 'Folder access, diagnosis and ordinary reading remain Free' docs/GROWTH_MONETIZATION.md

# Long-form Core/performance invariants.
grep -q 'jingdu_core_performance_gate_test' core/native/CMakeLists.txt
grep -q 'JINGDU_PERF_FIXTURE_MIB' core/native/tests/core_performance_gate_test.cpp
grep -q '1000' core/native/tests/core_performance_gate_test.cpp
grep -q 'peakRssMiB' core/native/tests/core_performance_gate_test.cpp

# Reading reliability: source-identity progress/bookmarks and proportional encoding rescue.
grep -q 'PROGRESS_SAVE_INTERVAL_MS' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'PROGRESS_SAVE_CHAR_DELTA' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -Fq '"bookmarks.${book.id}"' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'mapOffset(oldPosition, oldLength, newLength)' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'mapOffset(offset, oldLength, newLength)' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'putStringSet(bookmarkKey(updated), mappedBookmarks)' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'repository.updateCharCount(updated, newLength)' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'existing != null ? existing.progress : 0' apps/android/app/src/main/java/com/junchen/jingdu/BookRepository.java
grep -q 'book.progress >= charCount' apps/android/app/src/main/java/com/junchen/jingdu/BookRepository.java

# Existing library / reversible Clean contracts.
grep -q 'library_filter_favorites' apps/android/app/src/main/java/com/junchen/jingdu/LibraryScreen.kt
grep -q 'LibrarySort.PROGRESS' apps/android/app/src/main/java/com/junchen/jingdu/LibraryScreen.kt
grep -q 'onToggleFavorite' apps/android/app/src/main/java/com/junchen/jingdu/LibraryScreen.kt
grep -q 'CleanHistory' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'undoSmartClean' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt

# OpenCC remains bounded and presentation-only.
grep -q 'enum class ChineseDisplayMode' apps/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt
grep -q 'OpenCC.convert' apps/android/app/src/main/java/com/junchen/jingdu/ChineseDisplayConverter.java
grep -q 'sourceCharsForDisplayed' apps/android/app/src/main/java/com/junchen/jingdu/ChineseDisplayConverter.java
grep -q 'ChineseDisplayConverter.convert(text, settings.chineseMode' apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt
grep -q 'ChineseDisplayConverter.searchVariants' apps/android/app/src/main/java/com/junchen/jingdu/ReaderController.java
grep -q 'io.github.laisuk:openccjava:1.4.2' apps/android/app/build.gradle
grep -q 'assets.srcDir rootProject.file("../../third_party")' apps/android/app/build.gradle

# P0 — TXT Doctor, Smart TOC, first-readable-before-full-index and professional local TTS.
grep -q 'SAMPLE_WINDOWS = 8' apps/android/app/src/main/java/com/junchen/jingdu/TxtDoctor.kt
grep -q 'reader.readAt(offset, SAMPLE_CHARS)' apps/android/app/src/main/java/com/junchen/jingdu/TxtDoctor.kt
grep -q 'diagnose(reader, book, SmartToc.analyze(reader), reader.noiseCandidates())' apps/android/app/src/main/java/com/junchen/jingdu/TxtDoctor.kt
grep -q 'specialHeadings' apps/android/app/src/main/java/com/junchen/jingdu/SmartToc.kt
grep -q 'offset: Long' apps/android/app/src/main/java/com/junchen/jingdu/TocOverrideStore.kt
grep -q 'MAX_PREVIEW_BYTES = 512 \* 1024' apps/android/app/src/main/java/com/junchen/jingdu/ProgressiveImport.kt
grep -q 'MAX_PREVIEW_CHARS = 12_000' apps/android/app/src/main/java/com/junchen/jingdu/ProgressiveImport.kt
grep -q 'ProgressiveImport(context).prepare' apps/android/app/src/main/java/com/junchen/jingdu/LibraryScreen.kt
grep -q 'warm.open(repository.normalizedFile(book), 0)' apps/android/app/src/main/java/com/junchen/jingdu/LibraryScreen.kt
grep -q 'repository.updateCharCount(book, warm.length())' apps/android/app/src/main/java/com/junchen/jingdu/LibraryScreen.kt
grep -q 'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK' apps/android/app/src/main/AndroidManifest.xml
grep -q 'android:foregroundServiceType="mediaPlayback"' apps/android/app/src/main/AndroidManifest.xml
grep -q 'android:name=".TtsPlaybackService"' apps/android/app/src/main/AndroidManifest.xml
grep -q 'MediaSession' apps/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.java
grep -q 'ACTION_MEDIA_BUTTON' apps/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.java
grep -q 'EXTRA_BOOK_ID' apps/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.java
grep -q 'repository.saveProgress(book' apps/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.java
grep -q 'EXTRA_ACTIVE' apps/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.java
grep -q 'state.panel == null' apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt
grep -q 'actions.onSyncTtsPosition(offset)' apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt
grep -q 'onSyncTtsPosition = ::syncTtsPosition' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'private fun syncTtsPosition(offset: Long)' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'onSyncTtsPosition = {}' apps/android/app/src/androidTest/java/com/junchen/jingdu/JingduUiTest.kt
if grep -q 'actions.onJump(offset)' apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt; then
  echo 'background TTS position sync must not reuse user jump semantics' >&2
  exit 1
fi
grep -q 'SpanStyle(background' apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt
grep -q 'setWillPauseWhenDucked(true)' apps/android/app/src/main/java/com/junchen/jingdu/TtsController.java

# P1 — explicit SAF folder library, deletion-aware incremental sync, Pro batch automation, privacy proof.
grep -q 'ActivityResultContracts.OpenDocumentTree' apps/android/app/src/main/java/com/junchen/jingdu/LibraryScreen.kt
grep -q 'takePersistableUriPermission' apps/android/app/src/main/java/com/junchen/jingdu/LibraryScreen.kt
grep -q 'releasePersistableUriPermission' apps/android/app/src/main/java/com/junchen/jingdu/LibraryScreen.kt
grep -q 'documentId' apps/android/app/src/main/java/com/junchen/jingdu/FolderLibraryStore.kt
grep -q 'lastModified' apps/android/app/src/main/java/com/junchen/jingdu/FolderLibraryStore.kt
grep -q 'needsImport(entry: FolderEntry, existingBookIds: Set<String>)' apps/android/app/src/main/java/com/junchen/jingdu/FolderLibraryStore.kt
grep -q 'previousBookId !in existingBookIds' apps/android/app/src/main/java/com/junchen/jingdu/FolderLibraryStore.kt
grep -q 'markImported(entry: FolderEntry, bookId: String)' apps/android/app/src/main/java/com/junchen/jingdu/FolderLibraryStore.kt
grep -q 'MAX_BOOKS = 100' apps/android/app/src/main/java/com/junchen/jingdu/BatchAutomation.kt
grep -q 'TxtDoctor.diagnose(reader, book, toc, candidates)' apps/android/app/src/main/java/com/junchen/jingdu/BatchAutomation.kt
grep -q 'if (applied > 0)' apps/android/app/src/main/java/com/junchen/jingdu/BatchAutomation.kt
grep -q 'containsBookText", false' apps/android/app/src/main/java/com/junchen/jingdu/BatchAutomation.kt
grep -q 'Manifest.permission.INTERNET !in permissions' apps/android/app/src/main/java/com/junchen/jingdu/PrivacyAudit.kt
grep -q 'knownAnalyticsClasses' apps/android/app/src/main/java/com/junchen/jingdu/PrivacyAudit.kt
grep -q 'knownAdsClasses' apps/android/app/src/main/java/com/junchen/jingdu/PrivacyAudit.kt
grep -q 'containsBookText", false' apps/android/app/src/main/java/com/junchen/jingdu/PrivacyAudit.kt
if grep -qE 'android.permission.(READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE|MANAGE_EXTERNAL_STORAGE)' apps/android/app/src/main/AndroidManifest.xml; then
  echo 'folder library must use SAF instead of broad storage permission' >&2
  exit 1
fi

# P2 — reversible fingerprint feedback + reproducible candidate-only tiny model + precision gate.
grep -q 'enum class SmartCleanFeedback { NONE, KEEP, DELETE, PROTECT }' apps/android/app/src/main/java/com/junchen/jingdu/SmartCleanFeedbackStore.kt
grep -q 'MessageDigest.getInstance("SHA-256")' apps/android/app/src/main/java/com/junchen/jingdu/SmartCleanFeedbackStore.kt
grep -q 'adjustAggregate(editor, fingerprint, previous, -1)' apps/android/app/src/main/java/com/junchen/jingdu/SmartCleanFeedbackStore.kt
grep -q 'adjustAggregate(editor, fingerprint, feedback, +1)' apps/android/app/src/main/java/com/junchen/jingdu/SmartCleanFeedbackStore.kt
grep -q 'TinyLocalSemanticCandidateClassifier' apps/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt
grep -q 'take(512)' apps/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt
grep -q 'private val weights = intArrayOf' apps/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt
grep -q 'BODY_THRESHOLD = -12' apps/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt
grep -q 'AD_THRESHOLD = 20' apps/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt
grep -q 'smartCleanModel(book.id, candidate)' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'smartCleanFeedback.record(book.id, candidate.reason, candidate.text, SmartCleanFeedback.DELETE)' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'smartCleanFeedback.clearBook(book.id)' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
python3 -m py_compile scripts/train-smartclean-model.py scripts/verify-smartclean-model.py
python3 scripts/train-smartclean-model.py --verify-source apps/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt
python3 scripts/verify-smartclean-model.py

# Third-party legal assets remain packaged.
grep -q '^MIT License$' third_party/licenses/OpenccJava-MIT.txt
grep -q '^Apache License$' third_party/licenses/OpenCC-Apache-2.0.txt
grep -q 'OpenccJava-MIT.txt' THIRD_PARTY_NOTICES.md
grep -q 'OpenCC-Apache-2.0.txt' THIRD_PARTY_NOTICES.md
grep -q 'This directory is packaged into the Android APK/AAB as application assets.' third_party/NOTICE.md

# Source-release governance stays single-path and immutable.
test ! -f .github/workflows/source-release.yml
python3 -m py_compile scripts/publish-source-release.py
grep -q '^  publish-source-release:$' .github/workflows/ci.yml
grep -Fq 'needs: [native-core, android, harmony-contract, play-store-contract, terminal-contract]' .github/workflows/ci.yml
grep -q 'contents: write' .github/workflows/ci.yml
grep -q 'if existing is not None and release_status != 404:' scripts/publish-source-release.py
grep -q 'orphan immutable tag' scripts/publish-source-release.py
grep -q 'There is no separate Source Release workflow' docs/RELEASE.md

# Offline/privacy/no-whole-document regressions.
if grep -q 'android.permission.INTERNET' apps/android/app/src/main/AndroidManifest.xml; then
  echo 'offline/privacy contract forbids Android INTERNET permission' >&2
  exit 1
fi
if git grep -n -E 'converted(File|Path)|converted-[^ ]+\.txt|opencc.*\.txt' -- apps/android/app/src/main/java; then
  echo 'full-book converted artifact mechanism found; conversion must remain windowed' >&2
  exit 1
fi
if grep -qE 'normalizedFile|documentFile|ReaderController|File\(' apps/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt; then
  echo 'semantic classifier must remain candidate-only and file-blind' >&2
  exit 1
fi
if grep -qE '<string name="(add|ok|close)">' apps/android/app/src/main/res/values/strings_competitive.xml; then
  echo 'temporary generic competitive resource aliases must not return' >&2
  exit 1
fi

# Android benchmark/baseline-profile source qualification remains intact.
grep -q 'id "com.android.test" version "9.3.1" apply false' apps/android/build.gradle
grep -q 'include(":app", ":macrobenchmark")' apps/android/settings.gradle
grep -q 'benchmark-macro-junit4:1.4.1' apps/android/macrobenchmark/build.gradle
grep -q 'StartupTimingMetric' apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/StartupBenchmark.kt
grep -q 'BaselineProfileMode.UseIfAvailable' apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/StartupBenchmark.kt
grep -q 'BaselineProfileRule' apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt
grep -q 'includeInStartupProfile = true' apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt
grep -q 'stableIterations = 3' apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt
grep -q '<profileable android:shell="true"' apps/android/app/src/main/AndroidManifest.xml
grep -q 'profileinstaller:1.4.1' apps/android/app/build.gradle

python3 ./scripts/verify-android-i18n.py

echo 'Terminal long-form TXT / TXT Doctor / Smart TOC / Smart Clean 4 / local automation / source-release quality contract OK'
