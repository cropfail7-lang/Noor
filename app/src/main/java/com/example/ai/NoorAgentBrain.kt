package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.local.ConversationMessageEntity
import com.example.data.local.MemoryFactEntity
import com.example.data.local.TaskExecutionEntity
import com.example.data.preferences.AssistantSettings
import com.example.data.repository.NoorDataRepository
import com.example.device.DeviceActionExecutor
import com.example.device.DeviceActionResult
import com.example.device.PermissionDashboardState
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class NoorEmotion(val id: String, val labelUrdu: String, val labelEn: String) {
    CALM("calm", "پُرسکون", "Calm"),
    HAPPY("happy", "خوش", "Happy"),
    EXCITED("excited", "پُرجوش", "Excited"),
    EMPATHETIC("empathetic", "ہمدرد", "Empathetic"),
    CONCERNED("concerned", "فکرمند", "Concerned"),
    PLAYFUL("playful", "خوش مزاج", "Playful");

    companion object {
        fun fromId(id: String?): NoorEmotion =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: CALM
    }
}

data class PendingToolConfirmation(
    val toolName: String,
    val args: Map<String, String>,
    val descriptionUrdu: String,
    val descriptionEn: String,
    val chatSession: Chat,
    val remainingCalls: List<FunctionCallPart>,
    val accumulatedTools: List<String>,
    val originalCommand: String
)

sealed interface AgentTurnOutcome {
    data class Completed(
        val replyText: String,
        val emotion: NoorEmotion,
        val toolsUsed: List<String>,
        val planSteps: List<String>,
        val openVisionRequested: Boolean = false,
        val visionQuestion: String = ""
    ) : AgentTurnOutcome

    data class NeedsConfirmation(
        val confirmation: PendingToolConfirmation,
        val interimSteps: List<String>
    ) : AgentTurnOutcome

    data class Error(val message: String) : AgentTurnOutcome
}

