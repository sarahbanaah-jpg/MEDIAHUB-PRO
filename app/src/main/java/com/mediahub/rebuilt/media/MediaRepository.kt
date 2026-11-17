package com.mediahub.rebuilt.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Safe MediaStore access. Never throws SecurityException to the UI.
 */
object MediaRepository {

    suspend fun loadVideos(context: Context): List<MediaItem> = withContext(Dispatchers.IO) {
        query(context, MediaItem.Kind.VIDEO)
    }

    suspend fun loadMusic(context: Context): List<MediaItem> = withContext(Dispatchers.IO) {
        query(context, MediaItem.Kind.AUDIO)
    }

    suspend fun loadPhotos(context: Context): List<MediaItem> = withContext(Dispatchers.IO) {
        query(context, MediaItem.Kind.IMAGE)
    }

    private fun query(context: Context, kind: MediaItem.Kind): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        try {
            val collection = when (kind) {
                MediaItem.Kind.VIDEO -> if (Build.VERSION.SDK_INT >= 29)
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

                MediaItem.Kind.AUDIO -> if (Build.VERSION.SDK_INT >= 29)
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

                MediaItem.Kind.IMAGE -> if (Build.VERSION.SDK_INT >= 29)
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val projection = when (kind) {
                MediaItem.Kind.VIDEO -> arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.MIME_TYPE,
                    MediaStore.Video.Media.DATE_ADDED
                )
                MediaItem.Kind.AUDIO -> arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.MIME_TYPE,
                    MediaStore.Audio.Media.DATE_ADDED
                )
                MediaItem.Kind.IMAGE -> arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.MIME_TYPE,
                    MediaStore.Images.Media.DATE_ADDED
                )
            }

            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val uri = ContentUris.withAppendedId(collection, id)

                    when (kind) {
                        MediaItem.Kind.VIDEO -> {
                            val title = cursor.getString(1) ?: "Untitled"
                            val duration = cursor.getLong(2)
                            val size = cursor.getLong(3)
                            val mime = cursor.getString(4)
                            val date = cursor.getLong(5) * 1000
                            result += MediaItem(
                                id = id,
                                title = title,
                                uri = uri,
                                durationMs = duration,
                                sizeBytes = size,
                                mimeType = mime,
                                dateAdded = date,
                                kind = MediaItem.Kind.VIDEO
                            )
                        }
                        MediaItem.Kind.AUDIO -> {
                            val title = cursor.getString(1) ?: "Untitled"
                            val artist = cursor.getString(2) ?: ""
                            val album = cursor.getString(3) ?: ""
                            val duration = cursor.getLong(4)
                            val size = cursor.getLong(5)
                            val mime = cursor.getString(6)
                            val date = cursor.getLong(7) * 1000
                            result += MediaItem(
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                uri = uri,
                                durationMs = duration,
                                sizeBytes = size,
                                mimeType = mime,
                                dateAdded = date,
                                kind = MediaItem.Kind.AUDIO
                            )
                        }
                        MediaItem.Kind.IMAGE -> {
                            val title = cursor.getString(1) ?: "Untitled"
                            val size = cursor.getLong(2)
                            val mime = cursor.getString(3)
                            val date = cursor.getLong(4) * 1000
                            result += MediaItem(
                                id = id,
                                title = title,
                                uri = uri,
                                sizeBytes = size,
                                mimeType = mime,
                                dateAdded = date,
                                kind = MediaItem.Kind.IMAGE
                            )
                        }
                    }
                }
            }
        } catch (se: SecurityException) {
            // Permission missing – return empty instead of crashing
        } catch (_: Exception) {
        }
        return result
    }

    private typealias MediaItem.Kind = MediaItem.MediaItem.Kind
}
