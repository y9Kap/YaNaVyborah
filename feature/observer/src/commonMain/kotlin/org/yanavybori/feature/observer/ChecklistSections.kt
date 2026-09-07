package org.yanavybori.feature.observer

import org.yanavybori.core.model.ChecklistDefinition
import org.yanavybori.core.model.ChecklistSectionState
import org.yanavybori.core.model.ChecklistStatus

internal fun ObserverUiState.sectionState(definition: ChecklistDefinition): ChecklistSectionState {
    val session = requireNotNull(activeSession)
    return checklistSections.firstOrNull {
        it.sessionId == session.id && it.votingDayId == session.currentVotingDay && it.definitionId == definition.id
    } ?: ChecklistSectionState(session.id, session.currentVotingDay, definition.id)
}

internal data class ChecklistProgress(val checked: Int, val total: Int, val notApplicable: Int)

internal fun ObserverUiState.checklistProgress(): ChecklistProgress {
    val session = activeSession ?: return ChecklistProgress(0, 0, 0)
    val definitions = checklistDefinitions.filter { session.currentVotingDay in it.votingDayIds }
    val allIds = definitions.flatMap { it.itemIds }.toSet()
    val applicableIds = definitions.filterNot { sectionState(it).notApplicable }.flatMap { it.itemIds }.toSet()
    val statuses = checklistStates.filter {
        it.sessionId == session.id && it.votingDayId == session.currentVotingDay
    }.associate { it.checklistItemId to it.status }
    val relevantIds = applicableIds.filterNot { statuses[it] == ChecklistStatus.NOT_APPLICABLE }
    return ChecklistProgress(
        checked = relevantIds.count { statuses[it] == ChecklistStatus.OK || statuses[it] == ChecklistStatus.PROBLEM },
        total = relevantIds.size,
        notApplicable = allIds.size - relevantIds.size,
    )
}
