package com.saannapunta.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.saannapunta.app.MainActivity
import com.saannapunta.app.R
import com.saannapunta.app.data.CycleCalculator
import com.saannapunta.app.data.DailyHabitEngine
import com.saannapunta.app.data.DataRepository
import java.util.concurrent.TimeUnit

class BillReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ID_BILLS = "saannapunta_bill_reminders"
        const val WORK_NAME_DAILY = "saannapunta_daily_bill_reminders"
        const val WORK_NAME_IMMEDIATE = "saannapunta_immediate_bill_reminders"
    }

    override suspend fun doWork(): Result {
        if (!hasNotificationPermission(applicationContext)) {
            return Result.success()
        }

        val repository = DataRepository.getInstance(applicationContext)
        val (bills, daysBefore) = repository.loadUpcomingBillsForReminders()
        val todayDate = CycleCalculator.toLocalDate(System.currentTimeMillis(), CycleCalculator.MANILA_ZONE)

        ensureChannel(applicationContext)

        for (bill in bills) {
            val decision = DailyHabitEngine.shouldRemindBill(
                bill = bill,
                todayDate = todayDate,
                daysBefore = daysBefore,
                zone = CycleCalculator.MANILA_ZONE
            ) ?: continue

            val posted = postBillNotification(
                context = applicationContext,
                billId = bill.id,
                title = decision.notificationTitle,
                body = decision.notificationBody
            )
            if (posted) {
                repository.updateBillLastReminded(bill.id, decision.newLastRemindedFor)
            }
        }

        return Result.success()
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    private fun postBillNotification(
        context: Context,
        billId: Long,
        title: String,
        body: String
    ): Boolean {
        val notificationId = (50_000 + (billId % 10_000)).toInt()

        val payBillIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_PAY_BILL_ID, billId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            payBillIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_BILLS)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        }.getOrDefault(false)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID_BILLS,
                "Bill Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Upcoming, due-day, and weekly overdue bill reminders"
            }
            nm.createNotificationChannel(channel)
        }
    }
}

object BillReminderScheduler {

    /**
     * Schedules the daily WorkManager job around 8:00 AM Manila time without requiring exact alarm permissions.
     */
    fun scheduleDailyCheck(context: Context) {
        val appCtx = context.applicationContext
        val initialDelayMs = DailyHabitEngine.computeInitialDelayToNext8AmManila(System.currentTimeMillis())

        val workRequest = PeriodicWorkRequestBuilder<BillReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(appCtx).enqueueUniquePeriodicWork(
            BillReminderWorker.WORK_NAME_DAILY,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * Runs an immediate check (e.g. after a user adds a bill or grants notification permission).
     */
    fun runCheckNow(context: Context) {
        val appCtx = context.applicationContext
        val oneTime = OneTimeWorkRequestBuilder<BillReminderWorker>().build()
        WorkManager.getInstance(appCtx).enqueueUniqueWork(
            BillReminderWorker.WORK_NAME_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            oneTime
        )
    }
}

