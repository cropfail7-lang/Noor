package com.example.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.local.CapturedNotificationEntity
import com.example.data.local.NoorDatabase
import com.example.data.repository.NoorDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NoorNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName || sbn.isOngoing) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty().trim()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty().trim()
        if (title.isBlank() && text.isBlank()) return

        val appName = try {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast('.')
        }

        serviceScope.launch {
            try {
                val repo = NoorDataRepository(NoorDatabase.getInstance(applicationContext))
                repo.addCapturedNotification(
                    CapturedNotificationEntity(
                        packageName = pkg,
                        appName = appName,
                        title = title,
                        body = text,
                        isReadByAssistant = false,
                        timestamp = sbn.postTime
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error storing captured notification", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "NoorNotifListener"

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val componentName = ComponentName(context, NoorNotificationListenerService::class.java)
            return flat.split(":").any {
                ComponentName.unflattenFromString(it) == componentName
            }
        }
    }
}
