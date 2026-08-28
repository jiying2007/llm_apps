# Reader V3 profile provenance

The product Baseline/Startup Profile assets are trace-derived, curated HRF rules. They intentionally stay compact instead of committing raw device-wide capture noise from Compose/framework dependencies.

Authoritative generation evidence for this revision family:

- source head: `c98e028bebd1cde06239339bc0222f477da121ac`
- GitHub Actions CI: `#596` / run `32989847747`
- generated baseline source: 26,166 rules, 2,753,358 bytes, SHA-256 `b5f087a15a354a4ef366e17f85b6ba2a6a63cd581ceb7630a09205c6894632ac`
- generated startup source: 24,739 rules, 2,587,595 bytes, SHA-256 `ec605e8e036cccd19c49c3bbc63d022f076a8683c6110b75b6a32f26b9d277af`

The committed `src/main/baseline-prof.txt` keeps the app-owned Reader V3 hot classes plus the Compose text/layout/lazy paths observed in the exact-head trace. `src/main/startup-prof.txt` is deliberately narrower and contains only the launcher/library/reader startup funnel. Runtime panel/scroll packages are not wildcarded into Startup Profile.

Performance gating remains independent: `ReaderJourneyBenchmark` uses `BaselineProfileMode.Disable` with one warmup iteration, the 40/80 ms CPU-frame SLO runs first, profile collection runs afterwards, and a red SLO remains red after profile generation.

Raw generated profile files are CI evidence, not source-of-truth product assets; regenerate them after material CUJ/hot-path changes and update this provenance together with the curated rules.
