#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

APP_GRADLE="apps/android/app/build.gradle"
CI=".github/workflows/ci.yml"
PHYSICAL=".github/workflows/android-physical-release-performance.yml"
MANIFEST="apps/android/app/src/main/AndroidManifest.xml"

# Production-native compatibility and symbolication must not regress silently.
grep -Fq 'ndkVersion = "29.0.14206865"' "$APP_GRADLE"
grep -Fq 'debugSymbolLevel = "FULL"' "$APP_GRADLE"
test -f scripts/verify-android-16k-page-size.sh
grep -Fq -- '"$ZIPALIGN" -c -P 16 -v 4' scripts/verify-android-16k-page-size.sh
grep -Fq 'llvm-readelf' scripts/verify-android-16k-page-size.sh

# AndroidTest must execute on a hosted emulator; compiling tests alone is not acceptance.
test -f scripts/run-android-functional-tests-ci.sh
grep -Fq 'android-functional:' "$CI"
grep -Fq 'connectedDebugAndroidTest' scripts/run-android-functional-tests-ci.sh
grep -Fq 'android-native-compat:' "$CI"
grep -Fq 'android-functional, android-native-compat, android-performance' "$CI"

# Physical evidence is explicitly bound to an immutable ref/SHA and never accepts an emulator.
grep -Fq 'source_ref:' "$PHYSICAL"
grep -Fq 'JINGDU_QUALIFIED_SOURCE_SHA' "$PHYSICAL"
grep -Fq 'Physical Release gate refuses emulator/generic devices' scripts/run-android-physical-release-performance.sh
grep -Fq 'source_sha=' scripts/run-android-physical-release-performance.sh

# Privacy moat remains architectural: diagnostics are local/bounded and the APK still has no INTERNET.
if grep -Fq 'android.permission.INTERNET' "$MANIFEST"; then
  echo "Android Manifest unexpectedly requests INTERNET" >&2
  exit 1
fi
test -f apps/android/app/src/main/java/com/junchen/jingdu/ProductErrorLog.kt
grep -Fq 'never exception messages, paths, URIs, book names/text, search queries or purchase' apps/android/app/src/main/java/com/junchen/jingdu/ProductErrorLog.kt
grep -Fq 'containsPurchaseTokens' apps/android/app/src/main/java/com/junchen/jingdu/PrivacyAudit.kt

# Commercial behavior and immutable private publication are isolated into testable policies.
test -f apps/android/app/src/main/java/com/junchen/jingdu/BillingEntitlementPolicy.kt
test -f apps/android/app/src/test/java/com/junchen/jingdu/BillingEntitlementPolicyTest.kt
test -f apps/android/app/src/main/java/com/junchen/jingdu/PrivateFilePublisher.kt
test -f apps/android/app/src/test/java/com/junchen/jingdu/PrivateFilePublisherTest.kt

# Smart Clean held-out evidence must remain production-scale and independent from training rows.
test -f quality/smartclean/eval-v2-matrix.json
python3 scripts/verify-smartclean-model.py

echo "Product maturity source contracts OK"
