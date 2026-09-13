package org.yanavybori.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class AppThemeTest {
    @Test
    fun lightThemeIsTheDefault() {
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromStored(null))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromStored("SYSTEM"))
    }

    @Test
    fun storedDarkThemeIsRestored() {
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromStored("DARK"))
    }
}
