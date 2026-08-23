#!/usr/bin/env bash
set -euo pipefail

for path in prototype android-prototype txt_ref_apps research apps/android/core core/src apps/android/dist docs/txt-reader; do
  test ! -e "$path" || { echo "legacy path remains: $path" >&2; exit 1; }
done

if find . -type f \( -name '*.apk' -o -name '*.aab' -o -name '*.hap' -o -name '*.hsp' \
  -o -name '*.jks' -o -name '*.keystore' -o -name '*.p12' -o -name '*.p7b' \) \
  -not -path './.git/*' | grep .; then
  echo 'committed binary/signing artifact found' >&2
  exit 1
fi

if git grep -n -E 'com\.jingdu\.txt\.w0|android-prototype|prototype/core|device-pending|console-pending' \
  -- ':!scripts/verify-terminal.sh'; then
  echo 'legacy implementation reference found' >&2
  exit 1
fi

if git grep -n -E 'implementation project\(":core"\)|include\(":app", ":core"\)' -- apps/android; then
  echo 'Android Java shared core dependency found' >&2
  exit 1
fi

if git grep -n -E 'uses:[[:space:]]+actions/[^@]+@v[0-9]+' -- .github/workflows; then
  echo 'floating GitHub Actions major tag found' >&2
  exit 1
fi

if git grep -n -E 'cleanPreviewFile|cleanRevisionPath|writeTextTask' -- apps; then
  echo 'legacy fixed clean-artifact mechanism found' >&2
  exit 1
fi

if git grep -n 'configurationCacheRequested' -- apps/android; then
  echo 'deprecated Gradle configuration-cache probe found' >&2
  exit 1
fi

if grep -q 'android.permission.INTERNET' apps/android/app/src/main/AndroidManifest.xml; then
  echo 'direct INTERNET permission violates local/private Android contract' >&2
  exit 1
fi

required=(
  README.md CONTRIBUTING.md SECURITY.md .clang-format .clang-tidy .editorconfig
  .github/CODEOWNERS .github/dependabot.yml .github/pull_request_template.md
  .github/workflows/ci.yml .github/workflows/harmony-device.yml
  docs/PRODUCT.md docs/PRODUCT_REQUIREMENTS.md docs/GROWTH_MONETIZATION.md docs/UX.md
  docs/ARCHITECTURE.md docs/CORE_CONTRACT.md docs/DATA_MODEL.md docs/ENCODING.md
  docs/PERFORMANCE.md docs/TESTING.md docs/DEVICE_MATRIX.md docs/QUALITY_GATES.md
  docs/PLAY_CONSOLE_SETUP.md docs/HARMONY_RUNNER.md docs/RELEASE.md
  fastlane/metadata/android/zh-CN/title.txt
  fastlane/metadata/android/zh-CN/short_description.txt
  fastlane/metadata/android/zh-CN/full_description.txt
  fastlane/metadata/android/en-US/title.txt
  fastlane/metadata/android/en-US/short_description.txt
  fastlane/metadata/android/en-US/full_description.txt
  store/play/CUSTOM_LISTINGS.zh-CN.md store/play/SCREENSHOT_BRIEF.zh-CN.md
  scripts/verify-play-store.sh
  core/native/include/jingdu/core_api.h core/native/src/core_api.cpp core/native/src/core_api_cached.cpp
  core/native/src/index_cache.h core/native/src/index_cache.cpp core/native/src/sha256.cpp
  apps/android/app/src/main/cpp/native_bridge.cpp
  apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
  apps/android/app/src/main/java/com/junchen/jingdu/JingduApp.kt
  apps/android/app/src/main/java/com/junchen/jingdu/LibraryScreen.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderSheets.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ProductSettingsSheet.kt
  apps/android/app/src/main/java/com/junchen/jingdu/UiComponents.kt
  apps/android/app/src/main/java/com/junchen/jingdu/UiFormatters.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt
  apps/android/app/src/main/java/com/junchen/jingdu/UiModels.kt
  apps/android/app/src/main/java/com/junchen/jingdu/NativeIndexCache.java
  apps/android/app/src/main/java/com/junchen/jingdu/BillingManager.kt
  apps/android/app/src/main/java/com/junchen/jingdu/RuleCodec.kt
  apps/android/app/src/main/java/com/junchen/jingdu/RuleLibrary.kt
  apps/android/app/src/main/java/com/junchen/jingdu/UserBackup.kt
  apps/android/app/src/main/java/com/junchen/jingdu/ReviewPrompter.kt
  apps/android/app/src/androidTest/java/com/junchen/jingdu/JingduUiTest.kt
  apps/android/app/src/main/res/values/themes.xml
  apps/android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
  apps/android/app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml
  apps/android/gradle/wrapper/gradle-wrapper.jar
  apps/android/gradle/wrapper/gradle-wrapper.properties
  apps/harmony/entry/src/main/cpp/napi_init.cpp
  apps/harmony/entry/src/main/ets/pages/Index.ets
  apps/harmony/entry/src/main/ets/model/BookStore.ets
  apps/harmony/entry/src/main/ets/model/BackgroundTasks.ets
  apps/harmony/entry/src/main/ets/model/ReaderController.ets
  apps/harmony/entry/src/main/ets/model/TtsController.ets
)
for path in "${required[@]}"; do
  test -f "$path" || { echo "required terminal asset missing: $path" >&2; exit 1; }
