package org.yanavybori.feature.observer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

private val squareBracketArea = Regex("\\[[^\\[\\]\\n]+]")
private val nonEditableBracketLabels = Regex(
    "^(?:\\d+(?:\\.\\.\\.)?|пятница|суббота|воскресенье|пятница и суббота|" +
        "суббота и воскресенье)$",
    RegexOption.IGNORE_CASE,
)

internal fun templatePlaceholderRanges(text: String): List<IntRange> =
    squareBracketArea.findAll(text)
        .filterNot { match ->
            val innerText = match.value.substring(1, match.value.lastIndex).trim()
            nonEditableBracketLabels.matches(innerText)
        }
        .map { it.range }
        .toList()

internal fun highlightTemplatePlaceholders(text: String, color: Color): AnnotatedString {
    val ranges = templatePlaceholderRanges(text)
    if (ranges.isEmpty()) return AnnotatedString(text)

    return AnnotatedString(
        text = text,
        spanStyles = ranges.map { range ->
            AnnotatedString.Range(
                item = SpanStyle(color = color, fontWeight = FontWeight.SemiBold),
                start = range.first,
                end = range.last + 1,
            )
        },
    )
}

internal class TemplatePlaceholderVisualTransformation(
    private val color: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = TransformedText(
        text = highlightTemplatePlaceholders(text.text, color),
        offsetMapping = OffsetMapping.Identity,
    )
}
