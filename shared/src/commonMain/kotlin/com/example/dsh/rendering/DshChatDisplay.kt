package com.example.dsh.rendering

import com.example.dsh.conversation.DshAskQuestionCard
import com.example.dsh.conversation.DshSearchCard
import com.example.dsh.conversation.DshWebCard

/**
 * 移动端「对话过程展示」的两种互斥方式（App 级偏好，本地持久化）：
 * - [UNIFIED]：对齐最新 dsh，把一轮中的思考 / 工具调用 / 过程消息折叠为一条摘要。
 * - [CLASSIC]：对齐电脑端 rc，思考与工具调用逐条展开，不做外层统一折叠。
 */
internal enum class DshProcessDisplayMode {
    UNIFIED,
    CLASSIC,
}

internal const val DSH_PREF_PROCESS_DISPLAY = "chat_process_display"
internal const val DSH_PREF_EXPAND_MODAL = "chat_expand_in_modal"
internal const val DSH_PREF_SHOW_CONNECTORS = "chat_show_connectors"
internal const val DSH_PREF_SHOW_RESULT_CARDS = "chat_show_result_cards"

internal fun dshProcessDisplayLabel(mode: DshProcessDisplayMode): String = when (mode) {
    DshProcessDisplayMode.UNIFIED -> "统一折叠"
    DshProcessDisplayMode.CLASSIC -> "经典"
}

internal fun dshProcessDisplayValue(mode: DshProcessDisplayMode): String = when (mode) {
    DshProcessDisplayMode.UNIFIED -> "unified"
    DshProcessDisplayMode.CLASSIC -> "classic"
}

internal fun dshProcessDisplayFromValue(value: String): DshProcessDisplayMode =
    if (value == "classic") DshProcessDisplayMode.CLASSIC else DshProcessDisplayMode.UNIFIED

/**
 * 展开内容弹窗的载荷：与 [DshDisclosureRowAttr] 的 body 字段一一对应，
 * 让「弹窗查看」直接复用同一套明细渲染，只是去掉灰底容器直接平铺。
 */
internal data class DshExpandedPayload(
    val title: String,
    val iconAsset: String,
    val body: String,
    val plainBody: Boolean,
    val error: Boolean,
    val jsonContent: String,
    val contextDetail: DshContextDetail?,
    val toolDetail: DshToolDetail?,
    val askCard: DshAskQuestionCard?,
    val webCard: DshWebCard? = null,
    val searchCard: DshSearchCard? = null,
)
