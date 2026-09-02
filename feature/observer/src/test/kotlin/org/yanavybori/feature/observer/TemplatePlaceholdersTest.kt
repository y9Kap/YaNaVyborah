package org.yanavybori.feature.observer

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplatePlaceholdersTest {
    @Test
    fun finds_editable_square_bracket_areas() {
        val text = "От [ФИО] в [орган внутренних дел] — [точное время]"

        assertEquals(
            listOf("[ФИО]", "[орган внутренних дел]", "[точное время]"),
            templatePlaceholderRanges(text).map { text.substring(it) },
        )
    }

    @Test
    fun ignores_day_labels_and_protocol_line_numbers() {
        val text = "[ПЯТНИЦА] [1] + [12] = [значение из акта]"

        assertEquals(
            listOf("[значение из акта]"),
            templatePlaceholderRanges(text).map { text.substring(it) },
        )
    }

    @Test
    fun ignores_unclosed_brackets() {
        assertEquals(emptyList<IntRange>(), templatePlaceholderRanges("Укажите [ФИО"))
    }
}
