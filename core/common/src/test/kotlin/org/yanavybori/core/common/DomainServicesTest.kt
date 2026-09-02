package org.yanavybori.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.yanavybori.core.model.ChecklistStatus
import org.yanavybori.core.model.Complaint
import org.yanavybori.core.model.ComplaintStatus
import org.yanavybori.core.model.ReconciliationDefinition
import org.yanavybori.core.model.ReconciliationFieldDefinition
import org.yanavybori.core.model.ReconciliationRule
import org.yanavybori.core.model.ReconciliationRuleType
import org.yanavybori.core.model.ReconciliationStatus

class DomainServicesTest {
    @Test
    fun checklist_allows_returning_to_any_state_and_counts_completion() {
        assertTrue(ChecklistStatePolicy.canTransition(ChecklistStatus.PROBLEM, ChecklistStatus.OK))
        assertTrue(ChecklistStatePolicy.canTransition(ChecklistStatus.OK, ChecklistStatus.NOT_CHECKED))
        assertEquals(
            3,
            ChecklistStatePolicy.completedCount(
                listOf(ChecklistStatus.OK, ChecklistStatus.PROBLEM,
                    ChecklistStatus.NOT_APPLICABLE, ChecklistStatus.NOT_CHECKED),
            ),
        )
    }

    @Test
    fun quick_journal_event_keeps_session_day_and_timestamp() {
        val factory = JournalEventFactory(Clock { 1234L }, IdGenerator { "event-1" })
        val event = factory.quick("session-1", "day-2")
        assertEquals("event-1", event.id)
        assertEquals("session-1", event.sessionId)
        assertEquals("day-2", event.votingDayId)
        assertEquals(1234L, event.timestamp)
    }

    @Test
    fun counter_increment_decrement_and_undo_are_bounded() {
        assertEquals(1L, CounterPolicy.increment(0))
        assertEquals(9L, CounterPolicy.increment(7, 2))
        assertEquals(8L, CounterPolicy.decrement(9))
        assertEquals(7L, CounterPolicy.decrement(9, 2))
        assertThrows(IllegalStateException::class.java) { CounterPolicy.decrement(0) }
        assertEquals(7L, CounterPolicy.undo(9, 2))
        assertEquals(0L, CounterPolicy.undo(0, 1))
        assertEquals(4L, CounterPolicy.undo(4, null))
    }

    @Test
    fun complaint_status_rejects_skipping_submission() {
        val draft = complaint(ComplaintStatus.DRAFT)
        assertFalse(ComplaintStatusPolicy.canTransition(ComplaintStatus.DRAFT, ComplaintStatus.ACCEPTED))
        val ready = ComplaintStatusPolicy.transition(draft, ComplaintStatus.READY, 10)
        val submitted = ComplaintStatusPolicy.transition(ready, ComplaintStatus.SUBMITTED, 20)
        assertEquals(ComplaintStatus.SUBMITTED, submitted.status)
        assertEquals(20L, submitted.submittedAt)
    }

