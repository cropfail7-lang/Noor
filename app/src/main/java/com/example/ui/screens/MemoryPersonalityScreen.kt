package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.MemoryFactEntity
import com.example.data.preferences.AssistantPersonality
import com.example.data.preferences.AssistantSettings
import com.example.data.preferences.LanguageMode
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

@Composable
fun MemoryPersonalityScreen(
    settings: AssistantSettings,
    memories: List<MemoryFactEntity>,
    micPermissionGranted: Boolean,
    isWakeWordRunning: Boolean,
    isVoiceActivationRunning: Boolean,
    onUpdateUserName: (String) -> Unit,
    onUpdateWakeWord: (String) -> Unit,
    onUpdatePersonality: (AssistantPersonality) -> Unit,
    onUpdateLanguageMode: (LanguageMode) -> Unit,
    onUpdateAutoSpeak: (Boolean) -> Unit,
    onUpdateConfirmSensitive: (Boolean) -> Unit,
    onUpdateVoiceSpeed: (Float) -> Unit,
    onUpdateVoicePitch: (Float) -> Unit,
    onToggleHandsFree: (Boolean) -> Unit,
    onToggleVoiceActivation: (Boolean) -> Unit,
    onRequestMicPermission: () -> Unit,
    onAddMemory: (String, String, Int) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    onClearAllMemories: () -> Unit,
    onClearConversation: () -> Unit,
    modifier: Modifier = Modifier
) {
    var wakeWordDraft by rememberSaveable(settings.wakeWord) { mutableStateOf(settings.wakeWord) }
    var userNameDraft by rememberSaveable(settings.userName) { mutableStateOf(settings.userName) }
    var selectedCategory by rememberSaveable { mutableStateOf("all") }
    var showAddMemoryDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val filteredMemories = remember(memories, selectedCategory) {
        if (selectedCategory == "all") memories else memories.filter { it.category == selectedCategory }
    }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "PERSONALITY & MEMORY",
                    style = MaterialTheme.typography.titleLarge,
                    color = NoorCyan
                )
                Text(
                    text = "نور کی شخصیت، آواز، ویک ورڈ اور ذاتی یادداشت کا انتظام",
                    style = TextStyle(fontFamily = UrduFontFamily, fontSize = 13.sp),
                    color = TextSecondary
                )
            }

            // Identity & Wake Word Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = NoorGold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Voice & Wake Word", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = userNameDraft,
                                onValueChange = { userNameDraft = it },
                                label = { Text("Your Name (آپ کا نام)") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NoorCyan,
                                    unfocusedBorderColor = ObsidianBorder
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { onUpdateUserName(userNameDraft) },
                                colors = ButtonDefaults.buttonColors(containerColor = NoorCyan, contentColor = ObsidianVoid),
                                modifier = Modifier.height(52.dp)
                            ) {
                                Text("Save")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = wakeWordDraft,
                                onValueChange = { wakeWordDraft = it },
                                label = { Text("Wake Word (e.g., Jarvis, Noor)") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NoorCyan,
                                    unfocusedBorderColor = ObsidianBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("wake_word_input")
                            )
                            Button(
                                onClick = { onUpdateWakeWord(wakeWordDraft) },
                                colors = ButtonDefaults.buttonColors(containerColor = NoorGold, contentColor = ObsidianVoid),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("save_wake_word_button")
                            ) {
                                Text("Set")
                            }
                        }

                        SettingToggleRow(
                            title = "Hands-Free Wake Word Service",
                            subtitle = if (isWakeWordRunning) "Listening in background for \"${settings.wakeWord}\"" else "Foreground mic listener (battery-friendly bursts)",
                            checked = isWakeWordRunning,
                            onCheckedChange = { wantEnable ->
                                if (wantEnable && !micPermissionGranted) {
                                    onRequestMicPermission()
                                } else {
                                    onToggleHandsFree(wantEnable)
                                }
                            },
                            testTag = "hands_free_switch"
                        )

                        SettingToggleRow(
                            title = "Audio Energy Voice Activation (VAD)",
                            subtitle = if (isVoiceActivationRunning) "AudioRecord service actively detecting speech" else "Triggers listening when direct speech is detected",
                            checked = isVoiceActivationRunning,
                            onCheckedChange = { wantEnable ->
                                if (wantEnable && !micPermissionGranted) {
                                    onRequestMicPermission()
                                } else {
                                    onToggleVoiceActivation(wantEnable)
                                }
                            },
                            testTag = "vad_activation_switch"
                        )

                        SettingToggleRow(
                            title = "Auto-Speak Pakistani Urdu Replies",
                            subtitle = "Automatically read Noor's responses aloud via TTS",
                            checked = settings.autoSpeakResponses,
                            onCheckedChange = onUpdateAutoSpeak,
                            testTag = "auto_speak_switch"
                        )

                        SettingToggleRow(
                            title = "Security Guardrail Confirmations",
                            subtitle = "Ask confirmation before calls, SMS/WhatsApp, or screen taps",
                            checked = settings.confirmSensitiveActions,
                            onCheckedChange = onUpdateConfirmSensitive,
                            testTag = "confirm_sensitive_switch"
                        )

                        Text(
                            text = "Voice Speed: ${"%.2f".format(settings.voiceSpeed)}x",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Slider(
                            value = settings.voiceSpeed,
                            onValueChange = onUpdateVoiceSpeed,
                            valueRange = 0.7f..1.4f,
                            colors = SliderDefaults.colors(thumbColor = NoorCyan, activeTrackColor = NoorCyan)
                        )

                        Text(
                            text = "Voice Pitch: ${"%.2f".format(settings.voicePitch)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Slider(
                            value = settings.voicePitch,
                            onValueChange = onUpdateVoicePitch,
                            valueRange = 0.75f..1.35f,
                            colors = SliderDefaults.colors(thumbColor = NoorGold, activeTrackColor = NoorGold)
                        )
                    }
                }
            }

            // Personality Selector
            item {
                Text("AI Personality", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(AssistantPersonality.entries) { persona ->
                        val selected = persona == settings.personality
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) NoorCyanContainer else ObsidianSurface
                            ),
                            border = BorderStroke(
                                1.5.dp,
                                if (selected) NoorCyan else ObsidianBorder
                            ),
                            modifier = Modifier
                                .width(220.dp)
                                .clickable { onUpdatePersonality(persona) }
                                .testTag("persona_${persona.id}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = persona.titleUrdu,
                                    style = TextStyle(fontFamily = UrduFontFamily, fontSize = 15.sp),
                                    color = if (selected) NoorCyan else NoorGold
                                )
                                Text(
                                    text = persona.titleEn,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = persona.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Language Mode Selector
            item {
                Text("Primary Language Mode", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(LanguageMode.entries) { mode ->
                        val selected = mode == settings.languageMode
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdateLanguageMode(mode) },
                            label = {
                                Text(
                                    text = mode.label,
                                    style = TextStyle(fontFamily = UrduFontFamily, fontSize = 13.sp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NoorCyanContainer,
                                selectedLabelColor = NoorCyan
                            )
                        )
                    }
                }
            }

            // Personal Memory Header + Controls
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = NoorCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Personal Memory (${memories.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { showAddMemoryDialog = true },
                            border = BorderStroke(1.dp, NoorCyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NoorCyan),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("add_memory_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add memory", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add")
                        }
                        if (memories.isNotEmpty()) {
                            IconButton(
                                onClick = { showClearConfirmDialog = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("clear_memories_button")
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all memories", tint = NoorRose)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                val categories = listOf("all", "preference", "instruction", "habit", "ongoing_task", "app_usage", "style")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.replace("_", " ")) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ObsidianSurfaceElevated,
                                selectedLabelColor = NoorGold
                            )
                        )
                    }
                }
            }

            if (filteredMemories.isEmpty()) {
                item {
                    Surface(
                        color = ObsidianSurface,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "ابھی اس کیٹیگری میں کوئی یادداشت محفوظ نہیں ہے۔ نور سے بات کریں یا 'Add' دبا کر اپنی پسند، عادات یا ہدایات محفوظ کریں۔",
                            style = TextStyle(
                                fontFamily = UrduFontFamily,
                                fontSize = 14.sp,
                                textDirection = TextDirection.Content
                            ),
                            color = TextSecondary,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                items(filteredMemories, key = { it.id }) { fact ->
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
                                Text(
                                    text = "${fact.category.uppercase()} • Priority ${fact.importance}/5",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NoorGold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = fact.content,
                                    style = TextStyle(
                                        fontFamily = UrduFontFamily,
                                        fontSize = 15.sp,
                                        textDirection = TextDirection.Content
                                    ),
                                    color = TextPrimary
                                )
                            }
                            IconButton(
                                onClick = { onDeleteMemory(fact.id) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete memory", tint = NoorRose)
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onClearConversation,
                    border = BorderStroke(1.dp, ObsidianBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Reset Chat Conversation History")
                }
            }
        }
    }

    if (showAddMemoryDialog) {
        var newContent by remember { mutableStateOf("") }
        var newCategory by remember { mutableStateOf("preference") }
        AlertDialog(
            onDismissRequest = { showAddMemoryDialog = false },
            containerColor = ObsidianSurfaceElevated,
            title = { Text("Add Personal Memory", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("preference", "instruction", "habit", "ongoing_task", "style")) { cat ->
                            FilterChip(
                                selected = newCategory == cat,
                                onClick = { newCategory = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = newContent,
                        onValueChange = { newContent = it },
                        label = { Text("Memory fact (Urdu or English)") },
                        placeholder = { Text("e.g., مجھے صبح 7 بجے چائے اور خبریں پسند ہیں") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newContent.isNotBlank()) {
                            onAddMemory(newCategory, newContent, 4)
                            showAddMemoryDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NoorCyan, contentColor = ObsidianVoid)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemoryDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            containerColor = ObsidianSurfaceElevated,
            title = { Text("Clear All Personal Memories?", color = NoorRose) },
            text = {
                Text(
                    "This will permanently delete all saved preferences, habits, and instructions from your local Room database.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllMemories()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NoorRose, contentColor = ObsidianVoid)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianVoid,
                checkedTrackColor = NoorEmerald
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
