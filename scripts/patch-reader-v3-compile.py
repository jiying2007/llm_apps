#!/usr/bin/env python3
from pathlib import Path


def patch(path: str, replacements):
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    for label, old, new in replacements:
        count = text.count(old)
        if count != 1:
            raise SystemExit(f"{path}: {label}: expected 1 marker, found {count}")
        text = text.replace(old, new, 1)
    p.write_text(text, encoding="utf-8")

screen = "apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreenV3.kt"
patch(screen, [
    ("layout direction import",
     "import androidx.compose.ui.platform.LocalHapticFeedback\n",
     "import androidx.compose.ui.platform.LocalHapticFeedback\nimport androidx.compose.ui.platform.LocalLayoutDirection\n"),
    ("paged presented name",
     "    val value = presented ?: return\n    val displayText = value.displayText\n    val map = value.map\n",
     "    val presentedValue = presented ?: return\n    val displayText = presentedValue.displayText\n    val map = presentedValue.map\n"),
    ("paged system gestures",
     "    val density = LocalDensity.current\n    val systemLeft = WindowInsets.systemGestures.getLeft(density)\n    val systemRight = WindowInsets.systemGestures.getRight(density)\n",
     "    val density = LocalDensity.current\n    val layoutDirection = LocalLayoutDirection.current\n    val systemLeft = WindowInsets.systemGestures.getLeft(density, layoutDirection)\n    val systemRight = WindowInsets.systemGestures.getRight(density, layoutDirection)\n"),
    ("continuous system gestures",
     "    val scrollState = rememberScrollState()\n    val density = LocalDensity.current\n    val systemLeft = WindowInsets.systemGestures.getLeft(density)\n    val systemRight = WindowInsets.systemGestures.getRight(density)\n",
     "    val scrollState = rememberScrollState()\n    val density = LocalDensity.current\n    val layoutDirection = LocalLayoutDirection.current\n    val systemLeft = WindowInsets.systemGestures.getLeft(density, layoutDirection)\n    val systemRight = WindowInsets.systemGestures.getRight(density, layoutDirection)\n"),
    ("frame paced auto scroll",
     "        while (isActive && state.autoScrolling) {\n            val now = withFrameNanos { it }\n            if (lastFrame != 0L) {\n                val seconds = (now - lastFrame).toDouble() / 1_000_000_000.0\n                scrollState.scrollBy(with(density) { (settings.autoScrollSpeedDpPerSecond * seconds).dp.toPx() })\n            }\n            lastFrame = now\n            val w = window ?: continue\n",
     "        while (isActive && state.autoScrolling) {\n            withFrameNanos { now ->\n                if (lastFrame != 0L) {\n                    val seconds = (now - lastFrame).toDouble() / 1_000_000_000.0\n                    scrollState.scrollBy(with(density) { (settings.autoScrollSpeedDpPerSecond * seconds).dp.toPx() })\n                }\n                lastFrame = now\n            }\n            val w = window ?: continue\n"),
])

settings = "apps/android/app/src/main/java/com/junchen/jingdu/ReaderSettingsScreen.kt"
patch(settings, [
    ("back semantics", "stringResource(R.string.back)", "stringResource(R.string.back_to_library)"),
])

panels = "apps/android/app/src/main/java/com/junchen/jingdu/ReaderV3Panels.kt"
patch(panels, [
    ("rememberSaveable import",
     "import androidx.compose.runtime.*\n",
     "import androidx.compose.runtime.*\nimport androidx.compose.runtime.saveable.rememberSaveable\n"),
])

print("Reader V3 compile sync applied")
