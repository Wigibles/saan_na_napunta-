package com.saannapunta.app.parser

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.saannapunta.app.data.DataRepository
import java.util.concurrent.atomic.AtomicInteger

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isNullOrEmpty()) return

        // Long bank SMS messages arrive split across multiple 153-char GSM parts. Group parts by
        // sender and concatenate their bodies before parsing.
        val groupedBySender = parts.groupBy { it.originatingAddress ?: "" }
        val repository = DataRepository.getInstance(context.applicationContext)
        val pendingResult = goAsync()
        val remaining = AtomicInteger(groupedBySender.size)

        for ((sender, smsList) in groupedBySender) {
            val fullBody = smsList.joinToString(separator = "") { it.messageBody ?: "" }
            val timestamp = smsList.firstOrNull()?.timestampMillis?.takeIf { it > 0L } ?: System.currentTimeMillis()
            repository.parseAndRecordMessage(
                sender = sender,
                messageText = fullBody,
                receivedAt = timestamp,
                onDone = {
                    if (remaining.decrementAndGet() <= 0) {
                        runCatching { pendingResult.finish() }
                    }
                }
            )
        }
    }
}
