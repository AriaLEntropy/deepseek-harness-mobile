package com.example.dsh.voice

import com.example.dsh.chat.DSH_VOICE_CANCEL_THRESHOLD
import com.example.dsh.chat.DSH_VOICE_WAVE_BARS

/** 波形无新采样后的衰减系数。 */
internal const val DSH_VOICE_DECAY_FACTOR = 0.55f

/** 低于该值即视为静音，不再衰减。 */
internal const val DSH_VOICE_DECAY_EPSILON = 0.001f

/**
 * 录音波形滚动窗口（纯逻辑）。
 *
 * 新采样从右侧入队、整体左移一格；静音时按 [DSH_VOICE_DECAY_FACTOR] 逐拍回退，
 * 避免原生 RMS 停止上报后波形看起来「卡住不动」。只维护数值，不涉及重绘。
 */
internal class DshVoiceWaveform(private val size: Int = DSH_VOICE_WAVE_BARS) {

    private val values = MutableList(size) { 0f }

    val levels: List<Float> get() = values

    val last: Float get() = values.lastOrNull() ?: 0f

    fun clear() {
        for (index in values.indices) values[index] = 0f
    }

    /** 新采样入队（裁剪到 0..1），窗口左移一格。 */
    fun push(level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        for (index in 0 until values.size - 1) values[index] = values[index + 1]
        if (values.isNotEmpty()) values[values.size - 1] = clamped
    }

    /** 静音时衰减一次；返回是否发生变化（供调用方决定是否触发重绘）。 */
    fun decay(factor: Float = DSH_VOICE_DECAY_FACTOR): Boolean {
        val current = last
        if (current <= DSH_VOICE_DECAY_EPSILON) return false
        push(current * factor)
        return true
    }
}

/** 按住说话：根据纵向位移判断是否进入「上滑取消」态。 */
internal fun dshVoiceCancelArmed(startPageY: Float, currentPageY: Float): Boolean =
    (startPageY - currentPageY) >= DSH_VOICE_CANCEL_THRESHOLD

/** 结束录音时取最终文本：优先原生 final，其次 partial，去空白。 */
internal fun dshVoiceResolvedText(committed: String, partial: String): String =
    committed.ifEmpty { partial }.trim()

/** 是否应把识别文本写入输入框（未取消且有内容）。 */
internal fun dshVoiceShouldCommit(cancelled: Boolean, text: String): Boolean =
    !cancelled && text.isNotEmpty()
