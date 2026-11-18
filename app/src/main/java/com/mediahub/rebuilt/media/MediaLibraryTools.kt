package com.mediahub.rebuilt.media

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Read-only local library metrics and search; never modifies user media. */
data class StorageSnapshot(
    val totalBytes: Long, val freeBytes: Long,
    val audioBytes: Long, val videoBytes: Long, val imageBytes: Long,
    val audioCount: Int, val videoCount: Int, val imageCount: Int
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
}

data class MediaSearchResult(
    val id: Long, val displayName: String, val mimeType: String?,
    val sizeBytes: Long, val dateAddedSeconds: Long, val mediaType: Int,
    val contentUri: android.net.Uri
)

object MediaLibraryTools {
    private val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.Files.FileColumns.DATE_ADDED,
        MediaStore.Files.FileColumns.MEDIA_TYPE
    )

    suspend fun storageSnapshot(context: Context): StorageSnapshot = withContext(Dispatchers.IO) {
        val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
        var audioBytes = 0L; var videoBytes = 0L; var imageBytes = 0L
        var audioCount = 0; var videoCount = 0; var imageCount = 0
        val uri = MediaStore.Files.getContentUri("external")
        val typeColumn = MediaStore.Files.FileColumns.MEDIA_TYPE
        val selection = "$typeColumn IN (?, ?, ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()
        )
        context.contentResolver.query(
            uri, arrayOf(MediaStore.Files.FileColumns.SIZE, typeColumn), selection, args, null
        )?.use { cursor ->
            val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
            val mediaCol = cursor.getColumnIndex(typeColumn)
            while (cursor.moveToNext()) {
                val size = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                when (if (mediaCol >= 0) cursor.getInt(mediaCol) else -1) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> { audioBytes += size; audioCount++ }
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> { videoBytes += size; videoCount++ }
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> { imageBytes += size; imageCount++ }
                }
            }
        }
        StorageSnapshot(stat.totalBytes, stat.availableBytes, audioBytes, videoBytes, imageBytes,
            audioCount, videoCount, imageCount)
    }

    suspend fun search(context: Context, query: String, limit: Int = 100): List<MediaSearchResult> =
        withContext(Dispatchers.IO) {
            val term = query.trim()
            if (term.isEmpty()) return@withContext emptyList()
            val uri = MediaStore.Files.getContentUri("external")
            val mediaType = MediaStore.Files.FileColumns.MEDIA_TYPE
            val selection = "$mediaType IN (?, ?, ?) AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            val args = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                "%${term.replace("%", "\\%").replace("_", "\\_")}%"
            )
            val found = ArrayList<MediaSearchResult>()
            context.contentResolver.query(uri, projection, selection, args,
                "${MediaStore.Files.FileColumns.DATE_ADDED} DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
                val typeCol = cursor.getColumnIndexOrThrow(mediaType)
                while (cursor.moveToNext() && found.size < limit.coerceIn(1, 500)) {
                    val id = cursor.getLong(idCol)
                    val type = cursor.getInt(typeCol)
                    val collection = when (type) {
                        MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        else -> continue
                    }
                    found += MediaSearchResult(id, cursor.getString(nameCol) ?: "Untitled",
                        if (mimeCol >= 0) cursor.getString(mimeCol) else null,
                        if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L,
                        if (dateCol >= 0) cursor.getLong(dateCol) else 0L, type,
                        android.content.ContentUris.withAppendedId(collection, id))
                }
            }
            found
        }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble(); var index = -1
        do { value /= 1024.0; index++ } while (value >= 1024.0 && index < units.lastIndex)
        return String.format(Locale.getDefault(), "%.1f %s", value, units[index])
    }
}
