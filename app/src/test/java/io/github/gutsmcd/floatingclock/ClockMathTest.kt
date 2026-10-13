package io.github.gutsmcd.floatingclock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class ClockMathTest {
    private fun cal(h: Int, m: Int, s: Int) = Calendar.getInstance().apply {
        set(2026, 9, 13, h, m, s)
    }

    @Test fun format24() {
        assertEquals("14:05", ClockMath.formatTime(cal(14, 5, 9), true, false))
        assertEquals("00:00:07", ClockMath.formatTime(cal(0, 0, 7), true, true))
    }

    @Test fun format12() {
        assertEquals("2:05 PM", ClockMath.formatTime(cal(14, 5, 0), false, false))
        assertEquals("12:00 AM", ClockMath.formatTime(cal(0, 0, 0), false, false))
        assertEquals("12:30:01 PM", ClockMath.formatTime(cal(12, 30, 1), false, true))
        assertEquals("9:15 午前", ClockMath.formatTime(cal(9, 15, 0), false, false, "午前", "午後"))
    }

    @Test fun tickDelay() {
        assertEquals(1000L, ClockMath.delayToNextTick(5_000L, true))
        assertEquals(250L, ClockMath.delayToNextTick(5_750L, true))
        assertEquals(60_000L, ClockMath.delayToNextTick(120_000L, false))
        assertEquals(1L, ClockMath.delayToNextTick(119_999L, false))
    }

    @Test fun clampAndSnap() {
        assertEquals(0, ClockMath.clamp(-20, 100, 1080))
        assertEquals(980, ClockMath.clamp(2000, 100, 1080))
        assertEquals(0, ClockMath.clamp(50, 2000, 1080))
        assertEquals(10, ClockMath.snapX(300, 100, 1080, 10))
        assertEquals(970, ClockMath.snapX(600, 100, 1080, 10))
    }

    @Test fun colors() {
        assertEquals(0xFFFF6B9D.toInt(), ClockMath.parseColor("#FF6B9D"))
        assertEquals(0x80112233.toInt(), ClockMath.parseColor("80112233"))
        assertNull(ClockMath.parseColor("pink"))
        assertNull(ClockMath.parseColor("#12345"))
    }

    @Test fun alphaCappedWhenLocked() {
        assertEquals(1.0f, ClockMath.windowAlpha(100, false), 0.001f)
        assertEquals(0.8f, ClockMath.windowAlpha(100, true), 0.001f)
        assertEquals(0.5f, ClockMath.windowAlpha(50, true), 0.001f)
        assertEquals(0.2f, ClockMath.windowAlpha(5, false), 0.001f)
    }

    @Test fun sizes() {
        assertEquals(10.5f, ClockMath.textSp(0, 1f), 0.001f)
        assertEquals(17f, ClockMath.textSp(2, 1f), 0.001f)
        assertEquals(8f, ClockMath.textSp(0, 0.6f), 0.001f)
        assertEquals(64f, ClockMath.textSp(3, 3f), 0.001f)
    }
}
