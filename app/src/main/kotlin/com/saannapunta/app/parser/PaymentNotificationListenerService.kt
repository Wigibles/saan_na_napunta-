package com.saannapunta.app.parser

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.saannapunta.app.data.DataRepository

class PaymentNotificationListenerService : NotificationListenerService() {

    private val watchedPackages: Set<String>
        get() = SmsMessageParser.allowedNotificationPackages

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName?.trim()?.lowercase() ?: return
        if (pkg !in watchedPackages) return
        // Group summaries repeat the text of the child notifications we already parse.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: text

        val combinedBody = if (bigText.isNotBlank()) bigText else text
        if (combinedBody.isBlank()) return

        val repository = DataRepository.getInstance(applicationContext)
        repository.parseAndRecordMessage(
            sender = pkg,
            messageText = combinedBody,
            receivedAt = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()
        )
    }
}

