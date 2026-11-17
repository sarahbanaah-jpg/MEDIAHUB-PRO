package com.mediahub.rebuilt.media

import android.net.Uri

data class MediaItem(
    val id: Long,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val uri: Uri,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val mimeType: String? = null,
    val dateAdded: Long = 0L,
    val kind: Kind,
    val albumId: Long = 0L
) {
    enum class Kind { AUDIO, VIDEO, IMAGE }
}