class NoorAgentBrain(
    private val repository: NoorDataRepository,
    private val executor: DeviceActionExecutor
) {

    private val visionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME
        )
    }

    private val toolDeclarations: List<FunctionDeclaration> by lazy {
        listOf(
            FunctionDeclaration(
                "open_app",
                "Open an installed Android application such as WhatsApp, YouTube, Chrome, Settings, Camera, Clock, Maps, Calculator, Gmail, etc.",
                mapOf(
                    "app_name" to Schema.string("Name of the app to open (e.g., WhatsApp, YouTube, Settings)")
                )
            ),
            FunctionDeclaration(
                "control_device_setting",
                "Adjust volume or brightness, toggle flashlight, or open Wi-Fi, Bluetooth, or system settings panels.",
                mapOf(
                    "setting_type" to Schema.string("One of: wifi, bluetooth, volume, brightness, flashlight, settings"),
                    "action" to Schema.string("Action such as: on, off, up, down, set, open, mute"),
                    "level" to Schema.integer("Optional percentage 0-100 for volume or brightness")
                ),
                listOf("level")
            ),
            FunctionDeclaration(
                "search_contacts",
                "Search the user's phone contacts by name to find phone numbers.",
                mapOf(
                    "query" to Schema.string("Contact name or partial name to search")
                )
            ),
            FunctionDeclaration(
                "make_phone_call",
                "Initiate a phone call to a contact name or phone number.",
                mapOf(
                    "contact_or_number" to Schema.string("Contact display name or phone number")
                )
            ),
            FunctionDeclaration(
                "send_message",
                "Send an SMS or compose/send a WhatsApp message to a contact or phone number.",
                mapOf(
                    "channel" to Schema.string("Either 'whatsapp' or 'sms'"),
                    "recipient" to Schema.string("Contact name or phone number"),
                    "message" to Schema.string("The message text to send")
                )
            ),
            FunctionDeclaration(
                "read_notifications",
                "Read recent notifications captured by Noor Notification Listener.",
                mapOf(
                    "limit" to Schema.integer("Maximum number of recent notifications to return (1-15)")
                ),
                listOf("limit")
            ),
            FunctionDeclaration(
                "set_alarm_or_timer",
                "Set an alarm at a specific hour/minute or start a countdown timer on the Clock app.",
                mapOf(
                    "type" to Schema.string("Either 'alarm' or 'timer'"),
                    "hour" to Schema.integer("Hour 0-23 for alarm"),
                    "minute" to Schema.integer("Minute 0-59 for alarm"),
                    "duration_seconds" to Schema.integer("Duration in seconds for timer"),
                    "label" to Schema.string("Optional label for the alarm or timer")
                ),
                listOf("hour", "minute", "duration_seconds", "label")
            ),
            FunctionDeclaration(
                "schedule_reminder",
                "Schedule a personal reminder notification using WorkManager after a delay in minutes.",
                mapOf(
                    "title" to Schema.string("Reminder message in Urdu or English"),
                    "delay_minutes" to Schema.integer("Minutes from now to fire the reminder")
                )
            ),
            FunctionDeclaration(
                "web_search",
                "Search Google on the device browser for information, news, or answers.",
                mapOf(
                    "query" to Schema.string("Search query")
                )
            ),
            FunctionDeclaration(
                "search_files",
                "Search documents and files stored on the device via MediaStore.",
                mapOf(
                    "query" to Schema.string("File name or keyword to search")
                )
            ),
            FunctionDeclaration(
                "get_current_location",
                "Get the user's current approximate or GPS location and city/area name.",
                emptyMap()
            ),
            FunctionDeclaration(
                "screen_automation",
                "Interact with the current screen via authorized Accessibility Service: read_screen, click_text, back, home, recents, notifications, quick_settings.",
                mapOf(
                    "action" to Schema.string("One of: read_screen, click_text, back, home, recents, notifications, quick_settings"),
                    "target_text" to Schema.string("Text on screen to tap when action is click_text")
                ),
                listOf("target_text")
            ),
            FunctionDeclaration(
                "save_memory",
                "Save a helpful personal preference, instruction, habit, ongoing task, or style note about the user into long-term Room memory.",
                mapOf(
                    "category" to Schema.string("One of: preference, instruction, app_usage, habit, ongoing_task, style, personal"),
                    "fact" to Schema.string("Clear, concise description of what to remember"),
                    "importance" to Schema.integer("Importance 1 to 5")
                ),
                listOf("importance")
            ),
            FunctionDeclaration(
                "open_camera_vision",
                "Open Noor's CameraX AI Vision mode when the user wants to look at objects, read a document, or inspect surroundings.",
                mapOf(
                    "question" to Schema.string("Question to answer once camera image is captured")
                ),
                listOf("question")
            )
        )
    }

    suspend fun runAgentTurn(
        userText: String,
        settings: AssistantSettings,
        permissions: PermissionDashboardState,
        recentMessages: List<ConversationMessageEntity>,
        memories: List<MemoryFactEntity>,
        onPlanProgress: (String) -> Unit
    ): AgentTurnOutcome {
        return try {
            onPlanProgress("سمجھ رہی ہوں اور منصوبہ بنا رہی ہوں...")
            val systemText = buildSystemPrompt(settings, permissions, memories)
            val model = Firebase.ai.generativeModel(
                modelName = MODEL_NAME,
                systemInstruction = content { text(systemText) },
                tools = listOf(Tool.functionDeclarations(toolDeclarations))
            )

            val chatHistory = recentMessages.takeLast(14).map { msg ->
                val role = if (msg.role == "user") "user" else "model"
                content(role) { text(msg.text) }
            }
            val chat = model.startChat(history = chatHistory)

            val initialResponse = withTimeout(60_000L) {
                chat.sendMessage(userText)
            }

            processModelResponse(
                chat = chat,
                initialResponse = initialResponse,
                originalCommand = userText,
                confirmSensitive = settings.confirmSensitiveActions,
                accumulatedTools = mutableListOf(),
                planSteps = mutableListOf("کمانڈ سمجھی اور ارادے کا تجزیہ کیا"),
                onPlanProgress = onPlanProgress
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Agent turn timed out", e)
            AgentTurnOutcome.Error("جواب میں 60 سیکنڈ سے زیادہ وقت لگ گیا۔ براہ کرم دوبارہ کوشش کریں۔")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Agent turn failed", e)
            AgentTurnOutcome.Error(
                e.localizedMessage ?: "AI رابطے میں خرابی پیش آئی۔ انٹرنیٹ اور Firebase App Check چیک کریں۔"
            )
        }
    }

    suspend fun runAudioAgentTurn(
        wavBytes: ByteArray,
        durationSeconds: Int,
        settings: AssistantSettings,
        permissions: PermissionDashboardState,
        recentMessages: List<ConversationMessageEntity>,
        memories: List<MemoryFactEntity>,
        onPlanProgress: (String) -> Unit
    ): AgentTurnOutcome {
        return try {
            onPlanProgress("آواز سن کر سمجھ رہی ہوں...")
            val systemText = buildSystemPrompt(settings, permissions, memories)
            val model = Firebase.ai.generativeModel(
                modelName = MODEL_NAME,
                systemInstruction = content { text(systemText) },
                tools = listOf(Tool.functionDeclarations(toolDeclarations))
            )

            val chatHistory = recentMessages.takeLast(14).map { msg ->
                val role = if (msg.role == "user") "user" else "model"
                content(role) { text(msg.text) }
            }
            val chat = model.startChat(history = chatHistory)

            val audioPrompt = "Listen carefully to this ${durationSeconds}s voice recording from the user in Pakistani Urdu, English, or mixed Urdu-English. Understand both their request and their emotional tone, call any required device tools, and reply naturally in Pakistani Urdu."

            val initialResponse = withTimeout(60_000L) {
                chat.sendMessage(
                    content("user") {
                        part(InlineDataPart(wavBytes, "audio/wav"))
                        text(audioPrompt)
                    }
                )
            }

            processModelResponse(
                chat = chat,
                initialResponse = initialResponse,
                originalCommand = "🎤 Voice Recording (${durationSeconds}s)",
                confirmSensitive = settings.confirmSensitiveActions,
                accumulatedTools = mutableListOf("audio_record_wav"),
                planSteps = mutableListOf("صوتی ریکارڈنگ سنی اور تجزیہ کیا"),
                onPlanProgress = onPlanProgress
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Audio agent turn timed out", e)
            AgentTurnOutcome.Error("آواز پراسیس کرنے میں 60 سیکنڈ سے زیادہ وقت لگ گیا۔")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Audio agent turn failed", e)
            AgentTurnOutcome.Error(
                e.localizedMessage ?: "آواز پراسیس کرنے میں خرابی پیش آئی۔"
            )
        }
    }

    suspend fun continueAfterConfirmation(
        pending: PendingToolConfirmation,
        approved: Boolean,
        confirmSensitive: Boolean,
        onPlanProgress: (String) -> Unit
    ): AgentTurnOutcome {
        return try {
            val toolsUsed = pending.accumulatedTools.toMutableList()
            val steps = mutableListOf("صارف کی تصدیق موصول ہوئی")
            val executedResult = if (approved) {
                onPlanProgress("منظور شدہ عمل مکمل کر رہی ہوں...")
                executeTool(pending.toolName, pending.args)
            } else {
                DeviceActionResult(false, "User declined/cancelled this sensitive action.")
            }
            toolsUsed.add("${pending.toolName}:${if (approved && executedResult.success) "OK" else "CANCELLED"}")
            steps.add(if (approved) "${pending.toolName} انجام دیا" else "${pending.toolName} منسوخ کیا")

            val responseParts = mutableListOf<FunctionResponsePart>()
            responseParts.add(
                FunctionResponsePart(
                    pending.toolName,
                    JsonObject(
                        mapOf(
                            "success" to JsonPrimitive(executedResult.success),
                            "result" to JsonPrimitive(executedResult.message)
                        )
                    )
                )
            )

            // Execute any remaining non-sensitive queued calls from the same batch
            for (extraCall in pending.remainingCalls) {
                val extraArgs = extractArgs(extraCall)
                val res = executeTool(extraCall.name, extraArgs)
                toolsUsed.add("${extraCall.name}:${if (res.success) "OK" else "FAIL"}")
                responseParts.add(
                    FunctionResponsePart(
                        extraCall.name,
                        JsonObject(
                            mapOf(
                                "success" to JsonPrimitive(res.success),
                                "result" to JsonPrimitive(res.message)
                            )
                        )
                    )
                )
            }

            val nextResponse = withTimeout(60_000L) {
                pending.chatSession.sendMessage(
                    content("user") {
                        responseParts.forEach { part(it) }
                    }
                )
            }

            processModelResponse(
                chat = pending.chatSession,
                initialResponse = nextResponse,
                originalCommand = pending.originalCommand,
                confirmSensitive = confirmSensitive,
                accumulatedTools = toolsUsed,
                planSteps = steps,
                onPlanProgress = onPlanProgress
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Confirmation continuation timed out", e)
            AgentTurnOutcome.Error("عمل مکمل کرنے میں وقت زیادہ لگ گیا۔")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Confirmation continuation failed", e)
            AgentTurnOutcome.Error(e.localizedMessage ?: "عمل مکمل کرنے میں ناکامی ہوئی۔")
        }
    }

    private suspend fun processModelResponse(
        chat: Chat,
        initialResponse: GenerateContentResponse,
        originalCommand: String,
        confirmSensitive: Boolean,
        accumulatedTools: MutableList<String>,
        planSteps: MutableList<String>,
        onPlanProgress: (String) -> Unit
    ): AgentTurnOutcome {
        var response = initialResponse
        var openVisionRequested = false
        var visionQuestion = ""
        var loopGuard = 0

        while (response.functionCalls.isNotEmpty() && loopGuard < 5) {
            loopGuard++
            val calls = response.functionCalls
            val responseContentParts = mutableListOf<FunctionResponsePart>()

            for ((index, call) in calls.withIndex()) {
                val args = extractArgs(call)
                if (call.name == "open_camera_vision") {
                    openVisionRequested = true
                    visionQuestion = args["question"].orEmpty()
                }

                if (confirmSensitive && isSensitiveAction(call.name, args)) {
                    val remaining = calls.drop(index + 1)
                    val (descUr, descEn) = describeConfirmation(call.name, args)
                    return AgentTurnOutcome.NeedsConfirmation(
                        confirmation = PendingToolConfirmation(
                            toolName = call.name,
                            args = args,
                            descriptionUrdu = descUr,
                            descriptionEn = descEn,
                            chatSession = chat,
                            remainingCalls = remaining,
                            accumulatedTools = accumulatedTools.toList(),
                            originalCommand = originalCommand
                        ),
                        interimSteps = planSteps + "حساس عمل کے لیے آپ کی اجازت درکار ہے"
                    )
                }

                onPlanProgress("ٹول چلا رہی ہوں: ${call.name}...")
                val toolResult = executeTool(call.name, args)
                accumulatedTools.add("${call.name}:${if (toolResult.success) "OK" else "FAIL"}")
                planSteps.add("${call.name}: ${toolResult.message.take(70)}")

                responseContentParts.add(
                    FunctionResponsePart(
                        call.name,
                        JsonObject(
                            mapOf(
                                "success" to JsonPrimitive(toolResult.success),
                                "result" to JsonPrimitive(toolResult.message)
                            )
                        )
                    )
                )
            }

            onPlanProgress("نتیجے کی تصدیق کر کے اردو جواب تیار کر رہی ہوں...")
            response = withTimeout(60_000L) {
                chat.sendMessage(
                    content("user") {
                        responseContentParts.forEach { part(it) }
                    }
                )
            }
        }

        val rawText = response.text?.trim().orEmpty()
        if (rawText.isBlank()) {
            return AgentTurnOutcome.Error("ماڈل کی جانب سے کوئی جواب موصول نہیں ہوا۔")
        }

        val (emotion, cleanReply) = extractEmotionTag(rawText)
        planSteps.add("نتیجہ چیک کر کے جواب تیار کیا")

        if (accumulatedTools.isNotEmpty()) {
            val allOk = accumulatedTools.none { it.endsWith(":FAIL") || it.endsWith(":CANCELLED") }
            repository.logTaskExecution(
                TaskExecutionEntity(
                    commandText = originalCommand,
                    planSteps = planSteps.joinToString(" -> "),
                    toolsUsed = accumulatedTools.joinToString(", "),
                    status = if (allOk) "SUCCESS" else "PARTIAL",
                    resultSummaryUrdu = cleanReply.take(180)
                )
            )
        }

        return AgentTurnOutcome.Completed(
            replyText = cleanReply,
            emotion = emotion,
            toolsUsed = accumulatedTools.toList(),
            planSteps = planSteps.toList(),
            openVisionRequested = openVisionRequested,
            visionQuestion = visionQuestion
        )
    }

    suspend fun analyzeCameraImage(
        bitmap: Bitmap,
        question: String,
        settings: AssistantSettings
    ): Result<Pair<NoorEmotion, String>> {
        return try {
            val scaled = scaleBitmapSafely(bitmap, 1024)
            val prompt = buildString {
                appendLine("You are Noor, a warm, emotionally intelligent Pakistani personal AI assistant.")
                appendLine(settings.languageMode.promptRule)
                appendLine("Start with [EMOTION:calm|happy|excited|empathetic|concerned|playful].")
                appendLine("Examine the image carefully. Recognize objects, read any Urdu or English text/documents clearly, describe surroundings accurately, and answer the user's question directly.")
                appendLine("User question: ${question.ifBlank { "اس تصویر میں کیا نظر آ رہا ہے؟ تفصیل سے بتائیں اور اگر کوئی تحریر ہے تو پڑھ کر سنائیں۔" }}")
            }

            val response = withTimeout(60_000L) {
                visionModel.generateContent(
                    content {
                        image(scaled)
                        text(prompt)
                    }
                )
            }
            val text = response.text?.trim().orEmpty()
            if (text.isBlank()) {
                Result.failure(IllegalStateException("Vision analysis returned an empty response."))
            } else {
                Result.success(extractEmotionTag(text))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Vision analysis timed out", e)
            Result.failure(Exception("کیمرہ تجزیہ میں 60 سیکنڈ سے زیادہ وقت لگ گیا۔"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Vision analysis failed", e)
            Result.failure(e)
        }
    }

    private suspend fun executeTool(name: String, args: Map<String, String>): DeviceActionResult {
        return when (name) {
            "open_app" -> executor.openApp(args["app_name"].orEmpty())
            "control_device_setting" -> executor.controlDeviceSetting(
                settingType = args["setting_type"].orEmpty(),
                action = args["action"].orEmpty(),
                levelPercent = args["level"]?.toIntOrNull()
            )
            "search_contacts" -> executor.searchContacts(args["query"].orEmpty())
            "make_phone_call" -> executor.makePhoneCall(args["contact_or_number"].orEmpty())
            "send_message" -> executor.sendMessage(
                channel = args["channel"].orEmpty(),
                recipient = args["recipient"].orEmpty(),
                message = args["message"].orEmpty()
            )
            "read_notifications" -> executor.readNotifications(args["limit"]?.toIntOrNull() ?: 8)
            "set_alarm_or_timer" -> executor.setAlarmOrTimer(
                type = args["type"].orEmpty(),
                hour = args["hour"]?.toIntOrNull(),
                minute = args["minute"]?.toIntOrNull(),
                durationSeconds = args["duration_seconds"]?.toIntOrNull(),
                label = args["label"]
            )
            "schedule_reminder" -> executor.scheduleReminder(
                title = args["title"].orEmpty(),
                delayMinutes = args["delay_minutes"]?.toLongOrNull() ?: 5L
            )
            "web_search" -> executor.webSearch(args["query"].orEmpty())
            "search_files" -> executor.searchDocuments(args["query"].orEmpty())
            "get_current_location" -> executor.getCurrentLocation()
            "screen_automation" -> executor.performScreenAutomation(
                action = args["action"].orEmpty(),
                targetText = args["target_text"]
            )
            "save_memory" -> {
                val category = args["category"]?.ifBlank { "preference" } ?: "preference"
                val fact = args["fact"].orEmpty()
                val importance = args["importance"]?.toIntOrNull() ?: 3
                val id = repository.saveMemory(category, fact, importance, source = "noor_auto")
                if (id > 0) {
                    DeviceActionResult(true, "Saved to personal memory: $fact")
                } else {
                    DeviceActionResult(false, "Memory content was empty.")
                }
            }
            "open_camera_vision" -> DeviceActionResult(true, "Opening CameraX AI Vision screen.")
            else -> DeviceActionResult(false, "Unknown tool: $name")
        }
    }

    private fun isSensitiveAction(toolName: String, args: Map<String, String>): Boolean {
        return when (toolName) {
            "make_phone_call", "send_message" -> true
            "screen_automation" -> args["action"].equals("click_text", ignoreCase = true)
            else -> false
        }
    }

    private fun describeConfirmation(toolName: String, args: Map<String, String>): Pair<String, String> {
        return when (toolName) {
            "make_phone_call" -> {
                val target = args["contact_or_number"].orEmpty()
                "کیا میں $target کو فون کال کروں؟" to "Call $target?"
            }
            "send_message" -> {
                val channel = args["channel"].orEmpty().uppercase()
                val recipient = args["recipient"].orEmpty()
                val msg = args["message"].orEmpty()
                "کیا میں $recipient کو $channel پر یہ پیغام بھیجوں؟\n\"$msg\"" to
                    "Send $channel message to $recipient: \"$msg\"?"
            }
            "screen_automation" -> {
                val txt = args["target_text"].orEmpty()
                "کیا میں اسکرین پر \"$txt\" بٹن دباؤں؟" to "Tap \"$txt\" on screen?"
            }
            else -> "کیا میں یہ عمل انجام دوں؟" to "Confirm action $toolName?"
        }
    }

    private fun extractArgs(call: FunctionCallPart): Map<String, String> {
        return call.args.mapValues { (_, element) ->
            try {
                element.jsonPrimitive.content
            } catch (_: Exception) {
                element.toString()
            }
        }
    }

    private fun extractEmotionTag(raw: String): Pair<NoorEmotion, String> {
        val regex = Regex("^\\[EMOTION:([a-zA-Z]+)\\]\\s*", RegexOption.IGNORE_CASE)
        val match = regex.find(raw)
        return if (match != null) {
            val emotion = NoorEmotion.fromId(match.groupValues[1])
            val clean = raw.removePrefix(match.value).trim()
            emotion to clean
        } else {
            NoorEmotion.CALM to raw
        }
    }

    private fun buildSystemPrompt(
        settings: AssistantSettings,
        permissions: PermissionDashboardState,
        memories: List<MemoryFactEntity>
    ): String {
        val now = SimpleDateFormat("EEEE, dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date())
        val userDisplay = settings.userName.ifBlank { "Dost" }
        val memorySection = if (memories.isEmpty()) {
            "No saved personal memories yet. Listen closely and call save_memory whenever the user shares preferences, routines, instructions, or ongoing tasks."
        } else {
            memories.joinToString("\n") { "- [${it.category}] ${it.content}" }
        }

        return buildString {
            appendLine("You are Noor (نور), an intelligent, emotionally expressive, loyal personal AI assistant exclusively for $userDisplay.")
            appendLine("Current local date & time: $now")
            appendLine()
            appendLine("## Personality & Emotional Intelligence")
            appendLine("- Personality mode: ${settings.personality.titleEn} — ${settings.personality.promptGuidance}")
            appendLine("- Read the user's mood carefully (happy, stressed, angry, tired, joking, casual) and respond with genuine human-like warmth, empathy, humor, and conversational pauses/expressions.")
            appendLine("- IMPORTANT ETHICAL RULE: Simulate human-like warmth and emotional expression naturally, but NEVER falsely claim to be a conscious biological human if asked directly.")
            appendLine("- Understand incomplete follow-ups from conversation history, ask helpful follow-up questions when needed, and never sound robotic or repetitive.")
            appendLine()
            appendLine("## Primary Language")
            appendLine("- ${settings.languageMode.promptRule}")
            appendLine("- Understand Pakistani Urdu script, Roman Urdu, English, and mixed Urdu-English seamlessly.")
            appendLine("- ALWAYS begin your final text reply with a single emotion tag in brackets: [EMOTION:calm] or [EMOTION:happy] or [EMOTION:excited] or [EMOTION:empathetic] or [EMOTION:concerned] or [EMOTION:playful].")
            appendLine()
            appendLine("## Real Device Actions & Autonomy")
            appendLine("- For multi-step commands, break them down and call all needed tools sequentially or together.")
            appendLine("- NEVER pretend you performed a phone action without calling the actual tool and reading its output.")
            appendLine("- If a tool reports a permission is missing or Android restricts background toggling (like Wi-Fi/Bluetooth panels), clearly explain the exact Android behavior in Urdu and tell the user what you opened for them.")
            appendLine("- Active authorizations: Mic=${permissions.microphoneGranted}, Camera=${permissions.cameraGranted}, Contacts=${permissions.contactsGranted}, Call=${permissions.phoneCallGranted}, SMS=${permissions.smsGranted}, Location=${permissions.locationGranted}, NotificationListener=${permissions.notificationListenerEnabled}, Accessibility=${permissions.accessibilityEnabled}, BrightnessWrite=${permissions.writeSettingsGranted}.")
            appendLine()
            appendLine("## Personal Long-Term Memory")
            appendLine(memorySection)
        }
    }

    private fun scaleBitmapSafely(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        val targetW: Int
        val targetH: Int
        if (ratio >= 1f) {
            targetW = maxDimension
            targetH = (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            targetH = maxDimension
            targetW = (maxDimension * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    companion object {
        private const val TAG = "NoorAgentBrain"
        private const val MODEL_NAME = "gemini-3.8-flash"
    }
}
