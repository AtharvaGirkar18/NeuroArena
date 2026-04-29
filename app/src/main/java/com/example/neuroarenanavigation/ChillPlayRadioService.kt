package com.example.neuroarenanavigation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ChillPlayRadioService : Service() {

    private var player: MediaPlayer? = null
    private var isPlaying: Boolean = false
    private var currentTrackIndex: Int = 0

    private val trackResIds = listOf(
        R.raw.soundtrack1,
        R.raw.soundtrack2,
        R.raw.soundtrack3
    )

    private val trackNames = listOf(
        "Soundtrack 1",
        "Soundtrack 2",
        "Soundtrack 3"
    )

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> {
                if (!isPlaying) {
                    playCurrentTone()
                }
                startForeground(NOTIFICATION_ID, buildNotification())
            }

            ACTION_PAUSE -> {
                pausePlayback()
                updateNotification()
            }

            ACTION_RESUME -> {
                if (!isPlaying) {
                    playCurrentTone()
                }
                updateNotification()
            }

            ACTION_NEXT -> {
                nextTone()
                updateNotification()
            }

            ACTION_STOP -> {
                markRunning(this, false)
                broadcastState()
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        stopPlayback()
        markRunning(this, false)
        broadcastState()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun playCurrentTone() {
        if (trackResIds.isEmpty()) return

        stopPlayback()
        val trackResId = trackResIds[currentTrackIndex % trackResIds.size]
        player = MediaPlayer.create(this, trackResId)?.apply {
            isLooping = true
            start()
        }
        isPlaying = player?.isPlaying == true
        markRunning(this, isPlaying)
        broadcastState()
    }

    private fun pausePlayback() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
            }
        }
        isPlaying = false
        markRunning(this, false)
        broadcastState()
    }

    private fun stopPlayback() {
        player?.runCatching {
            if (isPlaying) stop()
            release()
        }
        player = null
        isPlaying = false
    }

    private fun nextTone() {
        if (trackResIds.isEmpty()) return
        currentTrackIndex = (currentTrackIndex + 1) % trackResIds.size
        playCurrentTone()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseOrResumeAction = if (isPlaying) {
            NotificationCompat.Action(
                0,
                "Pause",
                servicePendingIntent(ACTION_PAUSE, 11)
            )
        } else {
            NotificationCompat.Action(
                0,
                "Resume",
                servicePendingIntent(ACTION_RESUME, 12)
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_RADIO)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Chill Play Radio - ${currentTrackName()}")
            .setContentText(if (isPlaying) "Now playing: ${currentTrackName()}" else "Paused on: ${currentTrackName()}")
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .addAction(pauseOrResumeAction)
            .addAction(NotificationCompat.Action(0, "Next", servicePendingIntent(ACTION_NEXT, 13)))
            .addAction(NotificationCompat.Action(0, "Stop", servicePendingIntent(ACTION_STOP, 14)))
            .build()
    }

    private fun currentTrackName(): String {
        if (trackNames.isEmpty()) return "Unknown"
        return trackNames[currentTrackIndex % trackNames.size]
    }

    private fun broadcastState() {
        val stateIntent = Intent(ACTION_RADIO_STATE_CHANGED).apply {
            setPackage(packageName)
            putExtra(EXTRA_IS_PLAYING, isPlaying)
            putExtra(EXTRA_TRACK_NAME, currentTrackName())
        }
        sendBroadcast(stateIntent)
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, ChillPlayRadioService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_RADIO,
            "Chill Play Radio",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing background chill audio"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_RADIO = "neuroarena_radio"
        private const val NOTIFICATION_ID = 3501

        const val ACTION_START = "com.example.neuroarenanavigation.radio.START"
        const val ACTION_PAUSE = "com.example.neuroarenanavigation.radio.PAUSE"
        const val ACTION_RESUME = "com.example.neuroarenanavigation.radio.RESUME"
        const val ACTION_NEXT = "com.example.neuroarenanavigation.radio.NEXT"
        const val ACTION_STOP = "com.example.neuroarenanavigation.radio.STOP"
        const val ACTION_RADIO_STATE_CHANGED = "com.example.neuroarenanavigation.radio.STATE_CHANGED"

        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_TRACK_NAME = "extra_track_name"

        private const val PREFS = "neuroarena_prefs"
        private const val KEY_RUNNING = "chill_radio_running"

        fun startService(context: Context) {
            val intent = Intent(context, ChillPlayRadioService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            markRunning(context, true)
        }

        fun stopService(context: Context) {
            val stopIntent = Intent(context, ChillPlayRadioService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(stopIntent)
            markRunning(context, false)
        }

        fun isRunning(context: Context): Boolean {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_RUNNING, false)
        }

        private fun markRunning(context: Context, running: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_RUNNING, running)
                .apply()
        }
    }
}