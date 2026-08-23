#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path

root = Path('.')
locales = ['zh-CN', 'en-US']
limits = {'title.txt': 30, 'short_description.txt': 80, 'full_description.txt': 4000}
for locale in locales:
    base = root / 'fastlane' / 'metadata' / 'android' / locale
    for filename, limit in limits.items():
        path = base / filename
        if not path.is_file():
            raise SystemExit(f'missing Play metadata: {path}')
        value = path.read_text(encoding='utf-8').strip()
        if not value:
            raise SystemExit(f'empty Play metadata: {path}')
        if len(value) > limit:
            raise SystemExit(f'{path}: {len(value)} characters > {limit}')

zh_title = (root / 'fastlane/metadata/android/zh-CN/title.txt').read_text(encoding='utf-8').strip()
if zh_title != '净读 - TXT 小说阅读器':
    raise SystemExit('unexpected zh-CN default title')
for banned in ('免费', '#1', '最强', '第一', '折扣', '限时'):
    if banned in zh_title:
        raise SystemExit(f'promotional/ranking term in Play title: {banned}')

short = (root / 'fastlane/metadata/android/zh-CN/short_description.txt').read_text(encoding='utf-8')
for required in ('TXT', '乱码', '净读', '大文件'):
    if required not in short:
        raise SystemExit(f'zh-CN short description missing product intent: {required}')

custom = (root / 'store/play/CUSTOM_LISTINGS.zh-CN.md').read_text(encoding='utf-8')
for key in ('txt-reader', 'txt-encoding', 'smart-clean', 'local-novel'):
    if f'`{key}`' not in custom:
        raise SystemExit(f'missing custom listing spec: {key}')

for path in (
    root / 'store/play/SCREENSHOT_BRIEF.zh-CN.md',
    root / 'docs/GROWTH_MONETIZATION.md',
    root / 'docs/PLAY_CONSOLE_SETUP.md',
):
    if not path.is_file() or not path.read_text(encoding='utf-8').strip():
        raise SystemExit(f'missing growth/store SSOT: {path}')
PY

grep -q 'getOrElse(4)' apps/android/app/build.gradle
grep -q 'getOrElse("2.2.0")' apps/android/app/build.gradle
grep -q 'com.android.billingclient:billing:9.1.0' apps/android/app/build.gradle
grep -q 'com.google.android.play:review:2.0.2' apps/android/app/build.gradle

grep -q 'jingdu_pro_lifetime' apps/android/app/src/main/java/com/junchen/jingdu/BillingManager.kt
grep -q 'enableOneTimeProducts' apps/android/app/src/main/java/com/junchen/jingdu/BillingManager.kt
grep -q 'queryPurchasesAsync' apps/android/app/src/main/java/com/junchen/jingdu/BillingManager.kt
grep -q 'Purchase.PurchaseState.PURCHASED' apps/android/app/src/main/java/com/junchen/jingdu/BillingManager.kt
grep -q 'acknowledgePurchase' apps/android/app/src/main/java/com/junchen/jingdu/BillingManager.kt

grep -q '免费扫描干扰文本' apps/android/app/src/main/java/com/junchen/jingdu/ReaderSheets.kt
grep -q '解锁 Pro 应用建议' apps/android/app/src/main/java/com/junchen/jingdu/ReaderSheets.kt
grep -q '离线朗读声音' apps/android/app/src/main/java/com/junchen/jingdu/ProductSettingsSheet.kt
grep -q '本地资产备份' apps/android/app/src/main/java/com/junchen/jingdu/ProductSettingsSheet.kt
grep -q 'OpenMultipleDocuments' apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'ReviewManagerFactory' apps/android/app/src/main/java/com/junchen/jingdu/ReviewPrompter.kt

if grep -q 'android.permission.INTERNET' apps/android/app/src/main/AndroidManifest.xml; then
  echo 'direct INTERNET permission is forbidden by v2.2 privacy position' >&2
  exit 1
fi

echo 'Play store/growth/monetization contract OK'
