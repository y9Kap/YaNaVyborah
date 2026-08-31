package org.yanavybori.feature.observer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.yanavybori.core.model.EventSeverity
import org.yanavybori.core.model.JournalCategory
import org.yanavybori.core.model.JournalEvent
import org.yanavybori.core.model.ObservationSession

class JournalExportTest {
    private val session = ObservationSession(
        id = "session",
        electionPackId = "pack",
        precinctNumber = "12 / 3",
        precinctName = "Школа \"Север\"",
        startedAt = 1,
        currentVotingDay = "day-1",
        currentStage = "stage",
        observerFullName = "Иван Иванов",
        region = "Москва",
    )

    @Test
    fun csv_is_utf8_friendly_chronological_and_escapes_fields() {
        val later = event("=HYPERLINK(\"https://example.invalid\")", 2, "Строка; с \"кавычками\"")
        val earlier = event("earlier", 1, "Первая\nстрока")

        val csv = buildJournalCsv(session, mapOf("day-1" to "День 1"), listOf(later, earlier))

        assertTrue(csv.startsWith("\uFEFF\"Участок\""))
        assertTrue(csv.indexOf("earlier") < csv.indexOf("HYPERLINK"))
        assertTrue("\"Строка; с \"\"кавычками\"\"\"" in csv)
        assertTrue("\"Школа \"\"Север\"\"\"" in csv)
        assertTrue("\"'=HYPERLINK(\"\"https://example.invalid\"\")\"" in csv)
    }

    @Test
    fun file_name_drops_unsafe_characters() {
        val fileName = journalExportFileName(session, 0)
        assertTrue(fileName.startsWith("journal-precinct-12-3-"))
        assertTrue(fileName.endsWith(".csv"))
        assertFalse('/' in fileName)
        assertFalse(' ' in fileName)
    }

    private fun event(id: String, timestamp: Long, description: String) = JournalEvent(
        id = id,
        sessionId = session.id,
        votingDayId = "day-1",
        timestamp = timestamp,
        category = JournalCategory.NORMAL,
        severity = EventSeverity.INFO,
        title = id,
        description = description,
    )
}
