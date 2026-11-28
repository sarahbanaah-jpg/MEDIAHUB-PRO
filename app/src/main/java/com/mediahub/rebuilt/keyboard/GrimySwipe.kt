package com.mediahub.rebuilt.keyboard

import android.graphics.PointF
import kotlin.math.sqrt

/**
 * Lightweight swipe-to-type path matcher.
 * Accumulates finger path over keys and picks the best dictionary word.
 */
class GrimySwipe {
    private val path = ArrayList<Pair<Char, PointF>>(32)

    fun clear() = path.clear()

    fun add(ch: Char, x: Float, y: Float) {
        val c = ch.lowercaseChar()
        if (c !in 'a'..'z') return
        if (path.isNotEmpty() && path.last().first == c) {
            path[path.lastIndex] = c to PointF(x, y)
            return
        }
        path += c to PointF(x, y)
    }

    fun candidate(): String? {
        if (path.size < 2) return null
        val keys = path.map { it.first }.joinToString("")
        // Exact sequence match against dictionary prefixes / words
        val dict = GrimySuggestions.swipeDictionary()
        val direct = dict.firstOrNull { it == keys }
        if (direct != null) return direct
        // Score: share start/end letter and contain path letters in order
        return dict
            .filter { it.length in (path.size - 2).coerceAtLeast(2)..(path.size + 3) }
            .filter { it.first() == path.first().first && it.last() == path.last().first }
            .map { w -> w to score(w, keys) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun score(word: String, keys: String): Int {
        var i = 0
        var hits = 0
        for (c in word) {
            val idx = keys.indexOf(c, i)
            if (idx < 0) return 0
            hits++
            i = idx + 1
        }
        return hits * 10 - kotlin.math.abs(word.length - keys.length)
    }

    fun pathLength(): Float {
        if (path.size < 2) return 0f
        var d = 0f
        for (i in 1 until path.size) {
            val a = path[i - 1].second
            val b = path[i].second
            val dx = b.x - a.x
            val dy = b.y - a.y
            d += sqrt(dx * dx + dy * dy)
        }
        return d
    }
}
