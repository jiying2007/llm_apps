# Growth & Monetization Contract

## Business position

Jingdu does not win by becoming another broad-format reader. It owns the difficult local-TXT workflow:
1. rescue mojibake/legacy encodings;
2. detect repeated ads/watermarks/URLs locally;
3. clean safely without modifying source;
4. reopen/read very long TXT comfortably;
5. accumulate reusable local rules/settings without an account.

Store copy and product behavior must describe the same value.

## Free / Pro boundary

### Free — complete daily reader
Free includes:
- single/multi TXT import;
- AUTO/manual encoding and re-decode;
- large-file reading/index cache;
- search, chapters, bookmarks, progress;
- Paper/Light/Night + typography;
- system-default TTS rate/pitch, auto page, sleep timer;
- exact per-book Clean rules;
- Smart Clean scan + full candidate preview.

### Pro Lifetime — automation and reusable local assets
Product id: `jingdu_pro_lifetime`.

Pro unlocks:
- one-action apply of selected Smart Clean suggestions;
- safe whole-line `*` wildcard rules;
- reusable global rule library;
- recommended Chinese web-novel rule pack;
- global rule JSON import/export;
- offline TTS voice selection when installed system engine exposes offline voices;
- local settings/global-rule backup/restore.

Do not move basic reading, search, chapters, bookmarks, themes or base TTS behind Pro.

## Pricing

Current product has no recurring server cost, so v2.2 uses a one-time purchase, not subscription.

Initial price-test candidates:
- US$4.99
- US$6.99
- US$8.99

Always display Google Play `formattedPrice`; never hard-code currency in UI. Use localized pricing and a Play one-time product price experiment when available. A future subscription requires a real recurring service such as encrypted sync/storage; local advanced features remain lifetime territory.

## Conversion path

Never show Pro at first launch.

```text
install
 -> import/read
 -> open Clean
 -> free Smart Clean scan
 -> see exact candidates/counts/confidence
 -> select useful candidates
 -> tap Apply
 -> lifetime Pro CTA
```

The user sees saved work before purchase. Whole-line wildcard/global rules/offline voice/backup may also surface contextual Pro CTAs, but none may block Free reading.

## Entitlement

- Unlock only Google Play `PURCHASED` purchases.
- Acknowledge completed non-consumables.
- Query owned INAPP purchases on connection/resume for restore/reinstall.
- Cache last Play-verified ownership for offline use.
- A successful authoritative query with no ownership may revoke cache.
- Pending purchase never unlocks.
- Billing outage/product-not-configured never disables Free features.
- No app account/backend exists in v2.2; book text never enters commerce APIs.

## User-owned retention assets

Retention comes from useful local state rather than notification spam:
- per-book rules;
- Pro global rules;
- recommended/user-created wildcard patterns;
- bookmarks/progress;
- reading preferences;
- selected offline TTS voice;
- local JSON backup/restore.

Backup intentionally excludes all book正文/source/normalized/clean files.

## Review timing

Use Play In-App Review only after meaningful local milestones such as repeated successful book opens, Smart Clean application or encoding rescue. No first-launch prompt, no sentiment pre-screen and a local cooldown between attempts.

## ASO

Default Simplified Chinese title:
`净读 - TXT 小说阅读器`

Intent clusters:
- TXT reader: TXT阅读器 / TXT小说阅读器 / 中文TXT
- encoding rescue: TXT乱码 / GBK / GB18030 / Big5
- Smart Clean: TXT清理 / 小说净化 / 广告水印 / 去干扰
- local/private: 本地小说阅读器 / 离线阅读器 / 本地阅读器

Do not keyword-stuff the default listing. Use Search-keyword Custom Store Listings when Play Console exposes meaningful query traffic.

## Custom listing strategy

Repository specs define four listing families:
- `txt-reader`
- `txt-encoding`
- `smart-clean`
- `local-novel`

Each listing has a different first screenshot/problem statement. Product functionality remains identical; listings only change discovery framing.

## Store experiments

Test one primary variable per experiment:
1. icon;
2. first screenshot/hero;
3. short description;
4. screenshot order;
5. secondary wording;
6. lifetime price points where Play supports one-time price experiments.

Do not optimize installs alone. Watch Play acquisition conversion, ratings, crashes/ANRs, refund/revenue signals and purchase conversion. No runtime analytics/advertising SDK is required.

## Release growth loop

For each release:
1. inspect Play search/acquisition terms;
2. map terms into the four intent clusters;
3. update metadata/screenshots only when the product actually supports the claim;
4. run controlled listing experiments;
5. keep winning creative, archive result notes;
6. use support/reviews to feed Smart Clean pattern/product quality, not to broaden format scope indiscriminately.

## Guardrails

- no deceptive “free/#1/best/fastest” title claims;
- no fake scarcity/subscription framing for lifetime Pro;
- no blocking first-run paywall;
- no private text in logs, billing, review or marketing systems;
- no arbitrary whole-book regex engine in the name of Pro feature count;
- no cloud feature until its privacy/cost/product value justifies it.