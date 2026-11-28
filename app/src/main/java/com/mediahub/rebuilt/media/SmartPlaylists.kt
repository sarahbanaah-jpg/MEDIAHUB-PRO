package com.mediahub.rebuilt.media

/**
 * Smart playlist builders from a local library list.
 * Pure helpers — UI can call these without network.
 */
object SmartPlaylists {

    fun recentlyAdded(items: List<MediaItem>, limit: Int = 50): List<MediaItem> =
        items.sortedByDescending { it.dateAdded }.take(limit)

    fun mostPlayed(items: List<MediaItem>, playCounts: Map<String, Int>, limit: Int = 50): List<MediaItem> =
        items.sortedByDescending { playCounts[it.id] ?: 0 }.take(limit)

    fun neverPlayed(items: List<MediaItem>, playCounts: Map<String, Int>, limit: Int = 50): List<MediaItem> =
        items.filter { (playCounts[it.id] ?: 0) == 0 }.take(limit)

    fun shortTracks(items: List<MediaItem>, maxMs: Long = 180_000L, limit: Int = 50): List<MediaItem> =
        items.filter { it.durationMs in 1 until maxMs }.take(limit)

    fun longTracks(items: List<MediaItem>, minMs: Long = 300_000L, limit: Int = 50): List<MediaItem> =
        items.filter { it.durationMs >= minMs }.take(limit)
}
