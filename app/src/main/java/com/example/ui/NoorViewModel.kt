package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AgentTurnOutcome
import com.example.ai.NoorAgentBrain
import com.example.ai.NoorEmotion
import com.example.ai.PendingToolConfirmation
import com.example.data.local.CapturedNotificationEntity
import com.example.data.local.ConversationMessageEntity
import com.example.data.local.MemoryFactEntity
import com.example.data.local.NoorDatabase
import com.example.data.local.TaskExecutionEntity
import com.example.data.preferences.AssistantPersonality
import com.example.data.preferences.AssistantPreferencesRepository
import com.example.data.preferences.AssistantSettings
import com.example.data.preferences.LanguageMode
import com.example.data.repository.NoorDataRepository
import com.example.data.security.SecureCredentialStore
import com.example.data.security.VaultSecretEntry
import com.example.device.DeviceActionExecutor
import com.example.device.PermissionDashboardState
import com.example.device.PermissionStatusChecker
import com.example.service.NoorForegroundService
import com.example.service.VoiceAssistantManager
import com.example.work.AssistantWorkScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BrainUiState {
    data object Idle : BrainUiState
    data class Thinking(val stepLabel: String) : BrainUiState
    data class Error(val message: String) : BrainUiState
}

sealed interface VisionUiState {
    data object Idle : VisionUiState
    data object Analyzing : VisionUiState
    data class Result(val emotion: NoorEmotion, val answer: String) : VisionUiState
    data class Error(val message: String) : VisionUiState
}

class NoorViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = NoorDatabase.getInstance(context)
    private val repository = NoorDataRepository(database)
    private val preferences = AssistantPreferencesRepository(context)
    private val credentialStore = SecureCredentialStore(context)
    private val executor = DeviceActionExecutor(context, repository)
    private val brain = NoorAgentBrain(repository, executor)
    val voiceManager = VoiceAssistantManager(context)

    val messages: StateFlow<List<ConversationMessageEntity>> = repository.messages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<MemoryFactEntity>> = repository.memories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val taskHistory: StateFlow<List<TaskExecutionEntity>> = repository.taskHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<CapturedNotificationEntity>> = repository.notifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AssistantSettings> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssistantSettings())

    private val _permissions = MutableStateFlow(PermissionStatusChecker.inspect(context))
    val permissions: StateFlow<PermissionDashboardState> = _permissions.asStateFlow()

    private val _brainState = MutableStateFlow<BrainUiState>(BrainUiState.Idle)
    val brainState: StateFlow<BrainUiState> = _brainState.asStateFlow()

    private val _currentEmotion = MutableStateFlow(NoorEmotion.CALM)
    val currentEmotion: StateFlow<NoorEmotion> = _currentEmotion.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingToolConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingToolConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _visionState = MutableStateFlow<VisionUiState>(VisionUiState.Idle)
    val visionState: StateFlow<VisionUiState> = _visionState.asStateFlow()

    private val _vaultEntries = MutableStateFlow(credentialStore.listEntries())
    val vaultEntries: StateFlow<List<VaultSecretEntry>> = _vaultEntries.asStateFlow()

    private val _navigateToVisionEvents = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val navigateToVisionEvents: SharedFlow<String> = _navigateToVisionEvents.asSharedFlow()

    private var activeTurnJob: Job? = null

    init {
        AssistantWorkScheduler.schedulePeriodicMaintenance(context)
        viewModelScope.launch {
            settings.collect { current ->
                voiceManager.updateVoiceTuning(current.voiceSpeed, current.voicePitch)
            }
        }
        viewModelScope.launch {
            NoorForegroundService.wakeCommandEvents.collect { commandAfterWake ->
                if (commandAfterWake.isNotBlank()) {
                    sendUserCommand(commandAfterWake)
                } else if (_permissions.value.microphoneGranted) {
                    startVoiceListening()
                }
            }
        }
        viewModelScope.launch {
            seedWelcomeIfNeeded()
        }
    }

    private suspend fun seedWelcomeIfNeeded() {
        val recent = repository.getRecentMessages(1)
        if (recent.isEmpty()) {
            repository.addMessage(
                ConversationMessageEntity(
                    role = "assistant",
                    text = "السلام علیکم! میں نور ہوں، آپ کی ذاتی اے آئی اسسٹنٹ۔ آپ مجھ سے پاکستانی اردو یا انگلش میں بات کر سکتے ہیں — چاہے فون کنٹرول کروانا ہو، کیمرے سے کچھ دکھانا ہو، یا بس دل کی بات کرنی ہو۔ بتائیے، آج کیا مدد کروں؟",
                    emotionTag = NoorEmotion.HAPPY.id
                )
            )
        }
    }

    fun refreshPermissions() {
        _permissions.value = PermissionStatusChecker.inspect(context)
    }

    fun startVoiceListening() {
        refreshPermissions()
        if (!_permissions.value.microphoneGranted) return
        val langTag = when (settings.value.languageMode) {
            LanguageMode.ENGLISH -> "en-PK"
            else -> "ur-PK"
        }
        voiceManager.startListening(langTag) { transcript ->
            sendUserCommand(transcript)
        }
    }

    fun stopVoiceListening() {
        voiceManager.stopListening()
    }

    fun speakText(text: String) {
        voiceManager.speak(text)
    }

    fun stopSpeaking() {
        voiceManager.stopSpeaking()
    }

    fun sendUserCommand(rawText: String) {
        val text = rawText.trim()
        if (text.isBlank()) return
        activeTurnJob?.cancel()
        activeTurnJob = viewModelScope.launch {
            repository.addMessage(
                ConversationMessageEntity(
                    role = "user",
                    text = text,
                    emotionTag = NoorEmotion.CALM.id
                )
            )
            _brainState.value = BrainUiState.Thinking("آپ کی بات سمجھ رہی ہوں...")
            refreshPermissions()

            val currentSettings = settings.value
            val recent = repository.getRecentMessages(16)
            val mems = repository.getTopMemories(35)

            val outcome = brain.runAgentTurn(
                userText = text,
                settings = currentSettings,
                permissions = _permissions.value,
                recentMessages = recent,
                memories = mems,
                onPlanProgress = { step ->
                    _brainState.value = BrainUiState.Thinking(step)
                }
            )
            handleAgentOutcome(outcome, currentSettings)
        }
    }

    fun resolveConfirmation(approved: Boolean) {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        activeTurnJob?.cancel()
        activeTurnJob = viewModelScope.launch {
            _brainState.value = BrainUiState.Thinking(
                if (approved) "عمل مکمل کر رہی ہوں..." else "عمل منسوخ کر رہی ہوں..."
            )
            val currentSettings = settings.value
            val outcome = brain.continueAfterConfirmation(
                pending = pending,
                approved = approved,
                confirmSensitive = currentSettings.confirmSensitiveActions,
                onPlanProgress = { step ->
                    _brainState.value = BrainUiState.Thinking(step)
                }
            )
            handleAgentOutcome(outcome, currentSettings)
        }
    }

    private suspend fun handleAgentOutcome(
        outcome: AgentTurnOutcome,
        currentSettings: AssistantSettings
    ) {
        when (outcome) {
            is AgentTurnOutcome.Completed -> {
                _currentEmotion.value = outcome.emotion
                _brainState.value = BrainUiState.Idle
                repository.addMessage(
                    ConversationMessageEntity(
                        role = "assistant",
                        text = outcome.replyText,
                        emotionTag = outcome.emotion.id,
                        planStepsSummary = outcome.planSteps.joinToString(" • "),
                        toolsUsedSummary = outcome.toolsUsed.joinToString(", ")
                    )
                )
                if (currentSettings.autoSpeakResponses) {
                    voiceManager.speak(outcome.replyText)
                }
                if (outcome.openVisionRequested) {
                    _navigateToVisionEvents.tryEmit(outcome.visionQuestion)
                }
            }
            is AgentTurnOutcome.NeedsConfirmation -> {
                _currentEmotion.value = NoorEmotion.CONCERNED
                _pendingConfirmation.value = outcome.confirmation
                _brainState.value = BrainUiState.Idle
                if (currentSettings.autoSpeakResponses) {
                    voiceManager.speak(outcome.confirmation.descriptionUrdu)
                }
            }
            is AgentTurnOutcome.Error -> {
                _currentEmotion.value = NoorEmotion.CONCERNED
                _brainState.value = BrainUiState.Error(outcome.message)
            }
        }
    }

    fun analyzeVisionBitmap(bitmap: Bitmap, question: String) {
        viewModelScope.launch {
            _visionState.value = VisionUiState.Analyzing
            val result = brain.analyzeCameraImage(bitmap, question, settings.value)
            result.fold(
                onSuccess = { (emotion, answer) ->
                    _currentEmotion.value = emotion
                    _visionState.value = VisionUiState.Result(emotion, answer)
                    if (settings.value.autoSpeakResponses) {
                        voiceManager.speak(answer)
                    }
                    repository.addMessage(
                        ConversationMessageEntity(
                            role = "assistant",
                            text = "کیمرہ تجزیہ: $answer",
                            emotionTag = emotion.id,
                            toolsUsedSummary = "camerax_vision"
                        )
                    )
                },
                onFailure = { err ->
                    _visionState.value = VisionUiState.Error(
                        err.localizedMessage ?: "کیمرہ تصویر کا تجزیہ ناکام رہا۔"
                    )
                }
            )
        }
    }

    fun clearVisionResult() {
        _visionState.value = VisionUiState.Idle
    }

    fun emergencyStopAll() {
        activeTurnJob?.cancel()
        activeTurnJob = null
        _pendingConfirmation.value = null
        _brainState.value = BrainUiState.Idle
        _currentEmotion.value = NoorEmotion.CALM
        voiceManager.emergencyStop()
        NoorForegroundService.stop(context)
        viewModelScope.launch {
            preferences.setHandsFreeEnabled(false)
        }
    }

    fun toggleHandsFreeService(enabled: Boolean) {
        refreshPermissions()
        viewModelScope.launch {
            if (enabled && _permissions.value.microphoneGranted) {
                preferences.setHandsFreeEnabled(true)
                NoorForegroundService.start(context, settings.value.wakeWord)
            } else {
                preferences.setHandsFreeEnabled(false)
                NoorForegroundService.stop(context)
            }
        }
    }

    // Memory & Settings controls
    fun addManualMemory(category: String, content: String, importance: Int = 3) {
        viewModelScope.launch {
            repository.saveMemory(category, content, importance, source = "user_manual")
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch { repository.deleteMemory(id) }
    }

    fun clearAllMemories() {
        viewModelScope.launch { repository.clearAllMemories() }
    }

    fun clearConversation() {
        viewModelScope.launch {
            repository.clearConversation()
            seedWelcomeIfNeeded()
        }
    }

    fun clearTaskHistory() {
        viewModelScope.launch { repository.clearTaskHistory() }
    }

    fun updateUserName(name: String) {
        viewModelScope.launch { preferences.setUserName(name) }
    }

    fun updateWakeWord(word: String) {
        viewModelScope.launch {
            preferences.setWakeWord(word)
            if (NoorForegroundService.isRunning.value) {
                NoorForegroundService.start(context, word)
            }
        }
    }

    fun updatePersonality(personality: AssistantPersonality) {
        viewModelScope.launch { preferences.setPersonality(personality) }
    }

    fun updateLanguageMode(mode: LanguageMode) {
        viewModelScope.launch { preferences.setLanguageMode(mode) }
    }

    fun updateAutoSpeak(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoSpeak(enabled) }
    }

    fun updateConfirmSensitive(enabled: Boolean) {
        viewModelScope.launch { preferences.setConfirmSensitiveActions(enabled) }
    }

    fun updateVoiceSpeed(speed: Float) {
        viewModelScope.launch { preferences.setVoiceSpeed(speed) }
    }

    fun updateVoicePitch(pitch: Float) {
        viewModelScope.launch { preferences.setVoicePitch(pitch) }
    }

    // Encrypted Local Vault controls
    fun saveVaultSecret(label: String, value: String): Boolean {
        val ok = credentialStore.saveSecret(label, value)
        _vaultEntries.value = credentialStore.listEntries()
        return ok
    }

    fun revealVaultSecret(label: String): String? = credentialStore.readDecryptedSecret(label)

    fun deleteVaultSecret(label: String) {
        credentialStore.deleteSecret(label)
        _vaultEntries.value = credentialStore.listEntries()
    }

    fun readNotificationsAloud() {
        viewModelScope.launch {
            val latest = repository.getLatestNotifications(5)
            repository.markAllNotificationsRead()
            if (latest.isEmpty()) {
                voiceManager.speak("ابھی کوئی نئی نوٹیفیکیشن موجود نہیں ہے۔")
            } else {
                val summary = latest.joinToString("۔ ") {
                    "${it.appName} سے ${it.title}: ${it.body}"
                }
                voiceManager.speak(summary)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.destroy()
    }
}