done

test ! -f apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.java
test ! -f apps/android/app/src/main/java/com/junchen/jingdu/JingduUi.kt

grep -q 'kAbiVersion = 2' core/native/src/core_api.cpp
grep -q 'typedef int32_t jd_status' core/native/include/jingdu/core_api.h
grep -q 'jd_noise_candidates' core/native/include/jingdu/core_api.h
grep -q 'lineGlobMatches' core/native/src/core_api_cached.cpp
grep -q 'load_index_cache' core/native/src/core_api_cached.cpp
grep -q 'JDX1' core/native/src/index_cache.cpp

grep -q 'sourceSha256' apps/android/app/src/main/java/com/junchen/jingdu/BookRepository.java
grep -q 'newSingleThreadExecutor' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'OpenMultipleDocuments' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'analyzeSmartClean' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'UserBackup' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'ReviewPrompter' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'STATE_NORMALIZED_SHA' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'previousBook.normalizedSha256 == book.normalizedSha256' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'bookmarkKey(book)' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'repository.redecode' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'NativeIndexCache.pruneOrphans' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'document-' apps/android/app/src/main/java/com/junchen/jingdu/BookRepository.java
grep -q 'clean-' apps/android/app/src/main/java/com/junchen/jingdu/BookRepository.java
grep -q 'NativeCore.search(handle' apps/android/app/src/main/java/com/junchen/jingdu/ReaderController.java
grep -q 'NativeCore.chapters(handle' apps/android/app/src/main/java/com/junchen/jingdu/ReaderController.java
grep -q 'NativeCore.noiseCandidates' apps/android/app/src/main/java/com/junchen/jingdu/ReaderController.java

grep -q 'GridCells.Adaptive' apps/android/app/src/main/java/com/junchen/jingdu/LibraryScreen.kt
grep -q '批量导入' apps/android/app/src/main/java/com/junchen/jingdu/LibraryScreen.kt
grep -q 'ProductSettingsSheet' apps/android/app/src/main/java/com/junchen/jingdu/JingduApp.kt
grep -q '智能净读' apps/android/app/src/main/java/com/junchen/jingdu/ReaderSheets.kt
grep -q '解锁 Pro 应用建议' apps/android/app/src/main/java/com/junchen/jingdu/ReaderSheets.kt
grep -q '离线朗读声音' apps/android/app/src/main/java/com/junchen/jingdu/ProductSettingsSheet.kt
grep -q '本地资产备份' apps/android/app/src/main/java/com/junchen/jingdu/ProductSettingsSheet.kt

grep -q 'jingdu_pro_lifetime' apps/android/app/src/main/java/com/junchen/jingdu/BillingManager.kt
grep -q 'queryPurchasesAsync' apps/android/app/src/main/java/com/junchen/jingdu/BillingManager.kt
grep -q 'acknowledgePurchase' apps/android/app/src/main/java/com/junchen/jingdu/BillingManager.kt
grep -q 'compose-bom:2026.08.00' apps/android/app/build.gradle
grep -q 'com.android.billingclient:billing:9.1.0' apps/android/app/build.gradle
grep -q 'com.google.android.play:review:2.0.2' apps/android/app/build.gradle
grep -q 'compileSdk = 37' apps/android/app/build.gradle

grep -q 'sourceSha256' apps/harmony/entry/src/main/ets/model/BookStore.ets
grep -q '@Concurrent' apps/harmony/entry/src/main/ets/model/BackgroundTasks.ets
grep -q 'document-${normalizedSha256}.txt' apps/harmony/entry/src/main/ets/model/BackgroundTasks.ets
grep -q 'clean-${revision}.txt' apps/harmony/entry/src/main/ets/model/BackgroundTasks.ets
grep -q "name.endsWith('.jdx')" apps/harmony/entry/src/main/ets/model/BackgroundTasks.ets
grep -q 'self-hosted,harmonyos' docs/HARMONY_RUNNER.md

grep -q 'version "9.3.1"' apps/android/build.gradle
grep -q '^org.gradle.configuration-cache=false$' apps/android/gradle.properties
grep -q 'gradle-9.5.0-bin.zip' apps/android/gradle/wrapper/gradle-wrapper.properties
grep -q 'distributionSha256Sum=553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746' \
  apps/android/gradle/wrapper/gradle-wrapper.properties
echo '497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7  apps/android/gradle/wrapper/gradle-wrapper.jar' \
  | sha256sum --check --strict

test ! -f .github/workflows/finalize-content-addressing.yml
test ! -f .github/workflows/final-runtime-polish.yml
test ! -f .github/workflows/upgrade-gradle-wrapper.yml
test ! -f apps/android/app/src/main/java/com/junchen/jingdu/ReaderSurfaceView.java

echo 'Terminal source/product/commercial contract OK'
