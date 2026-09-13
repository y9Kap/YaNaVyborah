package org.yanavybori.feature.voter

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.yanavybori.core.crypto.Sha256
import org.yanavybori.core.ui.AnonymousSignature

internal const val MAX_BALLOT_PHOTO_SOURCE_BYTES = 15 * 1024 * 1024
internal const val MAX_BALLOT_PHOTO_STORED_BYTES = 6 * 1024 * 1024
internal const val BALLOT_PHOTO_MAX_DIMENSION = 2200

@Serializable
internal data class BallotDraft(
    val election: String,
    val region: String,
    val precinctNumber: String,
    val ballotType: String,
    val choice: String,
    val votingDate: String? = null,
)

@Serializable
internal data class SignedBallotPayload(
    val schemaVersion: Int = 1,
    val election: String,
    val region: String,
    val precinctNumber: String,
    val ballotType: String,
    val choice: String,
    val votingDate: String? = null,
    val photoSha256: String,
    val photoMediaType: String = "image/jpeg",
)

@Serializable
internal data class BallotSignature(
    val algorithm: String,
    val publicKeyFormat: String,
    val signatureFormat: String,
    val publicKeyBase64: String,
    val signatureBase64: String,
    val signedPayloadSha256: String,
)

@Serializable
internal data class SavedBallotRecord(
    val id: String,
    val payload: SignedBallotPayload,
    val photoStorageKey: String,
    val photoSize: Int,
    val signature: BallotSignature,
)

@Serializable
internal data class AnonymousBallotExport(
    val format: String = "org.yanavybori.anonymous-ballot",
    val formatVersion: Int = 1,
    val payload: SignedBallotPayload,
    val integrity: BallotSignature,
    val signedPayloadBase64: String,
    val photoBase64: String,
)

internal object BallotArchiveJson {
    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = false
    }
    private val prettyJson = Json {
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = true
    }

    fun normalizedPayload(draft: BallotDraft, photoSha256: String): SignedBallotPayload {
        val normalized = BallotDraft(
            election = draft.election.trim(),
            region = draft.region.trim(),
            precinctNumber = draft.precinctNumber.trim(),
            ballotType = draft.ballotType.trim(),
            choice = draft.choice.trim(),
            votingDate = draft.votingDate?.trim()?.takeIf(String::isNotBlank),
        )
        require(normalized.election.isNotBlank()) { "Укажите выборы" }
        require(normalized.region.isNotBlank()) { "Укажите регион" }
        require(normalized.precinctNumber.isNotBlank()) { "Укажите номер УИК" }
        require(normalized.ballotType.isNotBlank()) { "Укажите тип бюллетеня" }
        require(normalized.choice.isNotBlank()) { "Укажите отметку в бюллетене" }
        listOf(
            normalized.election,
            normalized.region,
            normalized.precinctNumber,
            normalized.ballotType,
            normalized.choice,
        ).forEach { require(it.length <= 300) { "Одно из полей слишком длинное" } }
        normalized.votingDate?.let {
            require(DATE.matches(it)) { "Дата должна иметь формат ГГГГ-ММ-ДД" }
        }
        val normalizedHash = photoSha256.lowercase()
        require(SHA_256.matches(normalizedHash)) { "Некорректная контрольная сумма фотографии" }
        return SignedBallotPayload(
            election = normalized.election,
            region = normalized.region,
            precinctNumber = normalized.precinctNumber,
            ballotType = normalized.ballotType,
            choice = normalized.choice,
            votingDate = normalized.votingDate,
            photoSha256 = normalizedHash,
        )
    }

    fun signingBytes(payload: SignedBallotPayload): ByteArray = json.encodeToString(payload).encodeToByteArray()

    fun createRecord(
        payload: SignedBallotPayload,
        photoSize: Int,
        anonymousSignature: AnonymousSignature,
    ): SavedBallotRecord {
        require(photoSize in 1..MAX_BALLOT_PHOTO_STORED_BYTES) { "Фотография слишком большая" }
        val signedBytes = signingBytes(payload)
        val id = Sha256.digest(signedBytes)
        return SavedBallotRecord(
            id = id,
            payload = payload,
            photoStorageKey = "voter-ballot-$id.jpg",
            photoSize = photoSize,
            signature = BallotSignature(
                algorithm = anonymousSignature.algorithm,
                publicKeyFormat = anonymousSignature.publicKeyFormat,
                signatureFormat = anonymousSignature.signatureFormat,
                publicKeyBase64 = anonymousSignature.publicKeyBase64,
                signatureBase64 = anonymousSignature.signatureBase64,
                signedPayloadSha256 = id,
            ),
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun export(record: SavedBallotRecord, photoBytes: ByteArray): ByteArray {
        require(photoBytes.size == record.photoSize) { "Размер сохранённой фотографии изменился" }
        require(Sha256.digest(photoBytes) == record.payload.photoSha256) {
            "Контрольная сумма фотографии не совпадает"
        }
        require(Sha256.digest(signingBytes(record.payload)) == record.signature.signedPayloadSha256) {
            "Подписанные данные были изменены"
        }
        return prettyJson.encodeToString(
            AnonymousBallotExport(
                payload = record.payload,
                integrity = record.signature,
                signedPayloadBase64 = Base64.encode(signingBytes(record.payload)),
                photoBase64 = Base64.encode(photoBytes),
            ),
        ).encodeToByteArray()
    }

    fun decodeExport(raw: String): AnonymousBallotExport = json.decodeFromString(raw)

    private val DATE = Regex("^\\d{4}-(0[1-9]|1[0-2])-([0-2]\\d|3[01])$")
    private val SHA_256 = Regex("^[0-9a-f]{64}$")
}

internal fun ballotExportFileName(record: SavedBallotRecord): String {
    val precinct = record.payload.precinctNumber
        .replace(Regex("[^\\p{L}\\p{N}._-]+"), "-")
        .trim('-')
        .take(40)
        .ifBlank { "unknown" }
    return "anonymous-ballot-uik-$precinct-${record.id.take(12)}.json"
}
