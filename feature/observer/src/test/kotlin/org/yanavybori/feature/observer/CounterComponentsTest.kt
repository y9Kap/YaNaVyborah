package org.yanavybori.feature.observer

import org.junit.Assert.assertEquals
import org.junit.Test

class CounterComponentsTest {
    @Test
    fun elapsed_timer_formats_minutes_and_hours() {
        assertEquals("00:00", formatCounterElapsed(-1))
        assertEquals("01:05", formatCounterElapsed(65_999))
        assertEquals("01:01:01", formatCounterElapsed(3_661_000))
    }
}
