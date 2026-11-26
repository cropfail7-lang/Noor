package com.example

import com.example.ai.NoorEmotion
import com.example.data.preferences.AssistantPersonality
import com.example.data.preferences.LanguageMode
import com.example.service.AudioRecordingService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `audio service builds valid RIFF WAVE header and computes RMS`() {
        val pcm = ByteArray(320) { 10 }
        val wav = AudioRecordingService.buildWavBytes(pcm)
        assertEquals(44 + pcm.size, wav.size)
        assertEquals('R'.code.toByte(), wav[0])
        assertEquals('I'.code.toByte(), wav[1])
        assertEquals('F'.code.toByte(), wav[2])
        assertEquals('F'.code.toByte(), wav[3])
        assertEquals('W'.code.toByte(), wav[8])
        assertEquals('A'.code.toByte(), wav[9])
        assertEquals('V'.code.toByte(), wav[10])
        assertEquals('E'.code.toByte(), wav[11])

        val silenceRms = AudioRecordingService.computeNormalizedRms(ShortArray(100), 100)
        assertEquals(0f, silenceRms)

        val loudSamples = ShortArray(100) { 12000 }
        val loudRms = AudioRecordingService.computeNormalizedRms(loudSamples, 100)
        assertTrue(loudRms > 0.5f)
    }
}
