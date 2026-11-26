package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CapturedNotificationEntity
import com.example.data.local.TaskExecutionEntity
import com.example.data.security.VaultSecretEntry
import com.example.ui.theme.NoorCyan
import com.example.ui.theme.NoorCyanContainer
import com.example.ui.theme.NoorEmerald
import com.example.ui.theme.NoorGold
import com.example.ui.theme.NoorRose
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UrduFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ControlHistoryScreen(
    tasks: List<TaskExecutionEntity>,
    notifications: List<CapturedNotificationEntity>,
    vaultEntries: List<VaultSecretEntry>,
    onClearTasks: () -> Unit,
    onReadNotificationsAloud: () -> Unit,
    onSaveVaultSecret: (String, String) -> Boolean,
    onRevealVaultSecret: (String) -> String?,
    onDeleteVaultSecret: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAddSecretDialog by remember { mutableStateOf(false) }
    val revealedMap = remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val timeFormatter = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .align(Alignment.Center)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "ACTIVITY & SECURE VAULT",
                style = MaterialTheme.typography.titleLarge,
                color = NoorCyan
            )
            Text(
                text = "مکمل کیے گئے کام، نوٹیفیکیشنز اور خفیہ مقامی والٹ",
                style = TextStyle(fontFamily = UrduFontFamily, fontSize = 13.sp),
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("Tasks", "Notifications", "Vault").forEachIndexed { idx, label ->
                    SegmentedButton(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = 3),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = NoorCyanContainer,
                            activeContentColor = NoorCyan,
                            inactiveContainerColor = ObsidianSurface,
                            inactiveContentColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("history_tab_$idx")
                    ) {
                        Text(label)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> {
                    if (tasks.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onClearTasks) {
                                Text("Clear History", color = NoorRose)
                            }
                        }
                    }
                    if (tasks.isEmpty()) {
                        EmptySectionCard(
                            icon = Icons.Default.History,
                            text = "ابھی تک کوئی خودکار ٹاسک ریکارڈ نہیں ہوا۔ نور کو کوئی موبائل کمانڈ دیں، جیسے 'واٹس ایپ کھولو' یا 'الارم لگاؤ'۔"
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(tasks, key = { it.id }) { task ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                                    border = BorderStroke(1.dp, ObsidianBorder)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = task.status,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (task.status == "SUCCESS") NoorEmerald else NoorGold
                                            )
                                            Text(
                                                text = timeFormatter.format(Date(task.timestamp)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextMuted
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = task.commandText,
                                            style = TextStyle(
                                                fontFamily = UrduFontFamily,
                                                fontSize = 15.sp,
                                                textDirection = TextDirection.Content
                                            ),
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Tools: ${task.toolsUsed}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NoorCyan
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = task.resultSummaryUrdu,
                                            style = TextStyle(
                                                fontFamily = UrduFontFamily,
                                                fontSize = 13.5.sp,
                                                textDirection = TextDirection.Content
                                            ),
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Captured (${notifications.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Button(
                            onClick = onReadNotificationsAloud,
                            colors = ButtonDefaults.buttonColors(containerColor = NoorCyan, contentColor = ObsidianVoid),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("read_notifications_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Read in Urdu")
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    if (notifications.isEmpty()) {
                        EmptySectionCard(
                            icon = Icons.Default.Notifications,
                            text = "ابھی کوئی نوٹیفیکیشن محفوظ نہیں ہے۔ سسٹم سیٹنگز میں Notification Access آن کرنے پر آنے والی تمام اطلاعات یہاں دکھیں گی۔"
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(notifications, key = { it.id }) { notif ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                                    border = BorderStroke(1.dp, ObsidianBorder)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(notif.appName, style = MaterialTheme.typography.labelLarge, color = NoorGold)
                                            Text(
                                                text = timeFormatter.format(Date(notif.timestamp)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextMuted
                                            )
                                        }
                                        if (notif.title.isNotBlank()) {
                                            Text(notif.title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                                        }
                                        if (notif.body.isNotBlank()) {
                                            Text(notif.body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AES-256-GCM Local Vault",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        OutlinedButton(
                            onClick = { showAddSecretDialog = true },
                            border = BorderStroke(1.dp, NoorGold),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NoorGold),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("add_secret_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add secret")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Secret")
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Encrypted with Android Keystore hardware keys. Never uploaded or shared with AI.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (vaultEntries.isEmpty()) {
                        EmptySectionCard(
                            icon = Icons.Default.Lock,
                            text = "کوئی ذاتی خفیہ معلومات محفوظ نہیں ہیں۔ آپ یہاں پن، پاس ورڈ یا پرائیویٹ نوٹس مکمل انکرپشن کے ساتھ محفوظ رکھ سکتے ہیں۔"
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(vaultEntries, key = { it.label }) { entry ->
                                val revealedValue = revealedMap.value[entry.label]
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                                    border = BorderStroke(1.dp, ObsidianBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(entry.label, style = MaterialTheme.typography.titleMedium, color = NoorGold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = revealedValue ?: entry.maskedPreview,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (revealedValue != null) NoorEmerald else TextSecondary
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                revealedMap.value = if (revealedValue != null) {
                                                    revealedMap.value - entry.label
                                                } else {
                                                    val dec = onRevealVaultSecret(entry.label) ?: "Decryption error"
                                                    revealedMap.value + (entry.label to dec)
                                                }
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (revealedValue != null) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle secret visibility",
                                                tint = NoorCyan
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                revealedMap.value = revealedMap.value - entry.label
                                                onDeleteVaultSecret(entry.label)
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete secret", tint = NoorRose)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSecretDialog) {
        var label by remember { mutableStateOf("") }
        var secret by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddSecretDialog = false },
            containerColor = ObsidianSurfaceElevated,
            title = { Text("Store Encrypted Secret", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Label (e.g., Home Wi-Fi, Bank PIN)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = secret,
                        onValueChange = { secret = it },
                        label = { Text("Secret Value") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (onSaveVaultSecret(label, secret)) {
                            showAddSecretDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NoorGold, contentColor = ObsidianVoid)
                ) {
                    Text("Encrypt & Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSecretDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun EmptySectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Surface(
        color = ObsidianSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = NoorCyan, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = UrduFontFamily,
                    fontSize = 14.sp,
                    textDirection = TextDirection.Content
                ),
                color = TextSecondary
            )
        }
    }
}
