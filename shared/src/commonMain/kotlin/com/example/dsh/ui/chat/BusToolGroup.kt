package com.example.dsh.ui.chat

import com.example.dsh.message.DshMessage
import com.example.dsh.message.DshMessageRole

/**
 * Bus 对话页的一行渲染单元：普通消息逐条渲染，连续 tool 消息合并为一组。
 *
 * 展开状态不进入数据模型，由页面按 [key] 单独维护，这样列表刷新（轮询）
 * 时折叠状态不会丢。
 */
internal sealed class BusChatRow {
    abstract val key: String

    data class Message(val message: DshMessage) : BusChatRow() {
        override val key: String = "message-${message.id}"
    }

    data class ToolGroup(
        override val key: String,
        val tools: List<DshMessage>,
    ) : BusChatRow()
}

/**
 * 把消息列表切成渲染行：**连续两条以上**的 tool 消息合并成一组。
 *
 * 单条 tool 保持普通一行摘要，不折叠；只有成串的工具调用才值得收起。
 * 组 key 绑定首条 tool 的 id：后续工具继续到达时，已展开的组不会被重置。
 */
internal fun busChatRows(messages: List<DshMessage>): List<BusChatRow> {
    val rows = ArrayList<BusChatRow>(messages.size)
    var index = 0
    while (index < messages.size) {
        val message = messages[index]
        if (message.role != DshMessageRole.TOOL) {
            rows += BusChatRow.Message(message)
            index += 1
            continue
        }
        var end = index + 1
        while (end < messages.size && messages[end].role == DshMessageRole.TOOL) {
            end += 1
        }
        val run = messages.subList(index, end).toList()
        if (run.size == 1) {
            rows += BusChatRow.Message(message)
        } else {
            rows += BusChatRow.ToolGroup(
                key = "tool-group-${message.id}",
                tools = run,
            )
        }
        index = end
    }
    return rows
}

/** 工具组折叠头文案；页面与测试共用，避免两处口径不一致。 */
internal fun busToolGroupLabel(count: Int): String = "$count 次工具调用"

/** 切换一个工具组的展开状态；返回新集合，便于响应式状态整体替换。 */
internal fun toggleBusToolGroup(expanded: Set<String>, key: String): Set<String> =
    if (key in expanded) expanded - key else expanded + key
