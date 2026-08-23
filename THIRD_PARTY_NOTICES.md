# Third-Party Notices

Jingdu uses third-party components only as local application dependencies. No third-party service receives book text.

## OpenccJava

- Project: `laisuk/OpenccJava`
- Purpose: pure-Java OpenCC-compatible phrase conversion used for bounded reading/search/chapter/TTS strings.
- Version: `1.4.2`
- License: MIT
- Upstream: https://github.com/laisuk/OpenccJava

Copyright (c) 2025 OpenccJava contributors.

The MIT license permits use, modification and distribution subject to preservation of the copyright and permission notice. See the upstream `LICENSE` for the full terms.

## OpenCC dictionaries and configs

OpenccJava bundles/repackages OpenCC lexicon/config data used by its compatible conversion engine.

- Project: Open Chinese Convert (OpenCC)
- License: Apache License 2.0 for the bundled OpenCC lexicon/config material, as documented by OpenccJava's third-party notice.
- Upstream: https://github.com/BYVoid/OpenCC

The OpenCC lexicon remains subject to its upstream license and attribution requirements. See OpenccJava `THIRD_PARTY_NOTICES.md` and the bundled dictionary license files in that distribution for complete terms.

## Product boundary

OpenCC-compatible conversion is display-only in Jingdu. The source TXT and normalized immutable document are not rewritten by this dependency. Conversion operates on bounded UI/TTS strings and remains fully offline at runtime.
