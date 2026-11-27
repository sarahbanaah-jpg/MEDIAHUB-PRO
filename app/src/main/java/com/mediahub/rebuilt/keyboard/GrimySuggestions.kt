package com.mediahub.rebuilt.keyboard

/** Offline word suggestions for GRIMY BOARD. */
object GrimySuggestions {
    private val words = listOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
        "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
        "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
        "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
        "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
        "when", "make", "can", "like", "time", "no", "just", "him", "know", "take",
        "people", "into", "year", "your", "good", "some", "could", "them", "see", "other",
        "than", "then", "now", "look", "only", "come", "its", "over", "think", "also",
        "back", "after", "use", "two", "how", "our", "work", "first", "well", "way",
        "even", "new", "want", "because", "any", "these", "give", "day", "most", "us",
        "hello", "thanks", "please", "sorry", "yes", "okay", "love", "music", "video",
        "photo", "phone", "today", "tomorrow", "night", "morning", "friend", "family",
        "happy", "great", "awesome", "beautiful", "kenya", "nairobi", "eldoret",
        "swahili", "habari", "asante", "karibu", "sasa", "nzuri", "pole", "safari",
        "grimy", "mediahub", "update", "download", "install", "message", "chat",
        "play", "pause", "next", "song", "album", "artist", "listen", "watch"
    )

    private val nextWords = mapOf(
        "i" to listOf("am", "will", "love", "want", "need", "think"),
        "you" to listOf("are", "can", "will", "should", "know"),
        "thank" to listOf("you"),
        "thanks" to listOf("a", "for"),
        "good" to listOf("morning", "night", "luck", "job"),
        "how" to listOf("are", "is", "much", "to"),
        "what" to listOf("is", "are", "do", "time"),
        "mediahub" to listOf("pro", "update"),
        "grimy" to listOf("board", "mode", "emoji")
    )

    fun suggest(prefix: String, previous: String? = null): List<String> {
        val p = prefix.lowercase().trim()
        if (p.isEmpty()) {
            val prev = previous?.lowercase()?.trim().orEmpty()
            return nextWords[prev]?.take(3) ?: listOf("the", "I", "you")
        }
        return words.filter { it.startsWith(p) && it != p }.take(3)
            .ifEmpty { words.filter { it.contains(p) }.take(3) }
    }

    fun correct(word: String): String? {
        val w = word.lowercase()
        if (w.length < 3) return null
        if (words.contains(w)) return null
        return words.minByOrNull { levenshtein(w, it) }
            ?.takeIf { levenshtein(w, it) in 1..2 }
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
            )
        }
        return dp[a.length][b.length]
    }
}
