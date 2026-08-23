# Google Play Console Setup — Android v2.2+

This repository contains the code and store-copy SSOT. It does not have a connected Google Play Console automation provider, so the Console actions below must be performed in Play Console and then verified against this contract.

## 1. Lifetime Pro one-time product

Create one one-time in-app product for application `com.junchen.jingdu`:

- Product ID: `jingdu_pro_lifetime`
- Type: non-consumable / one-time product
- Product value: local Smart Clean automation and reusable rule assets
- Do not describe core reading as paid-only.

Localize the product name/description in all supported Play locales:

| Locale | Product name | Positioning |
| --- | --- | --- |
| `zh-CN` | `净读 Pro 永久版` | 一次买断的智能净读自动化、全局规则、离线 voice 与本地资产备份 |
| `zh-TW` | `淨讀 Pro 永久版` | 一次買斷的智慧淨讀自動化、全域規則、離線 voice 與本機資產備份 |
| `zh-HK` | `淨讀 Pro 永久版` | 一次買斷的智慧淨讀自動化、全域規則、離線 voice 與本機資產備份 |
| `en-US` | `Jingdu Pro Lifetime` | One-time unlock for Smart Clean automation, global rules, offline voice selection and local asset backup |

Activate an eligible one-time purchase offer and configure localized prices. The app never hard-codes a price; it renders Play’s localized `formattedPrice`.

Suggested first price experiment anchors:
- US$4.99
- US$6.99
- US$8.99

Choose the production starting price based on the target countries and then use Play one-time-product price experiments where available.

## 2. Billing tests

Before production rollout:

- add license testers;
- verify normal purchase;
- verify purchase cancellation;
- verify pending purchase does not unlock Pro;
- verify completed purchase unlocks Pro and is acknowledged;
- verify reinstall/clear-data restore through the same Play account;
- verify offline use after a previously Play-verified entitlement;
- verify a device without Play Billing keeps all Free reading capabilities functional;
- verify product-not-configured state shows a localized unavailable/retry message rather than blocking Clean or reading.

## 3. Default store listings

Repository source:

- `fastlane/metadata/android/zh-CN/`
- `fastlane/metadata/android/zh-TW/`
- `fastlane/metadata/android/zh-HK/`
- `fastlane/metadata/android/en-US/`

Expected titles:

- `zh-CN`: `净读 - TXT 小说阅读器`
- `zh-TW`: `淨讀 - TXT 小說閱讀器`
- `zh-HK`: `淨讀 - TXT 小說閱讀器`
- `en-US`: `Jingdu - Offline TXT Reader`

English localization communicates the same Chinese-TXT-depth product and must not imply EPUB/PDF/cloud catalog support.

Do not add ranking, award, temporary price, discount or “best/#1” claims to titles or graphic assets.

## 4. Search-keyword Custom Store Listings

Locale specifications:

- `store/play/CUSTOM_LISTINGS.zh-CN.md`
- `store/play/CUSTOM_LISTINGS.zh-TW.md`
- `store/play/CUSTOM_LISTINGS.zh-HK.md`
- `store/play/CUSTOM_LISTINGS.en-US.md`

Create keyword-targeted listings only with Search keyword bundles that Play Console makes available for the app. Target four intent groups when suitable traffic exists:

1. `txt-reader`
2. `txt-encoding`
3. `smart-clean`
4. `local-novel`

Each listing should use its matching hero screenshot first. The listing may customize app name, descriptions and graphic assets, while shared privacy/contact/category settings remain consistent.

## 5. Screenshots and graphics

Use the matching locale brief:

- `store/play/SCREENSHOT_BRIEF.zh-CN.md`
- `store/play/SCREENSHOT_BRIEF.zh-TW.md`
- `store/play/SCREENSHOT_BRIEF.zh-HK.md`
- `store/play/SCREENSHOT_BRIEF.en-US.md`

Capture actual release UI using synthetic/public-domain demo TXT content. Do not expose private user books or filenames. Do not put unverified performance numbers into screenshots. Do not reuse Simplified-Chinese captioned artwork for Traditional or English listings.

## 6. App-language verification

Android ships `zh-Hans`, `zh-Hant` and `en-US` UI resources and uses generated platform LocaleConfig. Before staged rollout:

- verify system/per-app language selection on `zh-CN`, `zh-TW`, `zh-HK`, `en-US`;
- verify an unsupported system language falls back to English;
- verify changing app language leaves book identity, progress, bookmarks, rules and selected offline TTS voice unchanged;
- verify Library/Reader/Clean/Settings with 200% font scaling in all three UI language families.

See `LOCALIZATION.md` and `DEVICE_MATRIX.md`.

## 7. Store listing experiments

Recommended order:

1. icon;
2. first/hero screenshot;
3. short description;
4. screenshot order.

Change one major variable per test. Compare install conversion and downstream user quality rather than chasing clicks alone.

## 8. Release safety

Before uploading v2.2+:

- use the retained Android upload key from the existing production signing identity;
- run `androidStoreCheck` with explicit version properties;
- archive signed APK/AAB, mapping, SHA256 manifest and signing certificate fingerprint;
- confirm Billing product and all four localized product descriptions are active before advertising Pro as purchasable;
- confirm all four default listings and intended Custom Listings are uploaded from repository SSOT;
- confirm Data safety / privacy declarations remain consistent with no text upload, no advertising SDK and no analytics SDK;
- use staged rollout rather than immediately exposing 100% of production users after major monetization/billing/localization changes.

## 9. Post-release checks

Verify in production Play:

- `zh-CN / zh-TW / zh-HK / en-US` default titles/descriptions render correctly;
- search-keyword listings route to the intended localized page;
- product title/description and price are localized correctly;
- purchase/restore works on a real production-installed build;
- unsupported system language falls back to English in-app;
- no unexpected INTERNET/runtime analytics dependency was introduced;
- review prompt appears only after meaningful milestones and never as a first-launch gate.
