package org.yanavybori.feature.observer

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.yanavybori.core.model.EventSeverity
import org.yanavybori.core.model.JournalEvent
import org.yanavybori.core.model.ObservationSession

private val dangerousCsvPrefixes = setOf('=', '+', '-', '@', '\r', '\n')

internal fun buildJournalCsv(
    session: ObservationSession,
    dayTitles: Map<String, String>,
    events: List<JournalEvent>,
): String = buildString {
    append('\uFEFF')
    appendCsvRow(
        listOf(
            "Участок",
            "Название участка",
            "Регион",
            "Наблюдатель",
            "День",
            "Дата и время",
            "ID записи",
            "Категория",
            "Важность",
            "Заголовок",
            "Описание",
            "Участники / примечания",
            "Количество медиафайлов",
            "Связанная ситуация",
            "Связанный пункт чек-листа",
            "Связанные жалобы",
            "Теги",
        ),
    )
    events.sortedBy(JournalEvent::timestamp).forEach { event ->
        appendCsvRow(
            listOf(
                session.precinctNumber,
                session.precinctName.orEmpty(),
                session.region,
                session.observerFullName,
                dayTitles[event.votingDayId] ?: event.votingDayId,
                formatTimestamp(event.timestamp),
                event.id,
                event.category.label(),
                event.severity.exportLabel(),
                event.title,
                event.description,
                event.participantNotes,
                event.mediaIds.size.toString(),
                event.relatedSituationId.orEmpty(),
                event.relatedChecklistItemId.orEmpty(),
                event.relatedComplaintIds.joinToString(", "),
                event.tags.joinToString(", "),
            ),
        )
    }
}

internal fun journalExportFileName(session: ObservationSession, now: Long): String {
    val precinct = session.precinctNumber
        .replace(Regex("[^\\p{L}\\p{N}._-]+"), "-")
        .trim('-')
        .ifBlank { "unknown" }
    val timestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date(now))
    return "journal-precinct-$precinct-$timestamp.csv"
}

private fun StringBuilder.appendCsvRow(values: List<String>) {
    append(values.joinToString(";") { value ->
        val firstMeaningfulCharacter = value.firstOrNull { it != ' ' && it != '\t' }
        val safeValue = if (
            firstMeaningfulCharacter != null && firstMeaningfulCharacter in dangerousCsvPrefixes
        ) {
            "'$value"
        } else {
            value
        }
        "\"${safeValue.replace("\"", "\"\"")}\""
    })
    append("\r\n")
}

private fun EventSeverity.exportLabel(): String = when (this) {
    EventSeverity.INFO -> "Информация"
    EventSeverity.ATTENTION -> "Требует внимания"
    EventSeverity.IMPORTANT -> "Важное"
    EventSeverity.URGENT -> "Срочное"
}
