package com.example.dsh.ui.home

import com.example.dsh.base.bridgeModule
import com.example.dsh.base.setTimeout
import com.example.dsh.ui.voice.DSH_VOICE_DECAY_GAP_MS
import com.example.dsh.ui.voice.DSH_VOICE_DECAY_TICK_MS
import com.example.dsh.ui.voice.DSH_VOICE_LEVEL_INTERVAL_MS
import com.example.dsh.ui.voice.DSH_VOICE_STOP_TIMEOUT_MS
import com.example.dsh.ui.voice.dshVoiceErrorMessage
import com.example.dsh.voice.dshVoiceCancelArmed
import com.example.dsh.voice.dshVoiceResolvedText
import com.example.dsh.voice.dshVoiceShouldCommit
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.datetime.DateTime
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.TextAreaView

/** 语音输入 UI 状态：把原来散落的 voice* observable 聚合成一个不可变状态。 */
internal data class DshVoiceUiState(
    val active: Boolean = false,
    val recording: Boolean = false,
    val cancelArmed: Boolean = false,
    val partialText: String = "",
    val waveformRevision: Int = 0,
)

internal fun DshHomePage.toggleVoice() {
    dismissKeyboard()
    commandSheetVisible = false
    // 切换模式时若正在录音，先取消，避免遗留会话
    if (voiceUi.recording) {
        voiceCancelRequested = true
        bridgeModule.cancelVoiceRecognition()
        finishVoiceRecord(cancelled = true)
    }
    voiceUi = voiceUi.copy(active = !voiceUi.active)
    if (!voiceUi.active) resetVoiceVisual()
}

/** 按住说话：按下，开始语音识别并显示录音浮层。 */
internal fun DshHomePage.beginVoiceRecord(startPageY: Float) {
    if (voiceUi.recording) return
    voiceUi = voiceUi.copy(active = true)
    dismissKeyboard()
    voiceHoldStartY = startPageY
    voiceStopping = false
    voiceCancelRequested = false
    voiceCommittedText = ""
    voiceLastLevelAt = 0L
    voiceStopFallbackScheduled = false
    voiceWaveformModel.clear()
    voiceUi = voiceUi.copy(
        cancelArmed = false,
        partialText = "",
        waveformRevision = voiceUi.waveformRevision + 1,
        recording = true,
    )
    bridgeModule.startVoiceRecognition { raw -> handleVoiceEvent(raw) }
    voiceDecayGeneration += 1
    scheduleVoiceDecay(voiceDecayGeneration)
}

/** 按住说话：移动，上滑超过阈值进入取消态。 */
internal fun DshHomePage.updateVoiceHold(currentPageY: Float) {
    if (!voiceUi.recording) return
    voiceUi = voiceUi.copy(cancelArmed = dshVoiceCancelArmed(voiceHoldStartY, currentPageY))
}

/** 按住说话：松手，取消或提交识别。 */
internal fun DshHomePage.endVoiceRecord() {
    if (!voiceUi.recording || voiceStopping) return
    voiceStopping = true
    if (voiceUi.cancelArmed) {
        voiceCancelRequested = true
        bridgeModule.cancelVoiceRecognition()
        finishVoiceRecord(cancelled = true)
        return
    }
    bridgeModule.stopVoiceRecognition()
    // 若最终结果已提前到达（系统自动结束），可直接提交；否则等待 final/end 或兜底超时
    if (voiceCommittedText.isNotEmpty()) {
        finishVoiceRecord(cancelled = false)
        return
    }
    if (!voiceStopFallbackScheduled) {
        voiceStopFallbackScheduled = true
        // 兜底定时器绑定本次录音代次：新一轮录音开始后旧定时器不再结束它。
        val generation = voiceDecayGeneration
        setTimeout(pagerId, DSH_VOICE_STOP_TIMEOUT_MS) {
            if (generation != voiceDecayGeneration) return@setTimeout
            voiceStopFallbackScheduled = false
            if (voiceUi.recording) finishVoiceRecord(cancelled = voiceCancelRequested)
        }
    }
}

