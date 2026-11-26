package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NoorCyan
import com.example.ui.theme.NoorEmerald
import com.example.ui.theme.NoorGold
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UrduFontFamily

@Composable
fun MicPermissionDialog(
    isPermanentlyDenied: Boolean,
    purposeLabel: String,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        icon = {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Microphone authorization",
                tint = NoorCyan,
                modifier = Modifier.size(34.dp)
            )
        },
        title = {
            Text(
                text = if (isPermanentlyDenied) {
                    "مائیکروفون کی اجازت درکار ہے"
                } else {
                    "آواز اور وائس ایکٹیویشن کی اجازت"
                },
                style = TextStyle(
                    fontFamily = UrduFontFamily,
                    fontSize = 19.sp,
                    textDirection = TextDirection.Content
                ),
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isPermanentlyDenied) {
                        "مائیکروفون کی اجازت سسٹم سے بند ہے۔ براہ کرم ایپ سیٹنگز کھول کر Microphone (اور Notifications) آن کریں تاکہ نور آپ کی آواز ریکارڈ اور پراسیس کر سکے۔"
                    } else {
                        "نور کو $purposeLabel کے لیے مائیکروفون (RECORD_AUDIO) اور فعال ریکارڈنگ کی اطلاع دکھانے کے لیے نوٹیفیکیشن کی اجازت درکار ہے۔"
                    },
                    style = TextStyle(
                        fontFamily = UrduFontFamily,
                        fontSize = 14.5.sp,
                        lineHeight = 24.sp,
                        textDirection = TextDirection.Content
                    ),
                    color = TextPrimary
                )

                Surface(
                    color = ObsidianSurface,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = NoorEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "No secret recording: audio is captured only when you tap Record/Mic or turn on Voice Activation, with a live indicator and instant STOP.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isPermanentlyDenied) {
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NoorGold,
                        contentColor = ObsidianVoid
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("open_mic_settings_button")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Settings")
                }
            } else {
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NoorCyan,
                        contentColor = ObsidianVoid
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("grant_mic_dialog_button")
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Allow Microphone")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("dismiss_mic_dialog_button")
            ) {
                Text("Not Now", color = TextSecondary)
            }
        }
    )
}
