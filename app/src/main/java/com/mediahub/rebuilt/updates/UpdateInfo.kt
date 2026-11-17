package com.mediahub.rebuilt.updates

data class UpdateInfo(
    val version: String,
    val versionCode: Int? = null,
    val releaseUrl: String,
    val apkUrl: String?,
    val releaseNotes: String = "",
    val apkSizeBytes: Long = 0L,
    val publishedAt: String = "",
    val mandatory: Boolean = false
) {
    val sizeLabel: String
        get() = when {
            apkSizeBytes <= 0L -> "Unknown size"
            apkSizeBytes < 1024 * 1024 -> "${apkSizeBytes / 1024} KB"
            else -> String.format("%.1f MB", apkSizeBytes / (1024.0 * 1024.0))
        }
}
