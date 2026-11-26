package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.NoorEmotion
import com.example.ai.PendingToolConfirmation
import com.example.data.local.ConversationMessageEntity
import com.example.data.preferences.AssistantSettings
import com.example.ui.BrainUiState
import com.example.ui.components.NoorOrbVisualizer
import com.example.ui.components.VoiceWaveformStrip
import com.example.ui.theme.NoorCyan
import com.example.ui.theme.NoorCyanContainer
import com.example.ui.theme.NoorEmerald
import com.example.ui.theme.NoorGold
import com.example.ui.theme.NoorGoldContainer
import com.example.ui.theme.NoorIndigo
import com.example.ui.theme.NoorRose
import com.example.ui.theme.NoorRoseContainer
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UrduFontFamily

@Composable
fun HomeChatScreen(
    messages: List<ConversationMessageEntity>,
    settings: AssistantSettings,
    brainState: BrainUiState,
    currentEmotion: NoorEmotion,
    pendingConfirmation: PendingToolConfirmation?,
    isListening: Boolean,
    isSpeaking: Boolean,
    isWakeWordRunning: Boolean,
    rmsLevel: Float,
    partialTranscript: String,
    micPermissionGranted: Boolean,
    onRequestMicPermission: () -> Unit,
    onToggleListening: () -> Unit,
    onSendCommand: (String) -> Unit,
    onSpeakMessage: (String) -> Unit,
    onConfirmAction: (Boolean) -> Unit,
    onEmergencyStop: () -> Unit,
    onOpenVision: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val isThinking = brainState is BrainUiState.Thinking

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(ObsidianVoid, ObsidianDeep, ObsidianVoid)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .align(Alignment.Center)
                .padding(horizontal = 16.dp)
        ) {
            // Top Futuristic Bar with Mood & Emergency Stop
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "NOOR AI",
                            style = MaterialTheme.typography.titleLarge,
                            color = NoorCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = ObsidianSurfaceElevated,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, ObsidianBorder)
                        ) {
                            Text(
                                text = "${currentEmotion.labelUrdu} • ${currentEmotion.labelEn}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NoorGold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isWakeWordRunning) Icons.Default.Hearing else Icons.Default.AutoAwesome,
                            contentDescription = "Wake status",
                            tint = if (isWakeWordRunning) NoorEmerald else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isWakeWordRunning) {
                                "Hands-free: \"${settings.wakeWord}\""
                            } else {
                                settings.personality.titleUrdu
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isWakeWordRunning) NoorEmerald else TextSecondary
                        )
                    }
                }

                // Emergency Stop Pill
                Button(
                    onClick = onEmergencyStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NoorRoseContainer,
                        contentColor = NoorRose
                    ),
                    border = BorderStroke(1.dp, NoorRose.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("emergency_stop_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Emergency Stop",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "STOP",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Orb + Voice Waveform + Live Thinking Status
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NoorOrbVisualizer(
                    emotion = currentEmotion,
                    isListening = isListening,
                    isSpeaking = isSpeaking,
                    isThinking = isThinking,
                    rmsLevel = rmsLevel,
                    size = 136.dp
                )

                VoiceWaveformStrip(
                    isActive = isListening || isSpeaking || isThinking,
                    rmsLevel = if (isSpeaking) 0.65f else rmsLevel,
                    accentColor = when {
                        isListening -> NoorEmerald
                        isSpeaking -> NoorGold
                        isThinking -> NoorIndigo
                        else -> NoorCyan
                    }
                )

                val statusLabel = when {
                    isListening && partialTranscript.isNotBlank() -> partialTranscript
                    isListening -> "سن رہی ہوں... اردو یا انگلش میں بولیں"
                    isThinking -> (brainState as BrainUiState.Thinking).stepLabel
                    isSpeaking -> "نور بول رہی ہے..."
                    else -> "حاضر ہوں — حکم دیجیے"
                }
                Text(
                    text = statusLabel,
                    style = TextStyle(
                        fontFamily = UrduFontFamily,
                        fontSize = 14.sp,
                        textDirection = TextDirection.Content
                    ),
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                )
            }

            // Transparent Error Banner
            AnimatedVisibility(visible = brainState is BrainUiState.Error) {
                val errMsg = (brainState as? BrainUiState.Error)?.message.orEmpty()
                Card(
                    colors = CardDefaults.cardColors(containerColor = NoorRoseContainer),
                    border = BorderStroke(1.dp, NoorRose.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = NoorRose
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Sensitive Action Security Confirmation Banner
            AnimatedVisibility(visible = pendingConfirmation != null) {
                pendingConfirmation?.let { pending ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NoorGoldContainer),
                        border = BorderStroke(1.dp, NoorGold),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "حفاظتی تصدیق درکار ہے • Security Confirmation",
                                style = MaterialTheme.typography.labelLarge,
                                color = NoorGold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = pending.descriptionUrdu,
                                style = TextStyle(
                                    fontFamily = UrduFontFamily,
                                    fontSize = 15.sp,
                                    textDirection = TextDirection.Content
                                ),
                                color = TextPrimary
                            )
                            Text(
                                text = pending.descriptionEn,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { onConfirmAction(true) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NoorEmerald,
                                        contentColor = ObsidianVoid
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("confirm_action_button")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Confirm")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("تصدیق کریں")
                                }
                                OutlinedButton(
                                    onClick = { onConfirmAction(false) },
                                    border = BorderStroke(1.dp, NoorRose),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NoorRose),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("cancel_action_button")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("منسوخ کریں")
                                }
                            }
                        }
                    }
                }
            }

            // Conversation Feed
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatMessageBubble(
                        message = msg,
                        onSpeakClick = { onSpeakMessage(msg.text) }
                    )
                }
            }

            // Quick Urdu/Bilingual Action Chips
            val quickPrompts = remember {
                listOf(
                    "واٹس ایپ کھولو" to "Open WhatsApp",
                    "نوٹیفیکیشن پڑھو" to "Read notifications",
                    "یوٹیوب کھولو" to "Open YouTube",
                    "فلیش لائٹ آن کرو" to "Flashlight ON",
                    "والیوم 70% کرو" to "Volume 70%",
                    "صبح 6 بجے الارم لگاؤ" to "Alarm 6 AM"
                )
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                items(quickPrompts) { (urduPrompt, _) ->
                    AssistChip(
                        onClick = { onSendCommand(urduPrompt) },
                        label = {
                            Text(
                                text = urduPrompt,
                                style = TextStyle(fontFamily = UrduFontFamily, fontSize = 13.sp),
                                color = NoorCyan
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = ObsidianSurface
                        ),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.testTag("quick_chip_${urduPrompt.hashCode()}")
                    )
                }
            }

            // Bottom Command Composer
            Surface(
                color = ObsidianSurface,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp, top = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onOpenVision,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("quick_vision_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Open Camera Vision",
                            tint = NoorGold
                        )
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = "اردو یا انگلش میں لکھیں...",
                                style = TextStyle(fontFamily = UrduFontFamily, fontSize = 14.sp),
                                color = TextMuted
                            )
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = UrduFontFamily,
                            fontSize = 15.sp,
                            color = TextPrimary,
                            textDirection = TextDirection.Content
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = NoorCyan
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank()) {
                                    onSendCommand(inputText)
                                    inputText = ""
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field")
                    )

                    FilledIconButton(
                        onClick = {
                            if (micPermissionGranted) {
                                onToggleListening()
                            } else {
                                onRequestMicPermission()
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isListening) NoorEmerald else ObsidianSurfaceElevated,
                            contentColor = if (isListening) ObsidianVoid else NoorCyan
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("mic_toggle_button")
                    ) {
                        Icon(
                            imageVector = when {
                                isListening -> Icons.Default.GraphicEq
                                micPermissionGranted -> Icons.Default.Mic
                                else -> Icons.Default.MicOff
                            },
                            contentDescription = if (isListening) "Stop listening" else "Voice command"
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendCommand(inputText)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank() && !isThinking,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = NoorCyan,
                            contentColor = ObsidianVoid
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send command"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ConversationMessageEntity,
    onSpeakClick: () -> Unit
) {
    val isUser = message.role == "user"
    val containsUrdu = message.text.any { it in '\u0600'..'\u06FF' }
    val emotion = NoorEmotion.fromId(message.emotionTag)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) NoorCyanContainer else ObsidianSurface,
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 20.dp
            ),
            border = BorderStroke(
                1.dp,
                if (isUser) NoorCyan.copy(alpha = 0.45f) else ObsidianBorder
            ),
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!isUser) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "نور • ${emotion.labelUrdu}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NoorGold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Speak message",
                            tint = NoorCyan,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onSpeakClick)
                                .padding(2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                CompositionLocalProvider(
                    LocalLayoutDirection provides if (containsUrdu) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    Text(
                        text = message.text,
                        style = TextStyle(
                            fontFamily = if (containsUrdu) UrduFontFamily else MaterialTheme.typography.bodyLarge.fontFamily,
                            fontSize = if (containsUrdu) 16.sp else 15.sp,
                            lineHeight = if (containsUrdu) 28.sp else 23.sp,
                            fontWeight = FontWeight.Normal,
                            textDirection = TextDirection.Content
                        ),
                        color = TextPrimary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (!isUser && message.toolsUsedSummary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = ObsidianSurfaceElevated,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "Executed tools",
                                tint = NoorEmerald,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = message.toolsUsedSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = NoorEmerald
                            )
                        }
                    }
                }
            }
        }
    }
}
