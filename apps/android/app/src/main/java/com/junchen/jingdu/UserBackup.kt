package com.junchen.jingdu

import org.json.JSONArray
import org.json.JSONObject

internal class UserBackup(
    private val readerPreferences: ReaderPreferences,
    private val ruleLibrary: RuleLibrary,
) {
    fun exportJson(): String {
        val settings = JSONObject()
        readerPreferences.exportMap().forEach { (key, value) -> settings.put(key, value) }
        val ruleRoot = JSONObject(ruleLibrary.exportJson())
        return JSONObject()
            .put("schema", 1)
            .put("type", "jingdu-local-user-backup")
            .put("settings", settings)
            .put("globalRules", ruleRoot.optJSONArray("rules") ?: JSONArray())
            .toString(2)
    }

    fun importJson(text: String): Result {
        val root = JSONObject(text)
        if (root.optInt("schema") != 1 || root.optString("type") != "jingdu-local-user-backup") {
            throw IllegalArgumentException("不是受支持的净读备份文件")
        }
        val settingsJson = root.optJSONObject("settings") ?: JSONObject()
        val map = mutableMapOf<String, Any?>()
        settingsJson.keys().forEach { key -> map[key] = settingsJson.opt(key) }
        val settings = readerPreferences.importMap(map)

        val ruleRoot = JSONObject()
            .put("schema", 1)
            .put("type", "jingdu-global-clean-rules")
            .put("rules", root.optJSONArray("globalRules") ?: JSONArray())
        val rules = if (ruleRoot.getJSONArray("rules").length() == 0) {
            ruleLibrary.load()
        } else {
            ruleLibrary.importJson(ruleRoot.toString())
        }
        return Result(settings, rules)
    }

    data class Result(
        val settings: ReaderSettings,
        val globalRules: List<RepairRule>,
    )
}
