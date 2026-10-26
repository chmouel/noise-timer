package com.chmouel.noisetimer

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TimerDisplayTest {
    @Test
    fun `countdown rounds positive fractions up to the next second`() {
        assertEquals("0:00", formatTimerMillis(0, Locale.US))
        assertEquals("0:00", formatTimerMillis(-1, Locale.US))
        assertEquals("0:01", formatTimerMillis(1, Locale.US))
        assertEquals("1:00", formatTimerMillis(59_001, Locale.US))
        assertEquals("1:00", formatTimerMillis(60_000, Locale.US))
    }

    @Test
    fun `countdown uses hours only when needed`() {
        assertEquals("59:59", formatTimerMillis(3_599_000, Locale.US))
        assertEquals("1:00:00", formatTimerMillis(3_599_001, Locale.US))
        assertEquals("1:01:01", formatTimerMillis(3_661_000, Locale.US))
        assertEquals("16:39:00", formatTimerMillis(999 * 60_000L, Locale.US))
    }
}
