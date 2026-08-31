package org.yanavybori.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryNormalizerTest {
    @Test
    fun normalizes_russian_query_and_matches_pack_tags() {
        val normalizer = SearchQueryNormalizer()
        assertEquals(listOf("меня", "выгоняют"), normalizer.normalize("  Меня, ВЫГОНЯЮТ! "))
        assertTrue(normalizer.matches("выгоняют", "Удаление наблюдателя", listOf("выгоняют", "полиция")))
    }
}
