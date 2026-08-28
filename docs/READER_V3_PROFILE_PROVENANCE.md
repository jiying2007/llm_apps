# Reader V3 profile provenance

The product Baseline/Startup Profile assets are trace-derived, curated HRF rules. They intentionally stay compact instead of committing raw device-wide capture noise from Compose/framework dependencies.

Authoritative generation evidence for this revision family:

- source head: `d2917b523088621b0a5c76671bbaba50d87a797a`
- GitHub Actions CI: `#671` / run `33164386045`
- generated baseline source: 24,155 rules, 2,511,097 bytes, SHA-256 `b770d56668ee9a18dcac948cf5c44428b5cc06317e6fd1b50ce1b55bf973d61d`
- generated startup source: 22,717 rules, 2,339,097 bytes, SHA-256 `43c1fa521bc19b648c0b8956060095e8273180728763d16cf4e3b73784a94204`

The committed `src/main/baseline-prof.txt` keeps app-owned Reader V3 hot classes and the Compose runtime, platform, graphics, text, node, layout, gesture and animation families that dominate the #671 critical-journey trace. Material coverage is limited to the controls still present in the measured reader path instead of wildcarding the entire UI toolkit. `src/main/startup-prof.txt` stays deliberately narrower and contains only the launcher/library/reader startup funnel. Runtime panel/scroll packages are not wildcarded into Startup Profile.

Performance gating is independent: `ReaderJourneyBenchmark` uses `CompilationMode.Partial(BaselineProfileMode.Require, warmupIterations = 0)` against the production R8 APK, modelling a fresh Play-style install using the profile already packaged in that APK. The 40/80 ms CPU-frame SLO runs first; profile collection runs afterwards on a separate non-minified target, so newly generated rules can never self-feed the same SLO run.

Raw generated profile files are CI evidence, not source-of-truth product assets. Regenerate them after material CUJ/hot-path changes and update this provenance together with the curated rules.
