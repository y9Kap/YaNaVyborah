package org.yanavybori.feature.voter

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PublishedRecommendationPackTest {
    @Test
    fun gosduma_2026_pack_passes_strict_application_import() {
        val file = findPublishedPack("gosduma-2026-umg.yanavyborah.json")
        val imported = RecommendationJson.import(
            raw = file.readText(),
            displayName = "",
            source = "Файл: ${file.name}",
        )

        assertEquals(215, imported.pack.recommendations.size)
        assertEquals("federal_party_list", imported.pack.recommendations.first().ballotType)
        assertEquals(
            214,
            imported.pack.recommendations.count { it.ballotType == "federal_single_mandate" },
        )
        assertNotNull(imported.pack.contentSha256)
        assertEquals(
            "Кузнецова Юлия Вадимовна",
            imported.pack.recommendations.single { it.districtNumber == "195" }.choice,
        )
    }

    @Test
    fun new_parliament_2026_pack_contains_every_single_member_district() {
        val file = findPublishedPack("new-parliament-gosduma-2026.yanavyborah.json")
        val imported = RecommendationJson.import(
            raw = file.readText(),
            displayName = "",
            source = "Файл: ${file.name}",
        )

        assertEquals("Новый парламент", imported.pack.publisher)
        assertEquals(225, imported.pack.recommendations.size)
        assertEquals(
            (1..225).map(Int::toString),
            imported.pack.recommendations.mapNotNull { it.districtNumber }.sortedBy(String::toInt),
        )
        assertNotNull(imported.pack.contentSha256)
    }

    @Test
    fun new_parliament_fills_only_districts_missing_from_umg_by_default() {
        val umg = importPublishedPack("gosduma-2026-umg.yanavyborah.json")
        val newParliament = importPublishedPack("new-parliament-gosduma-2026.yanavyborah.json")

        val result = resolveRecommendationMatches(
            sets = listOf(umg, newParliament),
            regionQuery = "",
            cityQuery = "",
            districtQuery = "",
            precinctQuery = "",
            ballotTypeQuery = "",
            interactionMode = RecommendationInteractionMode.COMPLEMENT_BY_PRIORITY,
            prioritySetId = umg.id,
        )

        assertEquals(215, result[0].recommendations.size)
        assertEquals(11, result[1].recommendations.size)
        assertEquals(214, result[1].suppressedByPriority)
        assertEquals(
            (1..225).map(Int::toString).toSet(),
            result.flatMap { it.recommendations }.mapNotNull { it.districtNumber }.toSet(),
        )
    }

    private fun importPublishedPack(fileName: String): ImportedRecommendationSet {
        val file = findPublishedPack(fileName)
        return RecommendationJson.import(
            raw = file.readText(),
            displayName = "",
            source = "Файл: ${file.name}",
        )
    }

    private fun findPublishedPack(fileName: String): File {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return sequenceOf(
            workingDirectory.resolve("data/recommendations/$fileName"),
            workingDirectory.resolve("../../data/recommendations/$fileName").canonicalFile,
        ).firstOrNull(File::isFile)
            ?: error("Не найден опубликованный JSON из ${workingDirectory.absolutePath}")
    }
}
