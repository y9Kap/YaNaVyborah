package org.yanavybori.core.common

import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.format.char

private val displayFormat = LocalDateTime.Format {
    day(); char('.'); monthNumber(); char('.'); year(); char(' ')
    hour(); char(':'); minute(); char(':'); second()
}
private val fileFormat = LocalDateTime.Format {
    year(); monthNumber(); day(); char('-'); hour(); minute()
}

fun formatLocalTimestamp(timestamp: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
    displayFormat.format(Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(timeZone))

fun formatFileTimestamp(timestamp: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
    fileFormat.format(Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(timeZone))
