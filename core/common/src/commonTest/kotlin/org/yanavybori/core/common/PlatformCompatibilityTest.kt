package org.yanavybori.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.datetime.TimeZone

class PlatformCompatibilityTest {
    @Test fun counter_keeps_checked_long_arithmetic() {
        assertEquals(Long.MAX_VALUE, CounterPolicy.increment(Long.MAX_VALUE - 1))
        assertFailsWith<ArithmeticException> { CounterPolicy.increment(Long.MAX_VALUE) }
        assertEquals(9_007_199_254_740_993L, CounterPolicy.increment(9_007_199_254_740_992L))
    }

    @Test fun dates_keep_local_timezone_and_zero_padding() {
        assertEquals("01.01.1970 00:00:00", formatLocalTimestamp(0, TimeZone.UTC))
        assertEquals("19700101-0300", formatFileTimestamp(0, TimeZone.of("UTC+03:00")))
        assertEquals("31.12.1969 23:59:59", formatLocalTimestamp(-1, TimeZone.UTC))
    }

    @Test fun ids_keep_uuid_format_and_clock_uses_epoch_milliseconds() {
        val first = UuidGenerator.newId()
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(first))
        assertNotEquals(first, UuidGenerator.newId())
        assertTrue(SystemClock.now() > 1_700_000_000_000L)
    }
}
