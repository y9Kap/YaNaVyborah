package org.yanavybori.feature.observer

import org.yanavybori.core.model.ChecklistDefinition
import org.yanavybori.core.model.ChecklistSectionState
import org.yanavybori.core.model.ChecklistStatus
import org.yanavybori.core.model.ObservationScope

internal fun ObserverUiState.sectionState(definition: ChecklistDefinition): ChecklistSectionState {
    val session = requireNotNull(activeSession)
    return checklistSections.firstOrNull {
        it.sessionId == session.id && it.votingDayId == session.currentVotingDay && it.definitionId == definition.id
    } ?: ChecklistSectionState(session.id, session.currentVotingDay, definition.id)
}

internal data class ChecklistProgress(val checked: Int, val total: Int, val notApplicable: Int)

internal data class ChecklistSectionProgress(val checked: Int, val total: Int)

internal fun formatChecklistItemCount(count: Int): String {
    val suffix = when {
        count % 100 in 11..14 -> "пунктов"
        count % 10 == 1 -> "пункт"
        count % 10 in 2..4 -> "пункта"
        else -> "пунктов"
    }
    return "$count $suffix"
}

internal fun ObserverUiState.checklistSectionProgress(definition: ChecklistDefinition): ChecklistSectionProgress {
    val session = activeSession ?: return ChecklistSectionProgress(0, definition.itemIds.size)
    val statuses = checklistStates.filter {
        it.sessionId == session.id && it.votingDayId == session.currentVotingDay
    }.associate { it.checklistItemId to it.status }
    val relevantIds = definition.itemIds.filterNot { statuses[it] == ChecklistStatus.NOT_APPLICABLE }
    return ChecklistSectionProgress(
        checked = relevantIds.count { statuses[it] == ChecklistStatus.OK || statuses[it] == ChecklistStatus.PROBLEM },
        total = relevantIds.size,
    )
}

internal fun ObserverUiState.selectedObservationScope(
    definitions: List<ChecklistDefinition>,
): ObservationScope? {
    val scoped = definitions.filter { it.observationScope != ObservationScope.SHARED }
    if (scoped.isEmpty()) return ObservationScope.SHARED
    val precinct = scoped.filter { it.observationScope == ObservationScope.PRECINCT }
    val home = scoped.filter { it.observationScope == ObservationScope.HOME }
    val precinctVisible = precinct.count { !sectionState(it).notApplicable }
    val homeVisible = home.count { !sectionState(it).notApplicable }
    return when {
        precinctVisible == precinct.size && homeVisible == home.size -> ObservationScope.SHARED
        precinctVisible == precinct.size && homeVisible == 0 -> ObservationScope.PRECINCT
        precinctVisible == 0 && homeVisible == home.size -> ObservationScope.HOME
        else -> null
    }
}

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
