#!/usr/bin/env bash
set -euo pipefail
: "${DEVECO_TOOLS_HOME:?set DEVECO_TOOLS_HOME to the HarmonyOS command-line tools root}"
cd "$(dirname "$0")/../apps/harmony"
"$DEVECO_TOOLS_HOME/hvigor/bin/hvigorw" --mode module -p product=default assembleHap --analyze=normal --no-parallel