/** 处理原生语音识别事件（ready/level/partial/final/error/end）。 */
internal fun DshHomePage.handleVoiceEvent(raw: String) {
    if (raw.isEmpty() || !pageAlive) return
    val event = runCatching { JSONObject(raw) }.getOrNull() ?: return
    when (event.optString("event")) {
        "ready" -> Unit
        "level" -> appendVoiceLevel(event.optDouble("level").toFloat())
        "partial" -> {
            val text = event.optString("text")
            if (text.isNotEmpty()) voiceUi = voiceUi.copy(partialText = text)
        }
        "final" -> {
            val text = event.optString("text")
            if (text.isNotEmpty()) {
                voiceCommittedText = text
                voiceUi = voiceUi.copy(partialText = text)
            }
        }
        "error" -> {
            val message = dshVoiceErrorMessage(
                code = event.optString("code"),
                locale = settingsSnapshot.localeValue,
                fallback = event.optString("message"),
            )
            val wasRecording = voiceUi.recording
            finishVoiceRecord(cancelled = true)
            if (wasRecording) bridgeModule.toast(message)
        }
        "end" -> {
            // 仅当已松手/已取消时才结束；录音中系统自动结束则保留结果，待松手提交
            if (voiceStopping || voiceCancelRequested) {
                finishVoiceRecord(cancelled = voiceCancelRequested)
            }
        }
    }
}

/** 音量事件节流后写入滚动窗口，驱动浮层方块高度。 */
internal fun DshHomePage.appendVoiceLevel(level: Float) {
    if (!voiceUi.recording) return
    val now = DateTime.currentTimestamp()
    if (now - voiceLastLevelAt < DSH_VOICE_LEVEL_INTERVAL_MS) return
    voiceLastLevelAt = now
    pushVoiceLevel(level)
}

/** 把新音量推入滚动窗口左移一格；每次自增 revision 触发波形重绘。 */
internal fun DshHomePage.pushVoiceLevel(level: Float) {
    voiceWaveformModel.push(level)
    voiceUi = voiceUi.copy(waveformRevision = voiceUi.waveformRevision + 1)
}

/**
 * 无新音量采样时让波形平滑衰减，避免原生 RMS 停止上报（静音/识别暂停）后
 * 波形看起来「卡住不动」。
 */
internal fun DshHomePage.scheduleVoiceDecay(generation: Int) {
    if (!voiceUi.recording || generation != voiceDecayGeneration) return
    val now = DateTime.currentTimestamp()
    if (now - voiceLastLevelAt >= DSH_VOICE_DECAY_GAP_MS) {
        if (voiceWaveformModel.decay()) voiceUi = voiceUi.copy(waveformRevision = voiceUi.waveformRevision + 1)
    }
    setTimeout(pagerId, DSH_VOICE_DECAY_TICK_MS) { scheduleVoiceDecay(generation) }
}

/** 原生输入框挂载完成：记录实例；若有语音转文字待回填则写入。 */
internal fun DshHomePage.onInputViewCreated(view: TextAreaView?) {
    if (view == null) return
    inputView = view
    pendingVoiceDraft?.let { text ->
        pendingVoiceDraft = null
        view.setText(text)
    }
}

/** 结束录音：取消丢弃，或把最终文本写入输入框并切回文字模式。 */
internal fun DshHomePage.finishVoiceRecord(cancelled: Boolean) {
    if (!voiceUi.recording) return
    voiceUi = voiceUi.copy(recording = false)
    // 递增代次，使仍在等待的旧兜底定时器/权限回调失效
    voiceDecayGeneration += 1
    // 会话结束，释放跨事件回调
    bridgeModule.releaseVoiceRecognition()
    val text = dshVoiceResolvedText(voiceCommittedText, voiceUi.partialText)
    val shouldCommit = dshVoiceShouldCommit(cancelled, text)
    resetVoiceVisual()
    if (shouldCommit) {
        // 切回文字模式后 TextArea 会重新挂载，待其 ref 回调时回填原生文本
        voiceUi = voiceUi.copy(active = false)
        draft = text
        pendingVoiceDraft = text
    }
}

internal fun DshHomePage.resetVoiceVisual() {
    voiceCommittedText = ""
    voiceStopping = false
    voiceCancelRequested = false
    voiceWaveformModel.clear()
    voiceUi = voiceUi.copy(
        partialText = "",
        cancelArmed = false,
        waveformRevision = voiceUi.waveformRevision + 1,
    )
}
