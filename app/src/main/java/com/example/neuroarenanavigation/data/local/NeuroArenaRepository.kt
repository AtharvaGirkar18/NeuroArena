package com.example.neuroarenanavigation.data.local

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.neuroarenanavigation.PrefsManager

data class UserProfileRecord(
    val uid: String,
    val email: String,
    val username: String,
    val avatarUri: String,
    val profileVideoUri: String,
    val overallScore: Int,
    val createdAt: Long,
    val updatedAt: Long
)

class NeuroArenaRepository(private val context: Context) {

    private val dbHelper by lazy { NeuroArenaSQLiteHelper(context.applicationContext) }

    suspend fun bootstrapFromPrefs(uid: String, email: String, fallbackName: String) {
        // Legacy migration path: only copy global prefs once for a uid with no existing DB data.
        if (hasAnyUserData(uid)) return

        val now = System.currentTimeMillis()
        val currentProfile = getProfileByUid(uid)

        val username = fallbackName.ifEmpty { "Player" }
        val avatarUri = PrefsManager.getProfileImageUri(context)
        val overallScore = PrefsManager.getOverallRankingScore(context)

        val profile = UserProfileRecord(
            uid = uid,
            email = email,
            username = username,
            avatarUri = avatarUri,
            profileVideoUri = PrefsManager.getProfileVideoUri(context),
            overallScore = overallScore,
            createdAt = currentProfile?.createdAt ?: now,
            updatedAt = now
        )
        upsertProfile(profile)

        upsertBestScore(uid, "Reaction Time", PrefsManager.getReactionTimeBestAvgMs(context), PrefsManager.getReactionTimeBestAvgMs(context).toReactionLabel(), now)
        upsertBestScore(uid, "Sequence Memory", PrefsManager.getSequenceMemoryBestLevel(context), PrefsManager.getSequenceMemoryBestLevel(context).toLevelLabel(), now)
        upsertBestScore(uid, "Verbal Memory", PrefsManager.getVerbalMemoryBestScore(context), PrefsManager.getVerbalMemoryBestScore(context).toWordsLabel(), now)
        upsertBestScore(uid, "Number Memory", PrefsManager.getNumberMemoryBestLevel(context), PrefsManager.getNumberMemoryBestLevel(context).toLevelLabel(), now)
        upsertBestScore(uid, "Visual Memory", PrefsManager.getVisualMemoryBestLevel(context), PrefsManager.getVisualMemoryBestLevel(context).toLevelLabel(), now)
        upsertBestScore(uid, "Chimp Test", PrefsManager.getChimpBestScore(context), PrefsManager.getChimpBestScore(context).toScoreLabel(), now)
    }

    suspend fun hydratePrefsForUid(uid: String, email: String, fallbackName: String) {
        val now = System.currentTimeMillis()
        val profile = getProfileByUid(uid)

        if (profile == null) {
            val created = UserProfileRecord(
                uid = uid,
                email = email,
                username = fallbackName.ifEmpty { "Player" },
                avatarUri = "",
                profileVideoUri = "",
                overallScore = 0,
                createdAt = now,
                updatedAt = now
            )
            upsertProfile(created)
            PrefsManager.applyUserSnapshot(
                context = context,
                username = created.username,
                avatarUri = created.avatarUri,
                videoUri = created.profileVideoUri,
                reactionBestAvgMs = 0,
                sequenceBestLevel = 0,
                verbalBestScore = 0,
                numberBestLevel = 0,
                visualBestLevel = 0,
                chimpBestScore = 0
            )
            return
        }

        val bestScores = getBestValuesByGame(uid)
        PrefsManager.applyUserSnapshot(
            context = context,
            username = profile.username,
            avatarUri = profile.avatarUri,
            videoUri = profile.profileVideoUri,
            reactionBestAvgMs = bestScores["Reaction Time"] ?: 0,
            sequenceBestLevel = bestScores["Sequence Memory"] ?: 0,
            verbalBestScore = bestScores["Verbal Memory"] ?: 0,
            numberBestLevel = bestScores["Number Memory"] ?: 0,
            visualBestLevel = bestScores["Visual Memory"] ?: 0,
            chimpBestScore = bestScores["Chimp Test"] ?: 0
        )
    }

    suspend fun syncCurrentPrefsToUid(uid: String, email: String, fallbackName: String) {
        val now = System.currentTimeMillis()
        val currentProfile = getProfileByUid(uid)
        val username = PrefsManager.getUsername(context).ifEmpty {
            currentProfile?.username ?: fallbackName.ifEmpty { "Player" }
        }

        val profile = UserProfileRecord(
            uid = uid,
            email = email,
            username = username,
            avatarUri = PrefsManager.getProfileImageUri(context),
            profileVideoUri = PrefsManager.getProfileVideoUri(context),
            overallScore = PrefsManager.getOverallRankingScore(context),
            createdAt = currentProfile?.createdAt ?: now,
            updatedAt = now
        )
        upsertProfile(profile)

        upsertBestScore(
            uid,
            "Reaction Time",
            PrefsManager.getReactionTimeBestAvgMs(context),
            PrefsManager.getReactionTimeBestAvgMs(context).toReactionLabel(),
            now
        )
        upsertBestScore(
            uid,
            "Sequence Memory",
            PrefsManager.getSequenceMemoryBestLevel(context),
            PrefsManager.getSequenceMemoryBestLevel(context).toLevelLabel(),
            now
        )
        upsertBestScore(
            uid,
            "Verbal Memory",
            PrefsManager.getVerbalMemoryBestScore(context),
            PrefsManager.getVerbalMemoryBestScore(context).toWordsLabel(),
            now
        )
        upsertBestScore(
            uid,
            "Number Memory",
            PrefsManager.getNumberMemoryBestLevel(context),
            PrefsManager.getNumberMemoryBestLevel(context).toLevelLabel(),
            now
        )
        upsertBestScore(
            uid,
            "Visual Memory",
            PrefsManager.getVisualMemoryBestLevel(context),
            PrefsManager.getVisualMemoryBestLevel(context).toLevelLabel(),
            now
        )
        upsertBestScore(
            uid,
            "Chimp Test",
            PrefsManager.getChimpBestScore(context),
            PrefsManager.getChimpBestScore(context).toScoreLabel(),
            now
        )
    }

