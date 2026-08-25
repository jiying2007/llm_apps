#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT/apps/android"
AVD_NAME="jingdu-v3-ci"
API_LEVEL="${JINGDU_BENCHMARK_API:-35}"
IMAGE="system-images;android-${API_LEVEL};google_apis;x86_64"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/local/lib/android/sdk}}"
SDKMANAGER="${SDKMANAGER:-$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager}"
AVDMANAGER="${AVDMANAGER:-$SDK_ROOT/cmdline-tools/latest/bin/avdmanager}"
EMULATOR="${EMULATOR:-$SDK_ROOT/emulator/emulator}"
ADB="${ADB:-$SDK_ROOT/platform-tools/adb}"
TEMP_DIR="${RUNNER_TEMP:-/tmp}"

cleanup() {
  if [[ -x "$ADB" ]]; then
    "$ADB" emu kill >/dev/null 2>&1 || true
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

cd "$ROOT"
python3 scripts/test-android-performance-slo.py
require_executable "$SDKMANAGER" sdkmanager
require_executable "$AVDMANAGER" avdmanager

echo "Android SDK root: $SDK_ROOT"
echo "Benchmark image: $IMAGE"

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
fi

echo no | "$AVDMANAGER" create avd --force --name "$AVD_NAME" --package "$IMAGE" --device "pixel_6"

GPU_MODE="swiftshader_indirect"
if [[ -e /dev/kvm ]]; then
  "$EMULATOR" -avd "$AVD_NAME" -no-window -no-audio -no-boot-anim -no-snapshot -camera-back none -camera-front none -gpu "$GPU_MODE" >"$TEMP_DIR/jingdu-emulator.log" 2>&1 &
else
  "$EMULATOR" -avd "$AVD_NAME" -no-window -no-audio -no-boot-anim -no-snapshot -camera-back none -camera-front none -gpu "$GPU_MODE" -accel off >"$TEMP_DIR/jingdu-emulator.log" 2>&1 &
fi

"$ADB" wait-for-device
for _ in $(seq 1 180); do
  if [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
    break
  fi
  sleep 1
done
if [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]]; then
  echo "Android emulator did not finish booting" >&2
  tail -n 200 "$TEMP_DIR/jingdu-emulator.log" >&2 || true
  exit 1
fi
"$ADB" shell input keyevent 82 || true
"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0

cd "$ANDROID_DIR"
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
