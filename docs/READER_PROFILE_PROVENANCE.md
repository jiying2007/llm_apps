# Reader profile provenance

The product Baseline/Startup Profile assets are trace-derived, curated HRF rules. They intentionally stay compact instead of committing raw device-wide capture noise from Compose/framework dependencies.

Authoritative generation evidence for this revision family:

- source head: `281c3ffa2d301746ae392509ce8ae7338b247f66`
- GitHub Actions CI: `#722` / run `33227054478`
- generated baseline source: 24,028 rules, 2,491,741 bytes, SHA-256 `46d5e7015be3132ccf8881a8592c560a00a6b40dd45a9aeb3d43689864b1ac3b`
- generated startup source: 22,897 rules, 2,353,766 bytes, SHA-256 `f845cecec641ecc014edc6b0c134d6307fd0d033e68b543daa27c23659d4ccb9`

The committed `src/main/baseline-prof.txt` keeps the app-owned Reader hot classes plus only framework families proven hot by the critical journeys. The #722 capture confirms `ReaderFastTextKt` and the native `ReaderContinuousScrollModel`, `ReaderContinuousViewportView` and `ReaderContinuousTextView` hot paths, in addition to the previously promoted controls/panel/framework families. `src/main/startup-prof.txt` remains deliberately narrower and contains only the launcher/library/reader startup funnel; runtime panel/scroll packages are not wildcarded into Startup Profile.

Performance gating is independent and cannot self-feed: `ReaderJourneyBenchmark` measures the production R8 APK first with `CompilationMode.Partial(BaselineProfileMode.Require, warmupIterations = 0)`, enforcing the 40/80 ms CPU-frame SLO against the profile already packaged in that APK. Fresh profile collection happens only afterwards on the separate non-minified Profile target. A red SLO remains red after profile generation; generated evidence can only be curated into a later commit and therefore a later fresh install.

Raw generated profile files are CI evidence, not source-of-truth product assets; regenerate them after material CUJ/hot-path changes and update this provenance together with the curated rules.
