#!/usr/bin/env python3
from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, found {count}")
    return text.replace(old, new, 1)

prefs_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt")
text = prefs_path.read_text(encoding="utf-8")
text = replace_once(text,
    "import com.junchen.jingdu.proto.ReaderFontWeightProto\n",
    "import com.junchen.jingdu.proto.ReaderFontWeightProto\nimport com.junchen.jingdu.proto.ReaderGestureActionProto\n",
    "gesture proto import")
text = replace_once(text,
    "enum class ReaderAutoPageMode { ADAPTIVE, FIXED }\n",
    "enum class ReaderAutoPageMode { ADAPTIVE, FIXED }\nenum class ReaderGestureAction { CONTROLS, BOOKMARK, NEXT, PREVIOUS, NONE }\n",
    "gesture enum")
text = replace_once(text,
    "    val advancedGestureCustomizationEnabled: Boolean = false,\n    val dictionaryProcessTextEnabled: Boolean = true,\n",
    "    val advancedGestureCustomizationEnabled: Boolean = false,\n    val centerTapAction: ReaderGestureAction = ReaderGestureAction.CONTROLS,\n    val doubleTapAction: ReaderGestureAction = ReaderGestureAction.NONE,\n    val dictionaryProcessTextEnabled: Boolean = true,\n",
    "gesture settings fields")
text = replace_once(text,
    "advancedGestureCustomizationEnabled = boolean(values[\"advancedGestureCustomizationEnabled\"], fallback.advancedGestureCustomizationEnabled), dictionaryProcessTextEnabled = boolean(values[\"dictionaryProcessTextEnabled\"], fallback.dictionaryProcessTextEnabled), namedThemes = parseThemes(values[\"namedThemes\"]), activeThemeId = string(values[\"activeThemeId\"], fallback.activeThemeId, 80),",
    "advancedGestureCustomizationEnabled = boolean(values[\"advancedGestureCustomizationEnabled\"], fallback.advancedGestureCustomizationEnabled), centerTapAction = enumValue(values[\"centerTapAction\"], fallback.centerTapAction), doubleTapAction = enumValue(values[\"doubleTapAction\"], fallback.doubleTapAction), dictionaryProcessTextEnabled = boolean(values[\"dictionaryProcessTextEnabled\"], fallback.dictionaryProcessTextEnabled), namedThemes = parseThemes(values[\"namedThemes\"]), activeThemeId = string(values[\"activeThemeId\"], fallback.activeThemeId, 80),",
    "gesture backup import")
text = replace_once(text,
    "advancedGestureCustomizationEnabled = value.advancedGestureCustomizationEnabled, dictionaryProcessTextEnabled = value.dictionaryProcessTextEnabled, namedThemes = value.namedThemesList.map { it.toModel() }.take(MAX_THEMES), activeThemeId = value.activeThemeId,",
    "advancedGestureCustomizationEnabled = value.advancedGestureCustomizationEnabled, centerTapAction = value.centerTapAction.toModel(), doubleTapAction = value.doubleTapAction.toModel(), dictionaryProcessTextEnabled = value.dictionaryProcessTextEnabled, namedThemes = value.namedThemesList.map { it.toModel() }.take(MAX_THEMES), activeThemeId = value.activeThemeId,",
    "gesture proto decode")
text = replace_once(text,
    ".setAdvancedGestureCustomizationEnabled(value.advancedGestureCustomizationEnabled).setDictionaryProcessTextEnabled(value.dictionaryProcessTextEnabled).addAllNamedThemes",
    ".setAdvancedGestureCustomizationEnabled(value.advancedGestureCustomizationEnabled).setCenterTapAction(value.centerTapAction.toProto()).setDoubleTapAction(value.doubleTapAction.toProto()).setDictionaryProcessTextEnabled(value.dictionaryProcessTextEnabled).addAllNamedThemes",
    "gesture proto encode")
text = replace_once(text,
    '"advancedGestureCustomizationEnabled" to value.advancedGestureCustomizationEnabled, "dictionaryProcessTextEnabled" to value.dictionaryProcessTextEnabled, "activeThemeId" to value.activeThemeId,',
    '"advancedGestureCustomizationEnabled" to value.advancedGestureCustomizationEnabled, "centerTapAction" to value.centerTapAction.name, "doubleTapAction" to value.doubleTapAction.name, "dictionaryProcessTextEnabled" to value.dictionaryProcessTextEnabled, "activeThemeId" to value.activeThemeId,',
    "gesture backup export")
