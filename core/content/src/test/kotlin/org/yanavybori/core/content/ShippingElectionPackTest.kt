package org.yanavybori.core.content

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.yanavybori.core.common.ElectionPackRepository
import org.yanavybori.core.model.ElectionPackContent
import org.yanavybori.core.model.ElectionPackManifest

class ShippingElectionPackTest {
    @Test
    fun bundled_priority_roadmap_pack_passes_strict_import_and_reference_validation() = runTest {
        val directory = findPackDirectory()
        val repository = RecordingRepository()
        val result = ElectionPackImporter(repository).import(
            object : ElectionPackSource {
                override suspend fun read(path: String): ByteArray = directory.resolve(path).readBytes()
            },
        )

        assertTrue(result is ElectionPackImportResult.Installed)
        val content = requireNotNull(repository.content)
        assertEquals(5, content.manifest.contentVersion)
        assertEquals("8 (800) 777-87-25", content.manifest.emergencyContacts.first().phone)
        assertEquals(156, content.checklistItems.count { it.sourceDocumentId == "reference-roadmap" })
        assertEquals(15, content.situations.count { it.parentId == "situation-roadmap-gross" })
        assertTrue(content.referenceDocuments.single { it.id == "reference-roadmap" }.content.isNotBlank())
        assertEquals(4, content.reconciliationDefinitions
            .single { it.id == "reconciliation-protocol-lines" }.rules.size)
    }

    private fun findPackDirectory(): File {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return sequenceOf(
            workingDirectory.resolve("app/src/main/assets/demo-election-pack"),
            workingDirectory.resolve("../../app/src/main/assets/demo-election-pack").canonicalFile,
        ).firstOrNull(File::isDirectory)
            ?: error("Не найден Election Pack из ${workingDirectory.absolutePath}")
    }

    private class RecordingRepository : ElectionPackRepository {
        private val active = MutableStateFlow<ElectionPackManifest?>(null)
        var content: ElectionPackContent? = null

        override fun observeActiveManifest(): Flow<ElectionPackManifest?> = active
        override suspend fun activeManifest(): ElectionPackManifest? = active.value
        override suspend fun replaceAtomically(content: ElectionPackContent) {
            this.content = content
            active.value = content.manifest
        }
    }
}
