#!/usr/bin/env bash
set -euo pipefail

required=(
  docs/READER_V2_PRELAUNCH.md
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderMotionController.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderSession.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderViewportEngine.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderAnnotationStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderFontStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderStatsStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderRoute.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderV2Panels.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderAdvancedSettingsSheet.kt
)
for path in "${required[@]}"; do test -f "$path" || { echo "Reader V2 asset missing: $path" >&2; exit 1; }; done

test ! -f .github/workflows/reader-v2-bootstrap.yml
test ! -f scripts/bootstrap-reader-v2-main.py

prefs=apps/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt
main=apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
screen=apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt
app=apps/android/app/src/main/java/com/junchen/jingdu/JingduApp.kt
advanced=apps/android/app/src/main/java/com/junchen/jingdu/ReaderAdvancedSettingsSheet.kt
service=apps/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.java
controller=apps/android/app/src/main/java/com/junchen/jingdu/TtsController.java

grep -q 'preferencesDataStore' "$prefs"
grep -q 'jingdu_reader_v2' "$prefs"
grep -q 'There is deliberately no SharedPreferences migration path' "$prefs"
! grep -q 'getSharedPreferences("jingdu.reader.settings' "$prefs"

grep -q 'enum class ReaderMotionState { IDLE, AUTO_SCROLL, AUTO_PAGE, TTS }' apps/android/app/src/main/java/com/junchen/jingdu/ReaderMotionController.kt
grep -q 'private val session = ReaderSession()' "$main"
grep -q 'private val motionController = ReaderMotionController()' "$main"
grep -q 'TtsPlaybackModel' "$main"
grep -q 'ReaderAnnotationStore' "$main"
grep -q 'annotationStore.remapBook(book.id, oldLength, newLength)' "$main"
grep -q 'ReaderFontStore' "$main"
grep -q 'ReaderStatsStore' "$main"

grep -q 'onRangeStart' "$controller"
grep -q 'EXTRA_RANGE_START' "$service"
grep -q 'EXTRA_RANGE_END' "$service"
! grep -q 'BroadcastReceiver' "$screen"
! grep -q 'TtsPlaybackService.ACTION_STATE' "$screen"
grep -q 'state.tts.rangeStart' "$screen"
grep -q 'state.motion' "$screen"

grep -q 'ReaderViewportEngine' "$screen"
grep -q 'ReaderPageLayoutCache.measure' "$screen"
grep -q 'MAX_WINDOWS' apps/android/app/src/main/java/com/junchen/jingdu/ReaderViewportEngine.kt
grep -q 'prefetch' apps/android/app/src/main/java/com/junchen/jingdu/ReaderViewportEngine.kt
grep -q 'currentWindowAdaptiveInfoV2' apps/android/app/src/main/java/com/junchen/jingdu/ReaderRoute.kt
grep -q 'windowPosture.hingeList' apps/android/app/src/main/java/com/junchen/jingdu/ReaderRoute.kt

grep -q 'ReaderPanel.QUICK_SETTINGS' "$app"
grep -q 'ReaderPanel.ANNOTATIONS' "$app"
grep -q 'ReaderPanel.READING_MAP' "$app"
grep -q 'ReaderQuickSettingsSheet' apps/android/app/src/main/java/com/junchen/jingdu/ReaderV2Panels.kt
grep -q 'ReaderAnnotationsSheet' apps/android/app/src/main/java/com/junchen/jingdu/ReaderV2Panels.kt
grep -q 'ReaderReadingMapSheet' apps/android/app/src/main/java/com/junchen/jingdu/ReaderV2Panels.kt

grep -q 'brightnessGestureEnabled' "$prefs"
grep -q 'pinchFontEnabled' "$prefs"
grep -q 'doubleTapBookmarkEnabled' "$prefs"
grep -q 'readerGesturesV2' "$screen"
grep -q 'detectTransformGestures' "$screen"
grep -q 'customActions' "$screen"
grep -q 'isTouchExplorationEnabled' "$screen"
grep -q 'HapticFeedbackType' "$screen"
grep -q 'FLAG_KEEP_SCREEN_ON' "$screen"
grep -q 'AutoScrollLiveControl' "$screen"

# Every retained advanced setting must remain user-configurable after the prelaunch hard cut.
grep -q 'ReaderPageAnimation.entries' "$advanced"
grep -q 'ReaderFontWeight.entries' "$advanced"
grep -q 'firstLineIndentEm' "$advanced"
grep -q 'ReaderTextAlignment.entries' "$advanced"
grep -q 'tapZoneEdgeFraction' "$advanced"
grep -q 'controlsAutoHideMs' "$advanced"
grep -q 'ReaderVolumeKeyMode.entries' "$advanced"
grep -q 'focusRulerLines' "$advanced"
grep -q 'v2OrientationLabel' "$advanced"
grep -q 'v2ColumnsLabel' "$advanced"
grep -q 'v2ChineseModeLabel' "$advanced"
! grep -qE '\.name\.lowercase\(\)|mode\.name\.replace' "$advanced"

grep -q 'ReaderAnnotationKind.HIGHLIGHT' "$screen"
grep -q 'ReaderAnnotationKind.NOTE' "$screen"
grep -q 'ACTION_SEND' "$screen"
grep -q 'CLIPBOARD_SERVICE' "$screen"
grep -q 'rememberReaderFontFamily' "$screen"

if grep -q 'android.permission.INTERNET' apps/android/app/src/main/AndroidManifest.xml; then
  echo 'Reader V2 remains offline; INTERNET permission found' >&2
  exit 1
fi
if git grep -n -E 'readAt\([^,]+,[[:space:]]*(Long\.MAX_VALUE|[0-9]+[[:space:]]*\*[[:space:]]*1024[[:space:]]*\*[[:space:]]*1024)' -- apps/android/app/src/main/java/com/junchen/jingdu/ReaderViewportEngine.kt apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt; then
  echo 'Reader V2 must keep bounded document windows' >&2
  exit 1
fi

echo 'Reader V2 prelaunch contract OK: hard-cut storage/motion/TTS/viewport/adaptive/annotation/accessibility/full-settings invariants aligned'
