package org.yanavybori.feature.voter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.yanavybori.core.crypto.Sha256

class RecommendationJsonTest {
    private val sample = """
        {
          "schemaVersion": 1,
          "title": "Совет автора",
          "publisher": "Автор",
          "recommendations": [
            {
              "region": "Москва",
              "city": "*",
              "district": "Округ 1",
              "ballotType": "federal_single_mandate",
              "choice": "Кандидат"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun imports_and_renames_valid_pack() {
        val imported = RecommendationJson.import(
            sample,
            displayName = "Моё название",
            source = "Файл: sample.json",
            expectedSha256 = Sha256.digest(sample.encodeToByteArray()),
        )

        assertEquals("Моё название", imported.displayName)
        assertEquals("Автор", imported.pack.publisher)
        assertEquals(1, imported.pack.recommendations.size)
        assertEquals(imported.rawSha256, imported.expectedSha256)
    }

    @Test
    fun rejects_wrong_hash_or_unknown_fields() {
        assertFailsWith<IllegalArgumentException> {
            RecommendationJson.import(sample, "", "file", "0".repeat(64))
        }
        assertFailsWith<Exception> {
            RecommendationJson.import(sample.replace("\"publisher\"", "\"unexpected\""), "", "file")
        }
    }

    @Test
    fun wildcard_scope_matches_user_location() {
        val recommendation = RecommendationJson.import(sample, "", "file").pack.recommendations.single()

        assertTrue(recommendation.matches("Москва", "Зеленоград", "1", "", "federal_single_mandate"))
        assertTrue(!recommendation.matches("Тула", "", "", "", ""))
    }

    @Test
    fun local_state_round_trips() {
        val imported = RecommendationJson.import(sample, "", "file")
        val state = VoterLocalState(recommendationSets = listOf(imported))

        assertEquals(state, RecommendationJson.decodeState(RecommendationJson.encodeState(state)))
    }

    @Test
    fun bundled_voter_guide_has_attributed_core_sections() {
        assertTrue(OVD_INFO_ELECTION_GUIDE_URL.startsWith("https://ovdinfo.legal/"))
        assertTrue(voterGuideSections.size >= 5)
        val text = voterGuideSections.flatMap { it.points }.joinToString(" ")
        assertTrue("тайное" in text)
        assertTrue("задерж" in text)
        assertTrue("бюллетень" in text)
    }
}
