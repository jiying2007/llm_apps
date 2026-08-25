#!/usr/bin/env python3
from pathlib import Path


def replace_exact(text: str, old: str, new: str, label: str, expected: int = 1) -> str:
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"{label}: expected {expected} marker(s), found {count}")
    return text.replace(old, new, expected)

screen_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreenV3.kt")
text = screen_path.read_text(encoding="utf-8")
text = replace_exact(
    text,
    "import androidx.compose.ui.platform.LocalHapticFeedback\n",
    "import androidx.compose.ui.platform.LocalHapticFeedback\nimport androidx.compose.ui.platform.LocalLayoutDirection\n",
    "ReaderScreenV3 layout direction import",
)
text = replace_exact(
    text,
    "    val value = presented ?: return\n    val displayText = value.displayText\n    val map = value.map\n",
    "    val presentedValue = presented ?: return\n    val displayText = presentedValue.displayText\n    val map = presentedValue.map\n",
    "ReaderScreenV3 presented value shadow",
)
text = replace_exact(
    text,
    "    val density = LocalDensity.current\n    val systemLeft = WindowInsets.systemGestures.getLeft(density)\n    val systemRight = WindowInsets.systemGestures.getRight(density)\n",
    "    val density = LocalDensity.current\n    val layoutDirection = LocalLayoutDirection.current\n    val systemLeft = WindowInsets.systemGestures.getLeft(density, layoutDirection)\n    val systemRight = WindowInsets.systemGestures.getRight(density, layoutDirection)\n",
    "ReaderScreenV3 system gesture insets",
    expected=2,
)
text = replace_exact(
    text,
    "        while (isActive && state.autoScrolling) {\n            val now = withFrameNanos { it }\n            if (lastFrame != 0L) {\n                val seconds = (now - lastFrame).toDouble() / 1_000_000_000.0\n                scrollState.scrollBy(with(density) { (settings.autoScrollSpeedDpPerSecond * seconds).dp.toPx() })\n            }\n            lastFrame = now\n            val w = window ?: continue\n",
    "        while (isActive && state.autoScrolling) {\n            withFrameNanos { now ->\n                if (lastFrame != 0L) {\n                    val seconds = (now - lastFrame).toDouble() / 1_000_000_000.0\n                    scrollState.scrollBy(with(density) { (settings.autoScrollSpeedDpPerSecond * seconds).dp.toPx() })\n                }\n                lastFrame = now\n            }\n            val w = window ?: continue\n",
    "ReaderScreenV3 frame paced auto scroll",
)
screen_path.write_text(text, encoding="utf-8")

settings_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderSettingsScreen.kt")
text = settings_path.read_text(encoding="utf-8")
text = replace_exact(text, "stringResource(R.string.back)", "stringResource(R.string.back_to_library)", "ReaderSettingsScreen back semantics")
settings_path.write_text(text, encoding="utf-8")

panels_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderV3Panels.kt")
text = panels_path.read_text(encoding="utf-8")
text = replace_exact(
    text,
    "import androidx.compose.runtime.*\n",
    "import androidx.compose.runtime.*\nimport androidx.compose.runtime.saveable.rememberSaveable\n",
    "ReaderV3Panels rememberSaveable import",
)
panels_path.write_text(text, encoding="utf-8")

print("Reader V3 compile sync applied")
