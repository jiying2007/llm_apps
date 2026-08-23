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
        if (text.length > MAX_BACKUP_CHARS) throw IllegalArgumentException("备份文件过大")
        val root = JSONObject(text)
        if (root.optInt("schema") != 1 || root.optString("type") != "jingdu-local-user-backup") {
            throw IllegalArgumentException("不是受支持的净读备份文件")
        }

        // Parse and validate every payload first. Nothing is persisted until both
        // settings and global rules have passed validation.
        val settings = parseSettings(root.optJSONObject("settings")
            ?: throw IllegalArgumentException("备份缺少阅读设置"))
        val ruleRoot = JSONObject()
            .put("schema", 1)
            .put("type", "jingdu-global-clean-rules")
            .put("rules", root.optJSONArray("globalRules") ?: JSONArray())
        val rules = ruleLibrary.parseExportJson(ruleRoot.toString(), allowEmpty = true)

        readerPreferences.save(settings)
        ruleLibrary.save(rules)
        return Result(settings, rules)
    }

    private fun parseSettings(value: JSONObject): ReaderSettings {
        val required = listOf(
            "palette", "typeface", "fontSizeSp", "lineHeightMultiplier",
            "horizontalPaddingDp", "ttsRate", "ttsPitch", "ttsVoiceName", "autoPageDelayMs",
        )
        if (required.any { !value.has(it) || value.isNull(it) }) {
            throw IllegalArgumentException("备份阅读设置不完整")
        }

        val palette = runCatching { ReaderPalette.valueOf(value.getString("palette")) }
            .getOrElse { throw IllegalArgumentException("备份页面色调无效") }
        val typeface = runCatching { ReaderTypeface.valueOf(value.getString("typeface")) }
            .getOrElse { throw IllegalArgumentException("备份字体设置无效") }
        val fontSize = finiteFloat(value, "fontSizeSp", 16f, 34f)
        val lineHeight = finiteFloat(value, "lineHeightMultiplier", 1.2f, 2.0f)
        val padding = finiteFloat(value, "horizontalPaddingDp", 12f, 48f)
        val rate = finiteFloat(value, "ttsRate", 0.6f, 1.8f)
        val pitch = finiteFloat(value, "ttsPitch", 0.7f, 1.4f)
        val voice = value.getString("ttsVoiceName")
        if (voice.length > 256) throw IllegalArgumentException("备份朗读 voice 名称过长")
        val delay = value.getLong("autoPageDelayMs")
        if (delay !in 2500L..15000L) throw IllegalArgumentException("备份自动翻页间隔无效")

        return ReaderSettings(
            palette = palette,
            typeface = typeface,
            fontSizeSp = fontSize,
            lineHeightMultiplier = lineHeight,
            horizontalPaddingDp = padding,
            ttsRate = rate,
            ttsPitch = pitch,
            ttsVoiceName = voice,
            autoPageDelayMs = delay,
        )
    }

    private fun finiteFloat(value: JSONObject, key: String, min: Float, max: Float): Float {
        val number = value.getDouble(key)
        if (!number.isFinite() || number < min || number > max) {
            throw IllegalArgumentException("备份设置 $key 超出范围")
        }
        return number.toFloat()
    }

    data class Result(
        val settings: ReaderSettings,
        val globalRules: List<RepairRule>,
    )

    companion object {
        private const val MAX_BACKUP_CHARS = 1024 * 1024
    }
}
