#!/usr/bin/env bash
set -euo pipefail

required=(
  docs/READING_EXPERIENCE.md
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderExperience.kt
  apps/android/app/src/main/res/values/strings_reader_experience.xml
  apps/android/app/src/main/res/values-b+zh+Hans/strings_reader_experience.xml
  apps/android/app/src/main/res/values-b+zh+Hant/strings_reader_experience.xml
)
for path in "${required[@]}"; do
  test -f "$path" || { echo "reading-experience asset missing: $path" >&2; exit 1; }
done

prefs=apps/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt
screen=apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt
runtime=apps/android/app/src/main/java/com/junchen/jingdu/ReaderExperience.kt
main=apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
app=apps/android/app/src/main/java/com/junchen/jingdu/JingduApp.kt
converter=apps/android/app/src/main/java/com/junchen/jingdu/ChineseDisplayConverter.java

grep -q 'enum class ReaderMode { PAGED, CONTINUOUS }' "$prefs"
grep -q 'enum class ReaderPageAnimation { NONE, SLIDE }' "$prefs"
grep -q 'enum class ReaderOrientation { SYSTEM, PORTRAIT, LANDSCAPE }' "$prefs"
grep -q 'enum class ReaderVolumeKeyMode { PAGE_WHEN_NOT_TTS, ALWAYS_PAGE, SYSTEM_VOLUME }' "$prefs"
grep -q 'enum class ReaderWideColumns { AUTO, SINGLE, DOUBLE }' "$prefs"
grep -q 'autoScrollSpeedDpPerSecond' "$prefs"
grep -q 'autoScrollEnabled = false' "$prefs"
grep -q 'applyPreset' "$prefs"

grep -q 'class ContinuousWindowReader' "$runtime"
grep -q 'ReaderController.WINDOW_CHARS' "$runtime"
grep -q 'readAt(start, ReaderController.WINDOW_CHARS)' "$runtime"
grep -q 'shouldUseVolumeKeysForPaging' "$runtime"
grep -q 'class ReadingPaceStore' "$runtime"

grep -q 'ContinuousReaderPage' "$screen"
grep -q 'PagedReaderPage' "$screen"
grep -q 'readerGestures' "$screen"
grep -q 'withFrameNanos' "$screen"
grep -q 'scrollState.scrollBy' "$screen"
grep -q 'WindowInsetsControllerCompat' "$screen"
grep -q 'screenBrightness' "$screen"
grep -q 'SCREEN_ORIENTATION_PORTRAIT' "$screen"
grep -q 'ReaderWideColumns.DOUBLE' "$screen"
grep -q 'TwoColumnPage' "$screen"
grep -q 'TextIndent' "$screen"
grep -q 'reader_location_back' "$screen"
grep -q 'actions.onSyncTtsPosition(absolute)' "$screen"

grep -q 'ReaderInteractionRuntime.shouldUseVolumeKeysForPaging' "$main"
grep -q 'reverseVolumeKeys' "$main"
grep -q 'locationBack' "$app"
grep -q 'locationForward' "$app"
grep -q 'displayedCharsForSource' "$converter"

grep -q 'same `ReaderController` source-offset domain' docs/READING_EXPERIENCE.md
grep -q 'whole TXT is never loaded into Compose' docs/READING_EXPERIENCE.md

if grep -q 'android.permission.INTERNET' apps/android/app/src/main/AndroidManifest.xml; then
  echo 'reading experience must remain offline; INTERNET permission found' >&2
  exit 1
fi

if grep -qE 'readAt\([^,]+,[[:space:]]*(Long\.MAX_VALUE|[0-9]+[[:space:]]*\*[[:space:]]*1024[[:space:]]*\*[[:space:]]*1024)' "$screen" "$runtime"; then
  echo 'continuous reader must remain a bounded window' >&2
  exit 1
fi

python3 scripts/verify-android-i18n.py

echo 'Reading experience contract OK: paged/continuous/gestures/auto-scroll/immersive/source-offset invariants aligned'
