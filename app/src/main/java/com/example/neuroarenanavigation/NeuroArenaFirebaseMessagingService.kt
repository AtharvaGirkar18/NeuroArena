package com.example.neuroarenanavigation

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class NeuroArenaFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PrefsManager.saveFcmToken(applicationContext, token)
        Log.d(TAG, "FCM token refreshed: $token")

        // TODO: send token to backend after auth is implemented (Experiment 11).
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        AppNotifications.ensureChannels(applicationContext)

        val dataTitle = message.data["title"]
        val dataBody = message.data["body"]

        val title = dataTitle
            ?: message.notification?.title
            ?: "NeuroArena Update"
        val body = dataBody
            ?: message.notification?.body
            ?: "You have a new notification."

        AppNotifications.notifyPush(applicationContext, title, body)
        Log.d(TAG, "FCM message received. title=$title body=$body")
    }

    companion object {
        private const val TAG = "NeuroArenaFCM"
    }
}
