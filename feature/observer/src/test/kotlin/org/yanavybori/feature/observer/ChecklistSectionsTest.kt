package org.yanavybori.feature.observer

import org.junit.Assert.assertEquals
import org.junit.Test
import org.yanavybori.core.model.ChecklistDefinition
import org.yanavybori.core.model.ChecklistItemState
import org.yanavybori.core.model.ChecklistSectionState
import org.yanavybori.core.model.ChecklistStatus
import org.yanavybori.core.model.ObservationSession

class ChecklistSectionsTest {
    private val definition = ChecklistDefinition("home", "pack", "На дому", listOf("day"), itemIds = listOf("a", "b", "c"))
    private val session = ObservationSession("s", "pack", "42", startedAt = 1, currentVotingDay = "day", currentStage = "voting")
    private val state = ObserverUiState(activeSession = session, checklistDefinitions = listOf(definition),
        checklistStates = listOf(mark("a", ChecklistStatus.PROBLEM), mark("b", ChecklistStatus.NOT_APPLICABLE)))

    @Test fun excluded_section_can_be_restored_without_losing_problem_mark() {
        val excluded = state.copy(checklistSections = listOf(ChecklistSectionState("s", "day", "home", true, true)))
        assertEquals(ChecklistProgress(0, 0, 3), excluded.checklistProgress())
        val restored = excluded.copy(checklistSections = listOf(excluded.sectionState(definition).copy(notApplicable = false)))
        assertEquals(ChecklistProgress(1, 2, 1), restored.checklistProgress())
        assertEquals(ChecklistStatus.PROBLEM, restored.checklistStates.first().status)
    }

    @Test fun collapsed_sections_still_count_toward_progress() {
        assertEquals(state.checklistProgress(), state.copy(checklistSections =
            listOf(ChecklistSectionState("s", "day", "home", collapsed = true))).checklistProgress())
    }

    @Test fun another_day_or_session_cannot_exclude_current_sections() {
        val other = state.copy(checklistSections = listOf(
            ChecklistSectionState("s", "yesterday", "home", true, true),
            ChecklistSectionState("other", "day", "home", true, true)))
        assertEquals(state.checklistProgress(), other.checklistProgress())
    }

    @Test fun stale_item_marks_from_previous_day_do_not_count() {
        assertEquals(ChecklistProgress(0, 3, 0), state.copy(checklistStates =
            state.checklistStates.map { it.copy(votingDayId = "yesterday") }).checklistProgress())
    }

    private fun mark(id: String, status: ChecklistStatus) = ChecklistItemState(id, "s", "day", id, status, 1)
}
