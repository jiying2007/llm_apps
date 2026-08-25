#!/usr/bin/env bash
set -euo pipefail

required=(
  docs/READING_EXPERIENCE.md
  docs/READER_V2_PRELAUNCH.md
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderRoute.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderMotionController.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderViewportEngine.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderAnnotationStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderAdvancedSettingsSheet.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderV2Panels.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt
)
for path in "${required[@]}"; do
  test -f "$path" || { echo "reading-experience V2 asset missing: $path" >&2; exit 1; }
done

prefs=apps/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt
screen=apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt
motion=apps/android/app/src/main/java/com/junchen/jingdu/ReaderMotionController.kt
viewport=apps/android/app/src/main/java/com/junchen/jingdu/ReaderViewportEngine.kt
route=apps/android/app/src/main/java/com/junchen/jingdu/ReaderRoute.kt
panels=apps/android/app/src/main/java/com/junchen/jingdu/ReaderV2Panels.kt
advanced=apps/android/app/src/main/java/com/junchen/jingdu/ReaderAdvancedSettingsSheet.kt
main=apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt

# Reader modes and presentation settings are first-class and persisted via DataStore.
grep -q 'enum class ReaderMode { PAGED, CONTINUOUS }' "$prefs"
grep -q 'enum class ReaderPageAnimation { NONE, SLIDE }' "$prefs"
grep -q 'enum class ReaderOrientation { SYSTEM, PORTRAIT, LANDSCAPE }' "$prefs"
grep -q 'enum class ReaderVolumeKeyMode { PAGE_WHEN_NOT_TTS, ALWAYS_PAGE, SYSTEM_VOLUME }' "$prefs"
grep -q 'enum class ReaderWideColumns { AUTO, SINGLE, DOUBLE }' "$prefs"
grep -q 'preferencesDataStore' "$prefs"
grep -q 'autoScrollSpeedDpPerSecond' "$prefs"
grep -q 'autoPageMode' "$prefs"
grep -q 'brightnessGestureEnabled' "$prefs"
grep -q 'pinchFontEnabled' "$prefs"
grep -q 'doubleTapBookmarkEnabled' "$prefs"
grep -q 'applyPreset' "$prefs"

# A single motion state coordinates automatic scrolling, automatic paging and TTS.
grep -q 'enum class ReaderMotionState { IDLE, AUTO_SCROLL, AUTO_PAGE, TTS }' "$motion"
grep -q 'adaptivePageDelayMs' "$motion"
grep -q 'private val motionController = ReaderMotionController()' "$main"

# Paged and continuous surfaces share bounded source-offset infrastructure.
grep -q 'PagedReaderPage' "$screen"
grep -q 'ContinuousReaderPage' "$screen"
grep -q 'ReaderViewportEngine' "$screen"
grep -q 'ReaderPageLayoutCache.measure' "$screen"
grep -q 'MAX_WINDOWS' "$viewport"
grep -q 'ReaderController.WINDOW_CHARS' "$viewport"
grep -q 'prefetch' "$viewport"
grep -q 'actions.onSyncTtsPosition(absolute)' "$screen"

# Mature reader interaction fundamentals.
grep -q 'readerGesturesV2' "$screen"
grep -q 'brightnessGestureEnabled' "$screen"
grep -q 'detectTransformGestures' "$screen"
grep -q 'lastCenterTapAt' "$screen"
grep -q 'onBookmark()' "$screen"
grep -q 'customActions' "$screen"
grep -q 'isTouchExplorationEnabled' "$screen"
grep -q 'WindowInsetsControllerCompat' "$screen"
grep -q 'screenBrightness' "$screen"
grep -q 'FLAG_KEEP_SCREEN_ON' "$screen"
grep -q 'AutoScrollLiveControl' "$screen"
grep -q 'scrollState.scrollBy' "$screen"
grep -q 'ReaderAnnotationKind.HIGHLIGHT' "$screen"
grep -q 'ReaderAnnotationKind.NOTE' "$screen"

# Quick controls and advanced settings are separate, intentional surfaces.
grep -q 'ReaderQuickSettingsSheet' "$panels"
grep -q 'ReaderAnnotationsSheet' "$panels"
grep -q 'ReaderReadingMapSheet' "$panels"
grep -q 'autoScrollEnabled = !state.autoScrolling' "$panels"
grep -q 'ReaderAdvancedSettingsSheet' "$advanced"
test ! -f apps/android/app/src/main/java/com/junchen/jingdu/ProductSettingsSheet.kt

# Adaptive window posture replaces fixed device assumptions.
grep -q 'currentWindowAdaptiveInfoV2' "$route"
grep -q 'windowPosture.hingeList' "$route"
grep -q 'prefersTwoColumns' "$route"

# Privacy/performance invariants.
if grep -q 'android.permission.INTERNET' apps/android/app/src/main/AndroidManifest.xml; then
  echo 'reading experience must remain offline; INTERNET permission found' >&2
  exit 1
fi
if git grep -n -E 'readAt\([^,]+,[[:space:]]*(Long\.MAX_VALUE|[0-9]+[[:space:]]*\*[[:space:]]*1024[[:space:]]*\*[[:space:]]*1024)' -- "$screen" "$viewport"; then
  echo 'reading experience must keep bounded document windows' >&2
  exit 1
fi

python3 scripts/verify-android-i18n.py
bash scripts/verify-reader-v2.sh

echo 'Reading experience V2 contract OK: paged/continuous/gestures/auto-read/adaptive/annotation/source-offset invariants aligned'
