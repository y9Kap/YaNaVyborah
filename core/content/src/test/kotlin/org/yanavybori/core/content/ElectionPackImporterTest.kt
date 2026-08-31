package org.yanavybori.core.content

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.yanavybori.core.common.ElectionPackRepository
import org.yanavybori.core.crypto.Sha256
import org.yanavybori.core.model.ChecklistDefinition
import org.yanavybori.core.model.ChecklistItem
import org.yanavybori.core.model.ComplaintTemplate
import org.yanavybori.core.model.ElectionPackContent
import org.yanavybori.core.model.ElectionPackFile
import org.yanavybori.core.model.ElectionPackManifest
import org.yanavybori.core.model.EmergencyContact
import org.yanavybori.core.model.EmergencyContactType
import org.yanavybori.core.model.LawReference
import org.yanavybori.core.model.ReconciliationDefinition
import org.yanavybori.core.model.ReferenceDocument
import org.yanavybori.core.model.Situation
import org.yanavybori.core.model.VotingDayDefinition
import org.yanavybori.core.model.VotingStageDefinition

class ElectionPackImporterTest {
    @Test
    fun parses_valid_pack_checks_hashes_and_installs_once() = runTest {
        val repository = FakeRepository()
        val source = testSource()
        val result = ElectionPackImporter(repository).import(source)

        assertTrue(result is ElectionPackImportResult.Installed)
        assertEquals(1, repository.replaceCount)
        assertEquals("demo-pack", repository.content?.manifest?.id)
        assertEquals(listOf("day-1"), repository.content?.votingDays?.map { it.id })
    }

    @Test
    fun rejects_unsupported_schema_before_database_write() = runTest {
        val repository = FakeRepository()
        val error = capture { ElectionPackImporter(repository).import(testSource(schemaVersion = 99)) }
        assertTrue(error is ElectionPackImportException.UnsupportedSchema)
        assertEquals(0, repository.replaceCount)
    }

    @Test
    fun schema_two_imports_emergency_contacts() = runTest {
        val repository = FakeRepository()
        ElectionPackImporter(repository).import(
            testSource(
                schemaVersion = 2,
                emergencyContacts = listOf(
                    EmergencyContact(
                        id = "legal",
                        title = "Юрист",
                        phone = "+7 000 000-00-00",
                        region = "Москва",
                        type = EmergencyContactType.LAWYER,
                    ),
                ),
            ),
        )

        assertEquals("+7 000 000-00-00", repository.content?.manifest?.emergencyContacts?.single()?.phone)
    }

    @Test
    fun imports_text_reference_document_content() = runTest {
        val repository = FakeRepository()
        val path = "reference_documents/guide.txt"
        ElectionPackImporter(repository).import(
            testSource(
                referenceDocuments = listOf(
                    ReferenceDocument(
                        id = "guide",
                        packId = "demo-pack",
                        title = "Guide",
                        description = "Full guide",
                        contentPath = path,
                        mimeType = "text/plain",
                    ),
                ),
                referenceFiles = mapOf(path to "Полный текст памятки".encodeToByteArray()),
            ),
        )

        assertEquals("Полный текст памятки", repository.content?.referenceDocuments?.single()?.content)
    }

    @Test
    fun rejects_invalid_content_version() = runTest {
        val repository = FakeRepository()
        val error = capture { ElectionPackImporter(repository).import(testSource(contentVersion = 0)) }
        assertTrue(error is ElectionPackImportException.InvalidManifest)
        assertEquals(0, repository.replaceCount)
    }

    @Test
    fun damaged_update_does_not_replace_previous_working_pack() = runTest {
        val previous = minimalContent("previous", 3)
        val repository = FakeRepository(previous)
        val valid = testSource(packId = "replacement")
        val damagedFiles = valid.files.toMutableMap().apply {
            this[ElectionPackImporter.LAWS_PATH] = "corrupted".encodeToByteArray()
        }
        val error = capture {
            ElectionPackImporter(repository).import(MapSource(damagedFiles))
        }
        assertTrue(error is ElectionPackImportException.HashMismatch)
        assertSame(previous, repository.content)
        assertEquals(0, repository.replaceCount)
    }

