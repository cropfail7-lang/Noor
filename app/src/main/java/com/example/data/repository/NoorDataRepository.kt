package com.example.data.repository

import com.example.data.local.CapturedNotificationEntity
import com.example.data.local.ConversationDao
import com.example.data.local.ConversationMessageEntity
import com.example.data.local.MemoryDao
import com.example.data.local.MemoryFactEntity
import com.example.data.local.NoorDatabase
import com.example.data.local.NotificationDao
import com.example.data.local.TaskExecutionDao
import com.example.data.local.TaskExecutionEntity
import kotlinx.coroutines.flow.Flow

class NoorDataRepository(
    private val conversationDao: ConversationDao,
    private val memoryDao: MemoryDao,
    private val taskExecutionDao: TaskExecutionDao,
    private val notificationDao: NotificationDao
) {
    constructor(database: NoorDatabase) : this(
        conversationDao = database.conversationDao(),
        memoryDao = database.memoryDao(),
        taskExecutionDao = database.taskExecutionDao(),
        notificationDao = database.notificationDao()
    )

    val messages: Flow<List<ConversationMessageEntity>> = conversationDao.observeAllMessages()
    val memories: Flow<List<MemoryFactEntity>> = memoryDao.observeAllMemories()
    val taskHistory: Flow<List<TaskExecutionEntity>> = taskExecutionDao.observeTaskHistory()
    val notifications: Flow<List<CapturedNotificationEntity>> = notificationDao.observeNotifications()

    suspend fun addMessage(message: ConversationMessageEntity): Long =
        conversationDao.insertMessage(message)

    suspend fun getRecentMessages(limit: Int = 20): List<ConversationMessageEntity> =
        conversationDao.getRecentMessages(limit).reversed()

    suspend fun clearConversation() = conversationDao.clearConversation()

    suspend fun saveMemory(
        category: String,
        content: String,
        importance: Int = 3,
        source: String = "assistant"
    ): Long {
        val cleanContent = content.trim()
        if (cleanContent.isBlank()) return -1L
        val existing = memoryDao.getTopMemories(80).firstOrNull {
            it.content.equals(cleanContent, ignoreCase = true)
        }
        val entity = if (existing != null) {
            existing.copy(
                category = category,
                importance = importance.coerceIn(1, 5),
                updatedAt = System.currentTimeMillis()
            )
        } else {
            MemoryFactEntity(
                category = category,
                content = cleanContent,
                importance = importance.coerceIn(1, 5),
                source = source
            )
        }
        return memoryDao.insertMemory(entity)
    }

    suspend fun getTopMemories(limit: Int = 40): List<MemoryFactEntity> =
        memoryDao.getTopMemories(limit)

    suspend fun deleteMemory(id: Long) = memoryDao.deleteMemoryById(id)

    suspend fun clearAllMemories() = memoryDao.clearAllMemories()

    suspend fun logTaskExecution(task: TaskExecutionEntity): Long =
        taskExecutionDao.insertTask(task)

    suspend fun clearTaskHistory() = taskExecutionDao.clearTaskHistory()

    suspend fun addCapturedNotification(notification: CapturedNotificationEntity): Long =
        notificationDao.insertNotification(notification)

    suspend fun getLatestNotifications(limit: Int = 10): List<CapturedNotificationEntity> =
        notificationDao.getLatestNotifications(limit)

    suspend fun markAllNotificationsRead() = notificationDao.markAllRead()

    suspend fun clearNotifications() = notificationDao.clearNotifications()

    suspend fun pruneOldNotifications(olderThanMillis: Long) =
        notificationDao.deleteOlderThan(olderThanMillis)
}
