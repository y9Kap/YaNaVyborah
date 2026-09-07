package org.yanavybori.core.search

class SearchQueryNormalizer {
    fun normalize(query: String): List<String> = query
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .split(Regex("\\s+"))
        .filter { it.length >= 2 }
        .distinct()

    fun matches(query: String, text: String, tags: List<String>): Boolean {
        val tokens = normalize(query)
        if (tokens.isEmpty()) return false
        val haystack = normalize(text + " " + tags.joinToString(" ")).toSet()
        return tokens.all { token -> haystack.any { candidate -> candidate.startsWith(token) } }
    }
}
