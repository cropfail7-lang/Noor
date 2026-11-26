package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_messages")
data class ConversationMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user", "assistant", "system"
    val text: String,
    val emotionTag: String = "calm", // calm, happy, excited, empathetic, concerned, playful
    val planStepsSummary: String = "",
    val toolsUsedSummary: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "memory_facts")
data class MemoryFactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // preference, instruction, app_usage, habit, ongoing_task, style, personal
    val content: String,
    val importance: Int = 3, // 1..5
    val source: String = "conversation",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "task_executions")
data class TaskExecutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val commandText: String,
    val planSteps: String,
    val toolsUsed: String,
    val status: String, // SUCCESS, PARTIAL, FAILED, CANCELLED, NEEDS_CONFIRMATION
    val resultSummaryUrdu: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "captured_notifications")
data class CapturedNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String,
    val body: String,
    val isReadByAssistant: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
