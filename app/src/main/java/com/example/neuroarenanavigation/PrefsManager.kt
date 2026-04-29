package com.example.neuroarenanavigation

import android.content.Context

object PrefsManager {
    private const val PREFS_NAME = "neuroarena_prefs"
    private const val KEY_USERNAME = "saved_username"
    private const val KEY_SELECTED_GAME = "selected_game"
    private const val KEY_DARK_MODE = "dark_mode_enabled"
    private const val KEY_REACTION_BEST_AVG_MS = "reaction_best_avg_ms"
    private const val KEY_CHIMP_BEST_SCORE = "chimp_best_score"
    private const val KEY_NUMBER_MEMORY_BEST_LEVEL = "number_memory_best_level"
    private const val KEY_VERBAL_MEMORY_BEST_SCORE = "verbal_memory_best_score"
    private const val KEY_VISUAL_MEMORY_BEST_LEVEL = "visual_memory_best_level"
    private const val KEY_SEQUENCE_MEMORY_BEST_LEVEL = "sequence_memory_best_level"
    private const val KEY_TOTAL_SESSIONS = "total_sessions"
    private const val KEY_LAST_PLAYED_AT = "last_played_at"
    private const val KEY_STREAK_DAYS = "streak_days"
    private const val KEY_STREAK_LAST_EPOCH_DAY = "streak_last_epoch_day"
    private const val KEY_TOTAL_BEST_IMPROVEMENTS = "total_best_improvements"
    private const val KEY_PROGRESS_NOTIFS_ENABLED = "progress_notifs_enabled"
    private const val KEY_REMINDER_NOTIFS_ENABLED = "reminder_notifs_enabled"
    private const val KEY_DIGEST_NOTIFS_ENABLED = "digest_notifs_enabled"
    private const val KEY_PUSH_NOTIFS_ENABLED = "push_notifs_enabled"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val KEY_FCM_TOKEN_UPDATED_AT = "fcm_token_updated_at"
    private const val KEY_LAST_MINI_CHALLENGE_AT = "last_mini_challenge_at"
    private const val KEY_PASSWORD = "saved_password"
    private const val KEY_PROFILE_AVATAR = "profile_avatar"
    private const val KEY_PROFILE_IMAGE_URI = "profile_image_uri"
    private const val KEY_PROFILE_VIDEO_URI = "profile_video_uri"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveUsername(context: Context, username: String) {
        prefs(context).edit().putString(KEY_USERNAME, username).apply()
    }

    fun getUsername(context: Context): String {
        return prefs(context).getString(KEY_USERNAME, "") ?: ""
    }

    fun savePassword(context: Context, password: String) {
        prefs(context).edit().putString(KEY_PASSWORD, password).apply()
    }

    fun getPassword(context: Context): String {
        return prefs(context).getString(KEY_PASSWORD, "") ?: ""
    }

    fun saveProfileAvatar(context: Context, avatarKey: String) {
        prefs(context).edit().putString(KEY_PROFILE_AVATAR, avatarKey).apply()
    }

    fun getProfileAvatar(context: Context): String {
        return prefs(context).getString(KEY_PROFILE_AVATAR, "bolt") ?: "bolt"
    }

    fun saveProfileImageUri(context: Context, imageUri: String) {
        prefs(context).edit().putString(KEY_PROFILE_IMAGE_URI, imageUri).apply()
    }

    fun getProfileImageUri(context: Context): String {
        return prefs(context).getString(KEY_PROFILE_IMAGE_URI, "") ?: ""
    }

    fun clearProfileImageUri(context: Context) {
        prefs(context).edit().remove(KEY_PROFILE_IMAGE_URI).apply()
    }

    fun saveProfileVideoUri(context: Context, videoUri: String) {
        prefs(context).edit().putString(KEY_PROFILE_VIDEO_URI, videoUri).apply()
    }

    fun getProfileVideoUri(context: Context): String {
        return prefs(context).getString(KEY_PROFILE_VIDEO_URI, "") ?: ""
    }

    fun clearProfileVideoUri(context: Context) {
        prefs(context).edit().remove(KEY_PROFILE_VIDEO_URI).apply()
    }

    fun saveSelectedGame(context: Context, gameName: String) {
        prefs(context).edit().putString(KEY_SELECTED_GAME, gameName).apply()
    }

    fun getSelectedGame(context: Context): String {
        return prefs(context).getString(KEY_SELECTED_GAME, "Reaction Time") ?: "Reaction Time"
    }

