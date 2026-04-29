package com.example.neuroarenanavigation

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object NotificationScheduler {
    private const val WORK_INACTIVITY = "work_inactivity_reminder"
    private const val WORK_WEEKLY_DIGEST = "work_weekly_digest"
    private const val WORK_MINI_CHALLENGE = "work_mini_challenge"

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)

        val inactivityWork = PeriodicWorkRequestBuilder<InactivityReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(6, TimeUnit.HOURS)
            .build()

        val weeklyDigestWork = PeriodicWorkRequestBuilder<WeeklyDigestWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.DAYS)
            .build()

        val miniChallengeWork = PeriodicWorkRequestBuilder<MiniChallengeWorker>(15, TimeUnit.MINUTES)
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()

        wm.enqueueUniquePeriodicWork(
            WORK_INACTIVITY,
            ExistingPeriodicWorkPolicy.UPDATE,
            inactivityWork
        )

        wm.enqueueUniquePeriodicWork(
            WORK_WEEKLY_DIGEST,
            ExistingPeriodicWorkPolicy.UPDATE,
            weeklyDigestWork
        )

        wm.enqueueUniquePeriodicWork(
            WORK_MINI_CHALLENGE,
            ExistingPeriodicWorkPolicy.UPDATE,
            miniChallengeWork
        )
    }
}
