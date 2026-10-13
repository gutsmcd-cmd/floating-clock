package io.github.gutsmcd.floatingclock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Foreground service (specialUse) that keeps the process alive while the clock floats,
 * and optionally keeps a Show/Hide shortcut in the notification shade.
 */
class ClockService : Service() {

    companion object {
        private const val TAG = "FloatingClock"
        const val ACTION_SHOW = "io.github.gutsmcd.floatingclock.SHOW"
        const val ACTION_HIDE = "io.github.gutsmcd.floatingclock.HIDE"
        const val ACTION_LOCK = "io.github.gutsmcd.floatingclock.LOCK"
        const val ACTION_SYNC = "io.github.gutsmcd.floatingclock.SYNC"
        private const val CHANNEL = "clock"
        private const val NOTIF_ID = 1
        private var instance: ClockService? = null

        fun start(c: Context, action: String): Boolean = try {
            c.startForegroundService(Intent(c, ClockService::class.java).setAction(action)); true
        } catch (e: Exception) {
            Log.w(TAG, "could not start service", e); false
        }

        fun refresh(c: Context) {
            val s = instance
            if (s != null) s.updateState()
            else if (ClockController.isShowing || Prefs.notifShortcut(c)) start(c, ACTION_SYNC)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!goForeground(buildNotification())) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_SHOW -> ClockController.show(this)
            ACTION_HIDE -> ClockController.hide(this)
            ACTION_LOCK -> {
                Prefs.setLocked(this, !Prefs.locked(this))
                ClockController.applySettings(this)
            }
        }
        updateState()
        return START_NOT_STICKY
    }

    fun updateState() {
        if (ClockController.isShowing || Prefs.notifShortcut(this)) {
            try {
                createChannel()
                getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification())
            } catch (_: Exception) { }
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun goForeground(n: Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
        true
    } catch (e: Exception) {
        Log.w(TAG, "startForeground failed", e); false
    }

    private fun createChannel() {
        val loc = Prefs.localized(this)
        val ch = NotificationChannel(CHANNEL, loc.getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        ch.setShowBadge(false)
        ch.setSound(null, null)
        ch.enableVibration(false)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
    }

    private fun svc(code: Int, action: String): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, ClockService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun buildNotification(): Notification {
        val loc = Prefs.localized(this)
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = Icon.createWithResource(this, R.drawable.ic_tile)
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        if (ClockController.isShowing) {
            b.setContentTitle(loc.getString(R.string.notif_on_title))
                .setContentText(loc.getString(R.string.notif_on_text))
                .addAction(Notification.Action.Builder(icon, loc.getString(R.string.action_hide), svc(1, ACTION_HIDE)).build())
                .addAction(
                    Notification.Action.Builder(
                        icon,
                        loc.getString(if (Prefs.locked(this)) R.string.action_unlock else R.string.action_lock),
                        svc(3, ACTION_LOCK),
                    ).build(),
                )
        } else {
            b.setContentTitle(loc.getString(R.string.notif_off_title))
                .setContentText(loc.getString(R.string.notif_off_text))
                .addAction(Notification.Action.Builder(icon, loc.getString(R.string.action_show), svc(2, ACTION_SHOW)).build())
        }
        return b.build()
    }
}
