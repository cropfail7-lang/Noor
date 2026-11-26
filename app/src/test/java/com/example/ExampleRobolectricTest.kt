package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.NoorEmotion
import com.example.data.local.CapturedNotificationEntity
import com.example.data.local.ConversationMessageEntity
import com.example.data.local.NoorDatabase
import com.example.data.local.TaskExecutionEntity
import com.example.data.preferences.AssistantPersonality
import com.example.data.preferences.LanguageMode
import com.example.data.repository.NoorDataRepository
import com.example.device.PermissionStatusChecker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: NoorDatabase
    private lateinit var repository: NoorDataRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, NoorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = NoorDataRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `app name matches Noor AI`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Noor AI", appName)
    }

    @Test
    fun `room memory repository saves deduplicates and deletes Urdu facts`() = runTest {
        val id1 = repository.saveMemory("preference", "مجھے صبح چائے پسند ہے", 4)
        assertTrue(id1 > 0)

        // Duplicate update should keep count 1
        repository.saveMemory("habit", "مجھے صبح چائے پسند ہے", 5)
        val stored = repository.memories.first()
        assertEquals(1, stored.size)
        assertEquals(5, stored.first().importance)
        assertEquals("habit", stored.first().category)

        repository.deleteMemory(stored.first().id)
        assertTrue(repository.memories.first().isEmpty())
    }

    @Test
    fun `room stores conversation task logs and captured notifications`() = runTest {
        repository.addMessage(
            ConversationMessageEntity(
                role = "user",
                text = "واٹس ایپ کھولو",
                emotionTag = NoorEmotion.CALM.id
            )
        )
        repository.logTaskExecution(
            TaskExecutionEntity(
                commandText = "واٹس ایپ کھولو",
                planSteps = "Intent -> open_app",
                toolsUsed = "open_app:OK",
                status = "SUCCESS",
                resultSummaryUrdu = "واٹس ایپ کھول دیا گیا ہے۔"
            )
        )
        repository.addCapturedNotification(
            CapturedNotificationEntity(
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                title = "Ammi",
                body = "Ghar kab aoge?"
            )
        )

        assertEquals(1, repository.getRecentMessages(5).size)
        assertEquals(1, repository.taskHistory.first().size)
        assertEquals(1, repository.getLatestNotifications(5).size)
    }

    @Test
    fun `personality language and permission inspection behave consistently`() {
        assertEquals(AssistantPersonality.WARM_COMPANION, AssistantPersonality.fromId("unknown"))
        assertEquals(LanguageMode.PAKISTANI_URDU, LanguageMode.fromId(null))
        assertEquals(NoorEmotion.EMPATHETIC, NoorEmotion.fromId("empathetic"))

        val state = PermissionStatusChecker.inspect(context)
        assertEquals(10, state.totalCount)
    }
}