    @Test
    fun reconciliation_evaluates_all_generic_rule_types_without_legal_conclusions() {
        val definition = definition(
            ReconciliationRule("equal", ReconciliationRuleType.EQUAL, listOf("a", "b"), message = "equal"),
            ReconciliationRule("sum", ReconciliationRuleType.SUM_EQUALS, listOf("a", "b"), "total", message = "sum"),
            ReconciliationRule(
                "sum-limit",
                ReconciliationRuleType.SUM_LESS_OR_EQUAL,
                listOf("a", "b"),
                "total",
                message = "sum limit",
            ),
            ReconciliationRule(
                "sum-to-sum",
                ReconciliationRuleType.SUM_EQUALS_SUM,
                listOf("a", "b"),
                comparisonInputIds = listOf("c"),
                message = "sum to sum",
            ),
            ReconciliationRule("range", ReconciliationRuleType.RANGE, listOf("a"), minimum = 1, maximum = 9, message = "range"),
            ReconciliationRule("previous", ReconciliationRuleType.MATCH_PREVIOUS, listOf("a"), previousInputId = "old_a", message = "previous"),
            ReconciliationRule("unique", ReconciliationRuleType.UNIQUE, listOf("a", "b", "c"), message = "unique"),
            ReconciliationRule("sequence", ReconciliationRuleType.SEQUENTIAL, listOf("a", "b", "c"), message = "sequence"),
            ReconciliationRule("custom", ReconciliationRuleType.CUSTOM_WARNING, emptyList(), message = "manual"),
        )
        val results = ReconciliationEngine().evaluate(
            definition,
            mapOf("a" to "1", "b" to "2", "c" to "3", "total" to "3"),
            mapOf("old_a" to "1"),
        ).associateBy { it.ruleId }
        assertEquals(ReconciliationStatus.ERROR, results.getValue("equal").status)
        assertEquals(ReconciliationStatus.OK, results.getValue("sum").status)
        assertEquals(ReconciliationStatus.OK, results.getValue("sum-limit").status)
        assertEquals(ReconciliationStatus.OK, results.getValue("sum-to-sum").status)
        assertEquals(ReconciliationStatus.OK, results.getValue("range").status)
        assertEquals(ReconciliationStatus.OK, results.getValue("previous").status)
        assertEquals(ReconciliationStatus.OK, results.getValue("unique").status)
        assertEquals(ReconciliationStatus.OK, results.getValue("sequence").status)
        assertEquals(ReconciliationStatus.WARNING, results.getValue("custom").status)
        assertTrue(results.values.none { "фальсифика" in it.explanation.lowercase() })
    }

    @Test
    fun reconciliation_reports_missing_inputs_as_not_checked() {
        val result = ReconciliationEngine().evaluate(
            definition(ReconciliationRule("eq", ReconciliationRuleType.EQUAL, listOf("a", "b"), message = "eq")),
            mapOf("a" to "1"),
        ).single()
        assertEquals(ReconciliationStatus.NOT_CHECKED, result.status)
        assertEquals(mapOf("a" to "1", "b" to ""), result.sourceValues)
    }

    @Test
    fun reconciliation_detects_sum_limit_and_sum_to_sum_mismatches() {
        val results = ReconciliationEngine().evaluate(
            definition(
                ReconciliationRule(
                    "sum-limit",
                    ReconciliationRuleType.SUM_LESS_OR_EQUAL,
                    listOf("a", "b"),
                    targetInputId = "total",
                    message = "sum limit",
                ),
                ReconciliationRule(
                    "sum-to-sum",
                    ReconciliationRuleType.SUM_EQUALS_SUM,
                    listOf("a", "b"),
                    comparisonInputIds = listOf("c"),
                    message = "sum to sum",
                ),
            ),
            mapOf("a" to "2", "b" to "2", "c" to "3", "total" to "3"),
        ).associateBy { it.ruleId }

        assertEquals(ReconciliationStatus.ERROR, results.getValue("sum-limit").status)
        assertEquals(ReconciliationStatus.ERROR, results.getValue("sum-to-sum").status)
        assertTrue(results.getValue("sum-limit").explanation.contains("Строка А"))
        assertTrue(results.getValue("sum-limit").explanation.contains("превышает"))
        assertFalse(results.getValue("sum-limit").explanation.contains("a="))
    }

    private fun complaint(status: ComplaintStatus) = Complaint(
        id = "c", sessionId = "s", recipient = "r", createdAt = 1,
        status = status, text = "text",
    )

    private fun definition(vararg rules: ReconciliationRule) = ReconciliationDefinition(
        id = "d",
        packId = "p",
        title = "demo",
        description = "demo",
        fields = listOf("a", "b", "c", "total").mapIndexed { index, id ->
            ReconciliationFieldDefinition(
                id,
                mapOf("a" to "Строка А", "b" to "Строка Б", "c" to "Строка В", "total" to "Итого")
                    .getValue(id),
                index,
            )
        },
        rules = rules.toList(),
    )
}
