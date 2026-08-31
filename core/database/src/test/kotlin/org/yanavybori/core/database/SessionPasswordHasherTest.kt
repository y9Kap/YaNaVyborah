package org.yanavybori.core.database

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPasswordHasherTest {
    private val hasher = SessionPasswordHasher(iterations = 10)

    @Test
    fun hash_uses_salt_and_accepts_only_the_original_password() {
        val first = hasher.create("секрет")
        val second = hasher.create("секрет")

        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.encodedHash, second.encodedHash)
        assertTrue(hasher.matches("секрет", first.salt, first.encodedHash))
        assertFalse(hasher.matches("другой", first.salt, first.encodedHash))
    }

    @Test(expected = IllegalArgumentException::class)
    fun short_password_is_rejected() {
        hasher.create("123")
    }
}
