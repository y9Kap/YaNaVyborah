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
          "contentSha256": "da6288d830ab74e219839fa94a267594ffdd970296dddee2bf0c831e94311417",
          "recommendations": [
            {
              "region": "Москва",
              "city": "*",
              "district": "Округ 1",
              "districtNumber": "195",
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
        assertTrue(recommendation.matches("Москва", "", "195", "", "federal_single_mandate"))
        assertTrue(!recommendation.matches("Тула", "", "", "", ""))
    }

    @Test
    fun precinct_number_matches_exactly_instead_of_as_a_substring() {
        val recommendation = VoteRecommendation(
            region = "Москва",
            precinct = "УИК №127",
            ballotType = "other",
            choice = "Вариант",
        )

        assertTrue(recommendation.matches("Москва", "", "", "127", ""))
        assertTrue(recommendation.matches("Москва", "", "", "УИК 127", ""))
        assertTrue(!recommendation.matches("Москва", "", "", "27", ""))
    }

    @Test
    fun lower_priority_list_only_fills_missing_ballots() {
        val primary = recommendationSet(
            id = "umg",
            title = "Умное голосование — Госдума 2026",
            recommendations = listOf(
                recommendation("1", "federal_party_list", "Партия УмГ"),
                recommendation("1", "federal_single_mandate", "Кандидат УмГ"),
            ),
        )
        val supplement = recommendationSet(
            id = "new-parliament",
            title = "Новый парламент — Госдума 2026",
            recommendations = listOf(
                recommendation("1", "federal_party_list", "Партия НП"),
                recommendation("1", "federal_single_mandate", "Кандидат НП"),
                recommendation("1", "regional_party_list", "Региональная партия НП"),
                recommendation("1", "regional_single_mandate", "Региональный кандидат НП"),
            ),
        )

        val result = resolveRecommendationMatches(
            sets = listOf(primary, supplement),
            regionQuery = "",
            cityQuery = "",
            districtQuery = "1",
            precinctQuery = "",
            ballotTypeQuery = "",
            interactionMode = RecommendationInteractionMode.COMPLEMENT_BY_PRIORITY,
            prioritySetId = primary.id,
        )

        assertEquals(listOf("Партия УмГ", "Кандидат УмГ"), result[0].recommendations.map { it.choice })
        assertEquals(
            listOf("Региональная партия НП", "Региональный кандидат НП"),
            result[1].recommendations.map { it.choice },
        )
        assertEquals(2, result[1].suppressedByPriority)
    }

    @Test
    fun lower_priority_list_fills_district_missing_from_primary_list() {
        val primary = recommendationSet(
            id = "umg",
            title = "Умное голосование — Госдума 2026",
            recommendations = listOf(recommendation("1", "federal_single_mandate", "УмГ, округ 1")),
        )
        val supplement = recommendationSet(
            id = "new-parliament",
            title = "Новый парламент — Госдума 2026",
            recommendations = listOf(
                recommendation("1", "federal_single_mandate", "НП, округ 1"),
                recommendation("2", "federal_single_mandate", "НП, округ 2"),
            ),
        )

        val result = resolveRecommendationMatches(
            sets = listOf(primary, supplement),
            regionQuery = "",
            cityQuery = "",
            districtQuery = "",
            precinctQuery = "",
            ballotTypeQuery = "",
            interactionMode = RecommendationInteractionMode.COMPLEMENT_BY_PRIORITY,
            prioritySetId = primary.id,
        )

        assertEquals(listOf("УмГ, округ 1"), result[0].recommendations.map { it.choice })
        assertEquals(listOf("НП, округ 2"), result[1].recommendations.map { it.choice })
    }

    @Test
    fun local_state_round_trips() {
        val imported = RecommendationJson.import(sample, "", "file")
        val state = VoterLocalState(recommendationSets = listOf(imported))

        assertEquals(state, RecommendationJson.decodeState(RecommendationJson.encodeState(state)))
    }

    @Test
    fun bundled_recommendation_is_installed_once_and_preserves_local_data() {
        val localChoice = PersonalVoteChoice(id = "mine", ballotType = "other", choice = "Мой выбор")
        val initial = VoterLocalState(personalChoices = listOf(localChoice))

        val installed = RecommendationJson.installBundled(listOf(sample, sample), initial)

        assertEquals(BUNDLED_RECOMMENDATION_VERSION, installed.bundledRecommendationVersion)
        assertEquals(listOf(localChoice), installed.personalChoices)
        assertEquals("Совет автора", installed.recommendationSets.single().displayName)
        assertEquals(installed, RecommendationJson.installBundled(listOf(sample, sample), installed))
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

    private fun recommendationSet(
        id: String,
        title: String,
        recommendations: List<VoteRecommendation>,
    ) = ImportedRecommendationSet(
        id = id,
        displayName = title,
        source = "test",
        rawSha256 = id,
        pack = RecommendationPack(title = title, recommendations = recommendations),
    )

    private fun recommendation(districtNumber: String, ballotType: String, choice: String) = VoteRecommendation(
        region = "Регион",
        district = "Округ $districtNumber",
        districtNumber = districtNumber,
        ballotType = ballotType,
        choice = choice,
    )
}
