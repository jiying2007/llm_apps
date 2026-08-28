#!/usr/bin/env python3
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
generator = (ROOT / "apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt").read_text(encoding="utf-8")
journey = (ROOT / "apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt").read_text(encoding="utf-8")
runner = (ROOT / "scripts/run-android-macrobenchmark-ci.sh").read_text(encoding="utf-8")
checker = (ROOT / "scripts/check-android-performance-slo.py").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
app_gradle = (ROOT / "apps/android/app/build.gradle").read_text(encoding="utf-8")
macro_gradle = (ROOT / "apps/android/macrobenchmark/build.gradle").read_text(encoding="utf-8")
root_gradle = (ROOT / "apps/android/build.gradle").read_text(encoding="utf-8")
benchmark_provider = (ROOT / "apps/android/app/src/benchmark/java/com/junchen/jingdu/ReaderBenchmarkFixtureProvider.kt").read_text(encoding="utf-8")
proguard = (ROOT / "apps/android/app/proguard-rules.pro").read_text(encoding="utf-8")
product_baseline_path = ROOT / "apps/android/app/src/main/baseline-prof.txt"
product_startup_path = ROOT / "apps/android/app/src/main/startup-prof.txt"
provenance_path = ROOT / "docs/READER_V3_PROFILE_PROVENANCE.md"

startup_marker = "@Test fun readerV3Startup()"
runtime_marker = "@Test fun readerV3CriticalJourneys()"
assert startup_marker in generator, "Reader V3 startup profile CUJ missing"
assert runtime_marker in generator, "Reader V3 runtime profile CUJ missing"
assert generator.count("includeInStartupProfile = true") == 1, "exactly one startup-profile CUJ is required"
assert generator.count("includeInStartupProfile = false") == 1, "runtime CUJs must be Baseline-only"
assert 'outputFilePrefix = "jingdu-reader-v3-startup"' in generator
assert 'outputFilePrefix = "jingdu-reader-v3-critical"' in generator

startup_at = generator.index(startup_marker)
runtime_at = generator.index(runtime_marker)
assert startup_at < runtime_at, "startup CUJ must remain separate from runtime CUJs"
startup_block = generator[startup_at:runtime_at]
runtime_block = generator[runtime_at:]
assert "includeInStartupProfile = true" in startup_block
assert "KEYCODE_VOLUME_DOWN" not in startup_block, "page turns must not inflate Startup Profile"
assert "requireChaptersClick" not in startup_block
assert "includeInStartupProfile = false" in runtime_block
assert "KEYCODE_VOLUME_DOWN" in runtime_block
assert "requireChaptersClick" in runtime_block
assert 'setProfileMode("continuous")' in runtime_block, "runtime profile must switch mode through deterministic provider protocol"
assert 'requireContinuousModeClick' not in generator, "localized/UI-text profile mode switch retained"
assert 'content call --uri content://com.junchen.jingdu.benchmarkfixture --method mode --arg $mode' in generator

# Stage 1 measures the production R8 APK in the install state Android defines for a fresh Play-style
# install: the curated Baseline Profile already packaged in the APK is required and precompiled. The
# profile generated later in this job is independent freshness evidence and can never self-feed SLO.
assert "BaselineProfileMode.Require" in journey
assert "BaselineProfileMode.Disable" not in journey
assert "warmupIterations = 0" in journey
assert journey.count('repeat(6) {') >= 2, "page and continuous journeys must both retain six interactions"
assert 'os.environ.get("JINGDU_FRAME_P95_MS", "40")' in checker
assert 'os.environ.get("JINGDU_FRAME_P99_MS", "80")' in checker
assert 'sampled.get("frameDurationCpuMs")' in checker

