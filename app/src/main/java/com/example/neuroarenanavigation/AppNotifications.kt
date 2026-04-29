package com.example.neuroarenanavigation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object AppNotifications {
    const val CHANNEL_PROGRESS = "neuroarena_progress"
    const val CHANNEL_REMINDERS = "neuroarena_reminders"
    const val CHANNEL_DIGEST = "neuroarena_digest"
    const val CHANNEL_PUSH = "neuroarena_push"

    private const val ID_PROGRESS_BASE = 3100
    private const val ID_REMINDER = 3201
    private const val ID_WEEKLY_DIGEST = 3301
    private const val ID_PUSH_BASE = 3400

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channels = listOf(
            NotificationChannel(
                CHANNEL_PROGRESS,
                "Progress & Milestones",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Personal bests and milestones" },
            NotificationChannel(
                CHANNEL_REMINDERS,
                "Practice Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Inactivity and daily challenge reminders" },
            NotificationChannel(
                CHANNEL_DIGEST,
                "Weekly Digest",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Weekly app progress summaries" },
            NotificationChannel(
                CHANNEL_PUSH,
                "Push Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Realtime push notifications from cloud" }
        )

        channels.forEach { manager.createNotificationChannel(it) }
    }

    fun notifyPersonalBest(context: Context, gameName: String, valueText: String) {
        if (!canNotify(context) || !PrefsManager.areProgressNotificationsEnabled(context)) return

        show(
            context = context,
            channelId = CHANNEL_PROGRESS,
            notificationId = ID_PROGRESS_BASE + gameName.hashCode().absoluteValueMod(400),
            title = "New Personal Best!",
            text = "$gameName: $valueText"
        )
    }

    fun notifyMilestone(context: Context, title: String, text: String) {
        if (!canNotify(context) || !PrefsManager.areProgressNotificationsEnabled(context)) return

        show(
            context = context,
            channelId = CHANNEL_PROGRESS,
            notificationId = ID_PROGRESS_BASE + 900,
            title = title,
            text = text
        )
    }

    fun notifyReminder(context: Context, title: String, text: String) {
        if (!canNotify(context) || !PrefsManager.areReminderNotificationsEnabled(context)) return

        show(
            context = context,
            channelId = CHANNEL_REMINDERS,
            notificationId = ID_REMINDER,
            title = title,
            text = text
        )
    }

    fun notifyWeeklyDigest(context: Context, text: String) {
        if (!canNotify(context) || !PrefsManager.areDigestNotificationsEnabled(context)) return

        show(
            context = context,
            channelId = CHANNEL_DIGEST,
            notificationId = ID_WEEKLY_DIGEST,
            title = "Your NeuroArena Weekly Digest",
            text = text
        )
    }

    fun notifyPush(context: Context, title: String, text: String) {
        if (!canNotify(context) || !PrefsManager.arePushNotificationsEnabled(context)) return

        show(
            context = context,
            channelId = CHANNEL_PUSH,
            notificationId = ID_PUSH_BASE + title.hashCode().absoluteValueMod(200),
            title = title,
            text = text
        )
    }

    private fun show(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        text: String
    ) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun Int.absoluteValueMod(mod: Int): Int {
        val v = if (this < 0) -this else this
        return v % mod
    }
}
