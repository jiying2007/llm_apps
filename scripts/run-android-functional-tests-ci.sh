#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT/apps/android"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/local/lib/android/sdk}}"
ADB="$SDK_ROOT/platform-tools/adb"
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/avdmanager"
EMULATOR="$SDK_ROOT/emulator/emulator"
IMAGE="system-images;android-35;google_apis_ps16k;x86_64"
AVD_HOME="${RUNNER_TEMP:-/tmp}/jingdu-functional-avd-home"
AVD_NAME="jingdu-functional-16k-ci"
LOG="${RUNNER_TEMP:-/tmp}/jingdu-functional-emulator.log"
FONT_SCALE_LOG="${RUNNER_TEMP:-/tmp}/jingdu-functional-font-scale-200.log"
BOOT_TIMEOUT_SECONDS="${JINGDU_FUNCTIONAL_BOOT_TIMEOUT_SECONDS:-300}"
FRAMEWORK_TIMEOUT_SECONDS="${JINGDU_FUNCTIONAL_FRAMEWORK_TIMEOUT_SECONDS:-90}"
EMULATOR_PID=""

cleanup() {
  "$ADB" shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
  "$ADB" emu kill >/dev/null 2>&1 || true
  if [[ -n "$EMULATOR_PID" ]] && kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
    kill "$EMULATOR_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

fail_emulator() {
  echo "$1" >&2
  echo "===== Android functional emulator log =====" >&2
  tail -n 240 "$LOG" >&2 || true
  if [[ -s "$FONT_SCALE_LOG" ]]; then
    echo "===== Android 200% font-scale instrumentation log =====" >&2
    cat "$FONT_SCALE_LOG" >&2 || true
  fi
  exit 1
}

[[ -x "$SDKMANAGER" ]] || { echo "sdkmanager missing: $SDKMANAGER" >&2; exit 1; }
if command -v apt-get >/dev/null 2>&1 && ! dpkg-query -W libpulse0 >/dev/null 2>&1; then
  sudo apt-get update -qq
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq --no-install-recommends libpulse0 >/dev/null
fi
yes | "$SDKMANAGER" --licenses >/dev/null || true
"$SDKMANAGER" "platform-tools" "emulator" "$IMAGE" >/dev/null

[[ -x "$ADB" ]] || { echo "adb missing: $ADB" >&2; exit 1; }
[[ -x "$EMULATOR" ]] || { echo "emulator missing: $EMULATOR" >&2; exit 1; }
if [[ ! -e /dev/kvm ]]; then
  echo "16 KiB x86_64 emulator requires KVM on hosted CI" >&2
  exit 1
fi
sudo chmod 666 /dev/kvm
[[ -r /dev/kvm && -w /dev/kvm ]] || { echo "hosted runner cannot access /dev/kvm" >&2; exit 1; }
echo "KVM acceleration enabled for Android functional CI"

rm -rf "$AVD_HOME"
mkdir -p "$AVD_HOME"
export ANDROID_AVD_HOME="$AVD_HOME"
echo no | "$AVDMANAGER" create avd \
  --force \
  --name "$AVD_NAME" \
  --package "$IMAGE" \
  --device "pixel_6" \
  --path "$AVD_HOME/$AVD_NAME.avd" >/dev/null

: >"$LOG"
: >"$FONT_SCALE_LOG"
"$EMULATOR" -avd "$AVD_NAME" \
  -no-window -no-audio -no-boot-anim -no-snapshot -wipe-data -no-metrics \
  -gpu swiftshader_indirect -accel on -camera-back none -camera-front none \
  >"$LOG" 2>&1 &
EMULATOR_PID=$!
echo "Android functional emulator PID: $EMULATOR_PID"
"$ADB" start-server >/dev/null

booted=0
for ((second = 1; second <= BOOT_TIMEOUT_SECONDS; second++)); do
  if ! kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
    wait "$EMULATOR_PID" || true
    fail_emulator "Android functional-test 16 KiB emulator exited before boot completed"
  fi
  adb_state="$("$ADB" get-state 2>/dev/null || true)"
  if [[ "$adb_state" == "device" ]]; then
    boot="$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [[ "$boot" == "1" ]]; then
      booted=1
      echo "Android functional emulator boot property completed in ${second}s"
      break
    fi
  fi
  if (( second % 15 == 0 )); then
    echo "Waiting for Android functional emulator: ${second}s/${BOOT_TIMEOUT_SECONDS}s (adb=${adb_state:-unavailable})"
  fi
  sleep 1
done
if (( booted == 0 )); then
  fail_emulator "Android functional-test 16 KiB emulator failed to boot within ${BOOT_TIMEOUT_SECONDS}s"
fi

# sys.boot_completed can become 1 before framework binder services are actually ready on a fresh
# hosted emulator. Do not issue SettingsProvider/ActivityManager/PackageManager commands until all
# three service families respond successfully; otherwise a valid runtime can fail with
# "Can't find service: settings" immediately after the boot property flips.
framework_ready=0
for ((second = 1; second <= FRAMEWORK_TIMEOUT_SECONDS; second++)); do
  if ! kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
    wait "$EMULATOR_PID" || true
    fail_emulator "Android functional-test emulator exited while waiting for framework services"
  fi
  if "$ADB" shell settings get global window_animation_scale >/dev/null 2>&1 && \
     "$ADB" shell pm path android >/dev/null 2>&1 && \
     "$ADB" shell am get-current-user >/dev/null 2>&1; then
    framework_ready=1
    echo "Android functional framework services ready in ${second}s after boot property"
    break
  fi
  if (( second % 10 == 0 )); then
    echo "Waiting for Android framework services: ${second}s/${FRAMEWORK_TIMEOUT_SECONDS}s"
  fi
  sleep 1
done
if (( framework_ready == 0 )); then
  fail_emulator "Android functional framework services were not ready within ${FRAMEWORK_TIMEOUT_SECONDS}s"
fi

PAGE_SIZE="$("$ADB" shell getconf PAGE_SIZE | tr -d '\r[:space:]')"
if [[ "$PAGE_SIZE" != "16384" ]]; then
  fail_emulator "Functional emulator is not a 16 KiB runtime: PAGE_SIZE=$PAGE_SIZE"
fi

echo "Android functional runtime confirmed: PAGE_SIZE=$PAGE_SIZE image=$IMAGE"
"$ADB" shell input keyevent 82 >/dev/null 2>&1 || true
"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0
"$ADB" shell wm dismiss-keyguard >/dev/null 2>&1 || true

cd "$ANDROID_DIR"
TEST_SOURCE="app/src/androidTest/java/com/junchen/jingdu/JingduUiTest.kt"
[[ -f "$TEST_SOURCE" ]] || { echo "functional test source missing: $TEST_SOURCE" >&2; exit 1; }
echo "Functional checkout SHA: $(git rev-parse HEAD)"
echo "JingduUiTest source SHA256: $(sha256sum "$TEST_SOURCE" | awk '{print $1}')"

# Functional acceptance is the app instrumentation suite on the 16 KiB runtime. Macrobenchmark and
# Baseline Profile instrumentation are intentionally owned by the independent android-performance
# gate; invoking the root aggregate connectedDebugAndroidTest here would duplicate that suite and can
# uninstall/replace the target package while app instrumentation is still running.
#
# Build from the exact checkout without Gradle task-output cache so the app/androidTest APKs and the
# first instrumentation result are source-bound. Root clean is harmless, while the execution target
# is deliberately app-scoped.
./gradlew --no-daemon --warning-mode all --no-build-cache clean :app:connectedDebugAndroidTest

# UTP may uninstall the target/test packages after connectedDebugAndroidTest. Reinstall the exact APKs
# produced by that already-successful source-bound build before the 200% rerun; this does not rebuild
# or substitute artifacts and keeps both font-scale passes tied to the same checkout and binaries.
APP_APK="app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
[[ -s "$APP_APK" ]] || fail_emulator "Source-bound debug APK missing after normal functional suite: $APP_APK"
[[ -s "$TEST_APK" ]] || fail_emulator "Source-bound androidTest APK missing after normal functional suite: $TEST_APK"
echo "Functional app APK SHA256: $(sha256sum "$APP_APK" | awk '{print $1}')"
echo "Functional androidTest APK SHA256: $(sha256sum "$TEST_APK" | awk '{print $1}')"
"$ADB" install -r -t "$APP_APK" >/dev/null || fail_emulator "Failed to reinstall source-bound debug APK"
"$ADB" install -r -t "$TEST_APK" >/dev/null || fail_emulator "Failed to reinstall source-bound androidTest APK"

# Reuse those already-built source-bound APKs at Android's 200% font scale. This is a hosted layout
# and discoverability regression gate, not a claim that emulator testing replaces physical
# TalkBack/OEM qualification. Invoking AndroidJUnitRunner directly makes the second run real while
# avoiding a second full Gradle build/reinstall cycle that adds no source evidence.
echo "Running JingduUiTest at Android font_scale=2.0"
"$ADB" shell settings put system font_scale 2.0
FONT_SCALE="$("$ADB" shell settings get system font_scale | tr -d '\r[:space:]')"
if [[ "$FONT_SCALE" != "2.0" && "$FONT_SCALE" != "2" ]]; then
  fail_emulator "Failed to configure Android 200% font scale: font_scale=$FONT_SCALE"
fi

TARGET_PACKAGE="com.junchen.jingdu.debug"
INSTRUMENTATION="$({ "$ADB" shell pm list instrumentation || true; } | tr -d '\r' | sed -n "s/^instrumentation:\([^ ]*\) (target=${TARGET_PACKAGE})$/\1/p" | head -n1)"
if [[ -z "$INSTRUMENTATION" ]]; then
  fail_emulator "Source-bound Android test instrumentation is not installed for the 200% font-scale rerun"
fi
"$ADB" shell am force-stop "$TARGET_PACKAGE" >/dev/null 2>&1 || true
set +e
"$ADB" shell am instrument -w -r \
  -e class com.junchen.jingdu.JingduUiTest \
  "$INSTRUMENTATION" | tr -d '\r' | tee "$FONT_SCALE_LOG"
FONT_SCALE_STATUS=${PIPESTATUS[0]}
set -e
if (( FONT_SCALE_STATUS != 0 )); then
  fail_emulator "Android 200% font-scale instrumentation command failed: status=$FONT_SCALE_STATUS"
fi
if grep -Eq 'FAILURES!!!|INSTRUMENTATION_ABORTED|INSTRUMENTATION_FAILED|shortMsg=Process crashed|DeadSystemException' "$FONT_SCALE_LOG"; then
  fail_emulator "Android 200% font-scale JingduUiTest reported a failure or system abort"
fi
if ! grep -Eq '^OK \([1-9][0-9]* tests?\)$' "$FONT_SCALE_LOG"; then
  fail_emulator "Android 200% font-scale JingduUiTest did not report a completed non-empty test run"
fi
"$ADB" shell settings put system font_scale 1.0

echo "Android 200% font-scale JingduUiTest PASS"
echo "Android functional instrumentation suite PASS on 16 KiB runtime"
