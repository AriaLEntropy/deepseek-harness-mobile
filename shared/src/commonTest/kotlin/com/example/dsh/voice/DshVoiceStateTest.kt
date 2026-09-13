package com.example.dsh.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.example.dsh.voice.DSH_VOICE_DECAY_EPSILON
import com.example.dsh.voice.dshVoiceCancelArmed
import com.example.dsh.voice.dshVoiceResolvedText
import com.example.dsh.voice.dshVoiceShouldCommit
import com.example.dsh.voice.DshVoiceWaveform

class DshVoiceStateTest {

    @Test
    fun pushShiftsWindowLeft() {
        val waveform = DshVoiceWaveform(size = 3)
        waveform.push(0.1f)
        waveform.push(0.2f)
        waveform.push(0.3f)
        assertEquals(listOf(0.1f, 0.2f, 0.3f), waveform.levels)
        waveform.push(0.4f)
        assertEquals(listOf(0.2f, 0.3f, 0.4f), waveform.levels)
        assertEquals(0.4f, waveform.last)
    }

    @Test
    fun pushClampsLevel() {
        val waveform = DshVoiceWaveform(size = 2)
        waveform.push(2f)
        assertEquals(1f, waveform.last)
        waveform.push(-1f)
        assertEquals(0f, waveform.last)
    }

    @Test
    fun clearZeroesWindow() {
        val waveform = DshVoiceWaveform(size = 3)
        waveform.push(1f)
        waveform.clear()
        assertEquals(listOf(0f, 0f, 0f), waveform.levels)
    }

    @Test
    fun decayReducesThenStopsAtSilence() {
        val waveform = DshVoiceWaveform(size = 2)
        waveform.push(1f)
        assertTrue(waveform.decay())
        assertEquals(0.55f, waveform.last)

        var guard = 0
        while (waveform.decay() && guard < 100) guard++
        assertTrue(waveform.last <= DSH_VOICE_DECAY_EPSILON, "last=${waveform.last}")
        assertFalse(waveform.decay())
    }

    @Test
    fun cancelArmedUsesVerticalThreshold() {
        assertTrue(dshVoiceCancelArmed(startPageY = 100f, currentPageY = 20f))
        assertFalse(dshVoiceCancelArmed(startPageY = 100f, currentPageY = 21f))
        assertFalse(dshVoiceCancelArmed(startPageY = 100f, currentPageY = 100f))
    }

    @Test
    fun resolvedTextPrefersFinalThenPartial() {
        assertEquals("final", dshVoiceResolvedText("final", "partial"))
        assertEquals("partial", dshVoiceResolvedText("", "  partial  "))
        assertEquals("", dshVoiceResolvedText("", ""))
    }

    @Test
    fun shouldCommitRequiresTextAndNotCancelled() {
        assertTrue(dshVoiceShouldCommit(cancelled = false, text = "hi"))
        assertFalse(dshVoiceShouldCommit(cancelled = true, text = "hi"))
        assertFalse(dshVoiceShouldCommit(cancelled = false, text = ""))
    }
}
