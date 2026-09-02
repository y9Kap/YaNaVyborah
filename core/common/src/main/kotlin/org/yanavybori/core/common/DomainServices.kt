package org.yanavybori.core.common

import java.util.UUID
import org.yanavybori.core.model.ChecklistStatus
import org.yanavybori.core.model.Complaint
import org.yanavybori.core.model.ComplaintStatus
import org.yanavybori.core.model.EventSeverity
import org.yanavybori.core.model.JournalCategory
import org.yanavybori.core.model.JournalEvent
import org.yanavybori.core.model.ReconciliationDefinition
import org.yanavybori.core.model.ReconciliationResult
import org.yanavybori.core.model.ReconciliationRule
import org.yanavybori.core.model.ReconciliationRuleType
import org.yanavybori.core.model.ReconciliationStatus

fun interface Clock { fun now(): Long }

fun interface IdGenerator { fun newId(): String }

object SystemClock : Clock { override fun now(): Long = System.currentTimeMillis() }

object UuidGenerator : IdGenerator { override fun newId(): String = UUID.randomUUID().toString() }

object ChecklistStatePolicy {
    fun canTransition(from: ChecklistStatus, to: ChecklistStatus): Boolean = true

    fun completedCount(states: Iterable<ChecklistStatus>): Int =
        states.count { it != ChecklistStatus.NOT_CHECKED }
}

object CounterPolicy {
    fun increment(currentValue: Long, delta: Int = 1): Long {
        require(delta > 0) { "Приращение должно быть положительным" }
        return Math.addExact(currentValue, delta.toLong())
    }

    fun decrement(currentValue: Long, delta: Int = 1): Long {
        require(delta > 0) { "Уменьшение должно быть положительным" }
        check(currentValue >= delta) { "Счётчик уже равен нулю" }
        return currentValue - delta
    }

    fun undo(currentValue: Long, lastDelta: Int?): Long =
        if (lastDelta == null) currentValue else (currentValue - lastDelta).coerceAtLeast(0)
}

object ComplaintStatusPolicy {
    private val allowed = mapOf(
        ComplaintStatus.DRAFT to setOf(ComplaintStatus.READY, ComplaintStatus.UNKNOWN),
        ComplaintStatus.READY to setOf(ComplaintStatus.DRAFT, ComplaintStatus.SUBMITTED, ComplaintStatus.UNKNOWN),
        ComplaintStatus.SUBMITTED to setOf(
            ComplaintStatus.ACCEPTED,
            ComplaintStatus.REJECTED,
            ComplaintStatus.UNKNOWN,
        ),
        ComplaintStatus.ACCEPTED to setOf(ComplaintStatus.UNKNOWN),
        ComplaintStatus.REJECTED to setOf(ComplaintStatus.UNKNOWN),
        ComplaintStatus.UNKNOWN to ComplaintStatus.entries.toSet(),
    )

    fun canTransition(from: ComplaintStatus, to: ComplaintStatus): Boolean =
        from == to || to in allowed.getValue(from)

    fun transition(complaint: Complaint, to: ComplaintStatus, at: Long): Complaint {
        require(canTransition(complaint.status, to)) {
            "Недопустимый переход статуса: ${complaint.status} -> $to"
        }
        return complaint.copy(
            status = to,
            submittedAt = if (to == ComplaintStatus.SUBMITTED) at else complaint.submittedAt,
        )
    }
}

class JournalEventFactory(
    private val clock: Clock = SystemClock,
    private val ids: IdGenerator = UuidGenerator,
) {
    fun quick(sessionId: String, votingDayId: String): JournalEvent = JournalEvent(
        id = ids.newId(),
        sessionId = sessionId,
        votingDayId = votingDayId,
        timestamp = clock.now(),
        category = JournalCategory.NORMAL,
        severity = EventSeverity.INFO,
        title = "Событие",
    )

    fun fromChecklist(
        sessionId: String,
        votingDayId: String,
        checklistItemId: String,
        title: String,
        status: ChecklistStatus,
    ): JournalEvent = JournalEvent(
        id = ids.newId(),
        sessionId = sessionId,
        votingDayId = votingDayId,
        timestamp = clock.now(),
        category = if (status == ChecklistStatus.PROBLEM) JournalCategory.PROBLEM else JournalCategory.CONTROL,
        severity = if (status == ChecklistStatus.PROBLEM) EventSeverity.ATTENTION else EventSeverity.INFO,
        title = title,
        description = "Состояние чек-листа: $status",
        relatedChecklistItemId = checklistItemId,
    )
}

