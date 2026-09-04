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
AVD_HOME="${RUNNER_TEMP:-/tmp}/jingdu-16k-avd-home"
AVD_NAME="jingdu-16k-ci"
LOG="${RUNNER_TEMP:-/tmp}/jingdu-16k-emulator.log"

cleanup() {
  "$ADB" emu kill >/dev/null 2>&1 || true
}
trap cleanup EXIT

[[ -x "$SDKMANAGER" ]] || { echo "sdkmanager missing: $SDKMANAGER" >&2; exit 1; }
if command -v apt-get >/dev/null 2>&1; then
  sudo apt-get update -qq
  sudo apt-get install -y -qq libpulse0 >/dev/null
fi
yes | "$SDKMANAGER" --licenses >/dev/null || true
"$SDKMANAGER" "platform-tools" "emulator" "$IMAGE" >/dev/null

rm -rf "$AVD_HOME"
mkdir -p "$AVD_HOME"
export ANDROID_AVD_HOME="$AVD_HOME"
echo no | "$AVDMANAGER" create avd --force --name "$AVD_NAME" --package "$IMAGE" --device "pixel_6" >/dev/null

"$EMULATOR" -avd "$AVD_NAME" \
  -no-window -no-audio -no-boot-anim -no-snapshot -wipe-data \
  -gpu swiftshader_indirect -accel auto -camera-back none -camera-front none \
  >"$LOG" 2>&1 &

"$ADB" wait-for-device
for attempt in $(seq 1 60); do
  boot="$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$boot" == "1" ]]; then break; fi
  if (( attempt == 60 )); then
    echo "16 KiB Android emulator failed to boot" >&2
    cat "$LOG" >&2 || true
    exit 1
  fi
  sleep 5
done

PAGE_SIZE="$("$ADB" shell getconf PAGE_SIZE | tr -d '\r[:space:]')"
if [[ "$PAGE_SIZE" != "16384" ]]; then
  echo "Expected 16 KiB runtime page size, got: $PAGE_SIZE" >&2
  cat "$LOG" >&2 || true
  exit 1
fi

echo "16 KiB emulator runtime confirmed: PAGE_SIZE=$PAGE_SIZE"
"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0

cd "$ANDROID_DIR"
./gradlew --no-daemon --warning-mode all \
  -Pandroid.testInstrumentationRunnerArguments.class=com.junchen.jingdu.NativePageSizeSmokeTest \
  connectedDebugAndroidTest

echo "Android 16 KiB runtime JNI/Core smoke PASS"
