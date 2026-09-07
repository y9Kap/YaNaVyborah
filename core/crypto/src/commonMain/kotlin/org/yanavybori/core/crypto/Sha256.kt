package org.yanavybori.core.crypto

import okio.ByteString.Companion.toByteString

object Sha256 {
    fun digest(bytes: ByteArray): String = bytes.toByteString().sha256().hex()
}
