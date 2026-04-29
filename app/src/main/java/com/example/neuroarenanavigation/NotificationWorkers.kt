2360002360*93**package com.example.neuroarenanavigation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class InactivityReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        AppNotifications.ensureChannels(applicationContext)

        val lastPlayedAt = PrefsManager.getLastPlayedAtMs(applicationContext)
        if (lastPlayedAt <= 0L) return Result.success()

        val now = System.currentTimeMillis()
        val hoursInactive = (now - lastPlayedAt) / (1000L * 60L * 60L)

        if (hoursInactive >= 36L) {
            val bestGame = PrefsManager.getSuggestedGameName(applicationContext)
            AppNotifications.notifyReminder(
                applicationContext,
                "Your streak is at risk",
                "You have been away for $hoursInactive hours. Quick round of $bestGame?"
            )
        }

        return Result.success()
    }
}

class WeeklyDigestWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        AppNotifications.ensureChannels(applicationContext)

        val sessions = PrefsManager.getTotalSessions(applicationContext)
        val streak = PrefsManager.getCurrentStreakDays(applicationContext)
        val improvements = PrefsManager.getTotalBestImprovements(applicationContext)
        val text = "Sessions: $sessions, Streak: $streak days, New bests: $improvements. Keep it going!"

        AppNotifications.notifyWeeklyDigest(applicationContext, text)
        return Result.success()
    }
}

class MiniChallengeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        AppNotifications.ensureChannels(applicationContext)

        // Keep reminders user-controlled and avoid spamming challenge prompts.
        if (!PrefsManager.areReminderNotificationsEnabled(applicationContext)) {
            return Result.success()
        }

        val now = System.currentTimeMillis()
        val lastSent = PrefsManager.getLastMiniChallengeAtMs(applicationContext)
        if (lastSent > 0L && now - lastSent < MIN_CHALLENGE_GAP_MS) {
            return Result.success()
        }

        val challenge = CHALLENGES[((now / MIN_CHALLENGE_GAP_MS) % CHALLENGES.size).toInt()]
        AppNotifications.notifyReminder(
            applicationContext,
            "Mini Challenge",
            challenge
        )
        PrefsManager.saveLastMiniChallengeAtMs(applicationContext, now)
        return Result.success()
    }

    companion object {
        private const val MIN_CHALLENGE_GAP_MS = 4L * 60L * 60L * 1000L

        private val CHALLENGES = listOf(
            "Quick one: beat your Reaction Time best in 3 tries.",
            "Memory rush: reach +1 level in Visual Memory today.",
            "Streak booster: complete any 2 games this session.",
            "Focus round: score 10+ in Verbal Memory.",
            "Fast lane: play Sequence Memory and clear 2 levels."
        )
    }
}
