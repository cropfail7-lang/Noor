package com.example

import com.example.ai.NoorEmotion
import com.example.data.preferences.AssistantPersonality
import com.example.data.preferences.LanguageMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun `emotion mapping handles case and fallback`() {
        assertEquals(NoorEmotion.HAPPY, NoorEmotion.fromId("HAPPY"))
        assertEquals(NoorEmotion.EMPATHETIC, NoorEmotion.fromId("empathetic"))
        assertEquals(NoorEmotion.CALM, NoorEmotion.fromId("unknown_tag"))
    }

    @Test
    fun `personality and language defaults resolve properly`() {
        assertEquals(AssistantPersonality.WARM_COMPANION, AssistantPersonality.fromId(null))
        assertEquals(AssistantPersonality.DIGITAL_SECRETARY, AssistantPersonality.fromId("digital_secretary"))
        assertEquals(LanguageMode.PAKISTANI_URDU, LanguageMode.fromId("ur_pk"))
        assertEquals(LanguageMode.PAKISTANI_URDU, LanguageMode.fromId("invalid"))
    }
}
