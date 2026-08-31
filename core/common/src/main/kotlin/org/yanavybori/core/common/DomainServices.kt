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
    ): List<ReconciliationResult> = definition.rules.map { rule ->
        evaluateRule(rule, rawValues, previousValues)
    }

    private fun evaluateRule(
        rule: ReconciliationRule,
        rawValues: Map<String, String>,
        previousValues: Map<String, String>,
    ): ReconciliationResult {
        val relevantIds = buildList {
            addAll(rule.inputIds)
            addAll(rule.comparisonInputIds)
            rule.targetInputId?.let(::add)
        }.distinct()
        val source = relevantIds.associateWith { rawValues[it].orEmpty() }
        val currentNumbers = rule.inputIds.map { rawValues[it]?.toLongOrNull() }
        val comparisonNumbers = rule.comparisonInputIds.map { rawValues[it]?.toLongOrNull() }
        fun result(status: ReconciliationStatus, explanation: String) = ReconciliationResult(
            ruleId = rule.id,
            status = status,
            message = rule.message,
            explanation = explanation,
            sourceValues = source,
        )
        if (rule.type != ReconciliationRuleType.CUSTOM_WARNING &&
            (currentNumbers.any { it == null } || comparisonNumbers.any { it == null } ||
                rule.targetInputId?.let { rawValues[it]?.toLongOrNull() == null } == true)
        ) {
            return result(ReconciliationStatus.NOT_CHECKED, "Недостаточно введённых числовых значений")
        }
        val numbers = currentNumbers.filterNotNull()
        return when (rule.type) {
            ReconciliationRuleType.EQUAL -> {
                val ok = numbers.distinct().size <= 1
                result(if (ok) ReconciliationStatus.OK else ReconciliationStatus.ERROR,
                    "Сравниваются: ${numbers.joinToString(" = ")}")
            }
            ReconciliationRuleType.SUM_EQUALS -> {
                val target = rawValues.getValue(rule.targetInputId!!).toLong()
                val sum = numbers.sum()
                result(if (sum == target) ReconciliationStatus.OK else ReconciliationStatus.ERROR,
                    "${numbers.joinToString(" + ")} = $sum; ожидаемое значение: $target")
            }
            ReconciliationRuleType.SUM_LESS_OR_EQUAL -> {
                val target = rawValues.getValue(rule.targetInputId!!).toLong()
                val sum = numbers.sum()
                result(if (sum <= target) ReconciliationStatus.OK else ReconciliationStatus.ERROR,
                    "${numbers.joinToString(" + ")} = $sum; значение не должно превышать $target")
            }
            ReconciliationRuleType.SUM_EQUALS_SUM -> {
                val comparison = comparisonNumbers.filterNotNull()
                val left = numbers.sum()
                val right = comparison.sum()
                result(if (left == right) ReconciliationStatus.OK else ReconciliationStatus.ERROR,
                    "${numbers.joinToString(" + ")} = $left; ${comparison.joinToString(" + ")} = $right")
            }
            ReconciliationRuleType.RANGE -> {
                val value = numbers.first()
                val min = rule.minimum ?: Long.MIN_VALUE
                val max = rule.maximum ?: Long.MAX_VALUE
                result(if (value in min..max) ReconciliationStatus.OK else ReconciliationStatus.WARNING,
                    "Значение $value; допустимый диапазон: $min..$max")
            }
            ReconciliationRuleType.MATCH_PREVIOUS -> {
                val previousId = rule.previousInputId ?: rule.inputIds.first()
                val previous = previousValues[previousId]?.toLongOrNull()
                    ?: return result(ReconciliationStatus.NOT_CHECKED, "Предыдущее значение не найдено")
                val current = numbers.first()
                result(if (current == previous) ReconciliationStatus.OK else ReconciliationStatus.ERROR,
                    "Текущее значение: $current; ранее введено: $previous")
            }
            ReconciliationRuleType.UNIQUE -> {
                val ok = numbers.size == numbers.distinct().size
                result(if (ok) ReconciliationStatus.OK else ReconciliationStatus.WARNING,
                    "Проверены значения: ${numbers.joinToString()}")
            }
            ReconciliationRuleType.SEQUENTIAL -> {
                val ok = numbers.zipWithNext().all { (a, b) -> b == a + 1 }
                result(if (ok) ReconciliationStatus.OK else ReconciliationStatus.WARNING,
                    "Проверена последовательность: ${numbers.joinToString(" -> ")}")
            }
            ReconciliationRuleType.CUSTOM_WARNING -> result(
                ReconciliationStatus.WARNING,
                "Информационная проверка из Election Pack; автоматический вывод не выполняется",
            )
        }
    }
}
