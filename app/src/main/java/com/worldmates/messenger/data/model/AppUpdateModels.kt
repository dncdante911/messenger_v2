package com.worldmates.messenger.data.model

import com.google.gson.annotations.SerializedName

data class AppUpdateResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: AppUpdateInfo? = null,
    @SerializedName("message") val message: String? = null
)

data class AppUpdateInfo(
    @SerializedName("latest_version") val latestVersion: String,
    @SerializedName("version_code") val versionCode: Int,
    @SerializedName("apk_url") val apkUrl: String,
    @SerializedName("changelog") val changelog: List<String> = emptyList(),
    @SerializedName("is_mandatory") val isMandatory: Boolean = false,
    @SerializedName("published_at") val publishedAt: String? = null
)
