package com.junchen.jingdu

import org.json.JSONArray
import org.json.JSONObject

/** First-launch backup schema. Schema 1 is intentionally unsupported because no store release used it. */
internal class UserBackup(
    private val readerPreferences: ReaderPreferences,
    private val ruleLibrary: RuleLibrary,
    private val annotationStore: ReaderAnnotationStore,
) {
    fun exportJson(): String {
        val settings = JSONObject()
        readerPreferences.exportMap().forEach { (key, value) -> settings.put(key, value) }
        val ruleRoot = JSONObject(ruleLibrary.exportJson())
        return JSONObject()
            .put("schema", SCHEMA)
            .put("type", "jingdu-local-user-backup")
            .put("settings", settings)
            .put("annotations", annotationStore.exportJson())
            .put("globalRules", ruleRoot.optJSONArray("rules") ?: JSONArray())
            .toString(2)
    }

    fun importJson(text: String): Result {
        if (text.length > MAX_BACKUP_CHARS) throw IllegalArgumentException("备份文件过大")
        val root = JSONObject(text)
        if (root.optInt("schema") != SCHEMA || root.optString("type") != "jingdu-local-user-backup") {
            throw IllegalArgumentException("不是 Reader V2 备份文件")
        }
        val settingsObject = root.optJSONObject("settings") ?: throw IllegalArgumentException("备份缺少阅读设置")
        val settingsMap = linkedMapOf<String, Any?>()
        settingsObject.keys().forEach { key -> settingsMap[key] = settingsObject.opt(key) }
        val annotations = root.optJSONArray("annotations") ?: JSONArray()
        if (annotations.length() > 20_000) throw IllegalArgumentException("备份标注过多")
        val ruleRoot = JSONObject()
            .put("schema", 1)
            .put("type", "jingdu-global-clean-rules")
            .put("rules", root.optJSONArray("globalRules") ?: JSONArray())
        val rules = ruleLibrary.parseExportJson(ruleRoot.toString(), allowEmpty = true)

        val settings = readerPreferences.importMap(settingsMap)
        annotationStore.importJson(annotations)
        ruleLibrary.save(rules)
        return Result(settings, rules)
    }

    data class Result(val settings: ReaderSettings, val globalRules: List<RepairRule>)

    private companion object {
        const val SCHEMA = 2
        const val MAX_BACKUP_CHARS = 4 * 1024 * 1024
    }
}
