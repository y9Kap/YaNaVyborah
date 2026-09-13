package org.yanavybori.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.yanavybori.core.model.UserDataSnapshot

class UserDataExportTest {
    @Test
    fun exportHasStableFormatAndDoesNotContainPasswordMaterial() {
        val raw = encodeUserDataExport(
            applicationVersion = "test-version",
            exportedAt = 42,
            snapshot = UserDataSnapshot(),
            voterState = JsonNull,
            mediaFiles = emptyList(),
            ballotPhotos = emptyList(),
        ).decodeToString()
        val root = Json.parseToJsonElement(raw).jsonObject

        assertEquals("org.yanavybori.user-data-export", root.getValue("format").jsonPrimitive.content)
        assertEquals("test-version", root.getValue("applicationVersion").jsonPrimitive.content)
        assertTrue("observerData" in root)
        assertTrue("voterData" in root)
        assertFalse("password" in raw.lowercase())
    }
}
