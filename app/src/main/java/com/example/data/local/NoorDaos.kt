package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversation_messages ORDER BY timestamp ASC")
    fun observeAllMessages(): Flow<List<ConversationMessageEntity>>

    @Query("SELECT * FROM conversation_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int = 20): List<ConversationMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationMessageEntity): Long

    @Query("DELETE FROM conversation_messages")
    suspend fun clearConversation()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_facts ORDER BY importance DESC, updatedAt DESC")
    fun observeAllMemories(): Flow<List<MemoryFactEntity>>

    @Query("SELECT * FROM memory_facts ORDER BY importance DESC, updatedAt DESC LIMIT :limit")
    suspend fun getTopMemories(limit: Int = 40): List<MemoryFactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryFactEntity): Long

    @Query("DELETE FROM memory_facts WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM memory_facts")
    suspend fun clearAllMemories()
}

@Dao
interface TaskExecutionDao {
    @Query("SELECT * FROM task_executions ORDER BY timestamp DESC LIMIT 100")
    fun observeTaskHistory(): Flow<List<TaskExecutionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskExecutionEntity): Long

    @Query("DELETE FROM task_executions")
    suspend fun clearTaskHistory()
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM captured_notifications ORDER BY timestamp DESC LIMIT 50")
    fun observeNotifications(): Flow<List<CapturedNotificationEntity>>

    @Query("SELECT * FROM captured_notifications ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLatestNotifications(limit: Int = 10): List<CapturedNotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: CapturedNotificationEntity): Long

    @Query("UPDATE captured_notifications SET isReadByAssistant = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM captured_notifications")
    suspend fun clearNotifications()

    @Query("DELETE FROM captured_notifications WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
