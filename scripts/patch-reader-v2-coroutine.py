#!/usr/bin/env python3
from pathlib import Path
p = Path('apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt')
t = p.read_text(encoding='utf-8')
old = 'import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.Job\n'
new = 'import kotlinx.coroutines.CoroutineScope\nimport kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.Job\nimport kotlinx.coroutines.currentCoroutineContext\n'
if old not in t: raise SystemExit('missing coroutine imports marker')
t = t.replace(old, new, 1)
old = '    var lastCenterTapAt = 0L\n    var pendingCenterTap: Job? = null\n    awaitEachGesture {\n'
new = '    var lastCenterTapAt = 0L\n    var pendingCenterTap: Job? = null\n    val gestureScope = CoroutineScope(currentCoroutineContext())\n    awaitEachGesture {\n'
if old not in t: raise SystemExit('missing gesture scope marker')
t = t.replace(old, new, 1)
old = '                            pendingCenterTap = launch { delay(280L); onToggleControls() }\n'
new = '                            pendingCenterTap = gestureScope.launch { delay(280L); onToggleControls() }\n'
if old not in t: raise SystemExit('missing delayed tap launch marker')
t = t.replace(old, new, 1)
p.write_text(t, encoding='utf-8')
