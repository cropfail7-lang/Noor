package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ai.configureAppCheck
import com.example.device.PermissionStatusChecker
import com.example.service.AudioRecordingService
import com.example.service.NoorForegroundService
import com.example.ui.NoorViewModel
import com.example.ui.components.MicPermissionDialog
import com.example.ui.screens.CameraVisionScreen
import com.example.ui.screens.ControlHistoryScreen
import com.example.ui.screens.HomeChatScreen
import com.example.ui.screens.MemoryPersonalityScreen
import com.example.ui.screens.PermissionsSafetyScreen
import com.example.ui.theme.NoorAiTheme
import com.example.ui.theme.NoorCyan
import com.example.ui.theme.NoorCyanContainer
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextSecondary

private enum class NoorNavRoute(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HUB("hub", "Noor", Icons.Default.AutoAwesome),
    VISION("vision", "Vision", Icons.Default.CameraAlt),
    MEMORY("memory", "Memory", Icons.Default.Psychology),
    CONTROL("control", "Activity", Icons.Default.History),
    PERMISSIONS("permissions", "Safety", Icons.Default.Security)
}

class MainActivity : ComponentActivity() {

    private var pendingWakeCommandState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before any UI or Firebase calls.
        configureAppCheck(this, intent)

        handleWakeIntent(intent)
        enableEdgeToEdge()

        setContent {
            NoorAiTheme {
                val viewModel: NoorViewModel = viewModel()
                val wakeCmd by pendingWakeCommandState
                LaunchedEffect(wakeCmd) {
                    val cmd = wakeCmd
                    if (cmd != null) {
                        if (cmd.isNotBlank()) {
                            viewModel.sendUserCommand(cmd)
                        } else {
                            viewModel.startVoiceListening()
                        }
                        pendingWakeCommandState.value = null
                    }
                }
                NoorAppRoot(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWakeIntent(intent)
    }

    private fun handleWakeIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(NoorForegroundService.EXTRA_WAKE_TRIGGERED, false) == true) {
            pendingWakeCommandState.value =
                intent.getStringExtra(NoorForegroundService.EXTRA_WAKE_COMMAND).orEmpty()
        }
    }
}

