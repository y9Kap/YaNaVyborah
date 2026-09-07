package org.yanavybori.core.content

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.yanavybori.core.crypto.Sha256
import org.yanavybori.core.database.ContentDatabase
import org.yanavybori.core.database.RoomElectionPackRepository
import org.yanavybori.core.database.RoomKnowledgeRepository
import org.yanavybori.core.model.ChecklistDefinition
import org.yanavybori.core.model.ChecklistItem
import org.yanavybori.core.model.ComplaintTemplate
import org.yanavybori.core.model.ElectionPackFile
import org.yanavybori.core.model.ElectionPackManifest
import org.yanavybori.core.model.LawReference
import org.yanavybori.core.model.ReconciliationDefinition
import org.yanavybori.core.model.ReferenceDocument
import org.yanavybori.core.model.Situation
import org.yanavybori.core.model.VotingDayDefinition

@RunWith(AndroidJUnit4::class)
class ElectionPackRoomIntegrationTest {
    private lateinit var database: ContentDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            ContentDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun verified_source_is_imported_into_content_room_database() = runBlocking {
        val repository = RoomElectionPackRepository(database.contentDao())
        val result = ElectionPackImporter(repository).import(source())
        val days = RoomKnowledgeRepository(database.contentDao())
            .observeVotingDays("integration-pack").first()

        assertEquals("integration-pack", (result as ElectionPackImportResult.Installed).manifest.id)
        assertEquals(listOf("integration-day"), days.map { it.id })
    }

    private fun source(): ElectionPackSource {
        val json = Json { encodeDefaults = true; explicitNulls = false }
        val payloads = linkedMapOf(
            ElectionPackImporter.VOTING_DAYS_PATH to json.encodeToString(
                listOf(VotingDayDefinition("integration-day", "integration-pack", 1, "DEMO day")),
            ).encodeToByteArray(),
            ElectionPackImporter.CHECKLIST_DEFINITIONS_PATH to json.encodeToString(emptyList<ChecklistDefinition>()).encodeToByteArray(),
            ElectionPackImporter.CHECKLIST_ITEMS_PATH to json.encodeToString(emptyList<ChecklistItem>()).encodeToByteArray(),
            ElectionPackImporter.SITUATIONS_PATH to json.encodeToString(emptyList<Situation>()).encodeToByteArray(),
            ElectionPackImporter.LAWS_PATH to json.encodeToString(emptyList<LawReference>()).encodeToByteArray(),
            ElectionPackImporter.COMPLAINT_TEMPLATES_PATH to json.encodeToString(emptyList<ComplaintTemplate>()).encodeToByteArray(),
            ElectionPackImporter.RECONCILIATIONS_PATH to json.encodeToString(emptyList<ReconciliationDefinition>()).encodeToByteArray(),
            ElectionPackImporter.REFERENCE_DOCUMENTS_PATH to json.encodeToString(emptyList<ReferenceDocument>()).encodeToByteArray(),
        )
        val hashes = payloads.mapValues { Sha256.digest(it.value) }
        val manifest = ElectionPackManifest(
            id = "integration-pack",
            name = "DEMO integration",
            version = "1",
            locale = "ru",
            jurisdiction = "DEMO",
            electionType = "DEMO",
            contentVersion = 1,
            schemaVersion = 1,
            publisher = "DEMO",
            isDemo = true,
            files = hashes.map { ElectionPackFile(it.key, it.value) },
            hashes = hashes,
        )
        val all = payloads + (ElectionPackImporter.MANIFEST_PATH to json.encodeToString(manifest).encodeToByteArray())
        return object : ElectionPackSource {
            override suspend fun read(path: String): ByteArray = all.getValue(path)
        }
    }
}
