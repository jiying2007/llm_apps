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
REMOTE_RESULT_ROOT="/sdcard/Download/jingdu-reader-v3-ci"
RESULT_ROOT="$ANDROID_DIR/macrobenchmark/build/outputs/direct-instrumentation"
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

run_instrumentation() {
  local rule="$1"
  local remote_dir="$2"
  local log_file="$3"
  "$ADB" shell rm -rf "$remote_dir"
  "$ADB" shell mkdir -p "$remote_dir"

  set +e
  "$ADB" shell am instrument -w -r \
    -e no-isolated-storage true \
    -e additionalTestOutputDir "$remote_dir" \
    -e androidx.benchmark.suppressErrors EMULATOR \
    -e listener androidx.benchmark.macro.junit4.SideEffectRunListener \
    -e androidx.benchmark.enabledRules "$rule" \
    "$INSTRUMENTATION" | tee "$log_file"
  local status=${PIPESTATUS[0]}
  set -e

  if (( status != 0 )) || grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=Process crashed|Process crashed' "$log_file" || ! grep -q 'INSTRUMENTATION_CODE: -1' "$log_file"; then
    echo "Reader V3 ${rule} instrumentation failed" >&2
    cat "$log_file" >&2
    exit 1
  fi
}

preserve_failed_macro_evidence() {
  local remote_dir="$1"
  local local_dir="$RESULT_ROOT/macro-failure-evidence"
  mkdir -p "$local_dir"
  echo "Preserving Macrobenchmark failure traces from ${remote_dir}"
  "$ADB" shell "ls -lah $remote_dir" || true
  "$ADB" pull "$remote_dir" "$local_dir/" || true
  find "$local_dir" -type f \( -name '*.perfetto-trace' -o -name '*-benchmarkData.json' \) -print || true
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
# Ubuntu hosted image can omit that runtime library, so provision only the evidenced host dep.
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

GPU_MODE="software"
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

# Build both signed APKs, then install them explicitly. Gradle's connected-test UTP host plugin
# automatically copies every Perfetto trace and benchmark report after the run. On hosted emulators
# that 80+MiB fan-out can make adb go offline after all benchmarks have already succeeded. Running
# instrumentation directly keeps the exact same Macrobenchmark metrics on-device and lets CI pull
# only the machine-readable evidence it gates on, plus traces only when the SLO actually fails.
cd "$ANDROID_DIR"
./gradlew --no-daemon --warning-mode all :app:assembleBenchmark :macrobenchmark:assembleBenchmark
TARGET_APK="$(find "$ANDROID_DIR/app/build/outputs/apk/benchmark" -type f -name '*.apk' -print -quit)"
TEST_APK="$(find "$ANDROID_DIR/macrobenchmark/build/outputs/apk/benchmark" -type f -name '*.apk' -print -quit)"
if [[ -z "$TARGET_APK" || ! -f "$TARGET_APK" ]]; then
  echo "Benchmark target APK was not produced" >&2
  exit 1
fi
if [[ -z "$TEST_APK" || ! -f "$TEST_APK" ]]; then
  echo "Macrobenchmark test APK was not produced" >&2
  exit 1
fi

echo "Installing Macrobenchmark target: $TARGET_APK"
"$ADB" install -r "$TARGET_APK"
echo "Installing Macrobenchmark tests: $TEST_APK"
"$ADB" install -r "$TEST_APK"
TARGET_PATH="$("$ADB" shell pm path "$TARGET_PACKAGE" 2>/dev/null | tr -d '\r')"
if [[ "$TARGET_PATH" != package:* ]]; then
  echo "Macrobenchmark target package is not installed: $TARGET_PACKAGE" >&2
  "$ADB" shell pm list packages | grep 'com.junchen.jingdu' >&2 || true
  exit 1
fi
echo "Macrobenchmark target installed: $TARGET_PATH"

INSTRUMENTATION="$("$ADB" shell pm list instrumentation | tr -d '\r' | sed -n 's/^instrumentation:\([^ ]*\).*$/\1/p' | grep 'com.junchen.jingdu.macrobenchmark' | head -n 1)"
if [[ -z "$INSTRUMENTATION" ]]; then
  echo "Macrobenchmark instrumentation component was not registered" >&2
  "$ADB" shell pm list instrumentation >&2 || true
  exit 1
fi
echo "Macrobenchmark instrumentation: $INSTRUMENTATION"

rm -rf "$RESULT_ROOT"
mkdir -p "$RESULT_ROOT/macro" "$RESULT_ROOT/profile"
"$ADB" shell rm -rf "$REMOTE_RESULT_ROOT"
"$ADB" shell mkdir -p "$REMOTE_RESULT_ROOT"

MACRO_REMOTE="$REMOTE_RESULT_ROOT/macro"
run_instrumentation Macrobenchmark "$MACRO_REMOTE" "$RESULT_ROOT/macro-instrumentation.log"
REMOTE_JSON="$("$ADB" shell "ls -1 $MACRO_REMOTE/*-benchmarkData.json 2>/dev/null | head -n 1" | tr -d '\r')"
if [[ -z "$REMOTE_JSON" ]]; then
  echo "Macrobenchmark completed without benchmarkData.json" >&2
  "$ADB" shell "ls -lah $MACRO_REMOTE" >&2 || true
  preserve_failed_macro_evidence "$MACRO_REMOTE"
  exit 1
fi
"$ADB" pull "$REMOTE_JSON" "$RESULT_ROOT/macro/"

cd "$ROOT"
set +e
python3 scripts/check-android-performance-slo.py "$RESULT_ROOT/macro"
SLO_STATUS=$?
set -e
if (( SLO_STATUS != 0 )); then
  preserve_failed_macro_evidence "$MACRO_REMOTE"
  exit "$SLO_STATUS"
fi
find "$RESULT_ROOT/macro" -type f -name '*-benchmarkData.json' -print -quit | grep -q .

# The SLO gate no longer needs per-iteration traces after the JSON is safely on the host. Free them
# before running the profile journey so hosted emulator storage/adb transport stays bounded.
"$ADB" shell rm -rf "$MACRO_REMOTE"

PROFILE_REMOTE="$REMOTE_RESULT_ROOT/profile"
run_instrumentation BaselineProfile "$PROFILE_REMOTE" "$RESULT_ROOT/profile-instrumentation.log"
REMOTE_BASELINE="$("$ADB" shell "ls -1 $PROFILE_REMOTE/*baseline-prof.txt 2>/dev/null | head -n 1" | tr -d '\r')"
REMOTE_STARTUP="$("$ADB" shell "ls -1 $PROFILE_REMOTE/*startup-prof.txt 2>/dev/null | head -n 1" | tr -d '\r')"
if [[ -z "$REMOTE_BASELINE" || -z "$REMOTE_STARTUP" ]]; then
  echo "Baseline Profile journey did not produce both baseline and startup profiles" >&2
  "$ADB" shell "ls -lah $PROFILE_REMOTE" >&2 || true
  exit 1
fi
"$ADB" pull "$REMOTE_BASELINE" "$RESULT_ROOT/profile/"
"$ADB" pull "$REMOTE_STARTUP" "$RESULT_ROOT/profile/"
find "$RESULT_ROOT/profile" -type f -name '*baseline-prof.txt' -print -quit | grep -q .
find "$RESULT_ROOT/profile" -type f -name '*startup-prof.txt' -print -quit | grep -q .