    @Test
    fun same_or_newer_content_version_is_not_reimported() = runTest {
        val current = minimalContent("demo-pack", 2)
        val repository = FakeRepository(current)
        val result = ElectionPackImporter(repository).import(testSource(contentVersion = 1))
        assertTrue(result is ElectionPackImportResult.AlreadyCurrent)
        assertEquals(0, repository.replaceCount)
    }

    private suspend fun capture(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (error: Throwable) {
        error
    }

    private fun testSource(
        packId: String = "demo-pack",
        contentVersion: Int = 1,
        schemaVersion: Int = 1,
        emergencyContacts: List<EmergencyContact> = emptyList(),
        referenceDocuments: List<ReferenceDocument> = emptyList(),
        referenceFiles: Map<String, ByteArray> = emptyMap(),
    ): MapSource {
        val day = VotingDayDefinition(
            id = "day-1",
            packId = packId,
            order = 1,
            title = "DEMO day",
            stages = listOf(VotingStageDefinition("stage-1", "Stage", 1)),
        )
        val payloads = linkedMapOf(
            ElectionPackImporter.VOTING_DAYS_PATH to json.encodeToString(listOf(day)).encodeToByteArray(),
            ElectionPackImporter.CHECKLIST_DEFINITIONS_PATH to
                json.encodeToString(emptyList<ChecklistDefinition>()).encodeToByteArray(),
            ElectionPackImporter.CHECKLIST_ITEMS_PATH to
                json.encodeToString(emptyList<ChecklistItem>()).encodeToByteArray(),
            ElectionPackImporter.SITUATIONS_PATH to
                json.encodeToString(emptyList<Situation>()).encodeToByteArray(),
            ElectionPackImporter.LAWS_PATH to
                json.encodeToString(emptyList<LawReference>()).encodeToByteArray(),
            ElectionPackImporter.COMPLAINT_TEMPLATES_PATH to
                json.encodeToString(emptyList<ComplaintTemplate>()).encodeToByteArray(),
            ElectionPackImporter.RECONCILIATIONS_PATH to
                json.encodeToString(emptyList<ReconciliationDefinition>()).encodeToByteArray(),
            ElectionPackImporter.REFERENCE_DOCUMENTS_PATH to
                json.encodeToString(referenceDocuments).encodeToByteArray(),
        ).apply { putAll(referenceFiles) }
        val hashes = payloads.mapValues { Sha256.digest(it.value) }
        val manifest = ElectionPackManifest(
            id = packId,
            name = "DEMO",
            version = "1",
            locale = "ru-RU",
            jurisdiction = "DEMO",
            electionType = "DEMO",
            contentVersion = contentVersion,
            schemaVersion = schemaVersion,
            publisher = "DEMO publisher",
            isDemo = true,
            emergencyContacts = emergencyContacts,
            files = hashes.map { ElectionPackFile(it.key, it.value) },
            hashes = hashes,
        )
        return MapSource(payloads + (ElectionPackImporter.MANIFEST_PATH to
            json.encodeToString(manifest).encodeToByteArray()))
    }

    private fun minimalContent(id: String, version: Int): ElectionPackContent = ElectionPackContent(
        manifest = ElectionPackManifest(
            id, "DEMO", "1", "ru", "DEMO", "DEMO",
            contentVersion = version, schemaVersion = 1, publisher = "DEMO",
        ),
        votingDays = emptyList(),
        checklistDefinitions = emptyList(),
        checklistItems = emptyList(),
        situations = emptyList(),
        lawReferences = emptyList(),
        complaintTemplates = emptyList(),
        reconciliationDefinitions = emptyList(),
        referenceDocuments = emptyList(),
    )

    private class MapSource(val files: Map<String, ByteArray>) : ElectionPackSource {
        override suspend fun read(path: String): ByteArray = files[path]
            ?: throw ElectionPackImportException.MissingFile(path)
    }

    private class FakeRepository(initial: ElectionPackContent? = null) : ElectionPackRepository {
        private val active = MutableStateFlow(initial?.manifest)
        var content: ElectionPackContent? = initial
        var replaceCount: Int = 0
        override fun observeActiveManifest(): Flow<ElectionPackManifest?> = active
        override suspend fun activeManifest(): ElectionPackManifest? = active.value
        override suspend fun replaceAtomically(content: ElectionPackContent) {
            this.content = content
            replaceCount++
            active.value = content.manifest
        }
    }

    private companion object {
        val json = Json { encodeDefaults = true; explicitNulls = false }
    }
}
