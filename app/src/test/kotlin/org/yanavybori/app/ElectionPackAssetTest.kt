package org.yanavybori.app

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElectionPackAssetTest {
    @Test
    fun priority_roadmap_is_complete_and_manifest_hashes_are_current() {
        val pack = findPackDirectory()
        val manifest = parseObject(pack.resolve("manifest.json"))

        assertEquals("2026.09.02-roadmap-2026.09.18-20", manifest.string("version"))
        assertEquals(5, manifest.int("contentVersion"))
        assertEquals("2026-09-18", manifest.string("validFrom"))
        assertEquals("2026-09-20", manifest.string("validUntil"))
        assertTrue("Приоритетный источник" in manifest.string("publisher"))

        val hotline = manifest.array("emergencyContacts").map { it.jsonObject }
            .single { it.string("id") == "roadmap-hotline-yabloko" }
        assertEquals("8 (800) 777-87-25", hotline.string("phone"))

        manifest.array("files").map { it.jsonObject }.forEach { entry ->
            val path = entry.string("path")
            assertEquals("Устаревший SHA-256 для $path", entry.string("sha256"), sha256(pack.resolve(path)))
        }

        val items = parseArray(pack.resolve("checklists/items.json")).map { it.jsonObject }
        val roadmapItems = items.filter { it.optionalString("sourceDocumentId") == "reference-roadmap" }
        assertEquals(156, roadmapItems.size)
        assertEquals(132, roadmapItems.count { it.int("sourcePage") in 1..6 })
        assertEquals(
            mapOf(1 to 25, 2 to 26, 3 to 24, 4 to 24, 5 to 26, 6 to 7, 7 to 24),
            roadmapItems.groupingBy { it.int("sourcePage") }.eachCount().toSortedMap(),
        )
        assertTrue(roadmapItems.all { it.string("title").isNotBlank() })
        assertFalse(roadmapItems.any { "Не допускайте голосования помощника" in it.string("title") })
        assertFalse(roadmapItems.any { "Поставьте подпись" in it.string("title") })

        val situations = parseArray(pack.resolve("situations/situations.json")).map { it.jsonObject }
        assertEquals(15, situations.count { it.optionalString("parentId") == "situation-roadmap-gross" })
        assertFalse(situations.any { "свободно перемещаться" in it.string("title") })

        val laws = parseArray(pack.resolve("laws/laws.json")).map { it.jsonObject }
        assertTrue("Пункт 9 статьи 30" in laws.single { it.string("id") == "law-observer-rights" }.string("citation"))
        assertTrue("пункт 12 статьи 64" in laws.single { it.string("id") == "law-observer-limits" }.string("citation"))

        val forms = parseArray(pack.resolve("reconciliation_rules/forms.json")).map { it.jsonObject }
        val protocol = forms.single { it.string("id") == "reconciliation-protocol-lines" }
        val ruleTypes = protocol.array("rules").map { it.jsonObject }.map { it.string("type") }
        assertEquals(listOf("SUM_LESS_OR_EQUAL", "SUM_EQUALS_SUM", "SUM_EQUALS_SUM", "EQUAL"), ruleTypes)

        val documents = parseArray(pack.resolve("reference_documents/documents.json")).map { it.jsonObject }
        assertFalse(documents.any { "конспект" in it.string("description").lowercase() })
        assertEquals("reference_documents/source_roadmap.txt",
            documents.single { it.string("id") == "reference-roadmap" }.string("contentPath"))
        val source = pack.resolve("reference_documents/source_roadmap.txt").readText()
        assertTrue("8 (800) 777-87-25" in source)
        assertTrue("[1] ≥ [3] + [4] + [5]" in source)
        assertTrue("КОНСТРУКТОР ЖАЛОБЫ В КОМИССИЮ" in source)
        roadmapItems.forEach { item ->
            assertTrue("В полном тексте потерян пункт ${item.string("id")}", item.string("title") in source)
        }
    }

    private fun findPackDirectory(): File {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return sequenceOf(
            workingDirectory.resolve("src/main/assets/demo-election-pack"),
            workingDirectory.resolve("app/src/main/assets/demo-election-pack"),
        ).firstOrNull(File::isDirectory)
            ?: error("Не найден demo-election-pack из ${workingDirectory.absolutePath}")
    }

    private fun parseObject(file: File): JsonObject = Json.parseToJsonElement(file.readText()).jsonObject
    private fun parseArray(file: File): JsonArray = Json.parseToJsonElement(file.readText()).jsonArray

    private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content
    private fun JsonObject.optionalString(name: String): String? = get(name)?.jsonPrimitive?.content
    private fun JsonObject.int(name: String): Int = getValue(name).jsonPrimitive.content.toInt()
    private fun JsonObject.array(name: String): JsonArray = getValue(name).jsonArray
    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { byte -> "%02x".format(byte) }
}
