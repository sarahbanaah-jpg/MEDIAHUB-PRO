package com.mediahub.rebuilt.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Optional notification access. Only the short title is exposed to the in-app island. */
class MediaHubNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val title = notification.extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)
            ?.toString()?.trim().orEmpty()
        if (title.isBlank()) return
        getSharedPreferences("mediahub_settings", MODE_PRIVATE).edit()
            .putString("island_notification_title", title.take(64))
            .apply()
        sendBroadcast(android.content.Intent(ACTION_NOTIFICATION_UPDATED).setPackage(packageName))
    }

    companion object {
        const val ACTION_NOTIFICATION_UPDATED = "com.mediahub.rebuilt.NOTIFICATION_UPDATED"
    }
}
