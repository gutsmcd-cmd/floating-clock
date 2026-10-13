package io.github.gutsmcd.floatingclock

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import java.util.Locale

/** Tiny settings store plus the in-app language switch (English default). */
object Prefs {
    private const val FILE = "settings"
    const val LANG_EN = "en"
    const val LANG_JA = "ja"

    const val SIZE_TINY = 0
    const val SIZE_S = 1
    const val SIZE_M = 2
    const val SIZE_L = 3
    const val SCALE_MIN = 0.6f
    const val SCALE_MAX = 3.0f

    const val COLOR_WHITE = "white"
    const val COLOR_BLACK = "black"
    const val COLOR_GREEN = "green"
    const val COLOR_YELLOW = "yellow"
    const val COLOR_CUSTOM = "custom"

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private fun put(c: Context, k: String, v: Any) {
        val e = sp(c).edit()
        when (v) {
            is Boolean -> e.putBoolean(k, v)
            is Int -> e.putInt(k, v)
            is String -> e.putString(k, v)
        }
        e.apply()
    }

    fun lang(c: Context): String = sp(c).getString("lang", LANG_EN) ?: LANG_EN
    fun setLang(c: Context, v: String) = put(c, "lang", v)

    fun seconds(c: Context) = sp(c).getBoolean("seconds", false)
    fun setSeconds(c: Context, v: Boolean) = put(c, "seconds", v)
    fun showDate(c: Context) = sp(c).getBoolean("date", false)
    fun setShowDate(c: Context, v: Boolean) = put(c, "date", v)
    fun showBattery(c: Context) = sp(c).getBoolean("battery", false)
    fun setShowBattery(c: Context, v: Boolean) = put(c, "battery", v)
    fun use24h(c: Context) = sp(c).getBoolean("h24", true)
    fun setUse24h(c: Context, v: Boolean) = put(c, "h24", v)
    fun size(c: Context) = sp(c).getInt("size", SIZE_TINY)
    /** Picking a preset resets the pinch scale. */
    fun setSize(c: Context, v: Int) = sp(c).edit().putInt("size", v).putFloat("scale", 1f).apply()
    /** Extra pinch-to-resize factor on top of the preset. */
    fun scale(c: Context) = sp(c).getFloat("scale", 1f).coerceIn(SCALE_MIN, SCALE_MAX)
    fun setScale(c: Context, v: Float) = sp(c).edit().putFloat("scale", v.coerceIn(SCALE_MIN, SCALE_MAX)).apply()
    fun color(c: Context) = sp(c).getString("color", COLOR_WHITE) ?: COLOR_WHITE
    fun setColor(c: Context, v: String) = put(c, "color", v)
    fun customColor(c: Context) = sp(c).getString("custom_color", "#FF6B9D") ?: "#FF6B9D"
    fun setCustomColor(c: Context, v: String) = put(c, "custom_color", v)
    fun pill(c: Context) = sp(c).getBoolean("pill", true)
    fun setPill(c: Context, v: Boolean) = put(c, "pill", v)
    fun opacity(c: Context) = sp(c).getInt("opacity", 90)
    fun setOpacity(c: Context, v: Int) = put(c, "opacity", v)
    fun locked(c: Context) = sp(c).getBoolean("locked", false)
    fun setLocked(c: Context, v: Boolean) = put(c, "locked", v)
    fun snap(c: Context) = sp(c).getBoolean("snap", true)
    fun setSnap(c: Context, v: Boolean) = put(c, "snap", v)
    fun notifShortcut(c: Context) = sp(c).getBoolean("notif_shortcut", true)
    fun setNotifShortcut(c: Context, v: Boolean) = put(c, "notif_shortcut", v)
    fun overlayAsked(c: Context) = sp(c).getBoolean("overlay_asked", false)
    fun setOverlayAsked(c: Context, v: Boolean) = put(c, "overlay_asked", v)

    /** Saved position; -1 means "not set yet" (top right on first show). */
    fun posX(c: Context) = sp(c).getInt("x", -1)
    fun posY(c: Context) = sp(c).getInt("y", -1)
    fun setPos(c: Context, x: Int, y: Int) = sp(c).edit().putInt("x", x).putInt("y", y).apply()

    fun presetSp(c: Context): Float = ClockMath.presetSp(size(c))
    fun textSp(c: Context): Float = ClockMath.textSp(size(c), scale(c))

    fun textColor(c: Context): Int = when (color(c)) {
        COLOR_BLACK -> Color.BLACK
        COLOR_GREEN -> 0xFF39E07A.toInt()
        COLOR_YELLOW -> 0xFFFFD43B.toInt()
        COLOR_CUSTOM -> ClockMath.parseColor(customColor(c)) ?: Color.WHITE
        else -> Color.WHITE
    }

    /** A Context whose resources use the language picked inside the app. */
    fun localized(c: Context): Context {
        val conf = Configuration(c.resources.configuration)
        conf.setLocale(Locale.forLanguageTag(lang(c)))
        return c.createConfigurationContext(conf)
    }
}
