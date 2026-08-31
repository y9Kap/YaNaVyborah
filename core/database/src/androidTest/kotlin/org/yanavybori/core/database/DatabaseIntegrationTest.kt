package org.yanavybori.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.yanavybori.core.model.ElectionPackContent
import org.yanavybori.core.model.ElectionPackManifest
import org.yanavybori.core.model.ObservationSession
import org.yanavybori.core.model.VotingDayDefinition

@RunWith(AndroidJUnit4::class)
class DatabaseIntegrationTest {
    private lateinit var contentDatabase: ContentDatabase
    private lateinit var userDatabase: UserDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        contentDatabase = Room.inMemoryDatabaseBuilder(context, ContentDatabase::class.java)
            .allowMainThreadQueries().build()
        userDatabase = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        contentDatabase.close()
        userDatabase.close()
    }

    @Test
    fun replacing_content_database_does_not_delete_user_database() = runBlocking {
        val userSession = ObservationSessionEntity(
            id = "session",
            electionPackId = "old-pack",
            precinctNumber = "42",
            precinctName = null,
            startedAt = 1,
            finishedAt = null,
            currentVotingDay = "old-day",
            currentStage = "stage",
        )
        userDatabase.observationDao().upsert(userSession)
        val repository = RoomElectionPackRepository(contentDatabase.contentDao())

        repository.replaceAtomically(content("pack-v1", 1))
        repository.replaceAtomically(content("pack-v2", 2))

        assertEquals("pack-v2", repository.activeManifest()?.id)
        assertEquals(listOf("session"), userDatabase.observationDao().observeAll().first().map { it.id })
    }

    @Test
    fun counter_changes_keep_timestamped_marks_and_undo_last_only() = runBlocking {
        val dao = userDatabase.counterDao()
        dao.upsertCounter(CounterSessionEntity("counter", "session", "day", "Стол", 1, null, 0))
        dao.increment("counter", CounterMarkEntity("m1", "counter", 10, 1))
        dao.increment("counter", CounterMarkEntity("m2", "counter", 20, 1))
        dao.increment("counter", CounterMarkEntity("m3", "counter", 30, -1))

        assertEquals(1, dao.observeCounters("session", "day").first().single().currentValue)
        assertEquals(listOf(1, 1, -1), dao.observeMarks("counter").first().map { it.delta })
        assertEquals(listOf("m3"), dao.observeLastMarksForDay("session", "day").first().map { it.id })
        assertTrue(dao.undoLast("counter"))
        assertEquals(2, dao.observeCounters("session", "day").first().single().currentValue)
        assertEquals(listOf("m1", "m2"), dao.observeMarks("counter").first().map { it.id })
        assertEquals(listOf("m2"), dao.observeLastMarksForDay("session", "day").first().map { it.id })
    }

    @Test
    fun deleting_session_removes_its_counters_and_marks() = runBlocking {
        userDatabase.observationDao().upsert(
            ObservationSessionEntity(
                id = "session",
                electionPackId = "pack",
                precinctNumber = "42",
                precinctName = null,
                startedAt = 1,
                finishedAt = null,
                currentVotingDay = "day",
                currentStage = "stage",
            ),
        )
        userDatabase.counterDao().upsertCounter(
            CounterSessionEntity("counter", "session", "day", "Стол", 1, null, 0),
        )
        userDatabase.counterDao().increment(
            "counter",
            CounterMarkEntity("mark", "counter", 2, 1),
        )

        userDatabase.observationDao().deleteSession("session")

        assertTrue(userDatabase.observationDao().observeAll().first().isEmpty())
        assertTrue(userDatabase.counterDao().observeCounters("session", "day").first().isEmpty())
        assertTrue(userDatabase.counterDao().observeMarks("counter").first().isEmpty())
    }

    private fun content(id: String, version: Int) = ElectionPackContent(
        manifest = ElectionPackManifest(
            id = id,
            name = "DEMO",
            version = version.toString(),
            locale = "ru",
            jurisdiction = "DEMO",
            electionType = "DEMO",
            contentVersion = version,
            schemaVersion = 1,
            publisher = "DEMO",
            isDemo = true,
        ),
        votingDays = listOf(VotingDayDefinition("$id-day", id, 1, "day")),
        checklistDefinitions = emptyList(),
        checklistItems = emptyList(),
        situations = emptyList(),
        lawReferences = emptyList(),
        complaintTemplates = emptyList(),
        reconciliationDefinitions = emptyList(),
        referenceDocuments = emptyList(),
    )
}