# Real frame SLO and profile collection intentionally use different target variants. Macrobenchmark
# must see production-like R8 code; HRF collection must see a non-obfuscated profileable target.
benchmark_block = app_gradle[app_gradle.index("        benchmark {"):app_gradle.index("        profile {")]
profile_block = app_gradle[app_gradle.index("        profile {"):app_gradle.index("    sourceSets {")]
assert "initWith release" in benchmark_block
assert "minifyEnabled = true" in benchmark_block and "shrinkResources = true" in benchmark_block
assert "debuggable = false" in benchmark_block
assert "initWith release" in profile_block
assert "minifyEnabled = false" in profile_block and "shrinkResources = false" in profile_block
assert "debuggable = false" in profile_block
assert 'java.srcDir "src/benchmark/java"' in app_gradle
assert 'kotlin.srcDir "src/benchmark/java"' in app_gradle, "profile variant must compile the benchmark Kotlin fixture provider"
assert 'manifest.srcFile "src/benchmark/AndroidManifest.xml"' in app_gradle
assert "profile {" in macro_gradle and 'matchingFallbacks = ["profile"]' in macro_gradle
for task in (":app:assembleBenchmark", ":app:assembleProfile", ":macrobenchmark:assembleBenchmark", ":macrobenchmark:assembleProfile"):
    assert task in root_gradle, f"androidCheck must compile hosted variant: {task}"

# The hosted fixture is a stable R8-safe protocol and must match the real reader environment.
assert "controlsAutoHideMs = 3500L" in benchmark_provider, "benchmark must use production controls auto-hide"
assert "controlsAutoHideMs = 60_000L" not in benchmark_provider, "benchmark-only persistent controls bias retained"
assert "DataStore flush is synchronous" in benchmark_provider, "benchmark mode ACK rationale missing"
assert 'putLong("modeApplied", 1L)' not in benchmark_provider, "unstable R8 Bundle payload ACK retained"
assert 'result.contains("Result: Bundle[{}]")' in journey, "deterministic empty-Bundle mode ACK contract missing"
assert "-keep class com.junchen.jingdu.ReaderBenchmarkFixtureProvider { *; }" in proguard, "hosted fixture provider R8 keep missing"

slo_call = 'python3 scripts/check-android-performance-slo.py "$RESULT_ROOT/macro"'
profile_swap = 'install_pair "Profile collection" "$PROFILE_TARGET_APK" "$PROFILE_TEST_APK"'
profile_call = 'run_instrumentation BaselineProfile "$PROFILE_REMOTE" "$RESULT_ROOT/profile-instrumentation.log" "$PROFILE_CLASS"'
assert slo_call in runner and profile_call in runner and profile_swap in runner
assert ':app:assembleBenchmark :macrobenchmark:assembleBenchmark' in runner
assert ':app:assembleProfile :macrobenchmark:assembleProfile' in runner
assert 'BENCHMARK_TARGET_APK=' in runner and 'PROFILE_TARGET_APK=' in runner
assert runner.index(slo_call) < runner.index(profile_swap) < runner.index(profile_call), "R8 SLO must freeze before non-minified profile target is installed"
assert "SLO_STATUS=$?" in runner, "SLO result must be retained across profile generation"
assert 'preserve_failed_macro_evidence "$MACRO_REMOTE"' in runner
assert runner.index('if (( SLO_STATUS != 0 )); then\n  preserve_failed_macro_evidence "$MACRO_REMOTE"') < runner.index(profile_swap), "red SLO Perfetto must be retained before a later Profile failure can exit"
assert 'PROFILE_RAW="$RESULT_ROOT/profile/raw"' in runner
assert 'baseline-prof.txt' in runner and 'startup-prof.txt' in runner
assert 'sort -u > "$RESULT_ROOT/profile/baseline-prof.txt"' in runner
assert 'sort -u > "$RESULT_ROOT/profile/startup-prof.txt"' in runner
last_slo_exit = runner.rfind('exit "$SLO_STATUS"')
assert last_slo_exit > runner.index(profile_call), "red SLO must still fail after profiles are emitted"
assert 'GPU_MODE="${JINGDU_EMULATOR_GPU_MODE:-auto}"' in runner, "hosted emulator must use the recommended auto graphics mode by default"

