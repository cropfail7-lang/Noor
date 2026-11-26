package com.example.device

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.service.NoorAccessibilityService
import com.example.service.NoorNotificationListenerService

data class PermissionDashboardState(
    val microphoneGranted: Boolean = false,
    val cameraGranted: Boolean = false,
    val contactsGranted: Boolean = false,
    val phoneCallGranted: Boolean = false,
    val smsGranted: Boolean = false,
    val locationGranted: Boolean = false,
    val notificationsPostGranted: Boolean = true,
    val notificationListenerEnabled: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val writeSettingsGranted: Boolean = false
) {
    val grantedCount: Int
        get() = listOf(
            microphoneGranted,
            cameraGranted,
            contactsGranted,
            phoneCallGranted,
            smsGranted,
            locationGranted,
            notificationsPostGranted,
            notificationListenerEnabled,
            accessibilityEnabled,
            writeSettingsGranted
        ).count { it }

    val totalCount: Int = 10
}

object PermissionStatusChecker {

    fun inspect(context: Context): PermissionDashboardState {
        fun has(perm: String): Boolean =
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

        val postNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            has(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

        return PermissionDashboardState(
            microphoneGranted = has(Manifest.permission.RECORD_AUDIO),
            cameraGranted = has(Manifest.permission.CAMERA),
            contactsGranted = has(Manifest.permission.READ_CONTACTS),
            phoneCallGranted = has(Manifest.permission.CALL_PHONE),
            smsGranted = has(Manifest.permission.SEND_SMS),
            locationGranted = has(Manifest.permission.ACCESS_COARSE_LOCATION) ||
                has(Manifest.permission.ACCESS_FINE_LOCATION),
            notificationsPostGranted = postNotifications,
            notificationListenerEnabled = NoorNotificationListenerService.isEnabled(context),
            accessibilityEnabled = NoorAccessibilityService.isEnabledInSettings(context),
            writeSettingsGranted = Settings.System.canWrite(context)
        )
    }

    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openWriteSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
