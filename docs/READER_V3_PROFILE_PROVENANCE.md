# Reader V3 profile provenance

The product Baseline/Startup Profile assets are trace-derived, curated HRF rules. They intentionally stay compact instead of committing raw device-wide capture noise from Compose/framework dependencies.

Authoritative generation evidence for this revision family:

- source head: `97bd7b952735255d567fb13f7d8777bdf4c7858e`
- GitHub Actions CI: `#715` / run `33224899370`
- generated baseline source: 24,485 rules, 2,553,730 bytes, SHA-256 `141e3f372636d74862f437ee7a62cb424ca412447012870981c42023b0439509`
- generated startup source: 22,827 rules, 2,346,528 bytes, SHA-256 `791ee598c4eb2271a2c8cda213ff7af400f5f395143e4c52675851199618be82`

The committed `src/main/baseline-prof.txt` keeps the app-owned Reader V3 hot classes plus only framework families proven hot by the critical journeys. The #715 capture confirms `ReaderFastTextKt` and the native `ReaderContinuousScrollModel`, `ReaderContinuousViewportView` and `ReaderContinuousTextView` hot paths, in addition to the previously promoted controls/panel/framework families. `src/main/startup-prof.txt` remains deliberately narrower and contains only the launcher/library/reader startup funnel; runtime panel/scroll packages are not wildcarded into Startup Profile.

Performance gating is independent and cannot self-feed: `ReaderJourneyBenchmark` measures the production R8 APK first with `CompilationMode.Partial(BaselineProfileMode.Require, warmupIterations = 0)`, enforcing the 40/80 ms CPU-frame SLO against the profile already packaged in that APK. Fresh profile collection happens only afterwards on the separate non-minified Profile target. A red SLO remains red after profile generation; generated evidence can only be curated into a later commit and therefore a later fresh install.

Raw generated profile files are CI evidence, not source-of-truth product assets; regenerate them after material CUJ/hot-path changes and update this provenance together with the curated rules.
