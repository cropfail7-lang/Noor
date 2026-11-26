package com.example.work

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
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.local.NoorDatabase
import com.example.data.repository.NoorDataRepository
import java.util.concurrent.TimeUnit

class ScheduledReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: "نور اے آئی یاددہانی"
        val message = inputData.getString(KEY_MESSAGE) ?: "آپ کی مقرر کردہ یاددہانی کا وقت ہو گیا ہے۔"
        showReminderNotification(applicationContext, title, message)
        return Result.success()
    }

    private fun showReminderNotification(context: Context, title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            title.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            notification
        )
    }

    companion object {
        const val CHANNEL_ID = "noor_reminders_channel"
        const val KEY_TITLE = "reminder_title"
        const val KEY_MESSAGE = "reminder_message"
    }
}

class MemoryMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = NoorDataRepository(NoorDatabase.getInstance(applicationContext))
        val sevenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        repo.pruneOldNotifications(sevenDaysAgo)
        return Result.success()
    }
}

object AssistantWorkScheduler {
    private const val MAINTENANCE_WORK_NAME = "noor_memory_maintenance"

    fun scheduleReminder(context: Context, title: String, delayMinutes: Long): String {
        val safeDelay = delayMinutes.coerceAtLeast(1L)
        val input = Data.Builder()
            .putString(ScheduledReminderWorker.KEY_TITLE, "Noor یاددہانی")
            .putString(ScheduledReminderWorker.KEY_MESSAGE, title)
            .build()

        val request = OneTimeWorkRequestBuilder<ScheduledReminderWorker>()
            .setInitialDelay(safeDelay, TimeUnit.MINUTES)
            .setInputData(input)
            .build()

        WorkManager.getInstance(context).enqueue(request)
        return request.id.toString()
    }

    fun schedulePeriodicMaintenance(context: Context) {
        val request = PeriodicWorkRequestBuilder<MemoryMaintenanceWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MAINTENANCE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
