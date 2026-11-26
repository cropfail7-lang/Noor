package com.example.device

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Geocoder
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.data.repository.NoorDataRepository
import com.example.service.NoorAccessibilityService
import com.example.service.NoorNotificationListenerService
import com.example.work.AssistantWorkScheduler
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

data class DeviceActionResult(
    val success: Boolean,
    val message: String,
    val details: String = ""
)

class DeviceActionExecutor(
    private val context: Context,
    private val repository: NoorDataRepository
) {

    fun openApp(appName: String): DeviceActionResult {
        val query = appName.trim().lowercase()
        val pm = context.packageManager

        val knownPackages = mapOf(
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "youtube" to listOf("com.google.android.youtube"),
            "chrome" to listOf("com.android.chrome"),
            "gmail" to listOf("com.google.android.gm"),
            "maps" to listOf("com.google.android.apps.maps"),
            "google maps" to listOf("com.google.android.apps.maps"),
            "settings" to listOf("com.android.settings"),
            "clock" to listOf("com.google.android.deskclock", "com.android.deskclock"),
            "calculator" to listOf("com.google.android.calculator", "com.android.calculator2"),
            "camera" to listOf("com.android.camera", "com.google.android.GoogleCamera"),
            "messages" to listOf("com.google.android.apps.messaging", "com.android.mms"),
            "phone" to listOf("com.google.android.dialer", "com.android.dialer")
        )

        if (query == "settings" || query == "سیٹنگز") {
            return openSettings("general")
        }
        if (query == "camera" || query == "کیمرہ") {
            return try {
                val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                DeviceActionResult(true, "Opened device Camera app.")
            } catch (e: Exception) {
                DeviceActionResult(false, "Unable to open device camera: ${e.localizedMessage}")
            }
        }

        // Check known package aliases first
        val candidatePackages = knownPackages.entries
            .firstOrNull { query.contains(it.key) }
            ?.value
            .orEmpty()

        for (pkg in candidatePackages) {
            val launch = pm.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                return DeviceActionResult(true, "Opened $appName ($pkg).")
            }
        }

        // Search installed launcher activities via PackageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val activities = pm.queryIntentActivities(mainIntent, 0)
        val matched = activities.firstOrNull { resolveInfo ->
            val label = resolveInfo.loadLabel(pm).toString().lowercase()
            label == query || label.contains(query) || query.contains(label)
        }
        if (matched != null) {
            val launch = pm.getLaunchIntentForPackage(matched.activityInfo.packageName)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                return DeviceActionResult(true, "Opened ${matched.loadLabel(pm)}.")
            }
        }

        return DeviceActionResult(
            false,
            "App '$appName' is not installed or visible on this device."
        )
    }

    fun openSettings(section: String): DeviceActionResult {
        val action = when (section.lowercase().trim()) {
            "wifi", "wi-fi" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Settings.Panel.ACTION_WIFI
            } else {
                Settings.ACTION_WIFI_SETTINGS
            }
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "volume", "sound" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Settings.Panel.ACTION_VOLUME
            } else {
                Settings.ACTION_SOUND_SETTINGS
            }
            "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "internet" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Settings.Panel.ACTION_INTERNET_CONNECTIVITY
            } else {
                Settings.ACTION_WIRELESS_SETTINGS
            }
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            DeviceActionResult(true, "Opened $section settings panel.")
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
            DeviceActionResult(true, "Opened main system Settings.")
        }
    }

    fun controlDeviceSetting(
        settingType: String,
        action: String,
        levelPercent: Int?
    ): DeviceActionResult {
        return when (settingType.lowercase().trim()) {
            "wifi", "wi-fi" -> {
                val res = openSettings("wifi")
                DeviceActionResult(
                    res.success,
                    "Android 10+ security restricts silent background Wi-Fi toggling by apps; opened the quick Wi-Fi control panel so you can toggle it immediately."
                )
            }
            "bluetooth" -> {
                val res = openSettings("bluetooth")
                DeviceActionResult(
                    res.success,
                    "Android 13+ restricts silent Bluetooth toggling by apps; opened Bluetooth settings so you can toggle it in one tap."
                )
            }
            "volume" -> adjustVolume(action, levelPercent)
            "brightness" -> adjustBrightness(action, levelPercent)
            "flashlight", "torch" -> toggleFlashlight(action)
            "settings" -> openSettings(action)
            else -> DeviceActionResult(false, "Unsupported setting type: $settingType")
        }
    }

    private fun adjustVolume(action: String, levelPercent: Int?): DeviceActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_MUSIC
        val maxVol = audioManager.getStreamMaxVolume(stream)
        val currentVol = audioManager.getStreamVolume(stream)
        val targetVol = when {
            levelPercent != null -> ((levelPercent.coerceIn(0, 100) / 100f) * maxVol).toInt()
            action.equals("mute", true) || action.equals("off", true) -> 0
            action.equals("up", true) || action.equals("increase", true) -> (currentVol + (maxVol / 5).coerceAtLeast(1)).coerceAtMost(maxVol)
            action.equals("down", true) || action.equals("decrease", true) -> (currentVol - (maxVol / 5).coerceAtLeast(1)).coerceAtLeast(0)
            else -> currentVol
        }
        audioManager.setStreamVolume(stream, targetVol, AudioManager.FLAG_SHOW_UI)
        val pct = if (maxVol > 0) (targetVol * 100) / maxVol else 0
        return DeviceActionResult(true, "Media volume adjusted to $pct%.")
    }

    private fun adjustBrightness(action: String, levelPercent: Int?): DeviceActionResult {
        if (!Settings.System.canWrite(context)) {
            PermissionStatusChecker.openWriteSettings(context)
            return DeviceActionResult(
                false,
                "Modify System Settings permission is not granted yet. Opened the permission screen so you can enable brightness control."
            )
        }
        val current = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (_: Exception) {
            128
        }
        val target = when {
            levelPercent != null -> ((levelPercent.coerceIn(5, 100) / 100f) * 255).toInt()
            action.equals("up", true) || action.equals("increase", true) -> (current + 50).coerceAtMost(255)
            action.equals("down", true) || action.equals("decrease", true) -> (current - 50).coerceAtLeast(15)
            else -> current
        }
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, target)
        val pct = (target * 100) / 255
        return DeviceActionResult(true, "Screen brightness set to $pct%.")
    }

    private fun toggleFlashlight(action: String): DeviceActionResult {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return DeviceActionResult(false, "No camera flash unit found on this device.")

            val enable = action.equals("on", true) || action.equals("enable", true)
            cameraManager.setTorchMode(cameraId, enable)
            DeviceActionResult(true, if (enable) "Flashlight turned ON." else "Flashlight turned OFF.")
        } catch (e: Exception) {
            DeviceActionResult(false, "Flashlight control failed: ${e.localizedMessage}")
        }
    }

    fun searchContacts(query: String): DeviceActionResult {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            return DeviceActionResult(false, "READ_CONTACTS permission is not granted.")
        }

        val matches = mutableListOf<String>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%${query.trim()}%")
        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext() && matches.size < 8) {
                val name = cursor.getString(nameIdx) ?: continue
                val num = cursor.getString(numIdx) ?: continue
                matches.add("$name: $num")
            }
        }
        return if (matches.isEmpty()) {
            DeviceActionResult(true, "No matching contacts found for '$query'.")
        } else {
            DeviceActionResult(true, "Contacts found:\n" + matches.joinToString("\n"))
        }
    }

    fun resolvePhoneNumber(contactOrNumber: String): String {
        val cleaned = contactOrNumber.trim()
        if (cleaned.any { it.isDigit() } && cleaned.count { it.isDigit() } >= 4) {
            return cleaned
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return cleaned

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$cleaned%"),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                return cursor.getString(numIdx)?.trim() ?: cleaned
            }
        }
        return cleaned
    }

    fun makePhoneCall(contactOrNumber: String): DeviceActionResult {
        val number = resolvePhoneNumber(contactOrNumber)
        if (number.isBlank() || number.none { it.isDigit() }) {
            return DeviceActionResult(false, "Could not resolve a valid phone number for '$contactOrNumber'.")
        }
        val hasCallPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val intent = if (hasCallPerm) {
            Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}"))
        } else {
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            val mode = if (hasCallPerm) "Calling" else "Opened dialer for"
            DeviceActionResult(true, "$mode $contactOrNumber ($number).")
        } catch (e: Exception) {
            DeviceActionResult(false, "Call failed: ${e.localizedMessage}")
        }
    }

    fun sendMessage(channel: String, recipient: String, message: String): DeviceActionResult {
        val number = resolvePhoneNumber(recipient)
        return if (channel.equals("whatsapp", true)) {
            try {
                val digitsOnly = number.filter { it.isDigit() || it == '+' }
                val encodedMsg = URLEncoder.encode(message, "UTF-8")
                val intent = if (digitsOnly.length >= 7) {
                    val waNumber = if (digitsOnly.startsWith("0")) {
                        "92" + digitsOnly.drop(1)
                    } else {
                        digitsOnly.removePrefix("+")
                    }
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$waNumber?text=$encodedMsg"))
                } else {
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        setPackage("com.whatsapp")
                        putExtra(Intent.EXTRA_TEXT, message)
                    }
                }.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                DeviceActionResult(true, "Opened WhatsApp message composer for $recipient.")
            } catch (e: Exception) {
                DeviceActionResult(false, "Could not open WhatsApp: ${e.localizedMessage}")
            }
        } else {
            val hasSmsPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasSmsPerm && number.any { it.isDigit() }) {
                try {
                    val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getDefault()
                    }
                    smsManager.sendTextMessage(number, null, message, null, null)
                    DeviceActionResult(true, "SMS sent to $recipient ($number).")
                } catch (e: Exception) {
                    DeviceActionResult(false, "SMS failed: ${e.localizedMessage}")
                }
            } else {
                val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).apply {
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(smsIntent)
                DeviceActionResult(true, "Opened SMS app pre-filled for $recipient.")
            }
        }
    }

    suspend fun readNotifications(limit: Int): DeviceActionResult {
        val enabled = NoorNotificationListenerService.isEnabled(context)
        val stored = repository.getLatestNotifications(limit.coerceIn(1, 15))
        repository.markAllNotificationsRead()
        if (stored.isEmpty()) {
            val hint = if (!enabled) {
                "Notification Access is not enabled yet in Permissions."
            } else {
                "No unread or recent notifications captured yet."
            }
            return DeviceActionResult(enabled, hint)
        }
        val formatted = stored.joinToString("\n") {
            "- [${it.appName}] ${it.title}: ${it.body}"
        }
        return DeviceActionResult(true, formatted)
    }

    fun setAlarmOrTimer(
        type: String,
        hour: Int?,
        minute: Int?,
        durationSeconds: Int?,
        label: String?
    ): DeviceActionResult {
        return try {
            val intent = if (type.equals("timer", true)) {
                Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, (durationSeconds ?: 60).coerceAtLeast(1))
                    putExtra(AlarmClock.EXTRA_MESSAGE, label ?: "Noor Timer")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                }
            } else {
                Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, (hour ?: 7).coerceIn(0, 23))
                    putExtra(AlarmClock.EXTRA_MINUTES, (minute ?: 0).coerceIn(0, 59))
                    putExtra(AlarmClock.EXTRA_MESSAGE, label ?: "Noor Alarm")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                }
            }.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            DeviceActionResult(true, "Set $type (${label ?: "Noor"}).")
        } catch (e: Exception) {
            DeviceActionResult(false, "Could not open Clock app: ${e.localizedMessage}")
        }
    }

    fun scheduleReminder(title: String, delayMinutes: Long): DeviceActionResult {
        val id = AssistantWorkScheduler.scheduleReminder(context, title, delayMinutes)
        return DeviceActionResult(
            true,
            "Scheduled reminder '$title' in ${delayMinutes.coerceAtLeast(1)} minute(s) (WorkManager ID: $id)."
        )
    }

    fun webSearch(query: String): DeviceActionResult {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            DeviceActionResult(true, "Opened Google search for '$query'.")
        } catch (e: Exception) {
            DeviceActionResult(false, "Search failed: ${e.localizedMessage}")
        }
    }

    fun searchDocuments(query: String): DeviceActionResult {
        val results = mutableListOf<String>()
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%${query.trim()}%")
        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                while (cursor.moveToNext() && results.size < 10) {
                    val name = cursor.getString(nameCol) ?: continue
                    val mime = cursor.getString(mimeCol).orEmpty()
                    results.add("$name ($mime)")
                }
            }
        } catch (_: Exception) {
        }
        return if (results.isEmpty()) {
            DeviceActionResult(
                true,
                "No public MediaStore documents matched '$query'. You can open any file securely via the system document picker."
            )
        } else {
            DeviceActionResult(true, "Found documents:\n" + results.joinToString("\n"))
        }
    }

    suspend fun getCurrentLocation(): DeviceActionResult {
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCoarse && !hasFine) {
            return DeviceActionResult(false, "Location permission is not granted.")
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val loc = suspendCancellableCoroutine<android.location.Location?> { cont ->
            fusedClient.lastLocation
                .addOnSuccessListener { location -> cont.resume(location) }
                .addOnFailureListener { cont.resume(null) }
        } ?: return DeviceActionResult(false, "Last known location is currently unavailable. Please ensure GPS is on.")

        val addressText = withContext(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION")
                val list = Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)
                list?.firstOrNull()?.let { addr ->
                    listOfNotNull(addr.subLocality, addr.locality, addr.countryName).joinToString(", ")
                }.orEmpty()
            } catch (_: Exception) {
                ""
            }
        }
        val summary = if (addressText.isNotBlank()) {
            "Location: $addressText (${loc.latitude}, ${loc.longitude})"
        } else {
            "Coordinates: ${loc.latitude}, ${loc.longitude}"
        }
        return DeviceActionResult(true, summary)
    }

    fun performScreenAutomation(action: String, targetText: String?): DeviceActionResult {
        val service = NoorAccessibilityService.instance
            ?: return DeviceActionResult(
                false,
                "Noor Accessibility Service is not enabled. Enable it in Permissions to allow screen interaction."
            )
        return when (action.lowercase().trim()) {
            "read_screen" -> {
                val items = service.getScreenContentSummary()
                if (items.isEmpty()) {
                    DeviceActionResult(true, "Active screen has no readable text elements.")
                } else {
                    DeviceActionResult(true, "Visible screen text:\n" + items.joinToString(" | "))
                }
            }
            "click_text" -> {
                val text = targetText.orEmpty()
                if (text.isBlank()) return DeviceActionResult(false, "Target text is required.")
                val ok = service.clickNodeWithText(text)
                DeviceActionResult(ok, if (ok) "Tapped '$text' on screen." else "Could not find clickable element '$text'.")
            }
            "back", "home", "recents", "notifications", "quick_settings" -> {
                val ok = service.performSystemNavigation(action)
                DeviceActionResult(ok, if (ok) "Executed '$action' navigation action." else "Failed '$action' navigation.")
            }
            else -> DeviceActionResult(false, "Unknown accessibility action: $action")
        }
    }
}
