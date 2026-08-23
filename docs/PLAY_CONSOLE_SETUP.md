# Google Play Console Setup — Android v2.2+

This repository contains the code and store-copy SSOT. It does not have a connected Google Play Console automation provider, so the Console actions below must be performed in Play Console and then verified against this contract.

## 1. Lifetime Pro one-time product

Create one one-time in-app product for application `com.junchen.jingdu`:

- Product ID: `jingdu_pro_lifetime`
- Type: non-consumable / one-time product
- Display name: `净读 Pro 永久版`
- Product value: local Smart Clean automation and reusable rule assets
- Do not describe core reading as paid-only.

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
- verify product-not-configured state shows an unavailable/retry message rather than blocking Clean or reading.

## 3. Default store listing

Repository source:

- `fastlane/metadata/android/zh-CN/`
- `fastlane/metadata/android/en-US/`

Simplified Chinese default title:

`净读 - TXT 小说阅读器`

Do not add ranking, award, temporary price, discount or “best/#1” claims to the title or graphic assets.

## 4. Search-keyword Custom Store Listings

Use `store/play/CUSTOM_LISTINGS.zh-CN.md` as the copy/asset specification.

Create keyword-targeted listings only with Search keyword bundles that Play Console makes available for the app. Target four intent groups when suitable traffic exists:

1. `txt-reader`
2. `txt-encoding`
3. `smart-clean`
4. `local-novel`

Each listing should use its matching hero screenshot first. The listing may customize app name, descriptions and graphic assets, while shared privacy/contact/category settings remain consistent.

## 5. Screenshots and graphics

Use `store/play/SCREENSHOT_BRIEF.zh-CN.md`.

Capture actual release UI using synthetic/public-domain demo TXT content. Do not expose private user books or filenames. Do not put unverified performance numbers into screenshots.

## 6. Store listing experiments

Recommended order:

1. icon;
2. first/hero screenshot;
3. short description;
4. screenshot order.

Change one major variable per test. Compare install conversion and downstream user quality rather than chasing clicks alone.

## 7. Release safety

Before uploading v2.2+:

- use the retained Android upload key from the existing production signing identity;
- run `androidStoreCheck` with explicit version properties;
- archive signed APK/AAB, mapping, SHA256 manifest and signing certificate fingerprint;
- confirm Billing product is active before advertising Pro as purchasable;
- confirm Data safety / privacy declarations remain consistent with no text upload, no advertising SDK and no analytics SDK;
- use staged rollout rather than immediately exposing 100% of production users after major monetization/billing changes.

## 8. Post-release checks

Verify in production Play:

- default and localized title/description render correctly;
- search-keyword listings route to the intended page;
- product price is localized correctly;
- purchase/restore works on a real production-installed build;
- no unexpected INTERNET/runtime analytics dependency was introduced;
- review prompt appears only after meaningful milestones and never as a first-launch gate.
