package com.example.dsh.home

import com.example.dsh.base.*
import com.example.dsh.chat.*
import com.example.dsh.connection.*
import com.example.dsh.conversation.*
import com.example.dsh.home.*
import com.example.dsh.infrastructure.*
import com.example.dsh.rendering.*
import com.example.dsh.storage.*
import com.example.dsh.web.*
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.View

internal fun syncVisibleSessions(
    source: ObservableList<DshSession>,
    dest: ObservableList<DshSession>,
) {
    val next = source.toList().filterNot { it.blank }
    dest.diffUpdate(next) { old, new -> old.id == new.id }
    val count = minOf(dest.size, next.size)
    for (index in 0 until count) {
        if (dest[index] != next[index]) {
            dest[index] = next[index]
        }
    }
}

internal fun ViewContainer<*, *>.DshHitButton(onClick: () -> Unit) {
    View {
        attr {
            absolutePositionAllZero()
            backgroundColor(Color(0x00000000))
        }
        event { click { onClick() } }
    }
}

internal fun isConnectionReadyLabel(label: String): Boolean {
    return label.startsWith("已连接") ||
        label.endsWith("已连接") ||
        label.endsWith("已就绪") ||
        label == "连接成功"
}

internal fun isReconnectLabel(label: String): Boolean {
    return label == "远程连接重建中" ||
        label == "扫码连接重建中" ||
        label == "扫码连接重试中" ||
        label == "本地 DSH 连接重建中"
}

internal fun topBarConnectingText(label: String): String {
    val value = label.trim()
    if (value.isEmpty()) return "连接中"
    return value
}

/** 连接状态语义分类，用于胶囊指示点的颜色决策 */
internal enum class DshConnectionTone { READY, BUSY, ERROR, WARN, INFO }

internal fun connectionStatusTone(label: String): DshConnectionTone {
    if (isConnectionReadyLabel(label)) return DshConnectionTone.READY
    return when {
        label.contains("失败") || label.contains("未接受") || label.contains("已失效") || label.contains("尚未就绪") ->
            DshConnectionTone.ERROR
        label.contains("断开") || label.contains("已停止") || label.contains("等待远程连接") ->
            DshConnectionTone.WARN
        label.contains("重连") || label.contains("重试") || label.contains("重建") ||
            label.contains("正在") || label.contains("启动") || label.contains("同步") ||
            label.contains("生成") || label.contains("聆听") || label.contains("创建会话") ||
            label.contains("尚未连接") || label.contains("暂不能发送") ->
            DshConnectionTone.BUSY
        else -> DshConnectionTone.INFO
    }
}

internal fun DshConnectionTone.dotColor(): Int = when (this) {
    DshConnectionTone.READY -> 0xFF1F8A4C.toInt()
    DshConnectionTone.BUSY -> 0xFFD97706.toInt()
    DshConnectionTone.ERROR -> 0xFFE05252.toInt()
    DshConnectionTone.WARN -> 0xFFED8936.toInt()
    DshConnectionTone.INFO -> 0xFF6B7785.toInt()
}

internal const val COMPOSER_HEIGHT = 158f
internal const val CHAT_INITIAL_RENDER_COUNT = 48
internal const val CHAT_MAX_RENDERED_MESSAGES = 128
