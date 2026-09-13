package org.yanavybori.feature.voter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.yanavybori.core.crypto.Sha256
import org.yanavybori.core.ui.AnonymousSignature

class BallotArchiveTest {
    private val photo = "sanitized-jpeg-placeholder".encodeToByteArray()
    private val signature = AnonymousSignature(
        algorithm = "ECDSA-P256-SHA256",
        publicKeyFormat = "X.509-SPKI",
        signatureFormat = "ASN.1-DER",
        publicKeyBase64 = "cHVibGlj",
        signatureBase64 = "c2lnbmF0dXJl",
    )

    @Test
    fun exportContainsOnlyAnonymousElectionDataAndIntegrityFields() {
        val payload = BallotArchiveJson.normalizedPayload(
            BallotDraft(
                election = "Выборы депутатов",
                region = "Москва",
                precinctNumber = "101",
                ballotType = "municipal",
                choice = "Кандидат 3",
                votingDate = "2026-09-20",
            ),
            Sha256.digest(photo),
        )
        val record = BallotArchiveJson.createRecord(payload, photo.size, signature)
        val raw = BallotArchiveJson.export(record, photo).decodeToString()
        val decoded = BallotArchiveJson.decodeExport(raw)

        assertEquals(payload, decoded.payload)
        assertEquals(record.signature, decoded.integrity)
        assertTrue(decoded.signedPayloadBase64.isNotBlank())
        assertTrue("org.yanavybori.anonymous-ballot" in raw)
        assertFalse("userId" in raw)
        assertFalse("deviceId" in raw)
        assertFalse("originalName" in raw)
        assertFalse("capturedAt" in raw)
    }

    @Test
    fun exportRejectsChangedPhoto() {
        val payload = BallotArchiveJson.normalizedPayload(
            BallotDraft("Выборы", "Регион", "77", "other", "Вариант"),
            Sha256.digest(photo),
        )
        val record = BallotArchiveJson.createRecord(payload, photo.size, signature)
        val changed = photo.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }

        assertFailsWith<IllegalArgumentException> { BallotArchiveJson.export(record, changed) }
    }

    @Test
    fun invalidDateIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            BallotArchiveJson.normalizedPayload(
                BallotDraft("Выборы", "Регион", "77", "other", "Вариант", "20.09.2026"),
                Sha256.digest(photo),
            )
        }
    }
}
