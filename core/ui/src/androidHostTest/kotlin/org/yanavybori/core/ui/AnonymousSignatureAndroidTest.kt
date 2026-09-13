package org.yanavybori.core.ui

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class AnonymousSignatureAndroidTest {
    @Test
    fun generatedSignatureVerifiesAndDoesNotVerifyChangedPayload() {
        val payload = "anonymous ballot payload".encodeToByteArray()
        val result = createAnonymousSignature(payload)
        val publicKey = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(Base64.decode(result.publicKeyBase64)),
        )
        val signature = Base64.decode(result.signatureBase64)

        assertTrue(verify(publicKey, payload, signature))
        assertFalse(verify(publicKey, "changed payload".encodeToByteArray(), signature))
    }

    private fun verify(
        publicKey: java.security.PublicKey,
        payload: ByteArray,
        signatureBytes: ByteArray,
    ): Boolean = Signature.getInstance("SHA256withECDSA").run {
        initVerify(publicKey)
        update(payload)
        verify(signatureBytes)
    }
}
