#!/usr/bin/env python3
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "apps/android/app/src/main/res"
LOCALES = {
    "en-US": RES / "values/strings.xml",
    "zh-Hans": RES / "values-b+zh+Hans/strings.xml",
    "zh-Hant": RES / "values-b+zh+Hant/strings.xml",
}


def keys(path: Path) -> set[str]:
    if not path.is_file():
        raise SystemExit(f"missing locale resource: {path.relative_to(ROOT)}")
    root = ET.parse(path).getroot()
    names = {node.attrib["name"] for node in root if node.tag in {"string", "plurals", "string-array"} and "name" in node.attrib}
    if len(names) < 100:
        raise SystemExit(f"suspiciously small locale resource: {path.relative_to(ROOT)} ({len(names)} keys)")
    return names

_, base_path = next(iter(LOCALES.items()))
base_keys = keys(base_path)
for locale, path in LOCALES.items():
    current = keys(path)
    missing = sorted(base_keys - current)
    extra = sorted(current - base_keys)
    if missing or extra:
        raise SystemExit(f"locale key drift {locale}: missing={missing} extra={extra}")

props = (RES / "resources.properties").read_text(encoding="utf-8").strip()
if props != "unqualifiedResLocale=en-US":
    raise SystemExit("English must remain the unqualified resource fallback")

manifest = (ROOT / "apps/android/app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
if 'android:label="@string/app_name"' not in manifest:
    raise SystemExit("manifest label must use @string/app_name")

build = (ROOT / "apps/android/app/build.gradle").read_text(encoding="utf-8")
if "generateLocaleConfig = true" not in build:
    raise SystemExit("AGP automatic LocaleConfig generation must remain enabled")

cjk = re.compile(r"[\u3400-\u9fff]")
base = ROOT / "apps/android/app/src/main/java/com/junchen/jingdu"
presentation_files = [
    "JingduApp.kt",
    "LibraryScreen.kt",
    "ReaderScreen.kt",
    "ReaderSheets.kt",
    "ProductSettingsSheet.kt",
]
for name in presentation_files:
    text = (base / name).read_text(encoding="utf-8")
    if cjk.search(text):
        raise SystemExit(f"hard-coded CJK UI copy found in {name}; move it to strings.xml")
    if "stringResource(R.string." not in text:
        raise SystemExit(f"localized UI resource use missing in {name}")

# Runtime/controller messages must also be resource-backed. Content dictionaries such as
# ChineseScript/Smart-Clean marker tables are intentionally excluded because they model document text.
for name in ["MainActivity.kt", "BillingManager.kt"]:
    text = (base / name).read_text(encoding="utf-8")
    if cjk.search(text):
        raise SystemExit(f"hard-coded CJK runtime copy found in {name}; use R.string resources")
    if "R.string." not in text:
        raise SystemExit(f"runtime localization resource use missing in {name}")

print("Android i18n contract OK: en-US / zh-Hans / zh-Hant")
