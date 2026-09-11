package org.yanavybori.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.yanavybori.core.model.ChecklistStatus

class BrowserStoreTest {
    @Test
    fun sessionChecklistAndCounterRoundTrip() = runTest {
        val store = BrowserStore()
        store.observeSessions().first().forEach { session ->
            if (session.precinctNumber == "test-901") store.deleteSession(session.id, "test-pass")
        }
        val session = store.createSession(
            electionPackId = "pack",
            observerFullName = "Тестовый Наблюдатель",
            region = "Москва",
            precinctNumber = "test-901",
            precinctName = null,
            commissionMemberNames = emptyList(),
            deletionPassword = "test-pass",
            votingDayId = "day-1",
            stageId = "stage-1",
        )
        store.setChecklistState(session.id, "day-1", "item-1", ChecklistStatus.OK)
        assertEquals(ChecklistStatus.OK, store.observeChecklistStates(session.id, "day-1").first().single().status)

        val counter = store.createCounter(session.id, "day-1", "Вход")
        store.increment(counter.id)
        store.increment(counter.id)
        store.decrement(counter.id)
        assertEquals(1, store.observeCounters(session.id, "day-1").first().single().currentValue)
        assertEquals(true, store.undoLast(counter.id))
        assertEquals(2, store.observeCounters(session.id, "day-1").first().single().currentValue)

        assertFailsWith<IllegalArgumentException> { store.deleteSession(session.id, "wrong") }
        store.deleteSession(session.id, "test-pass")
        assertEquals(null, store.observeActiveSession().first())
    }
}
