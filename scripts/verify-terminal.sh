#!/usr/bin/env bash
set -euo pipefail

for path in prototype android-prototype txt_ref_apps research apps/android/core core/src apps/android/dist docs/txt-reader; do
  test ! -e "$path" || { echo "legacy path remains: $path" >&2; exit 1; }
done

PUBLIC_DEBUG_KEY='./config/signing/android-debug.keystore'
PUBLIC_DEBUG_KEY_SHA256='b327cb3fd3bf5eeaeb3958737335180f5b8c664d47429fd1b9eb08d32e178a56'
mapfile -t signing_artifacts < <(find . -type f \( -name '*.apk' -o -name '*.aab' -o -name '*.hap' -o -name '*.hsp' -o -name '*.jks' -o -name '*.keystore' -o -name '*.p12' -o -name '*.p7b' \) -not -path './.git/*' -print | sort)
for artifact in "${signing_artifacts[@]}"; do
  if [[ "$artifact" != "$PUBLIC_DEBUG_KEY" ]]; then
    echo "committed binary/signing artifact found: $artifact" >&2
    exit 1
  fi
done
test -f "$PUBLIC_DEBUG_KEY" || { echo 'repository-stable Android debug key missing' >&2; exit 1; }
test "$(sha256sum "$PUBLIC_DEBUG_KEY" | awk '{print $1}')" = "$PUBLIC_DEBUG_KEY_SHA256" || {
  echo 'repository-stable Android debug key checksum mismatch' >&2
  exit 1
}

if git grep -n -E 'uses:[[:space:]]+actions/[^@]+@v[0-9]+' -- .github/workflows; then
  echo 'floating GitHub Actions major tag found' >&2; exit 1
fi
if grep -q 'android.permission.INTERNET' apps/android/app/src/main/AndroidManifest.xml; then
  echo 'direct INTERNET permission violates local/private Android contract' >&2; exit 1
fi

required=(
  README.md CONTRIBUTING.md SECURITY.md .clang-format .clang-tidy .editorconfig
  .github/CODEOWNERS .github/REPOSITORY_POLICY.md .github/dependabot.yml .github/pull_request_template.md .github/workflows/ci.yml
  config/signing/android-debug.keystore config/signing/README.md
  docs/PRODUCT.md docs/ARCHITECTURE.md docs/PERFORMANCE.md docs/TESTING.md docs/RELEASE.md docs/PRODUCTION_READINESS.md
  scripts/verify-play-store.sh scripts/verify-android-i18n.py scripts/verify-release-version.py scripts/verify-reader.sh scripts/verify-reader-profile-contract.py scripts/publish-source-release.py
  core/native/include/jingdu/core_api.h core/native/src/core_api.cpp core/native/src/core_api_cached.cpp
  apps/android/readerproto/src/main/proto/reader_settings.proto
  apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
  apps/android/app/src/main/java/com/junchen/jingdu/JingduApp.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderMotionController.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderSession.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderViewportEngine.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderAnnotationStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderDatabase.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderSettingsScreen.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderQuickPanels.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderInsightsPanels.kt
  apps/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderTtsPlayer.kt
  apps/android/app/src/main/java/com/junchen/jingdu/TtsSemanticNavigator.kt
  apps/android/app/src/main/java/com/junchen/jingdu/UserBackup.kt
  apps/android/app/src/main/java/com/junchen/jingdu/UserAssetBackup.kt
  apps/android/app/src/main/java/com/junchen/jingdu/LibraryMetadataStore.kt
  apps/android/app/src/main/java/com/junchen/jingdu/SmartCleanFeedbackStore.kt
  apps/android/app/src/androidTest/java/com/junchen/jingdu/JingduUiTest.kt
  apps/android/app/src/androidTest/java/com/junchen/jingdu/PortableUserAssetsTest.kt
  apps/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt
)
for path in "${required[@]}"; do test -f "$path" || { echo "required terminal asset missing: $path" >&2; exit 1; }; done

for legacy in \
  apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.java \
  apps/android/app/src/main/java/com/junchen/jingdu/JingduUi.kt \
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderV2Panels.kt \
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderAdvancedSettingsSheet.kt \
  apps/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.java \
  scripts/verify-reader-v2.sh \
  .github/workflows/reader-v2-bootstrap.yml \
  scripts/bootstrap-reader-v2-main.py; do
  test ! -e "$legacy" || { echo "superseded Reader asset remains: $legacy" >&2; exit 1; }
done

python3 ./scripts/verify-android-i18n.py
python3 ./scripts/verify-release-version.py
python3 ./scripts/verify-reader-profile-contract.py
bash ./scripts/verify-reader.sh

grep -q 'kAbiVersion = 2' core/native/src/core_api.cpp
grep -q 'jd_noise_candidates' core/native/include/jingdu/core_api.h
grep -q 'load_index_cache' core/native/src/core_api_cached.cpp

grep -q 'ReaderSession' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'ReaderViewModel by viewModels' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'ReaderAnnotationStore' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'repository.redecode' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'NativeIndexCache.pruneOrphans' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'GridCells.Adaptive' apps/android/app/src/main/java/com/junchen/jingdu/LibraryScreen.kt
grep -q 'jingdu_pro_lifetime' apps/android/app/src/main/java/com/junchen/jingdu/BillingManager.kt
grep -q 'compose-bom:2026.08.00' apps/android/app/build.gradle
grep -q 'compileSdk = 37' apps/android/app/build.gradle
grep -q 'generateLocaleConfig = true' apps/android/app/build.gradle
grep -q 'project(":readerproto")' apps/android/app/build.gradle
grep -q 'media3-session:1.11.0' apps/android/app/build.gradle

grep -q 'const val SCHEMA = 4' apps/android/app/src/main/java/com/junchen/jingdu/UserBackup.kt
grep -q 'containsBookText' apps/android/app/src/main/java/com/junchen/jingdu/UserBackup.kt
grep -q 'consumeRestoredProgress' apps/android/app/src/main/java/com/junchen/jingdu/BookRepository.kt
grep -q 'jingdu-reading-stats' apps/android/app/src/main/java/com/junchen/jingdu/UserAssetBackup.kt
grep -q 'jingdu-smartclean-feedback' apps/android/app/src/main/java/com/junchen/jingdu/SmartCleanFeedbackStore.kt
grep -q 'manifest-sha256' scripts/publish-source-release.py
grep -q '"/git/tags"' scripts/publish-source-release.py
grep -q 'platform-enforced protection' .github/REPOSITORY_POLICY.md
grep -q 'Google Play production-qualified' docs/PRODUCTION_READINESS.md

if grep -q 'Android product line/source release: \*\*2\.2\.x' docs/PRODUCT.md; then
  echo 'stale 2.2.x current-product SSOT remains' >&2; exit 1
fi
if grep -q '^## Android v2\.2 commercial release' docs/RELEASE.md; then
  echo 'stale v2.2 release-heading SSOT remains' >&2; exit 1
fi

grep -q '@Concurrent' apps/harmony/entry/src/main/ets/model/BackgroundTasks.ets
grep -q 'DocumentViewPicker' apps/harmony/entry/src/main/ets/pages/Index.ets

grep -q 'gradle-9.5.0-bin.zip' apps/android/gradle/wrapper/gradle-wrapper.properties
echo '497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7  apps/android/gradle/wrapper/gradle-wrapper.jar' | sha256sum --check --strict

echo 'Terminal prelaunch Reader architecture/product/localization/profile/portable-assets/provenance contract OK'

python3 scripts/verify-android-source-conventions.py