@Composable
private fun NoorAppRoot(viewModel: NoorViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val tasks by viewModel.taskHistory.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val brainState by viewModel.brainState.collectAsStateWithLifecycle()
    val currentEmotion by viewModel.currentEmotion.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val visionState by viewModel.visionState.collectAsStateWithLifecycle()
    val vaultEntries by viewModel.vaultEntries.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = context as? Activity

    val isListening by viewModel.voiceManager.isListening.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.voiceManager.isSpeaking.collectAsStateWithLifecycle()
    val sttRmsLevel by viewModel.voiceManager.rmsLevel.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.voiceManager.partialTranscript.collectAsStateWithLifecycle()
    val isWakeWordRunning by NoorForegroundService.isRunning.collectAsStateWithLifecycle()

    val isAudioRecording by AudioRecordingService.isRecording.collectAsStateWithLifecycle()
    val isVoiceActivationRunning by AudioRecordingService.isVoiceActivationMonitoring.collectAsStateWithLifecycle()
    val audioRmsLevel by AudioRecordingService.recordingRmsLevel.collectAsStateWithLifecycle()
    val audioDurationSec by AudioRecordingService.recordingDurationSec.collectAsStateWithLifecycle()
    val audioServiceError by AudioRecordingService.serviceError.collectAsStateWithLifecycle()

    val effectiveRms = maxOf(sttRmsLevel, audioRmsLevel)

    var pendingVisionQuestion by remember { mutableStateOf("") }
    var showMicPermissionDialog by remember { mutableStateOf(false) }
    var micPermissionPurpose by remember { mutableStateOf("آواز ریکارڈ کرنے اور وائس ایکٹیویشن") }
    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var hasAskedMicOnce by rememberSaveable { mutableStateOf(false) }

    val micAndNotifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasAskedMicOnce = true
        viewModel.refreshPermissions()
        val micGranted = results[Manifest.permission.RECORD_AUDIO] == true ||
            viewModel.permissions.value.microphoneGranted
        if (micGranted) {
            showMicPermissionDialog = false
            pendingMicAction?.invoke()
            pendingMicAction = null
        } else {
            showMicPermissionDialog = true
        }
    }

    val requestMicFlow: (String, () -> Unit) -> Unit = { purpose, onGranted ->
        viewModel.refreshPermissions()
        if (viewModel.permissions.value.microphoneGranted) {
            onGranted()
        } else {
            micPermissionPurpose = purpose
            pendingMicAction = onGranted
            showMicPermissionDialog = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToVisionEvents.collect { question ->
            pendingVisionQuestion = question
            navController.navigate(NoorNavRoute.VISION.route) {
                launchSingleTop = true
            }
        }
    }

    val navigateToRoute: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            containerColor = ObsidianVoid,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = ObsidianDeep) {
                        NoorNavRoute.entries.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToRoute(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NoorCyan,
                                    selectedTextColor = NoorCyan,
                                    indicatorColor = NoorCyanContainer,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(containerColor = ObsidianDeep) {
                        NoorNavRoute.entries.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToRoute(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = NoorCyan,
                                    selectedTextColor = NoorCyan,
                                    indicatorColor = NoorCyanContainer,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = NoorNavRoute.HUB.route,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(NoorNavRoute.HUB.route) {
                        HomeChatScreen(
                            messages = messages,
                            settings = settings,
                            brainState = brainState,
                            currentEmotion = currentEmotion,
                            pendingConfirmation = pendingConfirmation,
                            isListening = isListening,
                            isSpeaking = isSpeaking,
                            isWakeWordRunning = isWakeWordRunning,
                            isAudioRecording = isAudioRecording,
                            isVoiceActivationRunning = isVoiceActivationRunning,
                            audioRecordingDurationSec = audioDurationSec,
                            audioServiceError = audioServiceError,
                            rmsLevel = effectiveRms,
                            partialTranscript = partialTranscript,
                            micPermissionGranted = permissions.microphoneGranted,
                            onRequestMicPermission = {
                                requestMicFlow("آواز سننے") {
                                    viewModel.startVoiceListening()
                                }
                            },
                            onToggleListening = {
                                if (isListening) viewModel.stopVoiceListening()
                                else viewModel.startVoiceListening()
                            },
                            onStartAudioRecording = {
                                requestMicFlow("آواز ریکارڈ کر کے پراسیس کرنے") {
                                    viewModel.startAudioRecording()
                                }
                            },
                            onStopAndProcessAudio = viewModel::stopAndProcessAudioRecording,
                            onCancelAudioRecording = viewModel::cancelAudioRecording,
                            onSendCommand = viewModel::sendUserCommand,
                            onSpeakMessage = viewModel::speakText,
                            onConfirmAction = viewModel::resolveConfirmation,
                            onEmergencyStop = viewModel::emergencyStopAll,
                            onOpenVision = { navigateToRoute(NoorNavRoute.VISION.route) }
                        )
                    }

                    composable(NoorNavRoute.VISION.route) {
                        CameraVisionScreen(
                            cameraPermissionGranted = permissions.cameraGranted,
                            initialQuestion = pendingVisionQuestion,
                            visionState = visionState,
                            onPermissionResult = viewModel::refreshPermissions,
                            onAnalyzeBitmap = viewModel::analyzeVisionBitmap,
                            onSpeakText = viewModel::speakText,
                            onClearResult = viewModel::clearVisionResult
                        )
                    }

                    composable(NoorNavRoute.MEMORY.route) {
                        MemoryPersonalityScreen(
                            settings = settings,
                            memories = memories,
                            micPermissionGranted = permissions.microphoneGranted,
                            isWakeWordRunning = isWakeWordRunning,
                            isVoiceActivationRunning = isVoiceActivationRunning,
                            onUpdateUserName = viewModel::updateUserName,
                            onUpdateWakeWord = viewModel::updateWakeWord,
                            onUpdatePersonality = viewModel::updatePersonality,
                            onUpdateLanguageMode = viewModel::updateLanguageMode,
                            onUpdateAutoSpeak = viewModel::updateAutoSpeak,
                            onUpdateConfirmSensitive = viewModel::updateConfirmSensitive,
                            onUpdateVoiceSpeed = viewModel::updateVoiceSpeed,
                            onUpdateVoicePitch = viewModel::updateVoicePitch,
                            onToggleHandsFree = { enable ->
                                if (enable) {
                                    requestMicFlow("ہینڈز فری ویک ورڈ چلانے") {
                                        viewModel.toggleHandsFreeService(true)
                                    }
                                } else {
                                    viewModel.toggleHandsFreeService(false)
                                }
                            },
                            onToggleVoiceActivation = { enable ->
                                if (enable) {
                                    requestMicFlow("وائس ایکٹیویشن سروس آن کرنے") {
                                        viewModel.toggleVoiceActivationMonitor(true)
                                    }
                                } else {
                                    viewModel.toggleVoiceActivationMonitor(false)
                                }
                            },
                            onRequestMicPermission = {
                                requestMicFlow("وائس ایکٹیویشن آن کرنے") {
                                    viewModel.toggleVoiceActivationMonitor(true)
                                }
                            },
                            onAddMemory = viewModel::addManualMemory,
                            onDeleteMemory = viewModel::deleteMemory,
                            onClearAllMemories = viewModel::clearAllMemories,
                            onClearConversation = viewModel::clearConversation
                        )
                    }

                    composable(NoorNavRoute.CONTROL.route) {
                        ControlHistoryScreen(
                            tasks = tasks,
                            notifications = notifications,
                            vaultEntries = vaultEntries,
                            onClearTasks = viewModel::clearTaskHistory,
                            onReadNotificationsAloud = viewModel::readNotificationsAloud,
                            onSaveVaultSecret = viewModel::saveVaultSecret,
                            onRevealVaultSecret = viewModel::revealVaultSecret,
                            onDeleteVaultSecret = viewModel::deleteVaultSecret
                        )
                    }

                    composable(NoorNavRoute.PERMISSIONS.route) {
                        PermissionsSafetyScreen(
                            permissions = permissions,
                            onRefreshPermissions = viewModel::refreshPermissions,
                            onEmergencyStop = viewModel::emergencyStopAll
                        )
                    }
                }
            }
        }

        if (showMicPermissionDialog) {
            val isPermanentlyDenied = hasAskedMicOnce &&
                !permissions.microphoneGranted &&
                activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.RECORD_AUDIO
                )

            MicPermissionDialog(
                isPermanentlyDenied = isPermanentlyDenied,
                purposeLabel = micPermissionPurpose,
                onRequestPermission = {
                    val permsToAsk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    } else {
                        arrayOf(Manifest.permission.RECORD_AUDIO)
                    }
                    micAndNotifLauncher.launch(permsToAsk)
                },
                onOpenSettings = {
                    showMicPermissionDialog = false
                    PermissionStatusChecker.openAppDetailsSettings(context)
                },
                onDismiss = {
                    showMicPermissionDialog = false
                    pendingMicAction = null
                }
            )
        }
    }
}