# Hosted instrumentation must run only the authority for each stage. This prevents unrelated
# Startup/Profile tests from turning the frame SLO into a mixed-suite infrastructure result.
assert 'local test_class="${4:-}"' in runner, "instrumentation class filter parameter missing"
assert 'class_args=(-e class "$test_class")' in runner, "instrumentation class filter wiring missing"
assert 'MACRO_CLASS="com.junchen.jingdu.macrobenchmark.ReaderJourneyBenchmark"' in runner
assert 'PROFILE_CLASS="com.junchen.jingdu.macrobenchmark.BaselineProfileGenerator"' in runner
assert 'StartupBenchmark' not in runner, "standalone startup suite must not contaminate the frame gate"

# An invalid instrumentation run is infrastructure evidence, not a performance result. Exactly one
# bounded Macrobenchmark recovery is allowed; valid measurements remain untouched.
assert "return 1" in runner[runner.index("run_instrumentation()") : runner.index("preserve_failed_macro_evidence()")]
assert "attempting one bounded guest recovery" in runner
assert "wait_for_android_ready 120" in runner
assert "INSTRUMENTATION_ABORTED" in runner and "System has crashed" in runner
assert runner.count("run_instrumentation Macrobenchmark") == 2, "exactly one Macrobenchmark retry is allowed"

# The generated evidence is curated into compact product assets. Startup stays intentionally narrow.
assert product_baseline_path.is_file() and product_startup_path.is_file(), "product Baseline/Startup Profile assets missing"
baseline = product_baseline_path.read_text(encoding="utf-8")
startup = product_startup_path.read_text(encoding="utf-8")
assert baseline.strip() and startup.strip()
for marker in (
    "Lcom/junchen/jingdu/ReaderScreenV3Kt;",
    "Lcom/junchen/jingdu/ReaderQuickPanelsKt;",
    "Lcom/junchen/jingdu/ReaderSmartChaptersPanelKt;",
    "Landroidx/compose/foundation/text/**",
    "Landroidx/compose/ui/text/**",
    "Landroidx/compose/ui/layout/**",
):
    assert marker in baseline, f"baseline hot path missing: {marker}"
for marker in (
    "Lcom/junchen/jingdu/MainActivity;",
    "Lcom/junchen/jingdu/JingduAppKt;",
    "Lcom/junchen/jingdu/LibraryScreenKt;",
    "Lcom/junchen/jingdu/ReaderScreenV3Kt;",
):
    assert marker in startup, f"startup funnel missing: {marker}"
for forbidden in ("ReaderQuickPanelsKt", "ReaderSmartChaptersPanelKt", "foundation/lazy", "continuous"):
    assert forbidden not in startup, f"runtime-only Startup Profile rule retained: {forbidden}"
assert len(startup.splitlines()) < len(baseline.splitlines()), "Startup Profile must remain a strict compact subset"

# Provenance is evidence for the exact revision family, not a verifier hard-coded to one historical run.
assert provenance_path.is_file(), "profile provenance missing"
provenance = provenance_path.read_text(encoding="utf-8")
assert re.search(r"source head: `?[0-9a-f]{40}`?", provenance), "profile provenance source head missing"
assert re.search(r"run `?[0-9]{8,}`?", provenance), "profile provenance CI run missing"
profile_evidence = re.findall(r"generated (?:baseline|startup) source: ([0-9,]+) rules, ([0-9,]+) bytes, SHA-256 `([0-9a-f]{64})`", provenance)
assert len(profile_evidence) == 2, "baseline/startup profile provenance evidence must include rules, bytes and SHA-256"

perf_job = workflow[workflow.index("  android-performance:"):workflow.index("  harmony-contract:")]
assert "runs-on: ubuntu-22.04" in perf_job, "hosted performance image must be pinned"
assert "Preserve Macrobenchmark evidence and failure Perfetto traces" in perf_job

print("Reader V3 Baseline/Startup Profile contract OK")
