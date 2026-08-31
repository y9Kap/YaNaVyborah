package org.yanavybori.core.crypto

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class Sha256Test {
    @Test
    fun sha256_matches_known_vector() {
        val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertEquals(expected, Sha256.digest("abc".encodeToByteArray()))
        assertEquals(expected, Sha256.digest(ByteArrayInputStream("abc".encodeToByteArray())))
    }
}
