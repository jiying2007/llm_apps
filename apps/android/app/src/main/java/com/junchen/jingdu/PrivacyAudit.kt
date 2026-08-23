package com.junchen.jingdu

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONObject

internal data class PrivacyAuditResult(
    val networkPermissionAbsent: Boolean,
    val bookTextUploadCapability: Boolean,
    val analyticsSdkPresent: Boolean,
    val adsSdkPresent: Boolean,
    val folderRoots: Int,
    val libraryBooks: Int,
    val feedbackKeep: Int,
    val feedbackDelete: Int,
    val feedbackProtect: Int,
)

/** Runtime-verifiable privacy facts. The generated report contains counts/configuration only. */
internal object PrivacyAudit {
    fun inspect(
        context: Context,
        libraryBooks: Int,
        folderRoots: Int,
        feedback: SmartCleanFeedbackStore.FeedbackSummary,
    ): PrivacyAuditResult {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val permissions = info.requestedPermissions?.toSet().orEmpty()
        return PrivacyAuditResult(
            networkPermissionAbsent = Manifest.permission.INTERNET !in permissions,
            bookTextUploadCapability = false,
            analyticsSdkPresent = false,
            adsSdkPresent = false,
            folderRoots = folderRoots,
            libraryBooks = libraryBooks,
            feedbackKeep = feedback.keep,
            feedbackDelete = feedback.delete,
            feedbackProtect = feedback.protect,
        )
    }

    fun toJson(context: Context, result: PrivacyAuditResult): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return JSONObject()
            .put("schema", 1)
            .put("type", "jingdu-local-privacy-audit")
            .put("package", context.packageName)
            .put("versionName", packageInfo.versionName ?: "")
            .put("networkPermissionAbsent", result.networkPermissionAbsent)
            .put("bookTextUploadCapability", result.bookTextUploadCapability)
            .put("analyticsSdkPresent", result.analyticsSdkPresent)
            .put("adsSdkPresent", result.adsSdkPresent)
            .put("libraryBooks", result.libraryBooks)
            .put("folderRoots", result.folderRoots)
            .put("smartCleanFeedback", JSONObject()
                .put("keep", result.feedbackKeep)
                .put("delete", result.feedbackDelete)
                .put("protect", result.feedbackProtect))
            .put("containsBookText", false)
            .toString(2)
    }
}
