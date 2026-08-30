package com.junchen.jingdu

import org.json.JSONArray
import org.json.JSONObject

/** First-launch backup schema. Earlier reader schemas are intentionally unsupported pre-launch. */
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
            .put("reader", "v3")
            .put("settings", settings)
            .put("annotations", annotationStore.exportJson())
            .put("globalRules", ruleRoot.optJSONArray("rules") ?: JSONArray())
            .toString(2)
    }

    fun importJson(text: String): Result {
        if (text.length > MAX_BACKUP_CHARS) throw IllegalArgumentException("backup too large")
        val root = JSONObject(text)
        if (root.optInt("schema") != SCHEMA || root.optString("type") != "jingdu-local-user-backup" || root.optString("reader") != "v3") {
            throw IllegalArgumentException("not a Reader V3 backup")
        }
        val settingsObject = root.optJSONObject("settings") ?: throw IllegalArgumentException("backup missing reader settings")
        val settingsMap = linkedMapOf<String, Any?>()
        settingsObject.keys().forEach { key -> settingsMap[key] = settingsObject.opt(key) }
        val annotations = root.optJSONArray("annotations") ?: JSONArray()
        if (annotations.length() > 20_000) throw IllegalArgumentException("too many annotations")
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
        const val SCHEMA = 3
        const val MAX_BACKUP_CHARS = 4 * 1024 * 1024
    }
}
