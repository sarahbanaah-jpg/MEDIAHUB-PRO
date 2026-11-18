package com.mediahub.rebuilt.media

import android.content.Context

/** Simple local favourites store for music track IDs. */
object Favourites {
    private const val PREFS = "mediahub_favourites"
    private const val KEY = "ids"

    fun all(context: Context): Set<String> {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())?.toSet().orEmpty()
    }

    fun isFavourite(context: Context, id: Long): Boolean = all(context).contains(id.toString())

    fun toggle(context: Context, id: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = all(context).toMutableSet()
        val key = id.toString()
        val nowFavourite = if (next.contains(key)) {
            next.remove(key)
            false
        } else {
            next.add(key)
            true
        }
        prefs.edit().putStringSet(KEY, next).apply()
        return nowFavourite
    }
}