    fun saveDarkModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    fun isDarkModeEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_DARK_MODE, false)
    }

    fun saveReactionTimeBestAvgMs(context: Context, bestAvgMs: Int) {
        val previous = getReactionTimeBestAvgMs(context)
        prefs(context).edit().putInt(KEY_REACTION_BEST_AVG_MS, bestAvgMs).apply()
        if (previous == 0 || bestAvgMs < previous) {
            incrementBestImprovements(context)
            AppNotifications.notifyPersonalBest(context, "Reaction Time", "$bestAvgMs ms avg")
        }
    }

    fun getReactionTimeBestAvgMs(context: Context): Int {
        return prefs(context).getInt(KEY_REACTION_BEST_AVG_MS, 0)
    }

    // Backward-compatible wrappers for older call sites.
    fun saveReactionTimeBestMs(context: Context, bestMs: Int) {
        saveReactionTimeBestAvgMs(context, bestMs)
    }

    fun getReactionTimeBestMs(context: Context): Int {
        return getReactionTimeBestAvgMs(context)
    }

    fun saveChimpBestScore(context: Context, score: Int) {
        val previous = getChimpBestScore(context)
        prefs(context).edit().putInt(KEY_CHIMP_BEST_SCORE, score).apply()
        if (score > previous) {
            incrementBestImprovements(context)
            AppNotifications.notifyPersonalBest(context, "Chimp Test", "Score $score")
        }
    }

    fun getChimpBestScore(context: Context): Int {
        return prefs(context).getInt(KEY_CHIMP_BEST_SCORE, 0)
    }

    fun saveNumberMemoryBestLevel(context: Context, level: Int) {
        val previous = getNumberMemoryBestLevel(context)
        prefs(context).edit().putInt(KEY_NUMBER_MEMORY_BEST_LEVEL, level).apply()
        if (level > previous) {
            incrementBestImprovements(context)
            AppNotifications.notifyPersonalBest(context, "Number Memory", "Level $level")
        }
    }

    fun getNumberMemoryBestLevel(context: Context): Int {
        return prefs(context).getInt(KEY_NUMBER_MEMORY_BEST_LEVEL, 0)
    }

    fun saveVerbalMemoryBestScore(context: Context, score: Int) {
        val previous = getVerbalMemoryBestScore(context)
        prefs(context).edit().putInt(KEY_VERBAL_MEMORY_BEST_SCORE, score).apply()
        if (score > previous) {
            incrementBestImprovements(context)
            AppNotifications.notifyPersonalBest(context, "Verbal Memory", "$score words")
        }
    }

    fun getVerbalMemoryBestScore(context: Context): Int {
        return prefs(context).getInt(KEY_VERBAL_MEMORY_BEST_SCORE, 0)
    }

    fun saveVisualMemoryBestLevel(context: Context, level: Int) {
        val previous = getVisualMemoryBestLevel(context)
        prefs(context).edit().putInt(KEY_VISUAL_MEMORY_BEST_LEVEL, level).apply()
        if (level > previous) {
            incrementBestImprovements(context)
            AppNotifications.notifyPersonalBest(context, "Visual Memory", "Level $level")
        }
    }

    fun getVisualMemoryBestLevel(context: Context): Int {
        return prefs(context).getInt(KEY_VISUAL_MEMORY_BEST_LEVEL, 0)
    }

    fun saveSequenceMemoryBestLevel(context: Context, level: Int) {
        val previous = getSequenceMemoryBestLevel(context)
        prefs(context).edit().putInt(KEY_SEQUENCE_MEMORY_BEST_LEVEL, level).apply()
        if (level > previous) {
            incrementBestImprovements(context)
            AppNotifications.notifyPersonalBest(context, "Sequence Memory", "Level $level")
        }
    }

    fun getSequenceMemoryBestLevel(context: Context): Int {
        return prefs(context).getInt(KEY_SEQUENCE_MEMORY_BEST_LEVEL, 0)
    }

    fun areProgressNotificationsEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_PROGRESS_NOTIFS_ENABLED, true)
    }

    fun areReminderNotificationsEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_REMINDER_NOTIFS_ENABLED, true)
    }

    fun areDigestNotificationsEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_DIGEST_NOTIFS_ENABLED, true)
    }

    fun arePushNotificationsEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_PUSH_NOTIFS_ENABLED, true)
    }

    fun setProgressNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PROGRESS_NOTIFS_ENABLED, enabled).apply()
    }

    fun setReminderNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_REMINDER_NOTIFS_ENABLED, enabled).apply()
    }

    fun setDigestNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DIGEST_NOTIFS_ENABLED, enabled).apply()
    }

    fun setPushNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PUSH_NOTIFS_ENABLED, enabled).apply()
    }

    fun saveFcmToken(context: Context, token: String) {
        prefs(context).edit()
            .putString(KEY_FCM_TOKEN, token)
            .putLong(KEY_FCM_TOKEN_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun getFcmToken(context: Context): String {
        return prefs(context).getString(KEY_FCM_TOKEN, "") ?: ""
    }

    fun getFcmTokenUpdatedAt(context: Context): Long {
        return prefs(context).getLong(KEY_FCM_TOKEN_UPDATED_AT, 0L)
    }

    fun saveLastMiniChallengeAtMs(context: Context, timestampMs: Long) {
        prefs(context).edit().putLong(KEY_LAST_MINI_CHALLENGE_AT, timestampMs).apply()
    }

    fun getLastMiniChallengeAtMs(context: Context): Long {
        return prefs(context).getLong(KEY_LAST_MINI_CHALLENGE_AT, 0L)
    }

    fun onGameSessionCompleted(context: Context, gameName: String) {
        val now = System.currentTimeMillis()
        val currentDay = now / MILLIS_PER_DAY
        val p = prefs(context)

        val totalSessions = p.getInt(KEY_TOTAL_SESSIONS, 0) + 1
        val prevStreak = p.getInt(KEY_STREAK_DAYS, 0)
        val lastDay = p.getLong(KEY_STREAK_LAST_EPOCH_DAY, -1L)
        val newStreak = when {
            lastDay == currentDay -> prevStreak
            lastDay == currentDay - 1L -> prevStreak + 1
            else -> 1
        }

        p.edit()
            .putInt(KEY_TOTAL_SESSIONS, totalSessions)
            .putLong(KEY_LAST_PLAYED_AT, now)
            .putInt(KEY_STREAK_DAYS, newStreak)
            .putLong(KEY_STREAK_LAST_EPOCH_DAY, currentDay)
            .apply()

        if (totalSessions == 5 || totalSessions == 10 || totalSessions == 25 || totalSessions == 50) {
            AppNotifications.notifyMilestone(
                context,
                "Session Milestone",
                "Great work! You completed $totalSessions sessions."
            )
        }

        if ((newStreak == 3 || newStreak == 7 || newStreak == 14) && lastDay != currentDay) {
            AppNotifications.notifyMilestone(
                context,
                "Streak Milestone",
                "You reached a $newStreak-day streak. Keep playing $gameName!"
            )
        }
    }

    fun getTotalSessions(context: Context): Int {
        return prefs(context).getInt(KEY_TOTAL_SESSIONS, 0)
    }

    fun getCurrentStreakDays(context: Context): Int {
        return prefs(context).getInt(KEY_STREAK_DAYS, 0)
    }

    fun getLastPlayedAtMs(context: Context): Long {
        return prefs(context).getLong(KEY_LAST_PLAYED_AT, 0L)
    }

    fun getTotalBestImprovements(context: Context): Int {
        return prefs(context).getInt(KEY_TOTAL_BEST_IMPROVEMENTS, 0)
    }

    fun getSuggestedGameName(context: Context): String {
        return getSelectedGame(context)
    }

    fun getOverallRankingScore(context: Context): Int {
        val reactionBest = getReactionTimeBestAvgMs(context)
        val reactionComponent = if (reactionBest == 0) 0 else (500 - reactionBest).coerceIn(0, 500)

        val sequenceComponent = getSequenceMemoryBestLevel(context) * 45
        val verbalComponent = getVerbalMemoryBestScore(context) * 12
        val numberComponent = getNumberMemoryBestLevel(context) * 40
        val visualComponent = getVisualMemoryBestLevel(context) * 42
        val chimpComponent = getChimpBestScore(context) * 28

        return reactionComponent + sequenceComponent + verbalComponent + numberComponent + visualComponent + chimpComponent
    }

    fun applyUserSnapshot(
        context: Context,
        username: String,
        avatarUri: String,
        videoUri: String,
        reactionBestAvgMs: Int,
        sequenceBestLevel: Int,
        verbalBestScore: Int,
        numberBestLevel: Int,
        visualBestLevel: Int,
        chimpBestScore: Int
    ) {
        val p = prefs(context)
        p.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PROFILE_IMAGE_URI, avatarUri)
            .putString(KEY_PROFILE_VIDEO_URI, videoUri)
            .putInt(KEY_REACTION_BEST_AVG_MS, reactionBestAvgMs)
            .putInt(KEY_SEQUENCE_MEMORY_BEST_LEVEL, sequenceBestLevel)
            .putInt(KEY_VERBAL_MEMORY_BEST_SCORE, verbalBestScore)
            .putInt(KEY_NUMBER_MEMORY_BEST_LEVEL, numberBestLevel)
            .putInt(KEY_VISUAL_MEMORY_BEST_LEVEL, visualBestLevel)
            .putInt(KEY_CHIMP_BEST_SCORE, chimpBestScore)
            .putInt(KEY_TOTAL_SESSIONS, 0)
            .putInt(KEY_STREAK_DAYS, 0)
            .putLong(KEY_STREAK_LAST_EPOCH_DAY, -1L)
            .putLong(KEY_LAST_PLAYED_AT, 0L)
            .putInt(KEY_TOTAL_BEST_IMPROVEMENTS, 0)
            .apply()
    }

    private fun incrementBestImprovements(context: Context) {
        val p = prefs(context)
        val current = p.getInt(KEY_TOTAL_BEST_IMPROVEMENTS, 0)
        p.edit().putInt(KEY_TOTAL_BEST_IMPROVEMENTS, current + 1).apply()
    }

    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }
}