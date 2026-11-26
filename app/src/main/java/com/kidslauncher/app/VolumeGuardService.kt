package com.kidslauncher.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import kotlin.math.roundToInt

/**
 * Keeps media volume at or below the parent's limit. Runs in the background so the limit
 * also applies while the child is inside a game or video app.
 */
class VolumeGuardService : Service() {

    private lateinit var audio: AudioManager
    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())

    private val settingsObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = enforce()
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = enforce()
    }

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        prefs = Prefs(this)
        startAsForeground()

        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, settingsObserver)
        val filter = IntentFilter(VOLUME_CHANGED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(volumeReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(volumeReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!prefs.volumeLimitEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        enforce()
        return START_STICKY
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(settingsObserver)
        unregisterReceiver(volumeReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun enforce() {
        if (!prefs.volumeLimitEnabled) return
        val stream = AudioManager.STREAM_MUSIC
        val max = audio.getStreamMaxVolume(stream)
        val cap = (max * prefs.maxVolumePercent / 100f).roundToInt().coerceIn(1, max)
        if (audio.getStreamVolume(stream) > cap) {
            audio.setStreamVolume(stream, cap, 0)
        }
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Volume limit", NotificationManager.IMPORTANCE_MIN)
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lock)
            .setContentTitle("Volume limit is on")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "volume_guard"
        private const val NOTIFICATION_ID = 1
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"

        /** Start or stop the guard to match the parent's setting. */
        fun sync(ctx: Context) {
            val intent = Intent(ctx, VolumeGuardService::class.java)
            if (Prefs(ctx).volumeLimitEnabled) {
                try {
                    ctx.startForegroundService(intent)
                } catch (e: Exception) {
                    // Can't start from the background on newer Android; the home screen retries on resume.
                }
            } else {
                ctx.stopService(intent)
            }
        }
    }
}
