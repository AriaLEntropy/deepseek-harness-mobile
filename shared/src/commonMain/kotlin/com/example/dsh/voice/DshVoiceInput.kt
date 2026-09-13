package com.example.dsh.voice

import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** 录音浮层波形方块数量（滚动窗口）。 */
internal const val DSH_VOICE_WAVE_BARS = 32

/** 上滑取消阈值（dp）：手指距按下点向上超过该距离即进入取消态。 */
internal const val DSH_VOICE_CANCEL_THRESHOLD = 80f

/** 音量采样最小间隔（毫秒），节流后驱动波形，避免高频重建列表。 */
internal const val DSH_VOICE_LEVEL_INTERVAL_MS = 90L

/** 松手后等待原生最终结果的兜底时长（毫秒）。 */
internal const val DSH_VOICE_STOP_TIMEOUT_MS = 900

/** 无新音量采样后开始衰减的间隔（毫秒）。 */
internal const val DSH_VOICE_DECAY_GAP_MS = 160L

/** 衰减刷新节拍（毫秒）。 */
internal const val DSH_VOICE_DECAY_TICK_MS = 90

/**
 * 语音输入相关文案。项目暂无全量 i18n 资源，这里按「语言」设置（settings.localeValue）
 * 在 zh / en 之间切换；其余 UI 文案仍为既有中文实现。
 */
internal data class DshVoiceStrings(
    val inputPlaceholder: String,
    val holdToTalk: String,
    val recordingReleaseToEnd: String,
    val slideUpToCancel: String,
    val releaseToCancel: String,
    val errorPermissionDenied: String,
    val errorUnsupported: String,
    val errorStartFailed: String,
    val errorNetwork: String,
    val errorBusy: String,
    val errorFailed: String,
)

private val DSH_VOICE_STRINGS_ZH = DshVoiceStrings(
    inputPlaceholder = "给智能体发消息",
    holdToTalk = "按住 说话",
    recordingReleaseToEnd = "松开 结束",
    slideUpToCancel = "松手发送，上滑取消",
    releaseToCancel = "松开手指，取消发送",
    errorPermissionDenied = "需要麦克风权限才能使用语音输入",
    errorUnsupported = "当前设备不支持语音识别",
    errorStartFailed = "语音识别启动失败",
    errorNetwork = "网络错误，语音识别需要联网",
    errorBusy = "语音识别服务繁忙，请稍后重试",
    errorFailed = "语音识别失败",
)

private val DSH_VOICE_STRINGS_EN = DshVoiceStrings(
    inputPlaceholder = "Message the agent",
    holdToTalk = "Hold to talk",
    recordingReleaseToEnd = "Release to end",
    slideUpToCancel = "Release to send, slide up to cancel",
    releaseToCancel = "Release to cancel",
    errorPermissionDenied = "Microphone permission is required for voice input",
    errorUnsupported = "Speech recognition is not available on this device",
    errorStartFailed = "Failed to start speech recognition",
    errorNetwork = "Network error. Speech recognition needs a connection",
    errorBusy = "Speech recognition is busy, please try again",
    errorFailed = "Speech recognition failed",
)

/** 按语言偏好解析语音文案；`en` 用英文，其余（含跟随电脑端）回退中文。 */
internal fun dshVoiceStrings(locale: String): DshVoiceStrings =
    if (locale == "en") DSH_VOICE_STRINGS_EN else DSH_VOICE_STRINGS_ZH

/** 把原生错误码解析为本地化文案；未知码回退原生 message，再回退通用失败文案。 */
internal fun dshVoiceErrorMessage(code: String, locale: String, fallback: String): String {
    val strings = dshVoiceStrings(locale)
    return when (code) {
        "permission_denied" -> strings.errorPermissionDenied
        "unsupported" -> strings.errorUnsupported
        "start_failed" -> strings.errorStartFailed
        "network" -> strings.errorNetwork
        "busy" -> strings.errorBusy
        "failed" -> strings.errorFailed
        else -> fallback.ifEmpty { strings.errorFailed }
    }
}

/**
 * 语音录音浮层：无边框贴底弹出。
 *
 * 按住「按住说话」时显示，包含提示文案、可选的中间识别文本，以及一排蓝色小方块；
 * 方块高度由实时音量 [levels]（滚动窗口，0..1）驱动，检测到音频输入时高度变化。
 * 上滑到取消区间时 [cancelArmed] 为真，文案与方块变红。
 *
 * 浮层不参与触摸分发（`touchEnable(false)`），按住手势仍由输入区按钮持有。
 *
 * 波形用 [revision]（每次采样 +1）驱动 `vbind` 整体重绘，避免滚动窗口在
 * `vfor` 差量复用下不刷新；`levels` 只读，不需要是 ObservableList。
 */
internal fun ViewContainer<*, *>.DshVoiceRecordOverlay(
    visible: () -> Boolean,
    cancelArmed: () -> Boolean,
    partialText: () -> String,
    levels: () -> List<Float>,
    revision: () -> Int,
    strings: () -> DshVoiceStrings = { DSH_VOICE_STRINGS_ZH },
    colors: () -> DshColorTokens = { DshDefaultTheme.light },
) {
    vif({ visible() }) {
        View {
            attr {
                absolutePositionAllZero()
                zIndex(900)
                touchEnable(false)
                flexDirectionColumn()
                justifyContentFlexEnd()
            }
            // 半透明遮罩：聚焦录音浮层，不拦截触摸
            View {
                attr {
                    absolutePositionAllZero()
                    backgroundColor(if (cancelArmed()) Color(0x14000000) else Color(0x08000000))
                }
            }
            // 无边框贴底面板
            View {
                attr {
                    width(pagerData.pageViewWidth)
                    paddingTop(28f)
                    paddingLeft(24f)
                    paddingRight(24f)
                    paddingBottom(24f + pagerData.safeAreaInsets.bottom)
                    flexDirectionColumn()
                    alignItemsCenter()
                    backgroundColor(colors().bgLayer1)
                    boxShadow(BoxShadow(0f, -4f, 20f, Color(0x14000000)))
                }
                Text {
                    attr {
                        text(if (cancelArmed()) strings().releaseToCancel else strings().slideUpToCancel)
                        fontSize(16f)
                        fontWeightMedium()
                        color(if (cancelArmed()) colors().stateErrorPrimary else colors().labelPrimary)
                    }
                }
                vif({ partialText().isNotEmpty() }) {
                    Text {
                        attr {
                            text(partialText())
                            marginTop(10f)
                            maxWidth(pagerData.pageViewWidth - 96f)
                            lines(2)
                            fontSize(14f)
                            color(colors().labelTertiary)
                        }
                    }
                }
                // 蓝色电平方块：高度随实时音量变化；revision 每次采样 +1 触发整体重绘
                vbind({ revision() to cancelArmed() }) {
                    View {
                        attr {
                            height(56f)
                            marginTop(18f)
                            flexDirectionRow()
                            alignItemsCenter()
                            justifyContentCenter()
                        }
                        val bars = levels()
                        for (index in 0 until DSH_VOICE_WAVE_BARS) {
                            val raw = bars.getOrNull(index) ?: 0f
                            View {
                                attr {
                                    width(3f)
                                    height(4f + raw.coerceIn(0f, 1f) * 40f)
                                    marginLeft(2f)
                                    borderRadius(1.5f)
                                    backgroundColor(
                                        if (cancelArmed()) colors().stateErrorPrimary else colors().stateBusinessPrimary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
