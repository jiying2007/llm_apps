# Reader V3 profile provenance

The product Baseline/Startup Profile assets are trace-derived, curated HRF rules. They intentionally stay compact instead of committing raw device-wide capture noise from Compose/framework dependencies.

Authoritative generation evidence for this revision family:

- source head: `09e0d7f988c74507d5a6c5442f95e91e8864f9bc`
- GitHub Actions CI: `#701` / run `33191024009`
- generated baseline source: 24,466 rules, 2,549,632 bytes, SHA-256 `03ec774b23504e8397980382602a8df6f50f4bb6cbabf569730ab28ec984a426`
- generated startup source: 22,781 rules, 2,342,202 bytes, SHA-256 `946667b8ea7cd0a156fd75bd449694aae3115ce78a4e9226626d4c251b4048ce`

The committed `src/main/baseline-prof.txt` keeps the app-owned Reader V3 hot classes plus only framework families proven hot by the critical journeys. The #701 capture specifically promoted `ReaderFastTextKt`, `ReaderHotControlsKt`, `ReaderHotPanelCanvasKt`, Canvas/gesture/layout and Material button/icon paths. `src/main/startup-prof.txt` remains deliberately narrower and contains only the launcher/library/reader startup funnel; runtime panel/scroll packages are not wildcarded into Startup Profile.

Performance gating is independent and cannot self-feed: `ReaderJourneyBenchmark` measures the production R8 APK first with `CompilationMode.Partial(BaselineProfileMode.Require, warmupIterations = 0)`, enforcing the 40/80 ms CPU-frame SLO against the profile already packaged in that APK. Fresh profile collection happens only afterwards on the separate non-minified Profile target. A red SLO remains red after profile generation; generated evidence can only be curated into a later commit and therefore a later fresh install.

Raw generated profile files are CI evidence, not source-of-truth product assets; regenerate them after material CUJ/hot-path changes and update this provenance together with the curated rules.
