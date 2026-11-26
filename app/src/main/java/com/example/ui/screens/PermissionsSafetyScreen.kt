package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.device.PermissionDashboardState
import com.example.device.PermissionStatusChecker
import com.example.ui.theme.NoorCyan
import com.example.ui.theme.NoorEmerald
import com.example.ui.theme.NoorGold
import com.example.ui.theme.NoorRose
import com.example.ui.theme.NoorRoseContainer
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UrduFontFamily

private data class PermissionEntryItem(
    val id: String,
    val titleEn: String,
    val titleUrdu: String,
    val description: String,
    val icon: ImageVector,
    val isGranted: Boolean,
    val onAuthorize: () -> Unit
)

@Composable
fun PermissionsSafetyScreen(
    permissions: PermissionDashboardState,
    onRefreshPermissions: () -> Unit,
    onEmergencyStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val singlePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRefreshPermissions()
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshPermissions()
    }

    val items = listOf(
        PermissionEntryItem(
            id = "mic",
            titleEn = "Microphone (Voice & Wake Word)",
            titleUrdu = "مائیکروفون — اردو آواز اور ویک ورڈ",
            description = "Needed only when listening for commands or hands-free mode.",
            icon = Icons.Default.Mic,
            isGranted = permissions.microphoneGranted,
            onAuthorize = { singlePermLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        ),
        PermissionEntryItem(
            id = "camera",
            titleEn = "Camera (CameraX AI Vision)",
            titleUrdu = "کیمرہ — اشیاء اور دستاویزات دیکھنے کے لیے",
            description = "Powers live object recognition, document reading, and torch.",
            icon = Icons.Default.CameraAlt,
            isGranted = permissions.cameraGranted,
            onAuthorize = { singlePermLauncher.launch(Manifest.permission.CAMERA) }
        ),
        PermissionEntryItem(
            id = "contacts",
            titleEn = "Contacts Lookup",
            titleUrdu = "کانٹیکٹس — نام سے نمبر تلاش کرنے کے لیے",
            description = "Resolves contact names like Ammi or Ali before dialing/messaging.",
            icon = Icons.Default.Contacts,
            isGranted = permissions.contactsGranted,
            onAuthorize = { singlePermLauncher.launch(Manifest.permission.READ_CONTACTS) }
        ),
        PermissionEntryItem(
            id = "call",
            titleEn = "Phone Calling",
            titleUrdu = "فون کال — براہ راست کال ملانے کے لیے",
            description = "Allows direct calls (always asks confirmation first).",
            icon = Icons.Default.Call,
            isGranted = permissions.phoneCallGranted,
            onAuthorize = { singlePermLauncher.launch(Manifest.permission.CALL_PHONE) }
        ),
        PermissionEntryItem(
            id = "sms",
            titleEn = "Send SMS",
            titleUrdu = "ایس ایم ایس — پیغام بھیجنے کے لیے",
            description = "Sends SMS after your explicit confirmation.",
            icon = Icons.Default.Sms,
            isGranted = permissions.smsGranted,
            onAuthorize = { singlePermLauncher.launch(Manifest.permission.SEND_SMS) }
        ),
        PermissionEntryItem(
            id = "location",
            titleEn = "Location Access",
            titleUrdu = "لوکیشن — موجودہ جگہ بتانے کے لیے",
            description = "Provides local city/coordinates when asked.",
            icon = Icons.Default.LocationOn,
            isGranted = permissions.locationGranted,
            onAuthorize = {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
            }
        ),
        PermissionEntryItem(
            id = "post_notif",
            titleEn = "Post Reminders & Wake Alerts",
            titleUrdu = "یاددہانی نوٹیفیکیشنز",
            description = "Shows WorkManager reminders and active mic indicator.",
            icon = Icons.Default.NotificationsNone,
            isGranted = permissions.notificationsPostGranted,
            onAuthorize = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    singlePermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        ),
        PermissionEntryItem(
            id = "notif_listener",
            titleEn = "Notification Reader Service",
            titleUrdu = "نوٹیفیکیشنز پڑھنے کی سروس",
            description = "Allows Noor to read your incoming notifications aloud in Urdu.",
            icon = Icons.Default.NotificationsActive,
            isGranted = permissions.notificationListenerEnabled,
            onAuthorize = { PermissionStatusChecker.openNotificationListenerSettings(context) }
        ),
        PermissionEntryItem(
            id = "accessibility",
            titleEn = "Accessibility Screen Control",
            titleUrdu = "اسکرین کنٹرول سروس",
            description = "Allows Noor to read visible screen text and perform authorized taps/navigation.",
            icon = Icons.Default.AccessibilityNew,
            isGranted = permissions.accessibilityEnabled,
            onAuthorize = { PermissionStatusChecker.openAccessibilitySettings(context) }
        ),
        PermissionEntryItem(
            id = "write_settings",
            titleEn = "System Brightness Control",
            titleUrdu = "برائٹنیس کنٹرول",
            description = "Allows adjusting screen brightness directly via voice.",
            icon = Icons.Default.BrightnessMedium,
            isGranted = permissions.writeSettingsGranted,
            onAuthorize = { PermissionStatusChecker.openWriteSettings(context) }
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .align(Alignment.Center),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "PERMISSIONS & SAFETY",
                    style = MaterialTheme.typography.titleLarge,
                    color = NoorCyan
                )
                Text(
                    text = "تمام اینڈرائیڈ اجازتیں آپ کے اختیار میں ہیں — کوئی خفیہ ریکارڈنگ نہیں",
                    style = TextStyle(fontFamily = UrduFontFamily, fontSize = 13.sp),
                    color = TextSecondary
                )
            }

            // Progress & Security Charter Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                    border = BorderStroke(1.dp, NoorCyan.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = NoorEmerald)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Authorizations Active: ${permissions.grantedCount} / ${permissions.totalCount}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { permissions.grantedCount.toFloat() / permissions.totalCount.toFloat() },
                            color = NoorEmerald,
                            trackColor = ObsidianVoid,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onEmergencyStop,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NoorRoseContainer,
                                contentColor = NoorRose
                            ),
                            border = BorderStroke(1.dp, NoorRose),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("safety_emergency_stop")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Emergency Stop All Voice & Automation")
                        }
                    }
                }
            }

            items(items, key = { it.id }) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(
                        1.dp,
                        if (item.isGranted) NoorEmerald.copy(alpha = 0.45f) else ObsidianBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (item.isGranted) NoorEmerald else NoorGold,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.titleEn,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = item.titleUrdu,
                                style = TextStyle(
                                    fontFamily = UrduFontFamily,
                                    fontSize = 13.5.sp,
                                    textDirection = TextDirection.Content
                                ),
                                color = NoorCyan
                            )
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (item.isGranted) {
                            Surface(
                                color = ObsidianSurfaceElevated,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Granted",
                                        tint = NoorEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Active", style = MaterialTheme.typography.labelSmall, color = NoorEmerald)
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = item.onAuthorize,
                                border = BorderStroke(1.dp, NoorCyan),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NoorCyan),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("authorize_${item.id}")
                            ) {
                                Text("Enable")
                            }
                        }
                    }
                }
            }
        }
    }
}
