package com.mediahub.rebuilt.media

import android.content.ContentUris
import android.content.Context
import android.provider.DocumentsContract
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.mediahub.rebuilt.diagnostics.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans ALL external volumes (internal + SD card) for media.
 */
object MediaRepository {

    suspend fun loadVideos(context: Context): List<MediaItem> = withContext(Dispatchers.IO) {
        queryAllVolumes(context, MediaItem.Kind.VIDEO)
    }

    suspend fun loadMusic(context: Context): List<MediaItem> = withContext(Dispatchers.IO) {
        val indexed = queryAllVolumes(context, MediaItem.Kind.AUDIO)
        val selectedFolders = querySavedMusicFolders(context)
        (indexed + selectedFolders).distinctBy { it.uri.toString() }.sortedByDescending { it.dateAdded }
    }

    /** Persist a user-selected folder so audio missed by MediaStore can still be listed. */
    fun rememberMusicFolder(context: Context, treeUri: Uri) {
        val prefs = context.getSharedPreferences("mediahub_music_folders", Context.MODE_PRIVATE)
        val existing = prefs.getStringSet("tree_uris", emptySet()).orEmpty().toMutableSet()
        existing.add(treeUri.toString())
        prefs.edit().putStringSet("tree_uris", existing).apply()
    }

