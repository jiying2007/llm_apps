# UX — Android v2.2

## Design direction

Jingdu is a calm Material 3 reading product, not an engineering toolbar and not a paywall-first app. Library and Reader are the two product states. Search, chapters, bookmarks, Clean, encoding and settings are contextual sheets.

Commercial UX follows one rule: **show useful local results before asking for money**. Free users must be able to read normally and inspect Smart Clean candidates; Pro appears only when automation/reusable assets are requested.

All user-facing copy is resource-backed. Android supports Simplified Chinese, Traditional Chinese and English; layouts must tolerate wording expansion and 200% font scale without relying on fixed Chinese string lengths. UI locale and document language are separate concepts.

## Library

- In-app brand follows the active locale (`净读` / `淨讀` / `Jingdu`); store discovery titles may include localized TXT-reader keywords.
- Supporting privacy promise is localized from resources rather than embedded in Compose source.
- Primary action imports one TXT; batch import is secondary and uses SAF multi-select.
- Cards prioritize title/progress; encoding/size/last-read are secondary.
- Empty state explains mojibake rescue, Clean and privacy before file selection.

## Reader

- Text owns the screen.
- Search and chapters are top-level actions.
- Previous/progress/next/TTS are persistent bottom actions and have localized accessibility descriptions.
- Bookmarks/Clean/encoding/settings/delete remain progressive-disclosure actions.
- System back closes a sheet before Reader → Library; predictive back remains compatible.
- Long English or Traditional-Chinese labels must wrap/ellipsize according to Material hierarchy instead of forcing fixed widths.

## Clean conversion flow

### Smart Clean is not a hidden Pro teaser
1. User opens Clean.
2. The localized free-scan action runs locally.
3. Results show localized reason plus exact text, count and confidence.
4. User can include/exclude candidates.
5. Only applying selected suggestions asks for Pro when not owned.
6. After purchase, the same selection is applied and Clean preview opens.

Core candidate reasons are stable codes (`url`, `promo`, `repeated`, `promo_repeated`); the Android UI maps those codes to the active locale. Smart Clean content detection itself covers Simplified and Traditional common promotion/watermark forms and never follows the UI locale.

No first-launch Pro modal and no artificial blur/hidden candidate text.

### Per-book rules
- Exact literal rules remain Free.
- Empty replacement means delete.
- Safe whole-line wildcard `*` is labeled Pro and explains that it matches a whole line.
- No arbitrary regex editor in v2.2.

### Global rule library
- Clearly labeled Pro/user-owned asset.
- Recommended Simplified and Traditional rules are opt-in and editable.
- Import/export is visible only as an explicit user action.
- Applying rules never mutates source TXT.

## Search

- Exact text search remains primary.
- Android may additionally try curated one-to-one Simplified/Traditional character variants and merge duplicate offsets.
- The UI does not claim general-purpose conversion; ambiguous mappings are intentionally not guessed.
- Cross-script search behavior follows document/query text, not the selected UI language.

## Reading settings

Free groups:
1. page tone;
2. font/font size/line height/margins;
3. base TTS rate/pitch and start/stop;
4. auto paging;
5. sleep timer.

Pro groups:
- offline TTS voice selection, showing only voices the system engine marks as not requiring network;
- local settings/global-rule backup and restore.

When no voice has been explicitly selected, Android infers a suitable `zh-CN`, `zh-TW`, `zh-HK` or English TTS locale from the current document text. A user-selected offline voice always has priority over automatic language selection.

Backup copy explicitly says book正文 is excluded and nothing is uploaded.

## Pro purchase surface

- Product is one-time lifetime, never described as subscription.
- CTA uses Google Play `formattedPrice` when available.
- Billing/error messages are localized with Android resources.
- If Billing/product details are unavailable, show retry/restore wording without blocking Free features.
- Existing owners always get a visible localized restore/re-check path.
- `PENDING` purchases do not show Pro unlocked.

## Review prompt

- No review on first launch.
- Request only after meaningful local milestones such as multiple book opens, Smart Clean use or encoding rescue.
- No “Do you like Jingdu?” pre-gate.
- Respect Play’s system UI and local cooldown.

## Reading surface / navigation

- 16–34sp base type, adjustable line height/margins, system sans/serif, Paper/Light/Night.
- Expanded windows cap text measure rather than stretching paragraphs.
- Viewport layout reports visible code-point span for sequential paging.
- Search/chapter jumps reset sequential page history.
- Clean offsets never persist into original progress/bookmarks.

## Adaptive / accessibility

- Material controls retain 48dp targets.
- Icon-only actions have localized content descriptions.
- 200% font scale keeps primary navigation usable.
- Color is never the sole state signal.
- Busy work has progress indicator + localized descriptive text.
- Destructive actions require confirmation.
- Pro is never communicated by color alone; use text/icon labels.
- Locale changes must not alter document identity, reader offset semantics, rule persistence or TTS voice preference.

## Error language

Errors explain next action rather than exposing native status codes. Translation belongs in Android resources; stable internal error/reason codes may cross business boundaries, but localized display text must not be persisted as identity or protocol data.

Representative categories include encoding/import failure, Google Play unavailability, TTS readiness/audio focus, invalid rule/backup format and export destination failure. All active locales must retain equivalent actionability rather than literal machine translation.

## Localization verification

- `en-US`, `zh-Hans` and `zh-Hant` resource key sets must stay identical.
- Format placeholders (`%1$s`, `%1$d`, etc.) must stay aligned across locales.
- Major Compose and runtime-controller files may not contain hard-coded CJK UI copy.
- Compose AndroidTest resolves labels/descriptions through `targetContext.getString(...)`, so the same smoke test contract works under the active locale.
- Hosted CI assembles AndroidTest sources; real-device locale switching at `zh-CN`, `zh-TW`, `zh-HK`, `en-US` belongs in the Android qualification/device matrix.

See `LOCALIZATION.md` for resource and store-locale structure.

## Store-to-product continuity

The first-run experience must deliver the same promises shown in localized Play screenshots: encoding rescue, local Smart Clean, long-form comfort and privacy. Store imagery must not advertise features that are only roadmap items, and English localization must not imply EPUB/cloud catalog support that the product does not provide.
