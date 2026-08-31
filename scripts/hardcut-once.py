#!/usr/bin/env python3
from pathlib import Path

body_path = Path(__file__).with_name("hardcut-body.py")
source = body_path.read_text(encoding="utf-8")
source = source.replace(
    'FORBIDDEN = ("ReaderScreenV3", "ReaderV3", "reader_v3", "reader-v3", "smart_clean3", "strings_reader_v3", "strings_smart_clean3")',
    'FORBIDDEN = ("ReaderScreen" + "V3", "Reader" + "V3", "reader_" + "v3", "reader-" + "v3", "smart_clean" + "3", "strings_reader_" + "v3", "strings_smart_clean" + "3")',
)
source = source.replace(
    '.replace("ReaderScreenV3", "ReaderScreen")',
    '.replace("ReaderV3Panels.kt", "ReaderInsightsPanels.kt").replace("ReaderScreenV3", "ReaderScreen")',
)
source = source.replace(
    '.replace("READER_V3_PROFILE_PROVENANCE.md", "READER_PROFILE_PROVENANCE.md")',
    '.replace("READER_V3_PROFILE_PROVENANCE.md", "READER_PROFILE_PROVENANCE.md").replace("READER_V3_PRELAUNCH_FINAL.md", "PRODUCTION_READINESS.md").replace("READER_V2_PRELAUNCH.md", "PRODUCTION_READINESS.md")',
)
source = source.replace(
    'delete("scripts/hardcut-once.py")',
    'delete("scripts/hardcut-once.py")\ndelete("scripts/hardcut-body.py")',
)
exec(compile(source, str(body_path), "exec"), {"__file__": __file__, "__name__": "__main__"})
