package org.yanavybori.feature.voter

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.yanavybori.core.crypto.Sha256

internal const val VOTER_STATE_KEY = "voter-recommendations.v1"
internal const val MAX_RECOMMENDATION_JSON_BYTES = 2 * 1024 * 1024
internal const val OVD_INFO_ELECTION_GUIDE_URL =
    "https://ovdinfo.legal/instruction/ya-khochu-poyti-na-vybory-kak-podgotovitsya-i-obezopasit-sebya"

@Serializable
internal data class RecommendationPack(
    val schemaVersion: Int = 1,
    val title: String,
    val publisher: String? = null,
    val election: String? = null,
    val publishedAt: String? = null,
    val sourceUrl: String? = null,
    val contentSha256: String? = null,
    val recommendations: List<VoteRecommendation>,
)

@Serializable
internal data class VoteRecommendation(
    val region: String? = null,
    val city: String? = null,
    val district: String? = null,
    val precinct: String? = null,
    val ballotType: String,
    val choice: String,
    val party: String? = null,
    val candidateNumber: String? = null,
    val note: String? = null,
)

@Serializable
internal data class ImportedRecommendationSet(
    val id: String,
    val displayName: String,
    val source: String,
    val rawSha256: String,
    val expectedSha256: String? = null,
    val pack: RecommendationPack,
)

@Serializable
internal data class PersonalVoteChoice(
    val id: String,
    val region: String? = null,
    val city: String? = null,
    val district: String? = null,
    val ballotType: String,
    val choice: String,
    val note: String? = null,
)

@Serializable
internal data class VoterLocalState(
    val recommendationSets: List<ImportedRecommendationSet> = emptyList(),
    val personalChoices: List<PersonalVoteChoice> = emptyList(),
)

internal object RecommendationJson {
    private val strictJson = Json {
        ignoreUnknownKeys = false
        isLenient = false
        explicitNulls = false
        encodeDefaults = true
    }
    private val storageJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun import(
        raw: String,
        displayName: String,
        source: String,
        expectedSha256: String = "",
    ): ImportedRecommendationSet {
        require(raw.encodeToByteArray().size <= MAX_RECOMMENDATION_JSON_BYTES) { "JSON-файл слишком большой" }
        val pack = strictJson.decodeFromString<RecommendationPack>(raw)
        validate(pack)

        val rawHash = Sha256.digest(raw.encodeToByteArray())
        val expected = expectedSha256.trim().lowercase().takeIf(String::isNotBlank)
        if (expected != null) {
            require(SHA_256.matches(expected)) { "Контрольная сумма должна содержать 64 шестнадцатеричных символа" }
            require(rawHash == expected) { "SHA-256 файла не совпадает с указанной контрольной суммой" }
        }

        pack.contentSha256?.let { declared ->
            val normalized = declared.trim().lowercase()
            require(SHA_256.matches(normalized)) { "contentSha256 имеет неверный формат" }
            val canonicalRecommendations = strictJson.encodeToString(pack.recommendations).encodeToByteArray()
            require(Sha256.digest(canonicalRecommendations) == normalized) {
                "contentSha256 не совпадает с содержимым recommendations"
            }
        }

        return ImportedRecommendationSet(
            id = rawHash,
            displayName = displayName.trim().takeIf(String::isNotBlank) ?: pack.title.trim(),
            source = source.trim().take(MAX_TEXT_LENGTH),
            rawSha256 = rawHash,
            expectedSha256 = expected,
            pack = pack.normalized(),
        )
    }

    fun decodeState(raw: String?): VoterLocalState =
        raw?.takeIf(String::isNotBlank)?.let { storageJson.decodeFromString<VoterLocalState>(it) }
            ?: VoterLocalState()

    fun encodeState(state: VoterLocalState): String = storageJson.encodeToString(state)

    private fun validate(pack: RecommendationPack) {
        require(pack.schemaVersion == 1) { "Поддерживается только schemaVersion 1" }
        require(pack.title.isNotBlank()) { "У набора отсутствует title" }
        require(pack.title.length <= 160) { "Название набора слишком длинное" }
        require(pack.recommendations.isNotEmpty()) { "Список recommendations пуст" }
        require(pack.recommendations.size <= 5_000) { "В одном наборе допускается не более 5000 рекомендаций" }
        listOf(pack.publisher, pack.election, pack.publishedAt, pack.sourceUrl).forEach { value ->
            require(value == null || value.length <= MAX_TEXT_LENGTH) { "Одно из полей набора слишком длинное" }
        }
        pack.sourceUrl?.let {
            require(it.startsWith("https://")) { "sourceUrl должен быть HTTPS-ссылкой" }
        }
        pack.recommendations.forEachIndexed { index, item ->
            require(item.ballotType.isNotBlank()) { "У рекомендации ${index + 1} отсутствует ballotType" }
            require(item.choice.isNotBlank()) { "У рекомендации ${index + 1} отсутствует choice" }
            listOf(
                item.region,
                item.city,
                item.district,
                item.precinct,
                item.ballotType,
                item.choice,
                item.party,
                item.candidateNumber,
                item.note,
            ).forEach { value ->
                require(value == null || value.length <= MAX_TEXT_LENGTH) {
                    "Слишком длинное поле в рекомендации ${index + 1}"
                }
            }
        }
    }

    private fun RecommendationPack.normalized() = copy(
        title = title.trim(),
        publisher = publisher.clean(),
        election = election.clean(),
        publishedAt = publishedAt.clean(),
        sourceUrl = sourceUrl.clean(),
        contentSha256 = contentSha256?.trim()?.lowercase(),
        recommendations = recommendations.map { item ->
            item.copy(
                region = item.region.clean(),
                city = item.city.clean(),
                district = item.district.clean(),
                precinct = item.precinct.clean(),
                ballotType = item.ballotType.trim(),
                choice = item.choice.trim(),
                party = item.party.clean(),
                candidateNumber = item.candidateNumber.clean(),
                note = item.note.clean(),
            )
        },
    )

    private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotBlank)
    private val SHA_256 = Regex("^[0-9a-f]{64}$")
    private const val MAX_TEXT_LENGTH = 2_000
}

internal val knownBallotTypes = linkedMapOf(
    "federal_party_list" to "Федеральный партийный список",
    "federal_single_mandate" to "Федеральный одномандатный округ",
    "regional_party_list" to "Региональный партийный список",
    "regional_single_mandate" to "Региональный одномандатный округ",
    "municipal" to "Муниципальный бюллетень",
    "other" to "Другой бюллетень",
)

internal fun ballotTypeLabel(value: String): String = knownBallotTypes[value] ?: value

internal fun VoteRecommendation.matches(
    regionQuery: String,
    cityQuery: String,
    districtQuery: String,
    precinctQuery: String,
    ballotTypeQuery: String,
): Boolean =
    matchesField(region, regionQuery) &&
        matchesField(city, cityQuery) &&
        matchesField(district, districtQuery) &&
        matchesField(precinct, precinctQuery) &&
        (ballotTypeQuery.isBlank() || ballotType.equals(ballotTypeQuery, ignoreCase = true))

private fun matchesField(value: String?, query: String): Boolean {
    if (query.isBlank()) return true
    if (value.isNullOrBlank() || value == "*") return true
    return value.contains(query.trim(), ignoreCase = true)
}
