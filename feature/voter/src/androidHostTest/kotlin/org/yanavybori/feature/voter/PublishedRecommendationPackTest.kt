package org.yanavybori.feature.voter

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PublishedRecommendationPackTest {
    @Test
    fun gosduma_2026_pack_passes_strict_application_import() {
        val file = findPublishedPack()
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

    private fun findPublishedPack(): File {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return sequenceOf(
            workingDirectory.resolve("data/recommendations/gosduma-2026-umg.yanavyborah.json"),
            workingDirectory.resolve("../../data/recommendations/gosduma-2026-umg.yanavyborah.json").canonicalFile,
        ).firstOrNull(File::isFile)
            ?: error("Не найден опубликованный JSON из ${workingDirectory.absolutePath}")
    }
}
