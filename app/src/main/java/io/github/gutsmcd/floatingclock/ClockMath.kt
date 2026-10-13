package io.github.gutsmcd.floatingclock

import java.util.Calendar

/** Pure helpers (no Android types) so they can be unit tested. */
object ClockMath {

    /** "14:05", "2:05 PM", optionally with seconds. */
    fun formatTime(cal: Calendar, use24h: Boolean, seconds: Boolean, am: String = "AM", pm: String = "PM"): String {
        val h24 = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        val s = cal.get(Calendar.SECOND)
        val sec = if (seconds) ":" + two(s) else ""
        return if (use24h) {
            two(h24) + ":" + two(m) + sec
        } else {
            val h12 = if (h24 % 12 == 0) 12 else h24 % 12
            "$h12:${two(m)}$sec ${if (h24 < 12) am else pm}"
        }
    }

    private fun two(n: Int) = if (n < 10) "0$n" else n.toString()

    /** Milliseconds until the next whole second or minute boundary (at least 1 ms). */
    fun delayToNextTick(nowMs: Long, seconds: Boolean): Long {
        val unit = if (seconds) 1000L else 60_000L
        val d = unit - Math.floorMod(nowMs, unit)
        return if (d <= 0) unit else d
    }

    /** Keep the window fully on screen. */
    fun clamp(pos: Int, size: Int, screen: Int): Int =
        if (size >= screen) 0 else pos.coerceIn(0, screen - size)

    /** Snap to the nearer left/right edge, keeping a small margin. */
    fun snapX(x: Int, width: Int, screenW: Int, margin: Int): Int {
        val center = x + width / 2
        return if (center < screenW / 2) margin else (screenW - width - margin).coerceAtLeast(0)
    }

    /** Parse "#RRGGBB" / "RRGGBB" / "#AARRGGBB" into an ARGB int, or null. */
    fun parseColor(s: String): Int? {
        val t = s.trim().removePrefix("#")
        if (!t.matches(Regex("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}"))) return null
        val v = t.toLong(16)
        return if (t.length == 6) (0xFF000000L or v).toInt() else v.toInt()
    }

    /** Text size per preset: Tiny (default), Small, Medium, Large. */
    fun presetSp(size: Int): Float = when (size) { 0 -> 10.5f; 1 -> 13f; 2 -> 17f; else -> 26f }

    /** Preset times pinch scale, kept between 8 sp and 64 sp. */
    fun textSp(size: Int, scale: Float): Float = (presetSp(size) * scale).coerceIn(8f, 64f)

    /** Android 12+ drops pass-through touches over overlays more opaque than 0.8. */
    fun windowAlpha(opacityPct: Int, locked: Boolean): Float {
        val a = opacityPct.coerceIn(20, 100) / 100f
        return if (locked) minOf(a, 0.8f) else a
    }
}
