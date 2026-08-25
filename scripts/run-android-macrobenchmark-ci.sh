#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT/apps/android"
AVD_NAME="jingdu-v3-ci"
TARGET_PACKAGE="com.junchen.jingdu"
API_LEVEL="${JINGDU_BENCHMARK_API:-35}"
IMAGE="system-images;android-${API_LEVEL};google_apis;x86_64"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/local/lib/android/sdk}}"
SDKMANAGER="${SDKMANAGER:-$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager}"
AVDMANAGER="${AVDMANAGER:-$SDK_ROOT/cmdline-tools/latest/bin/avdmanager}"
EMULATOR="${EMULATOR:-$SDK_ROOT/emulator/emulator}"
ADB="${ADB:-$SDK_ROOT/platform-tools/adb}"
TEMP_DIR="${RUNNER_TEMP:-/tmp}"
AVD_HOME="${ANDROID_AVD_HOME:-$TEMP_DIR/jingdu-avd-home}"
BOOT_TIMEOUT_SECONDS="${JINGDU_EMULATOR_BOOT_TIMEOUT_SECONDS:-240}"
EMULATOR_LOG="$TEMP_DIR/jingdu-emulator.log"
EMULATOR_PID=""
export ANDROID_AVD_HOME="$AVD_HOME"

