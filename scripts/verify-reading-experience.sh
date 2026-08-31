#!/usr/bin/env bash
set -euo pipefail

# The generic reading-experience entry point follows the current prelaunch architecture only.
# Reader V2 implementation-specific gates were intentionally removed before the first store launch.
bash ./scripts/verify-reader.sh

echo 'Reading experience contract OK: Reader source-offset/selection/skim/typography/storage/TTS invariants aligned'