text = replace_once(text,
    "private fun ReaderAutoPageModeProto.toModel() = ReaderAutoPageMode.entries.getOrElse(ordinal) { ReaderAutoPageMode.ADAPTIVE }",
    "private fun ReaderAutoPageModeProto.toModel() = ReaderAutoPageMode.entries.getOrElse(ordinal) { ReaderAutoPageMode.ADAPTIVE }\nprivate fun ReaderGestureAction.toProto() = ReaderGestureActionProto.values()[ordinal]\nprivate fun ReaderGestureActionProto.toModel() = ReaderGestureAction.entries.getOrElse(ordinal) { ReaderGestureAction.NONE }",
    "gesture proto converters")
prefs_path.write_text(text, encoding="utf-8")

settings_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderSettingsScreen.kt")
text = settings_path.read_text(encoding="utf-8")
old = "    SettingSwitch(stringResource(R.string.reader_advanced_gestures), s.advancedGestureCustomizationEnabled) { actions.onSettingsChanged(s.copy(advancedGestureCustomizationEnabled = it)) }\n    SettingSwitch(stringResource(R.string.reader_dictionary_actions), s.dictionaryProcessTextEnabled) { actions.onSettingsChanged(s.copy(dictionaryProcessTextEnabled = it)) }"
new = "    SettingSwitch(stringResource(R.string.reader_advanced_gestures), s.advancedGestureCustomizationEnabled) { actions.onSettingsChanged(s.copy(advancedGestureCustomizationEnabled = it)) }\n    if (s.advancedGestureCustomizationEnabled) {\n        Section(stringResource(R.string.reader_gesture_center_action)) {\n            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                items(ReaderGestureAction.entries) { value -> FilterChip(s.centerTapAction == value, { actions.onSettingsChanged(s.copy(centerTapAction = value)) }, label = { Text(gestureActionLabel(value)) }) }\n            }\n        }\n        Section(stringResource(R.string.reader_gesture_double_action)) {\n            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                items(ReaderGestureAction.entries) { value -> FilterChip(s.doubleTapAction == value, { actions.onSettingsChanged(s.copy(doubleTapAction = value)) }, label = { Text(gestureActionLabel(value)) }) }\n            }\n        }\n    }\n    SettingSwitch(stringResource(R.string.reader_dictionary_actions), s.dictionaryProcessTextEnabled) { actions.onSettingsChanged(s.copy(dictionaryProcessTextEnabled = it)) }"
text = replace_once(text, old, new, "gesture settings UI")
text += "\n@Composable private fun gestureActionLabel(value: ReaderGestureAction): String = stringResource(when (value) { ReaderGestureAction.CONTROLS -> R.string.reader_gesture_controls; ReaderGestureAction.BOOKMARK -> R.string.reader_gesture_bookmark; ReaderGestureAction.NEXT -> R.string.reader_gesture_next; ReaderGestureAction.PREVIOUS -> R.string.reader_gesture_previous; ReaderGestureAction.NONE -> R.string.reader_gesture_none })\n"
settings_path.write_text(text, encoding="utf-8")

screen_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreenV3.kt")
text = screen_path.read_text(encoding="utf-8")
old = "                else -> {\n                    val tapAt = last.uptimeMillis\n                    if (settings.doubleTapBookmarkEnabled && lastCenterTapAt > 0L && tapAt - lastCenterTapAt in 40L..320L) {\n                        lastCenterTapAt = 0L; onBookmark()\n                    } else {\n                        lastCenterTapAt = tapAt; onToggleControls()\n                    }\n                }"
new = "                else -> {\n                    fun dispatch(action: ReaderGestureAction) {\n                        when (action) {\n                            ReaderGestureAction.CONTROLS -> onToggleControls()\n                            ReaderGestureAction.BOOKMARK -> onBookmark()\n                            ReaderGestureAction.NEXT -> onNext()\n                            ReaderGestureAction.PREVIOUS -> onPrevious()\n                            ReaderGestureAction.NONE -> Unit\n                        }\n                    }\n                    val centerAction = if (settings.advancedGestureCustomizationEnabled) settings.centerTapAction else ReaderGestureAction.CONTROLS\n                    val doubleAction = if (settings.advancedGestureCustomizationEnabled) settings.doubleTapAction else if (settings.doubleTapBookmarkEnabled) ReaderGestureAction.BOOKMARK else ReaderGestureAction.NONE\n                    val tapAt = last.uptimeMillis\n                    if (doubleAction != ReaderGestureAction.NONE && lastCenterTapAt > 0L && tapAt - lastCenterTapAt in 40L..320L) {\n                        lastCenterTapAt = 0L\n                        dispatch(doubleAction)\n                    } else {\n                        lastCenterTapAt = tapAt\n                        dispatch(centerAction)\n                    }\n                }"
text = replace_once(text, old, new, "gesture runtime dispatch")
screen_path.write_text(text, encoding="utf-8")

print("Reader V3 advanced gesture actions wired")
