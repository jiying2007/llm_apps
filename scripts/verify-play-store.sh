#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path
import re

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
PY

grep -q 'jingdu_pro_lifetime' apps/android/app/src/main/java/com/junchen/jingdu/BillingManager.kt
grep -q 'billing:9.1.0' apps/android/app/build.gradle
grep -q 'review:2.0.2' apps/android/app/build.gradle
grep -q 'enableOneTimeProducts' apps/android/app/src/main/java/com/junchen/jingdu/BillingManager.kt
grep -q 'queryPurchasesAsync' apps/android/app/src/main/java/com/junchen/jingdu/BillingManager.kt
grep -q 'acknowledgePurchase' apps/android/app/src/main/java/com/junchen/jingdu/BillingManager.kt

echo 'Play store/growth contract OK'