    private fun querySavedMusicFolders(context: Context): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        val folders = context.getSharedPreferences("mediahub_music_folders", Context.MODE_PRIVATE).getStringSet("tree_uris", emptySet()).orEmpty()
        folders.forEach { value ->
            try {
                val tree = Uri.parse(value)
                scanDocumentFolder(context, tree, DocumentsContract.getTreeDocumentId(tree), result, 0)
            } catch (error: Exception) {
                Diagnostics.recordIssue(context, "Music folder scan " + value, error)
            }
        }
        return result
    }

    private fun scanDocumentFolder(context: Context, tree: Uri, documentId: String, output: MutableList<MediaItem>, depth: Int) {
        if (depth > 20 || output.size >= 10000) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        context.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val dateCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (cursor.moveToNext() && output.size < 10000) {
                val id = cursor.getString(idCol) ?: continue
                val name = cursor.getString(nameCol) ?: "Audio file"
                val mime = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty() else ""
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    scanDocumentFolder(context, tree, id, output, depth + 1)
                } else if (isAudioDocument(name, mime)) {
                    val title = name.substringBeforeLast(".", name).ifBlank { name }
                    output += MediaItem(id = documentUri.toString().hashCode().toLong(), title = title, uri = documentUri, durationMs = 0L,
                        sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L,
                        mimeType = mime.ifBlank { null }, dateAdded = if (dateCol >= 0) cursor.getLong(dateCol) else 0L, kind = MediaItem.Kind.AUDIO)
                }
            }
        }
    }

    private fun isAudioDocument(name: String, mime: String): Boolean {
        if (mime.startsWith("audio/", ignoreCase = true)) return true
        return name.substringAfterLast(".", "").lowercase() in setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma", "amr", "aiff", "mid", "midi", "3gp")
    }

    suspend fun loadPhotos(context: Context): List<MediaItem> = withContext(Dispatchers.IO) {
        queryAllVolumes(context, MediaItem.Kind.IMAGE)
    }

    private fun volumeNames(context: Context): List<String> {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val names = MediaStore.getExternalVolumeNames(context).toList()
                if (names.isNotEmpty()) names else listOf(MediaStore.VOLUME_EXTERNAL)
            } else {
                listOf("external")
            }
        } catch (_: Exception) {
            listOf(if (Build.VERSION.SDK_INT >= 29) MediaStore.VOLUME_EXTERNAL else "external")
        }
    }

    private fun queryAllVolumes(context: Context, kind: MediaItem.Kind): List<MediaItem> {
        val seen = LinkedHashMap<String, MediaItem>()
        for (volume in volumeNames(context)) {
            for (item in queryVolume(context, kind, volume)) {
                val key = item.uri.toString()
                if (!seen.containsKey(key)) seen[key] = item
            }
        }
        // Also query legacy EXTERNAL_CONTENT_URI as fallback (covers some OEM SD paths)
        if (Build.VERSION.SDK_INT >= 29) {
            for (item in queryLegacy(context, kind)) {
                val key = "${item.title}|${item.sizeBytes}|${item.durationMs}|${item.artist}"
                if (!seen.containsKey(key)) seen[key] = item
            }
        }
        return seen.values.sortedByDescending { it.dateAdded }
    }

    private fun collectionUri(kind: MediaItem.Kind, volume: String): Uri {
        return when (kind) {
            MediaItem.Kind.VIDEO -> if (Build.VERSION.SDK_INT >= 29)
                MediaStore.Video.Media.getContentUri(volume)
            else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

            MediaItem.Kind.AUDIO -> if (Build.VERSION.SDK_INT >= 29)
                MediaStore.Audio.Media.getContentUri(volume)
            else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

            MediaItem.Kind.IMAGE -> if (Build.VERSION.SDK_INT >= 29)
                MediaStore.Images.Media.getContentUri(volume)
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
    }

    private fun queryLegacy(context: Context, kind: MediaItem.Kind): List<MediaItem> {
        val uri = when (kind) {
            MediaItem.Kind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            MediaItem.Kind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            MediaItem.Kind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        return queryUri(context, kind, uri)
    }

    private fun queryVolume(context: Context, kind: MediaItem.Kind, volume: String): List<MediaItem> {
        return try {
            queryUri(context, kind, collectionUri(kind, volume))
        } catch (e: Exception) {
            Diagnostics.recordIssue(context, "MediaRepository.$kind volume=$volume", e)
            emptyList()
        }
    }

    private fun queryUri(context: Context, kind: MediaItem.Kind, collection: Uri): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        try {
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
                    MediaStore.Audio.Media.DATE_ADDED,
                    MediaStore.Audio.Media.ALBUM_ID
                )
                MediaItem.Kind.IMAGE -> arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.MIME_TYPE,
                    MediaStore.Images.Media.DATE_ADDED
                )
            }

            // No IS_MUSIC filter — include all audio on device + SD (mp3, m4a, flac, etc.)
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
                            result += MediaItem(
                                id = id,
                                title = cursor.getString(1) ?: "Untitled",
                                uri = uri,
                                durationMs = cursor.getLong(2),
                                sizeBytes = cursor.getLong(3),
                                mimeType = cursor.getString(4),
                                dateAdded = cursor.getLong(5) * 1000,
                                kind = MediaItem.Kind.VIDEO
                            )
                        }
                        MediaItem.Kind.AUDIO -> {
                            val mime = cursor.getString(6) ?: ""
                            // Keep short clips and formats whose duration is not indexed.
                            val duration = cursor.getLong(4)
                            val size = cursor.getLong(5)
                            result += MediaItem(
                                id = id,
                                title = cursor.getString(1) ?: "Untitled",
                                artist = cursor.getString(2) ?: "",
                                album = cursor.getString(3) ?: "",
                                uri = uri,
                                durationMs = duration,
                                sizeBytes = size,
                                mimeType = mime,
                                dateAdded = cursor.getLong(7) * 1000,
                                kind = MediaItem.Kind.AUDIO,
                                albumId = if (cursor.columnCount > 8) cursor.getLong(8) else 0L
                            )
                        }
                        MediaItem.Kind.IMAGE -> {
                            result += MediaItem(
                                id = id,
                                title = cursor.getString(1) ?: "Untitled",
                                uri = uri,
                                sizeBytes = cursor.getLong(2),
                                mimeType = cursor.getString(3),
                                dateAdded = cursor.getLong(4) * 1000,
                                kind = MediaItem.Kind.IMAGE
                            )
                        }
                    }
                }
            }
        } catch (se: SecurityException) {
            Diagnostics.recordIssue(context, "MediaRepository.$kind permission", se)
        } catch (error: Exception) {
            Diagnostics.recordIssue(context, "MediaRepository.$kind query", error)
        }
        return result
    }
}
