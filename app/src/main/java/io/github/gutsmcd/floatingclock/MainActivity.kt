package io.github.gutsmcd.floatingclock

import android.Manifest
import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Single screen: permissions, Show/Hide, and settings. Built in code, no AndroidX. */
class MainActivity : Activity() {

    private lateinit var loc: Context
    private var scrollY = 0
    private val stateListener: () -> Unit = { if (!isFinishing) render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        ClockController.addListener(stateListener)
        render()
        if (ClockController.canDraw(this)) ClockService.refresh(this)
    }

    override fun onPause() {
        ClockController.removeListener(stateListener)
        super.onPause()
    }

    private fun s(id: Int) = loc.getString(id)
    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    /** Save a setting, restyle the live clock, refresh the notification, redraw this screen. */
    private fun changed(redraw: Boolean = true) {
        ClockController.applySettings(this)
        ClockService.refresh(this)
        if (redraw) render()
    }

    private fun render() {
        loc = Prefs.localized(this)
        (window?.decorView?.findViewWithTag<ScrollView>("scroll"))?.let { scrollY = it.scrollY }
        val canDraw = ClockController.canDraw(this)
        val needNotif = Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(28f), dp(20f), dp(28f))
        }
        val scroll = ScrollView(this).apply {
            tag = "scroll"
            setBackgroundColor(BG)
            fitsSystemWindows = true
            addView(col)
        }

        col.addView(text(s(R.string.app_name), 30f, Color.WHITE, bold = true))
        col.addView(text(s(R.string.tagline), 16f, GREY).apply { setPadding(0, dp(4f), 0, dp(12f)) })
        col.addView(row(
            choice("English", Prefs.lang(this) == Prefs.LANG_EN) { Prefs.setLang(this, Prefs.LANG_EN); changed() },
            choice("日本語", Prefs.lang(this) == Prefs.LANG_JA) { Prefs.setLang(this, Prefs.LANG_JA); changed() },
        ))

        if (!canDraw || needNotif) {
            col.addView(section(s(R.string.setup_title)))
            if (!canDraw) {
                col.addView(card(s(R.string.perm_overlay_title), s(R.string.perm_overlay_why), s(R.string.allow)) {
                    Prefs.setOverlayAsked(this, true)
                    openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                })
                // Android 13+ greys out this switch for sideloaded apps ("restricted setting").
                if (Build.VERSION.SDK_INT >= 33 && Prefs.overlayAsked(this)) col.addView(restrictedHelp())
            }
            if (needNotif) {
                col.addView(card(s(R.string.perm_notif_title), s(R.string.perm_notif_why), s(R.string.allow)) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                })
            }
        }

        // Show / hide
        col.addView(section(s(R.string.clock_title)))
        val showing = ClockController.isShowing
        col.addView(Button(this).apply {
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(Color.WHITE)
            background = rounded(if (!canDraw) 0xFF333333.toInt() else if (showing) 0xFF444444.toInt() else ACCENT, 16f)
            setPadding(dp(16f), dp(18f), dp(16f), dp(18f))
            isEnabled = canDraw
            text = s(if (showing) R.string.hide_clock else R.string.show_clock)
            setOnClickListener {
                if (ClockController.isShowing) ClockController.hide(this@MainActivity)
                else if (!ClockController.show(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, s(R.string.start_failed), Toast.LENGTH_LONG).show()
                }
                render()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (Build.VERSION.SDK_INT >= 33) {
            col.addView(outlineButton(s(R.string.add_tile)) { requestTile() }.apply {
                (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(12f)
            })
        }

        // Look
        col.addView(section(s(R.string.look_title)))
        col.addView(label(s(R.string.size_label)))
        col.addView(row(
            choice(s(R.string.size_tiny), Prefs.size(this) == Prefs.SIZE_TINY) { Prefs.setSize(this, Prefs.SIZE_TINY); changed() },
            choice(s(R.string.size_s), Prefs.size(this) == Prefs.SIZE_S) { Prefs.setSize(this, Prefs.SIZE_S); changed() },
            choice(s(R.string.size_m), Prefs.size(this) == Prefs.SIZE_M) { Prefs.setSize(this, Prefs.SIZE_M); changed() },
            choice(s(R.string.size_l), Prefs.size(this) == Prefs.SIZE_L) { Prefs.setSize(this, Prefs.SIZE_L); changed() },
        ))
        col.addView(text(s(R.string.pinch_hint), 13f, GREY))
        col.addView(label(s(R.string.color_label)))
        val c = Prefs.color(this)
        fun colorChoice(id: Int, key: String) = choice(s(id), c == key) { Prefs.setColor(this, key); changed() }
        col.addView(row(
            colorChoice(R.string.color_white, Prefs.COLOR_WHITE),
            colorChoice(R.string.color_black, Prefs.COLOR_BLACK),
            colorChoice(R.string.color_green, Prefs.COLOR_GREEN),
        ))
        col.addView(row(
            colorChoice(R.string.color_yellow, Prefs.COLOR_YELLOW),
            colorChoice(R.string.color_custom, Prefs.COLOR_CUSTOM),
        ))
        if (c == Prefs.COLOR_CUSTOM) {
            val input = EditText(this).apply {
                setText(Prefs.customColor(this@MainActivity))
                setTextColor(Color.WHITE)
                setHintTextColor(GREY)
                hint = "#FF6B9D"
                isSingleLine = true
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
            val apply = choice(s(R.string.apply), false) {
                val v = input.text.toString().trim()
                if (ClockMath.parseColor(v) == null) {
                    Toast.makeText(this, s(R.string.bad_color), Toast.LENGTH_SHORT).show()
                } else {
                    Prefs.setCustomColor(this, if (v.startsWith("#")) v else "#$v"); changed()
                }
            }
            col.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(apply)
            })
        }
        col.addView(switchRow(s(R.string.pill_title), s(R.string.pill_desc), Prefs.pill(this)) { Prefs.setPill(this, it); changed(false) })
        col.addView(label(s(R.string.opacity_label)))
        val pct = text(String.format(java.util.Locale.ROOT, "%d%%", Prefs.opacity(this)), 14f, LIGHT)
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(SeekBar(this@MainActivity).apply {
                max = 80 // 20..100 %
                progress = Prefs.opacity(this@MainActivity) - 20
                contentDescription = s(R.string.opacity_label)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        Prefs.setOpacity(this@MainActivity, p + 20)
                        pct.text = String.format(java.util.Locale.ROOT, "%d%%", p + 20)
                        ClockController.applySettings(this@MainActivity)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar) {}
                    override fun onStopTrackingTouch(sb: SeekBar) {}
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(pct, LinearLayout.LayoutParams(dp(52f), ViewGroup.LayoutParams.WRAP_CONTENT))
        })

        // Shows
        col.addView(section(s(R.string.shows_title)))
        col.addView(switchRow(s(R.string.h24_title), s(R.string.h24_desc), Prefs.use24h(this)) { Prefs.setUse24h(this, it); changed(false) })
        col.addView(switchRow(s(R.string.seconds_title), s(R.string.seconds_desc), Prefs.seconds(this)) { Prefs.setSeconds(this, it); changed(false) })
        col.addView(switchRow(s(R.string.date_title), s(R.string.date_desc), Prefs.showDate(this)) { Prefs.setShowDate(this, it); changed(false) })
        col.addView(switchRow(s(R.string.battery_title), s(R.string.battery_desc), Prefs.showBattery(this)) { Prefs.setShowBattery(this, it); changed(false) })

        // Position
        col.addView(section(s(R.string.position_title)))
        col.addView(switchRow(s(R.string.lock_title), s(R.string.lock_desc), Prefs.locked(this)) { Prefs.setLocked(this, it); changed(false) })
        col.addView(switchRow(s(R.string.snap_title), s(R.string.snap_desc), Prefs.snap(this)) { Prefs.setSnap(this, it); changed(false) })
        col.addView(switchRow(s(R.string.shortcut_title), s(R.string.shortcut_desc), Prefs.notifShortcut(this)) {
            Prefs.setNotifShortcut(this, it); ClockService.refresh(this)
        })

        col.addView(section(s(R.string.howto_title)))
        col.addView(text(s(R.string.howto_body), 15f, LIGHT))
        col.addView(text(s(R.string.footer), 13f, GREY).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(28f), 0, 0)
        })

        setContentView(scroll)
        val y = scrollY
        scroll.post { scroll.scrollTo(0, y) }
        title = s(R.string.app_name)
    }

    private fun requestTile() {
        if (Build.VERSION.SDK_INT < 33) return
        val sbm = getSystemService(StatusBarManager::class.java) ?: return
        try {
            sbm.requestAddTileService(
                ComponentName(this, ClockTileService::class.java),
                s(R.string.tile_label),
                Icon.createWithResource(this, R.drawable.ic_tile),
                mainExecutor,
            ) { result ->
                if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                ) Toast.makeText(this, s(R.string.tile_added), Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(this, s(R.string.tile_manual), Toast.LENGTH_LONG).show()
        }
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    /** Short "Allow restricted settings" walkthrough with a button straight to App info. */
    private fun restrictedHelp(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xFF1E1A0E.toInt(), 14f, WARN)
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        }
        box.addView(text(s(R.string.restricted_title), 16f, WARN, bold = true))
        box.addView(text(s(R.string.restricted_body), 14f, LIGHT).apply { setPadding(0, dp(6f), 0, dp(10f)) })
        box.addView(outlineButton(s(R.string.open_app_info)) {
            openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        box.addView(text(s(R.string.restricted_note), 13f, GREY).apply { setPadding(0, dp(10f), 0, 0) })
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(10f))
            addView(box)
        }
    }

    // ---- tiny view helpers ----

    private fun text(t: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(0f, 1.2f)
    }

    private fun label(t: String) = text(t, 15f, Color.WHITE).apply { setPadding(0, dp(10f), 0, dp(6f)) }

    private fun section(t: String) = text(t, 13f, ACCENT, bold = true).apply {
        isAllCaps = true
        letterSpacing = 0.08f
        setPadding(0, dp(28f), 0, dp(10f))
    }

    private fun rounded(color: Int, r: Float, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
        if (stroke != null) setStroke(dp(1f), stroke)
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, 0, 0, dp(8f))
        views.forEachIndexed { i, v ->
            if (i > 0) addView(View(context), LinearLayout.LayoutParams(dp(8f), 1))
            addView(v)
        }
    }

    private fun choice(label: String, selected: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(if (selected) Color.BLACK else Color.WHITE)
        background = rounded(if (selected) Color.WHITE else BG, 20f, Color.WHITE)
        setPadding(dp(16f), dp(6f), dp(16f), dp(6f))
        minHeight = dp(40f); minimumHeight = dp(40f)
        minWidth = 0; minimumWidth = 0
        setOnClickListener { onClick() }
    }

    private fun outlineButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(BG, 14f, 0xFF555555.toInt())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setOnClickListener { onClick() }
    }

    private fun card(title: String, why: String, action: String, onClick: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xFF1A1F1C.toInt(), 14f)
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        }
        box.addView(text(title, 17f, Color.WHITE, bold = true))
        box.addView(text(why, 14f, GREY).apply { setPadding(0, dp(4f), 0, dp(10f)) })
        box.addView(Button(this).apply {
            text = action
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(ACCENT, 12f)
            setOnClickListener { onClick() }
        })
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(10f))
            addView(box)
        }
    }

    @Suppress("DEPRECATION")
    private fun switchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6f), 0, dp(10f))
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, 16f, Color.WHITE))
            addView(text(desc, 13f, GREY))
        }
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Switch(this).apply {
            isChecked = checked
            contentDescription = title
            setOnCheckedChangeListener { _, v -> onChange(v) }
        })
        return row
    }

    private companion object {
        const val BG = 0xFF0E1110.toInt()
        const val ACCENT = 0xFF2BB673.toInt()
        const val GREY = 0xFF9A9A9A.toInt()
        const val LIGHT = 0xFFDDDDDD.toInt()
        const val WARN = 0xFFFFC857.toInt()
    }
}