    suspend fun clearAllLocalData() {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("game_session", null, null)
            db.delete("game_best_score", null, null)
            db.delete("user_profile", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun clearLocalDataForUid(uid: String) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("game_session", "uid = ?", arrayOf(uid))
            db.delete("game_best_score", "uid = ?", arrayOf(uid))
            db.delete("user_profile", "uid = ?", arrayOf(uid))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun upsertAuthProfile(uid: String, email: String, username: String, avatarUri: String = "") {
        val now = System.currentTimeMillis()
        val current = getProfileByUid(uid)
        val profile = UserProfileRecord(
            uid = uid,
            email = email,
            username = username,
            avatarUri = if (avatarUri.isNotBlank()) avatarUri else (current?.avatarUri ?: ""),
            profileVideoUri = current?.profileVideoUri ?: "",
            overallScore = current?.overallScore ?: 0,
            createdAt = current?.createdAt ?: now,
            updatedAt = now
        )
        upsertProfile(profile)
    }

    suspend fun getProfileByUid(uid: String): UserProfileRecord? {
        val db = dbHelper.readableDatabase
        db.rawQuery(
            "SELECT uid, email, username, avatarUri, profileVideoUri, overallScore, createdAt, updatedAt FROM user_profile WHERE uid = ? LIMIT 1",
            arrayOf(uid)
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return UserProfileRecord(
                uid = cursor.getString(0),
                email = cursor.getString(1),
                username = cursor.getString(2),
                avatarUri = cursor.getString(3),
                profileVideoUri = cursor.getString(4),
                overallScore = cursor.getInt(5),
                createdAt = cursor.getLong(6),
                updatedAt = cursor.getLong(7)
            )
        }
    }

    private fun upsertProfile(profile: UserProfileRecord) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("uid", profile.uid)
            put("email", profile.email)
            put("username", profile.username)
            put("avatarUri", profile.avatarUri)
            put("profileVideoUri", profile.profileVideoUri)
            put("overallScore", profile.overallScore)
            put("createdAt", profile.createdAt)
            put("updatedAt", profile.updatedAt)
        }
        db.insertWithOnConflict("user_profile", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun hasAnyUserData(uid: String): Boolean {
        val db = dbHelper.readableDatabase
        db.rawQuery(
            "SELECT EXISTS(SELECT 1 FROM user_profile WHERE uid = ?) OR EXISTS(SELECT 1 FROM game_best_score WHERE uid = ?)",
            arrayOf(uid, uid)
        ).use { cursor ->
            if (!cursor.moveToFirst()) return false
            return cursor.getInt(0) == 1
        }
    }

    private fun getBestValuesByGame(uid: String): Map<String, Int> {
        val db = dbHelper.readableDatabase
        val results = mutableMapOf<String, Int>()
        db.rawQuery(
            "SELECT gameName, bestValue FROM game_best_score WHERE uid = ?",
            arrayOf(uid)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results[cursor.getString(0)] = cursor.getInt(1)
            }
        }
        return results
    }

    private fun upsertBestScore(uid: String, gameName: String, bestValue: Int, scoreLabel: String, updatedAt: Long) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("uid", uid)
            put("gameName", gameName)
            put("bestValue", bestValue)
            put("scoreLabel", scoreLabel)
            put("updatedAt", updatedAt)
        }
        db.insertWithOnConflict("game_best_score", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun Int.toReactionLabel(): String = if (this == 0) "--" else "$this ms avg"
    private fun Int.toLevelLabel(): String = if (this == 0) "--" else "Level $this"
    private fun Int.toWordsLabel(): String = if (this == 0) "--" else "$this words"
    private fun Int.toScoreLabel(): String = if (this == 0) "--" else "Score $this"
}

private class NeuroArenaSQLiteHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE user_profile (
                uid TEXT PRIMARY KEY,
                email TEXT NOT NULL,
                username TEXT NOT NULL,
                avatarUri TEXT NOT NULL,
                profileVideoUri TEXT NOT NULL DEFAULT '',
                overallScore INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE game_best_score (
                uid TEXT NOT NULL,
                gameName TEXT NOT NULL,
                bestValue INTEGER NOT NULL,
                scoreLabel TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(uid, gameName)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE game_session (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uid TEXT NOT NULL,
                gameName TEXT NOT NULL,
                scoreValue INTEGER NOT NULL,
                scoreLabel TEXT NOT NULL,
                playedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE user_profile ADD COLUMN profileVideoUri TEXT NOT NULL DEFAULT ''")
        }
    }

    companion object {
        private const val DB_NAME = "neuroarena_sqlite.db"
        private const val DB_VERSION = 2
    }
}
