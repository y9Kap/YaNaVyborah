package org.yanavybori.core.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256VectorsTest {
    @Test fun standard_vectors_match_on_every_target() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.digest(byteArrayOf()))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.digest("abc".encodeToByteArray()))
        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", Sha256.digest(ByteArray(1_000_000) { 97 }))
    }
}
