#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
generator = (ROOT / "apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt").read_text(encoding="utf-8")
journey = (ROOT / "apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt").read_text(encoding="utf-8")
runner = (ROOT / "scripts/run-android-macrobenchmark-ci.sh").read_text(encoding="utf-8")
checker = (ROOT / "scripts/check-android-performance-slo.py").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")

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
assert 'By.textContains("Continuous")' in runtime_block

# Product profiles must never weaken or self-feed the original hosted regression gate.
assert "BaselineProfileMode.Disable" in journey
assert "warmupIterations = 1" in journey
assert 'repeat(6) {' in journey, "Reader journeys must retain six interactions"
assert journey.count('repeat(6) {') >= 2, "page and continuous journeys must both retain six interactions"
assert 'os.environ.get("JINGDU_FRAME_P95_MS", "40")' in checker
assert 'os.environ.get("JINGDU_FRAME_P99_MS", "80")' in checker
assert 'sampled.get("frameDurationCpuMs")' in checker

slo_call = 'python3 scripts/check-android-performance-slo.py "$RESULT_ROOT/macro"'
profile_call = 'run_instrumentation BaselineProfile "$PROFILE_REMOTE"'
assert slo_call in runner and profile_call in runner
assert runner.index(slo_call) < runner.index(profile_call), "profile generation must happen after measured SLO"
assert "SLO_STATUS=$?" in runner, "SLO result must be retained across profile generation"
assert 'preserve_failed_macro_evidence "$MACRO_REMOTE"' in runner
assert 'PROFILE_RAW="$RESULT_ROOT/profile/raw"' in runner
assert 'baseline-prof.txt' in runner and 'startup-prof.txt' in runner
assert 'sort -u > "$RESULT_ROOT/profile/baseline-prof.txt"' in runner
assert 'sort -u > "$RESULT_ROOT/profile/startup-prof.txt"' in runner
last_slo_exit = runner.rfind('exit "$SLO_STATUS"')
assert last_slo_exit > runner.index(profile_call), "red SLO must still fail after profiles are emitted"
assert 'GPU_MODE="software"' in runner

perf_job = workflow[workflow.index("  android-performance:"):workflow.index("  harmony-contract:")]
assert "runs-on: ubuntu-22.04" in perf_job, "hosted performance image must be pinned"
assert "Preserve Macrobenchmark evidence and failure Perfetto traces" in perf_job

print("Reader V3 Baseline/Startup Profile contract OK")
