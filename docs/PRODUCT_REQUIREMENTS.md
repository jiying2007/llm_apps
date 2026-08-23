# Product Requirements — Android v2.2

## Objective

Make Jingdu discoverable and worth paying for without weakening the complete Free reader or the offline/privacy architecture.

## P0 reader requirements

### Library / import
- Normal launch lands on Library.
- Cards show title, encoding, size, last activity and progress.
- Single import and SAF multi-select batch import are available without broad storage permission.
- Removing a book never deletes the external TXT.

### Encoding / large files
- AUTO is default; manual re-decode works from retained private source bytes.
- Re-decode creates a new immutable normalized revision; progress/bookmarks never leak across revision changes.
- File-size-proportional work stays off the main thread.
- Reopening a valid immutable revision reuses the Core `.jdx` cache when valid and safely rebuilds it when stale/corrupt.

### Reader
- Viewport layout determines sequential page span.
- Search, chapters, bookmarks, progress seeking and base TTS remain Free.
- Paper/Light/Night, font, size, line height, margins, auto paging, sleep timer and volume-key paging remain Free.
- Reader intent restores through stable id/revision/offset after configuration/process recreation; native handles/TTS state are recreated.

## P0 Smart Clean requirements

### Free value demonstration
- Smart Clean scan is fully local and available to Free users.
- It detects bounded-line high-frequency repetition, URLs/domains and common promotional/watermark markers.
- Candidate UI shows exact text, reason, occurrence count and confidence before any purchase request.
- User controls candidate selection; scan never modifies content.

### Pro automation
- Applying selected Smart Clean candidates requires Pro.
- Safe whole-line wildcard rules use `*` matching and run in the shared Core; arbitrary regex is not accepted.
- Existing exact literal rules remain Free.
- Whole-line wildcard export must preserve ordinary content and change only matching lines.

## P0 monetization requirements

- One-time product ID is exactly `jingdu_pro_lifetime`.
- There is no subscription in v2.2.
- Grant Pro only for Google Play `PURCHASED` state; pending purchases never unlock.
- Completed purchases are acknowledged.
- Owned purchases are queried on connection/resume for restore/reinstall.
- Last Play-verified entitlement may be cached for offline use; an authoritative successful query with no ownership may revoke it.
- Billing outage/unconfigured product never blocks Free reading.
- App displays Play `formattedPrice`; no hard-coded currency price.
- Paywall is contextual after value is visible, never a first-launch blocker.

## P0 reusable user assets

- Pro global Clean rules apply to all books.
- Recommended rule pack is explicit/editable, never silently destructive.
- Global rules can be imported/exported as bounded JSON.
- Pro local backup contains reading settings, selected offline TTS voice and global rules only; it excludes book text/private source files.
- Import validates schema, field sizes and rule count.

## P1 retention requirements

- Batch import handles partial failure and reports success/failure counts.
- Pro can select from system TTS voices that report `isNetworkConnectionRequired == false`; Free keeps system-default TTS.
- Play In-App Review is milestone based after meaningful use; no first-launch prompt and no sentiment pre-screen.
- Review request frequency is locally throttled.

## P0 ASO/store requirements

- Default Simplified Chinese store title: `净读 - TXT 小说阅读器`.
- Store metadata obeys Play title/short/full description length limits and avoids promotional superlatives in title.
- Four search-intent Custom Listing specs exist: TXT reader, encoding rescue, Smart Clean/noise removal, local/private novel reading.
- Screenshot brief tells a problem/solution story: mojibake rescue, noise detection, one-tap Pro apply, reading comfort, navigation, long-session tools, privacy.
- Store claims must be supported by product behavior/device evidence; no unverifiable “秒开/最快/#1” claims.

## Privacy requirements

- App manifest does not request direct `android.permission.INTERNET`.
- No account, advertising SDK or runtime analytics SDK.
- Google Play Billing and In-App Review are allowed platform commerce/feedback integrations; they must not receive private TXT content.
- Source TXT is never modified or deleted.
- User backup and rule exports contain no book正文.

## Acceptance

Exact candidate head must pass:
- Native Release build, CTest, Smart Clean/wildcard golden tests and clang-tidy;
- Android Debug/Release compile + lint, APK/AAB, AndroidTest assembly and product contract;
- Play-store metadata/commercial contract;
- Harmony source contract;
- terminal repository contract.

Before Play production rollout, external Console evidence must additionally prove the one-time product is active, license-test purchase/restore/acknowledge works, listing assets are published, signing reuses the retained upload key and staged rollout checks pass.