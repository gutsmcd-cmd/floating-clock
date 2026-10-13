package io.github.gutsmcd.floatingclock

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.service.quicksettings.TileService
import android.text.format.DateFormat
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Owns the small floating clock window. Process-level so the tile, service and activity
 * share one state. Main thread only. The window is WRAP_CONTENT and never focusable, so
 * the rest of the screen keeps working normally.
 */
@SuppressLint("StaticFieldLeak") // holds application-context views only
object ClockController {
    private const val TAG = "FloatingClock"

    private val main = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var timeView: TextView? = null
    private var infoView: TextView? = null
    private var lp: WindowManager.LayoutParams? = null
    private var app: Context? = null
    private var battery: Int = -1
    private var receiversOn = false
    private val listeners = mutableSetOf<() -> Unit>()

    val isShowing: Boolean get() = root != null

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }
    fun canDraw(c: Context): Boolean = Settings.canDrawOverlays(c)

    fun show(context: Context): Boolean {
        val a = context.applicationContext
        if (isShowing) return true
        if (!canDraw(a)) return false
        val wm = a.getSystemService(WindowManager::class.java) ?: return false
        app = a
        val view = buildView(a)
        val params = baseParams(a)
        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            Log.w(TAG, "addView failed", e)
            return false
        }
        root = view; lp = params
        applySettings(a)
        registerReceivers(a)
        // First show: place top-right once we know our size.
        if (Prefs.posX(a) < 0) view.post { placeDefault(a) }
        notifyChanged(a)
        return true
    }

    fun hide(context: Context) {
        val a = context.applicationContext
        val view = root ?: return
        main.removeCallbacks(tick)
        unregisterReceivers(a)
        root = null; timeView = null; infoView = null; lp = null
        try {
            a.getSystemService(WindowManager::class.java)?.removeViewImmediate(view)
        } catch (e: Exception) {
            Log.w(TAG, "removeView failed", e)
        }
        notifyChanged(a)
    }

    fun toggle(context: Context) { if (isShowing) hide(context) else show(context) }

    private fun notifyChanged(a: Context) {
        listeners.toList().forEach { it() }
        try {
            TileService.requestListeningState(a, ComponentName(a, ClockTileService::class.java))
        } catch (_: Exception) { }
        ClockService.refresh(a)
    }

    /** Re-read every setting and restyle the live clock (called after any settings change). */
    fun applySettings(context: Context) {
        val a = context.applicationContext
        val view = root ?: return
        val params = lp ?: return
        val tv = timeView ?: return
        val info = infoView ?: return
        val color = Prefs.textColor(a)
        tv.setTextColor(color)
        info.setTextColor(color)
        applyTextSize(a, Prefs.textSp(a))
        view.background = if (Prefs.pill(a)) GradientDrawable().apply {
            // Dark pill behind light text, light pill behind black text.
            setColor(if (isDark(color)) 0x99FFFFFF.toInt() else 0x99000000.toInt())
            cornerRadius = dp(a, 999f).toFloat()
        } else null
        val shadow = if (Prefs.pill(a)) 0f else 3f
        val shadowColor = if (isDark(color)) Color.WHITE else Color.BLACK
        tv.setShadowLayer(shadow, 0f, 1f, shadowColor)
        info.setShadowLayer(shadow, 0f, 1f, shadowColor)

        val locked = Prefs.locked(a)
        params.alpha = ClockMath.windowAlpha(Prefs.opacity(a), locked)
        params.flags = baseFlags() or (if (locked) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0)
        if (Prefs.posX(a) >= 0) { params.x = Prefs.posX(a); params.y = Prefs.posY(a) }
        updateLayout(a)
        if (Prefs.showBattery(a) && receiversOn) readBatteryNow(a)
        restartTicking()
    }

    /** Text + padding for a given size; padding stays minimal so Tiny is genuinely small. */
    private fun applyTextSize(a: Context, textSp: Float) {
        val view = root ?: return
        timeView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
        infoView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, (textSp * 0.6f).coerceAtLeast(7f))
        val padH = dp(a, textSp * 0.45f); val padV = dp(a, textSp * 0.15f)
        view.setPadding(padH, padV, padH, padV)
    }

    private fun isDark(color: Int): Boolean =
        (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000 < 128

    // ---- time ----

    private val tick = object : Runnable {
        override fun run() {
            val a = app ?: return
            if (!isShowing) return
            render(a)
            main.postDelayed(this, ClockMath.delayToNextTick(System.currentTimeMillis(), Prefs.seconds(a)))
        }
    }

    private fun restartTicking() {
        main.removeCallbacks(tick)
        val a = app ?: return
        val pm = a.getSystemService(PowerManager::class.java)
        if (pm?.isInteractive == false) { render(a); return } // screen off: no ticking
        tick.run()
    }

    private fun render(a: Context) {
        val tv = timeView ?: return
        val info = infoView ?: return
        val loc = Prefs.localized(a)
        val locale = Locale.forLanguageTag(Prefs.lang(a))
        val cal = Calendar.getInstance()
        tv.text = ClockMath.formatTime(
            cal, Prefs.use24h(a), Prefs.seconds(a),
            loc.getString(R.string.am), loc.getString(R.string.pm),
        )
        val parts = ArrayList<String>()
        if (Prefs.showDate(a)) {
            val pattern = DateFormat.getBestDateTimePattern(locale, "MMMdEEE")
            parts += SimpleDateFormat(pattern, locale).format(Date(cal.timeInMillis))
        }
        if (Prefs.showBattery(a) && battery >= 0) parts += "$battery%"
        info.text = parts.joinToString("  ·  ")
        val want = if (parts.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        if (info.visibility != want) info.visibility = want
    }

    // ---- screen on/off, time changes, battery ----

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> main.removeCallbacks(tick)
                Intent.ACTION_BATTERY_CHANGED -> { battery = pct(i); app?.let { render(it) } }
                else -> restartTicking() // SCREEN_ON, TIME_SET, TIMEZONE_CHANGED
            }
        }
    }

    private fun pct(i: Intent): Int {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (level < 0 || scale <= 0) -1 else level * 100 / scale
    }

    private fun readBatteryNow(a: Context) {
        val bm = a.getSystemService(BatteryManager::class.java)
        val v = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (v in 0..100) battery = v
    }

    private fun registerReceivers(a: Context) {
        if (receiversOn) return
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) a.registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED)
            else a.registerReceiver(receiver, f)
            receiversOn = true
        } catch (e: Exception) { Log.w(TAG, "registerReceiver failed", e) }
    }

    private fun unregisterReceivers(a: Context) {
        if (!receiversOn) return
        try { a.unregisterReceiver(receiver) } catch (_: Exception) { }
        receiversOn = false
    }

    // ---- window ----

    private fun baseFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private fun baseParams(a: Context) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        baseFlags(),
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = Prefs.posX(a).coerceAtLeast(0)
        y = Prefs.posY(a).coerceAtLeast(dp(a, 48f))
        title = "Floating Clock"
    }

    private fun screen(a: Context): Rect {
        val wm = a.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30 && wm != null) wm.currentWindowMetrics.bounds
        else a.resources.displayMetrics.let { Rect(0, 0, it.widthPixels, it.heightPixels) }
    }

    private fun placeDefault(a: Context) {
        val view = root ?: return
        val params = lp ?: return
        val s = screen(a)
        params.x = (s.width() - view.width - dp(a, 12f)).coerceAtLeast(0)
        params.y = dp(a, 64f)
        Prefs.setPos(a, params.x, params.y)
        updateLayout(a)
    }

    private fun updateLayout(a: Context) {
        val view = root ?: return
        val params = lp ?: return
        try { a.getSystemService(WindowManager::class.java)?.updateViewLayout(view, params) }
        catch (e: Exception) { Log.w(TAG, "updateViewLayout failed", e) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildView(a: Context): LinearLayout {
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isHapticFeedbackEnabled = false
        }
        val tv = TextView(a).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            fontFeatureSettings = "tnum" // steady width as digits change
            includeFontPadding = false
            gravity = Gravity.CENTER
        }
        val info = TextView(a).apply {
            includeFontPadding = false
            gravity = Gravity.CENTER
            visibility = android.view.View.GONE
        }
        box.addView(tv); box.addView(info)
        timeView = tv; infoView = info

        val slop = ViewConfiguration.get(a).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragging = false
        var pinching = false
        var liveScale = 1f
        // Pinch to resize while unlocked (when locked the window is NOT_TOUCHABLE, so no touches arrive).
        val scaler = ScaleGestureDetector(a, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
                pinching = true; dragging = false
                liveScale = Prefs.scale(a)
                return true
            }
            override fun onScale(d: ScaleGestureDetector): Boolean {
                liveScale = (liveScale * d.scaleFactor).coerceIn(Prefs.SCALE_MIN, Prefs.SCALE_MAX)
                applyTextSize(a, ClockMath.textSp(Prefs.size(a), liveScale))
                return true
            }
            override fun onScaleEnd(d: ScaleGestureDetector) {
                Prefs.setScale(a, liveScale)
                listeners.toList().forEach { it() }
            }
        })
        box.setOnTouchListener { v, ev ->
            val params = lp ?: return@setOnTouchListener false
            scaler.onTouchEvent(ev)
            if (pinching || ev.pointerCount > 1) {
                if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                    pinching = false
                    // Keep the bigger/smaller clock on screen.
                    v.post {
                        val s = screen(a)
                        params.x = ClockMath.clamp(params.x, v.width, s.width())
                        params.y = ClockMath.clamp(params.y, v.height, s.height())
                        updateLayout(a); Prefs.setPos(a, params.x, params.y)
                    }
                }
                return@setOnTouchListener true
            }
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; startX = params.x; startY = params.y; dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX; val dy = ev.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        val s = screen(a)
                        params.x = ClockMath.clamp(startX + dx.toInt(), v.width, s.width())
                        params.y = ClockMath.clamp(startY + dy.toInt(), v.height, s.height())
                        updateLayout(a)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        if (Prefs.snap(a)) {
                            params.x = ClockMath.snapX(params.x, v.width, screen(a).width(), dp(a, 6f))
                            updateLayout(a)
                        }
                        Prefs.setPos(a, params.x, params.y)
                    }
                    dragging = false
                }
            }
            true
        }
        return box
    }

    private fun dp(c: Context, v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.resources.displayMetrics).toInt()
}
