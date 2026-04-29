package com.example.neuroarenanavigation.data.remote

import android.content.Context
import com.example.neuroarenanavigation.PrefsManager
import com.google.firebase.database.FirebaseDatabase

data class OnlineUserProfile(
    val uid: String = "",
    val username: String = "",
    val usernameLower: String = "",
    val overallScore: Int = 0,
    val reactionTimeBestAvgMs: Int = 0,
    val sequenceBestLevel: Int = 0,
    val verbalBestScore: Int = 0,
    val numberBestLevel: Int = 0,
    val visualBestLevel: Int = 0,
    val chimpBestScore: Int = 0,
    val avatarKey: String = "user",
    val profileImageUri: String = ""
)

class OnlineProfilesRepository {

    private val usersRef = FirebaseDatabase.getInstance().getReference("users")

    fun syncCurrentUser(context: Context, uid: String, fallbackName: String) {
        if (uid.isBlank() || uid == "local_guest") return

        val username = PrefsManager.getUsername(context).ifEmpty { fallbackName }.trim()
        if (username.isBlank()) return

        val profile = OnlineUserProfile(
            uid = uid,
            username = username,
            usernameLower = username.lowercase(),
            overallScore = PrefsManager.getOverallRankingScore(context),
            reactionTimeBestAvgMs = PrefsManager.getReactionTimeBestAvgMs(context),
            sequenceBestLevel = PrefsManager.getSequenceMemoryBestLevel(context),
            verbalBestScore = PrefsManager.getVerbalMemoryBestScore(context),
            numberBestLevel = PrefsManager.getNumberMemoryBestLevel(context),
            visualBestLevel = PrefsManager.getVisualMemoryBestLevel(context),
            chimpBestScore = PrefsManager.getChimpBestScore(context),
            avatarKey = PrefsManager.getProfileAvatar(context),
            profileImageUri = PrefsManager.getProfileImageUri(context)
        )

        usersRef.child(uid).setValue(profile)
    }

    fun searchByUsernamePrefix(
        prefix: String,
        limit: Int = 5,
        onResult: (List<OnlineUserProfile>) -> Unit,
        onError: (String) -> Unit
    ) {
        val normalized = prefix.trim().lowercase()
        if (normalized.isBlank()) {
            onResult(emptyList())
            return
        }

        usersRef
            .orderByChild("usernameLower")
            .startAt(normalized)
            .endAt(normalized + "\uf8ff")
            .limitToFirst(limit)
            .get()
            .addOnSuccessListener { snapshot ->
                val results = snapshot.children.mapNotNull { it.getValue(OnlineUserProfile::class.java) }
                onResult(results)
            }
            .addOnFailureListener { error ->
                onError(error.message ?: "Search failed")
            }
    }
}
