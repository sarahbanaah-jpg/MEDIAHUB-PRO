package com.mediahub.rebuilt.social

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Displays high-priority, premium-styled message notifications.
 * Android/OEM settings and user permission still control whether heads-up
 * banners are allowed to appear.
 */
class ChatNotificationService : FirebaseMessagingService() {
    companion object {
        private const val CHANNEL_ID = "mediahub_messages_premium_v2"
        private const val REPLY_KEY = "chat_reply"
        private const val ACCENT = 0xFFFF69A8.toInt()
    }

    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .collection("devices").document(token)
            .set(mapOf("token" to token, "updatedAt" to FieldValue.serverTimestamp()))
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val chatId = data["chatId"] ?: return
        val senderId = data["senderId"] ?: return
        val senderName = data["senderName"] ?: "MEDIAHUB-PRO user"
        val body = data["body"] ?: "You have a new message"
        val manager = getSystemService(NotificationManager::class.java)
        ensureMessageChannel(manager)

        val open = Intent(this, ChatActivity::class.java)
            .putExtra(ChatActivity.EXTRA_UID, senderId)
            .putExtra(ChatActivity.EXTRA_NAME, senderName)
        val openPi = PendingIntent.getActivity(
            this, chatId.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val replyIntent = Intent(this, ChatNotificationActionService::class.java)
            .setAction(ChatNotificationActionService.ACTION_REPLY)
            .putExtra("chatId", chatId)
            .putExtra("receiverId", senderId)
        val replyPi = PendingIntent.getService(
            this, chatId.hashCode() + 1, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val remoteInput = RemoteInput.Builder(REPLY_KEY).setLabel("Write a reply…").build()
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send, "Reply", replyPi
        ).addRemoteInput(remoteInput).setAllowGeneratedReplies(true).build()

        val readIntent = Intent(this, ChatNotificationActionService::class.java)
            .setAction(ChatNotificationActionService.ACTION_READ)
            .putExtra("chatId", chatId)
        val readPi = PendingIntent.getService(
            this, chatId.hashCode() + 2, readIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.mediahub.rebuilt.R.drawable.ic_mediahub)
            .setContentTitle(senderName)
            .setContentText(body)
            .setSubText("MEDIAHUB-PRO  •  MESSAGES")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(senderName)
                    .bigText(body)
            )
            .setColor(ACCENT)
            .setColorized(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 220, 90, 220))
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setShowWhen(true)
            .addAction(replyAction)
            .addAction(android.R.drawable.checkbox_on_background, "Mark as read", readPi)
            .build()

        manager.notify(chatId.hashCode(), notification)
    }

    private fun ensureMessageChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(CHANNEL_ID) == null
        ) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MEDIAHUB-PRO Premium Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Premium message alerts and quick replies"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 220, 90, 220)
                enableLights(true)
                lightColor = ACCENT
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            manager.createNotificationChannel(channel)
        }
    }
}

class ChatNotificationActionService : android.app.IntentService("MediaHubChatNotificationActions") {
    companion object {
        const val ACTION_REPLY = "com.mediahub.rebuilt.chat.REPLY"
        const val ACTION_READ = "com.mediahub.rebuilt.chat.READ"
    }

    override fun onHandleIntent(intent: Intent?) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val chatId = intent?.getStringExtra("chatId") ?: return
        val db = FirebaseFirestore.getInstance()
        if (intent.action == ACTION_READ) {
            db.collection("chats").document(chatId)
                .update("readAt.${user.uid}", FieldValue.serverTimestamp())
            return
        }
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence("chat_reply")?.toString()?.trim().orEmpty()
        if (text.isBlank()) return
        val receiver = intent.getStringExtra("receiverId") ?: return
        db.collection("chats").document(chatId).collection("messages").add(
            mapOf(
                "senderId" to user.uid,
                "receiverId" to receiver,
                "type" to "text",
                "text" to text,
                "createdAt" to FieldValue.serverTimestamp(),
                "viewLimit" to 0,
                "viewCount" to 0,
                "viewedBy" to emptyList<String>(),
                "deletedFor" to emptyList<String>(),
                "deletedForEveryone" to false,
                "mediaUrl" to null,
                "expiresAt" to null
            )
        )
    }
}
