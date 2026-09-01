package com.luke.workouttracker.data.library

/**
 * Names matching [query], best matches first, capped at [limit].
 *
 * Every whitespace-separated token in the query must appear somewhere in the
 * name (case-insensitive), so "lat pull" finds "Neutral Grip Lat Pulldown".
 * A blank query returns everything up to [limit].
 */
fun filterExercises(all: List<String>, query: String, limit: Int = 8): List<String> {
    val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return all.take(limit)

    return all
        .mapNotNull { name ->
            val lower = name.lowercase()
            if (tokens.all { lower.contains(it) }) name to rank(lower, tokens) else null
        }
        .sortedWith(compareBy({ it.second }, { it.first }))
        .map { it.first }
        .take(limit)
}

/** Lower is better: 0 = name starts with the query, 1 = token starts a word, 2 = mid-word only. */
private fun rank(lowerName: String, tokens: List<String>): Int {
    val joined = tokens.joinToString(" ")
    if (lowerName.startsWith(joined)) return 0
    val words = lowerName.split(Regex("\\s+"))
    return if (tokens.all { token -> words.any { it.startsWith(token) } }) 1 else 2
}

/**
 * Stock and custom names as one sorted list, deduplicated case-insensitively.
 * A custom entry duplicating a stock one wins, so the user's own spelling shows.
 */
fun mergeExerciseNames(stock: List<String>, custom: List<String>): List<String> {
    val byLowercase = LinkedHashMap<String, String>()
    stock.forEach { byLowercase[it.lowercase()] = it }
    custom.forEach { byLowercase[it.lowercase()] = it }
    return byLowercase.values.sortedBy { it.lowercase() }
}
