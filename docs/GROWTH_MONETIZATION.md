# Growth & Monetization Contract

## Business position

Jingdu does not compete by becoming a broad-format reader. It competes on the difficult local-TXT workflow:

1. rescue mojibake/legacy encodings;
2. find repeated ads, watermarks, URLs and site tails locally;
3. clean them safely without modifying the source;
4. keep very long TXT files comfortable to reopen and read.

The growth promise and the product must say the same thing. ASO copy is a product contract, not a separate marketing fiction.

## Free / Pro boundary

### Free — a complete reader

Free users keep:
- TXT import and app-private source copy;
- AUTO/manual encoding and re-decode;
- large-file reading and native index cache;
- search, chapters, bookmarks and progress;
- Paper/Light/Night typography settings;
- system TTS, auto paging and sleep timer;
- manual exact per-book Clean replace/delete rules;
- Smart Clean scan and candidate preview.

### Pro Lifetime — automation and reusable assets

One-time product ID: `jingdu_pro_lifetime`.

Pro unlocks:
- applying Smart Clean suggestions in one action;
- safe whole-line wildcard rules using `*`;
- reusable global Clean rule library;
- recommended Chinese web-novel rule pack;
- global rule JSON import/export;
- future local advanced capabilities may join Pro only if the basic reader remains complete.

Do not move search, chapters, bookmarks, basic themes or basic TTS behind Pro merely to increase feature count in the paywall.

## Pricing strategy

Start with a one-time purchase, not a subscription. The product currently has no recurring server/cloud cost and the value is local automation.

Initial price test candidates:
- US$4.99
- US$6.99
- US$8.99

Use localized Play pricing and an actual one-time product price experiment where available. Do not hard-code a USD price in the app; display Google Play `formattedPrice`.

A subscription becomes reasonable only when Jingdu offers a real recurring service such as encrypted multi-device sync/storage with ongoing infrastructure cost. Local advanced features remain lifetime-purchase territory.

## Paywall timing

Never show the Pro paywall on first launch.

Primary conversion path:

```text
install
  -> import TXT
  -> read normally
  -> open Clean
  -> free Smart Clean scan
  -> see exact candidates/counts/confidence
  -> select valuable suggestions
  -> Pro CTA to apply
```

Users should understand the saved work before they are asked to buy.

## Entitlement contract

- Grant Pro only for Google Play purchases in `PURCHASED` state.
- Acknowledge completed non-consumable purchases.
- Query owned purchases at connection/start/resume so reinstall/restore works.
- Cache the last Play-verified entitlement to support offline use.
- A successful authoritative Play query with no ownership may revoke the cached entitlement.
- Pending purchases never grant Pro.
- If Billing is unavailable or the Play product is not configured, the Free reader continues to work normally.

The current product intentionally has no account/backend. This means the Play client is the source of purchase entitlement. If future fraud/security requirements justify a backend, add it without sending private book text.

## Retention without analytics SDKs

Jingdu does not add a runtime analytics/advertising SDK for growth. Use:
- Play acquisition/search-term reports;
- custom-store-listing conversion;
- store listing experiments;
- ratings/reviews;
- billing/revenue reports;
- Play Vitals/crash/ANR data;
- local-only counters for review timing.

Local review milestones:
- after multiple successful book opens;
- after multiple Smart Clean applications;
- after an encoding rescue following meaningful use.

No pre-rating gate such as “Do you like the app?” is used.

## ASO strategy

Default Simplified Chinese store name:

`净读 - TXT 小说阅读器`

Search-intent clusters:
- TXT reader: TXT阅读器 / TXT小说阅读器 / 中文TXT
- encoding rescue: TXT乱码 / GBK / GB18030 / Big5
- clean/noise: TXT清理 / 小说净化 / 广告水印 / 去干扰
- local/private: 本地小说阅读器 / 离线阅读器 / 本地阅读器

Do not keyword-stuff one listing. Use Play Search-keyword Custom Store Listings when those keyword bundles are available in the app’s Play Console traffic data.

## Store experiment order

Change one main variable per experiment:
1. icon;
2. hero/first screenshot;
3. short description;
4. screenshot order;
5. secondary wording.

Do not optimize raw installs alone. Compare downstream quality: ratings, retention proxies available from Play, Smart Clean engagement and Pro conversion.

## Product moat roadmap

The moat is accumulated local user value:
- personal per-book rules;
- global rules;
- safe reusable patterns;
- reading progress/bookmarks;
- reading preferences;
- local backup/export.

Avoid unrelated feature breadth that weakens the positioning or creates ongoing complexity without improving the core TXT jobs.