cleanup() {
  if [[ -x "$ADB" ]]; then
    "$ADB" emu kill >/dev/null 2>&1 || true
  fi
  if [[ -n "$EMULATOR_PID" ]] && kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
    kill "$EMULATOR_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

require_executable() {
  local path="$1"
  local label="$2"
  if [[ ! -x "$path" ]]; then
    echo "Missing ${label}: ${path}" >&2
    exit 1
  fi
}

fail_emulator() {
  local message="$1"
  echo "$message" >&2
  echo "===== Android emulator log =====" >&2
  tail -n 240 "$EMULATOR_LOG" >&2 || true
  exit 1
}

cd "$ROOT"
python3 scripts/test-android-performance-slo.py
require_executable "$SDKMANAGER" sdkmanager
require_executable "$AVDMANAGER" avdmanager

echo "Android SDK root: $SDK_ROOT"
echo "Benchmark image: $IMAGE"
echo "Android AVD home: $AVD_HOME"

# Install the runtime before validating adb/emulator. GitHub's performance job intentionally only
# guarantees Java + Android SDK roots; platform-tools/emulator may not be preinstalled or on PATH.
yes | "$SDKMANAGER" --licenses >/dev/null || true
"$SDKMANAGER" "platform-tools" "emulator" "$IMAGE"
require_executable "$ADB" adb
require_executable "$EMULATOR" emulator

# The current Android emulator binary links libpulse even when launched with -no-audio. The minimal
# Ubuntu 24.04 hosted image can omit that runtime library, so provision only the evidenced host dep.
if ! dpkg-query -W libpulse0 >/dev/null 2>&1; then
  sudo apt-get update
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends libpulse0
fi

"$ADB" version
"$EMULATOR" -version

# GitHub-hosted Linux runners normally expose /dev/kvm. Keep a software fallback for other runners.
if [[ -e /dev/kvm ]]; then
  sudo chmod 666 /dev/kvm || true
  echo "KVM acceleration available"
else
  echo "KVM unavailable; using software acceleration fallback"
fi

# Keep avdmanager and emulator on the same explicit AVD root. Relying on HOME/ANDROID_SDK_HOME
# produced an AVD that avdmanager accepted but the hosted emulator could not subsequently resolve.
rm -rf "$AVD_HOME"
mkdir -p "$AVD_HOME"
echo no | "$AVDMANAGER" create avd \
  --force \
  --name "$AVD_NAME" \
  --package "$IMAGE" \
  --device "pixel_6" \
  --path "$AVD_HOME/$AVD_NAME.avd"

echo "Available AVDs:"
"$EMULATOR" -list-avds
if ! "$EMULATOR" -list-avds | grep -Fxq "$AVD_NAME"; then
  echo "AVD metadata after creation:" >&2
  find "$AVD_HOME" -maxdepth 2 -type f -print >&2 || true
  exit 1
fi

GPU_MODE="swiftshader_indirect"
: >"$EMULATOR_LOG"
if [[ -e /dev/kvm ]]; then
  "$EMULATOR" -avd "$AVD_NAME" -no-window -no-audio -no-boot-anim -no-snapshot \
    -camera-back none -camera-front none -gpu "$GPU_MODE" >"$EMULATOR_LOG" 2>&1 &
else
  "$EMULATOR" -avd "$AVD_NAME" -no-window -no-audio -no-boot-anim -no-snapshot \
    -camera-back none -camera-front none -gpu "$GPU_MODE" -accel off >"$EMULATOR_LOG" 2>&1 &
fi
EMULATOR_PID=$!
echo "Android emulator PID: $EMULATOR_PID"
"$ADB" start-server >/dev/null

booted=0
for ((second = 1; second <= BOOT_TIMEOUT_SECONDS; second++)); do
  if ! kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
    wait "$EMULATOR_PID" || true
    fail_emulator "Android emulator process exited before boot completed"
  fi

  adb_state="$("$ADB" get-state 2>/dev/null || true)"
  if [[ "$adb_state" == "device" ]]; then
    boot_state="$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [[ "$boot_state" == "1" ]]; then
      booted=1
      echo "Android emulator boot completed in ${second}s"
      break
    fi
  fi

  if (( second % 15 == 0 )); then
    echo "Waiting for Android emulator boot: ${second}s/${BOOT_TIMEOUT_SECONDS}s (adb=${adb_state:-unavailable})"
  fi
  sleep 1
done

if (( booted == 0 )); then
  fail_emulator "Android emulator did not finish booting within ${BOOT_TIMEOUT_SECONDS}s"
fi

"$ADB" shell input keyevent 82 || true
"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0
"$ADB" shell getprop ro.build.version.release
"$ADB" shell getprop ro.product.cpu.abi

# Macrobenchmark CI is deliberately split into build -> install -> run. AGP connectedCheck can
# build the benchmark target without installing it on hosted CI, which makes every Macrobenchmark
# fail before measurement with PackageManager.NameNotFoundException. Make target installation an
# explicit hard precondition while retaining Gradle connectedCheck for result/Perfetto collection.
cd "$ANDROID_DIR"
./gradlew --no-daemon --warning-mode all :app:assembleBenchmark :macrobenchmark:assembleBenchmark
TARGET_APK="$(find "$ANDROID_DIR/app/build/outputs/apk/benchmark" -type f -name '*.apk' -print -quit)"
if [[ -z "$TARGET_APK" || ! -f "$TARGET_APK" ]]; then
  echo "Benchmark target APK was not produced" >&2
  exit 1
fi
echo "Installing Macrobenchmark target: $TARGET_APK"
"$ADB" install -r "$TARGET_APK"
TARGET_PATH="$("$ADB" shell pm path "$TARGET_PACKAGE" 2>/dev/null | tr -d '\r')"
if [[ "$TARGET_PATH" != package:* ]]; then
  echo "Macrobenchmark target package is not installed: $TARGET_PACKAGE" >&2
  "$ADB" shell pm list packages | grep 'com.junchen.jingdu' >&2 || true
  exit 1
fi
echo "Macrobenchmark target installed: $TARGET_PATH"

./gradlew --no-daemon --warning-mode all :macrobenchmark:connectedCheck \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark

cd "$ROOT"
RESULT_ROOT="$ANDROID_DIR/macrobenchmark/build/outputs"
python3 scripts/check-android-performance-slo.py "$RESULT_ROOT"
find "$RESULT_ROOT" -type f -name '*-benchmarkData.json' -print -quit | grep -q .

# Execute the Baseline Profile CUJ separately so the performance report and profile evidence are both real.
cd "$ANDROID_DIR"
./gradlew --no-daemon --warning-mode all :macrobenchmark:connectedCheck \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
cd "$ROOT"
find "$RESULT_ROOT" -type f \( -name '*baseline-prof.txt' -o -name '*startup-prof.txt' \) -print -quit | grep -q .
