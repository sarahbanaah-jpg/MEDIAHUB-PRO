package com.mediahub.rebuilt.social

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ChatNotificationService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .collection("devices").document(token).set(mapOf("token" to token, "updatedAt" to FieldValue.serverTimestamp()))
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val chatId = data["chatId"] ?: return
        val senderId = data["senderId"] ?: return
        val senderName = data["senderName"] ?: "MEDIAHUB-PRO user"
        val body = data["body"] ?: "New message"
        val channel = "mediahub_messages"
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channel, "Messages and calls", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = Intent(this, ChatActivity::class.java)
            .putExtra(ChatActivity.EXTRA_UID, senderId)
            .putExtra(ChatActivity.EXTRA_NAME, senderName)
        val openPi = PendingIntent.getActivity(this, chatId.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val replyKey = "chat_reply"
        val remote = RemoteInput.Builder(replyKey).setLabel("Reply").build()
        val replyIntent = Intent(this, ChatNotificationActionService::class.java)
            .setAction(ChatNotificationActionService.ACTION_REPLY)
            .putExtra("chatId", chatId).putExtra("receiverId", senderId)
        val replyPi = PendingIntent.getService(this, chatId.hashCode()+1, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        val reply = NotificationCompat.Action.Builder(android.R.drawable.ic_menu_send, "Reply", replyPi)
            .addRemoteInput(remote).setAllowGeneratedReplies(true).build()
        val readIntent = Intent(this, ChatNotificationActionService::class.java)
            .setAction(ChatNotificationActionService.ACTION_READ).putExtra("chatId", chatId)
        val readPi = PendingIntent.getService(this, chatId.hashCode()+2, readIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(com.mediahub.rebuilt.R.drawable.ic_mediahub)
            .setContentTitle(senderName).setContentText(body)
            .setContentIntent(openPi).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(reply).addAction(android.R.drawable.checkbox_on_background, "Mark as read", readPi)
            .build()
        getSystemService(NotificationManager::class.java).notify(chatId.hashCode(), notification)
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
            db.collection("chats").document(chatId).update("readAt.${user.uid}", FieldValue.serverTimestamp())
            return
        }
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence("chat_reply")?.toString()?.trim().orEmpty()
        if (text.isBlank()) return
        val receiver = intent.getStringExtra("receiverId") ?: return
        db.collection("chats").document(chatId).collection("messages").add(mapOf(
            "senderId" to user.uid, "receiverId" to receiver, "type" to "text", "text" to text,
            "createdAt" to FieldValue.serverTimestamp(), "viewLimit" to 0, "viewCount" to 0,
            "viewedBy" to emptyList<String>(), "deletedFor" to emptyList<String>(),
            "deletedForEveryone" to false, "mediaUrl" to null, "expiresAt" to null
        ))
    }
}
