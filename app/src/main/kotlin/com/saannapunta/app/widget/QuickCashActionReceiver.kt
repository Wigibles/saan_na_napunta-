package com.saannapunta.app.widget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.saannapunta.app.MainActivity
import com.saannapunta.app.R
import com.saannapunta.app.data.DataRepository
import com.saannapunta.app.data.model.MoneyFormatter

class QuickCashActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ONE_TAP_ADD = "com.saannapunta.app.action.ONE_TAP_ADD"
        const val ACTION_UNDO_QUICK_CASH = "com.saannapunta.app.action.UNDO_QUICK_CASH"
        const val EXTRA_UNDO_TX_ID = "extra_undo_tx_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val CHANNEL_ID_QUICK_CASH = "saannapunta_quick_cash"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val repository = DataRepository.getInstance(context.applicationContext)

        when (action) {
            ACTION_ONE_TAP_ADD -> {
                val amountCentavos = intent.getLongExtra(QuickCashEntryActivity.EXTRA_PRESET_AMOUNT_CENTAVOS, 0L)
                if (amountCentavos <= 0L) return

                val pendingResult = goAsync()
                repository.recordQuickCashExpense(
                    amountCentavos = amountCentavos,
                    category = null,
                    onCreated = { txId, category, accountName ->
                        try {
                            postUndoNotification(
                                context = context.applicationContext,
                                txId = txId,
                                amountCentavos = amountCentavos,
                                category = category,
                                accountName = accountName
                            )
                        } finally {
                            pendingResult.finish()
                        }
                    }
                )
            }

            ACTION_UNDO_QUICK_CASH -> {
                val txId = intent.getLongExtra(EXTRA_UNDO_TX_ID, -1L)
                val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                if (notifId != 0) {
                    NotificationManagerCompat.from(context.applicationContext).cancel(notifId)
                }
                if (txId <= 0L) return

                val pendingResult = goAsync()
                repository.deleteTransactionById(txId) { deleted ->
                    if (deleted) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            Toast.makeText(context.applicationContext, "Quick cash entry undone", Toast.LENGTH_SHORT).show()
                        }
                    }
                    pendingResult.finish()
                }
            }
        }
    }

    private fun postUndoNotification(
        context: Context,
        txId: Long,
        amountCentavos: Long,
        category: String,
        accountName: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "Logged ${MoneyFormatter.format(amountCentavos, false)} · $category",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }
        }

        ensureChannel(context)
        val notificationId = (40_000 + (txId % 10_000)).toInt()

        val undoIntent = Intent(context, QuickCashActionReceiver::class.java).apply {
            action = ACTION_UNDO_QUICK_CASH
            putExtra(EXTRA_UNDO_TX_ID, txId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val undoPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            undoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 100_000,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val amountStr = MoneyFormatter.format(amountCentavos, false)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_QUICK_CASH)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle("Logged $amountStr · $category")
            .setContentText("Paid from $accountName. Tap Undo if this was a mistake.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(30_000L)
            .setContentIntent(openAppPendingIntent)
            .addAction(0, "Undo", undoPendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID_QUICK_CASH,
                "Quick Cash Entries",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Confirmations and Undo actions for one-tap widget expenses"
            }
            nm.createNotificationChannel(channel)
        }
    }
}