class ReconciliationEngine {
    fun evaluate(
        definition: ReconciliationDefinition,
        rawValues: Map<String, String>,
        previousValues: Map<String, String> = emptyMap(),
    ): List<ReconciliationResult> {
        val fieldLabels = definition.fields.associate { it.id to it.label }
        return definition.rules.map { rule ->
            evaluateRule(rule, rawValues, previousValues, fieldLabels)
        }
    }

    private fun evaluateRule(
        rule: ReconciliationRule,
        rawValues: Map<String, String>,
        previousValues: Map<String, String>,
        fieldLabels: Map<String, String>,
    ): ReconciliationResult {
        val relevantIds = buildList {
            addAll(rule.inputIds)
            addAll(rule.comparisonInputIds)
            rule.targetInputId?.let(::add)
        }.distinct()
        val source = relevantIds.associateWith { rawValues[it].orEmpty() }
        val currentNumbers = rule.inputIds.map { rawValues[it]?.toLongOrNull() }
        val comparisonNumbers = rule.comparisonInputIds.map { rawValues[it]?.toLongOrNull() }
        fun label(id: String): String = fieldLabels[id] ?: "Поле «$id»"
        fun value(id: String): String = rawValues[id].orEmpty().ifBlank { "не заполнено" }
        fun labeledValue(id: String): String = "«${label(id)}» — ${value(id)}"
        fun expression(ids: List<String>, numbers: List<Long>): String =
            ids.zip(numbers).joinToString(" + ") { (id, number) -> "«${label(id)}» ($number)" }
        fun mismatch(left: Long, right: Long, leftName: String, rightName: String): String = when {
            left == right -> "Значения совпадают."
            left > right -> "Не совпадает: $leftName больше, чем $rightName, на ${left - right}."
            else -> "Не совпадает: $leftName меньше, чем $rightName, на ${right - left}."
        }
        fun result(status: ReconciliationStatus, explanation: String) = ReconciliationResult(
            ruleId = rule.id,
            status = status,
            message = rule.message,
            explanation = explanation,
            sourceValues = source,
        )
        val numericInputIds = buildList {
            addAll(rule.inputIds)
            addAll(rule.comparisonInputIds)
            rule.targetInputId?.let(::add)
        }.distinct()
        val invalidInputIds = numericInputIds.filter { rawValues[it]?.toLongOrNull() == null }
        if (rule.type != ReconciliationRuleType.CUSTOM_WARNING && invalidInputIds.isNotEmpty()) {
            return result(
                ReconciliationStatus.NOT_CHECKED,
                "Не заполнено или введено не число: ${invalidInputIds.joinToString { "«${label(it)}»" }}.",
            )
        }
        val numbers = currentNumbers.filterNotNull()
        return when (rule.type) {
            ReconciliationRuleType.EQUAL -> {
                val ok = numbers.distinct().size <= 1
                val compared = rule.inputIds.joinToString { labeledValue(it) }
                val spread = (numbers.maxOrNull() ?: 0L) - (numbers.minOrNull() ?: 0L)
                result(
                    if (ok) ReconciliationStatus.OK else ReconciliationStatus.ERROR,
                    if (ok) "Совпадают: $compared." else "Не совпадают: $compared. Разница — $spread.",
                )
            }
            ReconciliationRuleType.SUM_EQUALS -> {
                val targetId = rule.targetInputId!!
                val target = rawValues.getValue(targetId).toLong()
                val sum = numbers.sum()
                result(
                    if (sum == target) ReconciliationStatus.OK else ReconciliationStatus.ERROR,
                    "${expression(rule.inputIds, numbers)} = $sum; «${label(targetId)}» = $target. " +
                        mismatch(sum, target, "сумма", "«${label(targetId)}»"),
                )
            }
            ReconciliationRuleType.SUM_LESS_OR_EQUAL -> {
                val targetId = rule.targetInputId!!
                val target = rawValues.getValue(targetId).toLong()
                val sum = numbers.sum()
                val comparison = if (sum <= target) {
                    "Условие выполнено: сумма не превышает «${label(targetId)}» (запас ${target - sum})."
                } else {
                    "Условие нарушено: сумма превышает «${label(targetId)}» на ${sum - target}."
                }
                result(
                    if (sum <= target) ReconciliationStatus.OK else ReconciliationStatus.ERROR,
                    "${expression(rule.inputIds, numbers)} = $sum; «${label(targetId)}» = $target. $comparison",
                )
            }
            ReconciliationRuleType.SUM_EQUALS_SUM -> {
                val comparison = comparisonNumbers.filterNotNull()
                val left = numbers.sum()
                val right = comparison.sum()
                result(
                    if (left == right) ReconciliationStatus.OK else ReconciliationStatus.ERROR,
                    "${expression(rule.inputIds, numbers)} = $left; " +
                        "${expression(rule.comparisonInputIds, comparison)} = $right. " +
                        mismatch(left, right, "первая сумма", "вторая сумма"),
                )
            }
            ReconciliationRuleType.RANGE -> {
                val value = numbers.first()
                val min = rule.minimum ?: Long.MIN_VALUE
                val max = rule.maximum ?: Long.MAX_VALUE
                val bounds = when {
                    rule.minimum != null && rule.maximum != null -> "от $min до $max"
                    rule.minimum != null -> "не меньше $min"
                    else -> "не больше $max"
                }
                result(
                    if (value in min..max) ReconciliationStatus.OK else ReconciliationStatus.WARNING,
                    "${labeledValue(rule.inputIds.first())}. Допустимое значение: $bounds.",
                )
            }
            ReconciliationRuleType.MATCH_PREVIOUS -> {
                val previousId = rule.previousInputId ?: rule.inputIds.first()
                val previous = previousValues[previousId]?.toLongOrNull()
                    ?: return result(
                        ReconciliationStatus.NOT_CHECKED,
                        "Для «${label(previousId)}» нет сохранённого значения из предыдущей сверки.",
                    )
                val current = numbers.first()
                result(
                    if (current == previous) ReconciliationStatus.OK else ReconciliationStatus.ERROR,
                    "${labeledValue(rule.inputIds.first())}; ранее сохранено — $previous. " +
                        mismatch(current, previous, "текущее значение", "ранее сохранённое"),
                )
            }
            ReconciliationRuleType.UNIQUE -> {
                val ok = numbers.size == numbers.distinct().size
                val duplicates = numbers.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
                result(
                    if (ok) ReconciliationStatus.OK else ReconciliationStatus.WARNING,
                    if (ok) {
                        "Все значения различаются: ${rule.inputIds.joinToString { labeledValue(it) }}."
                    } else {
                        "Повторяются значения ${duplicates.joinToString()}: " +
                            rule.inputIds.joinToString { labeledValue(it) } + "."
                    },
                )
            }
            ReconciliationRuleType.SEQUENTIAL -> {
                val ok = numbers.zipWithNext().all { (a, b) -> b == a + 1 }
                val breaks = numbers.zipWithNext().mapIndexedNotNull { index, (first, second) ->
                    if (second == first + 1) null else {
                        "между «${label(rule.inputIds[index])}» ($first) и " +
                            "«${label(rule.inputIds[index + 1])}» ($second)"
                    }
                }
                result(
                    if (ok) ReconciliationStatus.OK else ReconciliationStatus.WARNING,
                    if (ok) {
                        "Последовательность соблюдена: ${rule.inputIds.joinToString { labeledValue(it) }}."
                    } else {
                        "Нарушена последовательность ${breaks.joinToString()}."
                    },
                )
            }
            ReconciliationRuleType.CUSTOM_WARNING -> result(
                ReconciliationStatus.WARNING,
                "Это ручная проверка: сопоставьте указанные документы и зафиксируйте наблюдаемые факты.",
            )
        }
    }
}
