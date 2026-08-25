#!/usr/bin/env python3
from pathlib import Path

path = Path("apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt")
text = path.read_text(encoding="utf-8")

def once(old: str, new: str, label: str):
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, found {count}")
    text = text.replace(old, new, 1)

once(
    "import androidx.activity.ComponentActivity\nimport androidx.activity.compose.setContent\n",
    "import androidx.activity.ComponentActivity\nimport androidx.activity.compose.setContent\nimport androidx.activity.viewModels\n",
    "activity imports",
)
once(
    "import androidx.compose.runtime.getValue\nimport androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.setValue\n",
    "import androidx.compose.runtime.getValue\nimport androidx.lifecycle.compose.collectAsStateWithLifecycle\n",
    "compose state imports",
)
once(
    "class MainActivity : ComponentActivity() {\n    private val main = Handler(Looper.getMainLooper())\n",
    "class MainActivity : ComponentActivity() {\n    private val readerViewModel: ReaderViewModel by viewModels()\n    private val main = Handler(Looper.getMainLooper())\n",
    "viewmodel owner",
)
once(
    "    private var uiState by mutableStateOf(AppUiState())\n",
    "    private var uiState: AppUiState\n        get() = readerViewModel.state.value\n        set(value) { readerViewModel.replace(value) }\n",
    "ui state owner",
)
once(
    "        setContent { JingduApp(uiState, actions) }\n",
    "        setContent {\n            val state by readerViewModel.state.collectAsStateWithLifecycle()\n            val location by readerViewModel.location.collectAsStateWithLifecycle()\n            JingduApp(\n                state = state, actions = actions, location = location,\n                onTrackLocation = { current, target, length -> readerViewModel.trackLocation(current, target, length) },\n                onLocationBack = { readerViewModel.backTarget(readerViewModel.state.value.position)?.let(::jumpTo) },\n                onLocationForward = { readerViewModel.forwardTarget(readerViewModel.state.value.position)?.let(::jumpTo) },\n            )\n        }\n",
    "compose collect",
)
once(
    "        if (::statsStore.isInitialized) statsStore.finish()\n        reader.close(); super.onDestroy()\n",
    "        if (::statsStore.isInitialized) statsStore.finish()\n        if (::readerPreferences.isInitialized) readerPreferences.flush(uiState.settings)\n        reader.close(); super.onDestroy()\n",
    "settings flush",
)

path.write_text(text, encoding="utf-8")
print("MainActivity Reader V3 UDF wiring applied")
