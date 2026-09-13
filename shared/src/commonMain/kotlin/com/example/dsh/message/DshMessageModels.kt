package com.example.dsh.message

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.example.dsh.message.contextInstructions
import com.example.dsh.message.contextRecalls
import com.example.dsh.message.contextRelaySender
import com.example.dsh.message.contextSections
import com.example.dsh.tool.DshRemoteToolCallModel
import com.example.dsh.models.DshToolCardType
import com.example.dsh.host.toolCardType

internal fun dshTurnStatusLabel(reconnecting: Boolean): String =
    if (reconnecting) "Reconnecting..." else "Deep diving..."

/** Matches DSH conversation `duration.seconds` / `duration.minutes`. */

/** Matches DSH conversation `duration.seconds` / `duration.minutes`. */
internal fun dshFormatTurnDuration(elapsedMs: Long): String {
    val total = maxOf(0L, elapsedMs / 1000L)
    val minutes = total / 60L
    val seconds = total % 60L
    return if (minutes > 0) "${minutes}分${seconds.toString().padStart(2, '0')}秒" else "${total}秒"
}

/** Host 下发的图片接入限额（来自 `imageLimits` projection，Host 为最终裁决者）。 */

internal enum class DshMessageRole {
    USER,
    ASSISTANT,
    TOOL,
    ERROR,
}

internal data class DshMessage(
    val id: String,
    val role: DshMessageRole,
    val content: String,
    val streaming: Boolean = false,
    val toolName: String? = null,
    val hidden: Boolean = false,
    val toolCardType: DshToolCardType = DshToolCardType.GENERIC,
    val toolRunning: Boolean = false,
    val toolError: Boolean = false,
    /** 工具调用被中断（stopped 态），与原版一致：非错误，用琥珀色提示。 */
    val toolStopped: Boolean = false,
    val isContextInjection: Boolean = false,
    val contextForm: String = "",
    val contextBody: String = "",
    val contextCatalog: List<DshContextCatalogEntry> = emptyList(),
    val contextSections: List<DshContextSection> = emptyList(),
    val contextRecalls: List<DshContextRecall> = emptyList(),
    val contextInstructions: List<DshContextInstruction> = emptyList(),
    val contextRelaySender: String = "",
    val isReasoning: Boolean = false,
    val attachmentId: String? = null,
    val attachmentIds: List<String> = emptyList(),
    val toolCallId: String = "",
    /**
     * 用户消息随文发送的图片本地预览（dataUrl）。
     * 仅存在于内存，不进入本地持久化（extra_json 未编码）；历史恢复走 Host timeline
     * 的 attachmentId，见 [DshImageAttachmentRef]。
     */
    val imagePreviews: List<String> = emptyList(),
    /** Remote-only structured tool state; LOCAL keeps this null. */
    val remoteTool: DshRemoteToolCallModel? = null,
    /** Ordered readable source including attachment metadata, never preview/Base64 data. */
    val readableContent: String? = null,
    /** Host event anchor, independent of the UI row id (live ids use local counters). */
    val sourceSeq: Int? = null,
)

internal fun dshIsLiveAssistantText(message: DshMessage): Boolean =
    message.role == DshMessageRole.ASSISTANT &&
        !message.isReasoning &&
        message.attachmentId == null

/**
 * What the assistant bubble should paint.
 *
 * The list row is only a placeholder until settle writes the finished string.
 * While this row is the live target, [live] is the source of truth — never a
 * shorter first-flush snapshot in [stored].
 */

/**
 * What the assistant bubble should paint.
 *
 * The list row is only a placeholder until settle writes the finished string.
 * While this row is the live target, [live] is the source of truth — never a
 * shorter first-flush snapshot in [stored].
 */
internal fun dshDisplayedAssistantContent(
    stored: String,
    live: String,
    isLiveRow: Boolean,
): String {
    if (isLiveRow && live.isNotEmpty()) {
        return if (live.length >= stored.length) live else stored
    }
    if (stored.length >= live.length) return stored.ifEmpty { live }
    return live
}

/**
 * The last settled assistant text of the current turn (after the latest user
 * message). Unlike [dshAssistantTailForCurrentTurn], this is the turn-tail
 * semantic used by footer rendering: it excludes streaming/reasoning/context
 * rows, and only one message per turn can match, so intermediate assistant
 * text that precedes a tool call never becomes a footer tail.
 */

/**
 * The last settled assistant text of the current turn (after the latest user
 * message). Unlike [dshAssistantTailForCurrentTurn], this is the turn-tail
 * semantic used by footer rendering: it excludes streaming/reasoning/context
 * rows, and only one message per turn can match, so intermediate assistant
 * text that precedes a tool call never becomes a footer tail.
 */
internal fun dshTurnTailAssistant(messages: List<DshMessage>): DshMessage? {
    val lastUserIndex = messages.indexOfLast { it.role == DshMessageRole.USER }
    return messages.withIndex().lastOrNull { (index, it) ->
        index > lastUserIndex &&
            it.role == DshMessageRole.ASSISTANT &&
            !it.streaming &&
            !it.isReasoning &&
            !it.isContextInjection
    }?.value
}

/**
 * 分享多选的可选项：真实用户发言 + 每个回合的最终助手正文。
 *
 * 排除隐藏项、上下文注入、思考（reasoning）与工具卡片；未结算的流式助手也排除。
 * 中间过程性助手正文（后面还有同一回合的助手正文）不作为「最终回复」。
 */

/**
 * 分享多选的可选项：真实用户发言 + 每个回合的最终助手正文。
 *
 * 排除隐藏项、上下文注入、思考（reasoning）与工具卡片；未结算的流式助手也排除。
 * 中间过程性助手正文（后面还有同一回合的助手正文）不作为「最终回复」。
 */
internal fun dshShareSelectableIds(messages: List<DshMessage>): Set<String> {
    val result = LinkedHashSet<String>()
    messages.forEachIndexed { index, message ->
        if (message.hidden || message.isContextInjection || message.isReasoning) return@forEachIndexed
        when (message.role) {
            DshMessageRole.USER -> result.add(message.id)
            DshMessageRole.ASSISTANT -> {
                if (message.streaming) return@forEachIndexed
                val nextUser = (index + 1 until messages.size)
                    .firstOrNull { messages[it].role == DshMessageRole.USER } ?: messages.size
                val hasLaterAssistant = (index + 1 until nextUser).any { i ->
                    val next = messages[i]
                    next.role == DshMessageRole.ASSISTANT && !next.isReasoning &&
                        !next.isContextInjection && !next.hidden
                }
                if (!hasLaterAssistant) result.add(message.id)
            }
            else -> Unit
        }
    }
    return result
}

/**
 * 一条可分享的「对话组」：触发该轮的用户 Prompt 与最终助手回复，以及两者之间的完整过程。
 *
 * [startIndex]/[endIndex] 为该回合在消息列表中的闭区间，用于按原始顺序导出正文、
 * 工具结果与卡片；[key] 以用户消息（无 Prompt 时用助手消息）的 id 生成。
 */

/**
 * 一条可分享的「对话组」：触发该轮的用户 Prompt 与最终助手回复，以及两者之间的完整过程。
 *
 * [startIndex]/[endIndex] 为该回合在消息列表中的闭区间，用于按原始顺序导出正文、
 * 工具结果与卡片；[key] 以用户消息（无 Prompt 时用助手消息）的 id 生成。
 */
internal data class DshShareGroup(
    val key: String,
    val startIndex: Int,
    val endIndex: Int,
    val userMessageId: String,
    val assistantMessageId: String?,
) {
    /** 组内可勾选的消息：用户 Prompt 与该轮最终助手回复（缺一可）。 */
    val selectableIds: List<String>
        get() = listOfNotNull(userMessageId.ifEmpty { null }, assistantMessageId)
}

/**
 * 按「用户 Prompt + 该轮最终助手回复」划分可分享对话组。
 *
 * 与 [dshShareSelectableIds] 保持一致：隐藏项、上下文注入与思考不参与分组；未结算的
 * 流式助手只挂到当前组，不单独成组；同一回合出现多段助手正文时，以最后一段作为最终回复。
 */

/**
 * 按「用户 Prompt + 该轮最终助手回复」划分可分享对话组。
 *
 * 与 [dshShareSelectableIds] 保持一致：隐藏项、上下文注入与思考不参与分组；未结算的
 * 流式助手只挂到当前组，不单独成组；同一回合出现多段助手正文时，以最后一段作为最终回复。
 */
internal fun dshShareGroups(messages: List<DshMessage>): List<DshShareGroup> {
    val groups = mutableListOf<DshShareGroup>()
    var startIndex = -1
    var userId = ""
    var lastAssistantId: String? = null
    var lastAssistantStreaming = false
    fun flush(endIndex: Int) {
        if (startIndex < 0) return
        val key = if (userId.isNotEmpty()) "u:$userId" else "a:$lastAssistantId"
        // 只有该回合最后一段助手正文且已结算时才作为最终回复；流式未结算不加入可选项。
        val assistantId = if (!lastAssistantStreaming) lastAssistantId else null
        groups.add(DshShareGroup(key, startIndex, endIndex, userId, assistantId))
        startIndex = -1
        userId = ""
        lastAssistantId = null
        lastAssistantStreaming = false
    }
    messages.forEachIndexed { index, message ->
        if (message.hidden || message.isContextInjection || message.isReasoning) return@forEachIndexed
        when (message.role) {
            DshMessageRole.USER -> {
                flush(index - 1)
                startIndex = index
                userId = message.id
            }
            DshMessageRole.ASSISTANT -> {
                if (startIndex < 0) startIndex = index
                lastAssistantId = message.id
                lastAssistantStreaming = message.streaming
            }
            else -> Unit
        }
    }
    flush(messages.lastIndex)
    return groups
}

/** 某个消息所属的对话组；用户 Prompt 或其最终助手回复都能定位到同一组。 */

/** 某个消息所属的对话组；用户 Prompt 或其最终助手回复都能定位到同一组。 */
internal fun dshShareGroupForMessage(messages: List<DshMessage>, messageId: String): DshShareGroup? =
    dshShareGroups(messages).firstOrNull { messageId in it.selectableIds }

/**
 * 一个已结算回合的「中间过程」分组：最终回答之前的思考、工具调用、上下文注入与
 * 过渡正文。默认折叠为一条摘要，点击后展开逐条明细（对齐 Codex 的过程折叠）。
 *
 * [isFirst] 标记当前消息是否为该过程块的首条；只有首条渲染摘要头，其余成员在折叠态隐藏。
 */

/**
 * 一个已结算回合的「中间过程」分组：最终回答之前的思考、工具调用、上下文注入与
 * 过渡正文。默认折叠为一条摘要，点击后展开逐条明细（对齐 Codex 的过程折叠）。
 *
 * [isFirst] 标记当前消息是否为该过程块的首条；只有首条渲染摘要头，其余成员在折叠态隐藏。
 */
internal data class DshTurnProcessGroup(
    val key: String,
    val label: String,
    val members: List<DshMessage>,
    val isFirst: Boolean,
)

/**
 * 解析 [message] 所属回合的过程分组；仅对最终回答之前的中间过程成员返回非空。
 *
 * 回合边界按前后最近的 USER 消息划分；仅当该回合已有已结算的非推理助手回答（tail）时
 * 才分组，因此流式进行中的回合保持逐步可见。key 固定绑定 tail.id，展开状态跨重渲染稳定。
 */

/**
 * 解析 [message] 所属回合的过程分组；仅对最终回答之前的中间过程成员返回非空。
 *
 * 回合边界按前后最近的 USER 消息划分；仅当该回合已有已结算的非推理助手回答（tail）时
 * 才分组，因此流式进行中的回合保持逐步可见。key 固定绑定 tail.id，展开状态跨重渲染稳定。
 */
internal fun dshTurnProcessGroup(messages: List<DshMessage>, message: DshMessage): DshTurnProcessGroup? {
    val index = messages.indexOfFirst { it.id == message.id }
    if (index < 0) return null
    val lastUser = (index - 1 downTo 0).firstOrNull { messages[it].role == DshMessageRole.USER } ?: return null
    val nextUser = (index + 1 until messages.size).firstOrNull { messages[it].role == DshMessageRole.USER } ?: messages.size
    val tailIndex = (nextUser - 1 downTo lastUser + 1).firstOrNull {
        val candidate = messages[it]
        candidate.role == DshMessageRole.ASSISTANT && !candidate.streaming &&
            !candidate.isReasoning && !candidate.isContextInjection
    } ?: return null
    if (index >= tailIndex) return null
    val processIndices = (lastUser + 1 until tailIndex).filter { i ->
        val candidate = messages[i]
        !candidate.hidden && (
            candidate.role == DshMessageRole.TOOL || candidate.isReasoning ||
                candidate.isContextInjection ||
                (candidate.role == DshMessageRole.ASSISTANT && !candidate.isReasoning)
            )
    }
    if (index !in processIndices) return null
    val members = processIndices.map { messages[it] }
    val toolCount = members.count { it.role == DshMessageRole.TOOL && !it.isContextInjection }
    val messageCount = members.count { it.role == DshMessageRole.ASSISTANT && !it.isReasoning }
    val labels = buildList {
        if (members.any { it.isReasoning }) add("思考")
        if (toolCount > 0) add("$toolCount 个工具调用")
        if (messageCount > 0) add("$messageCount 条过程消息")
        if (members.any { it.isContextInjection }) add("上下文")
    }
    return DshTurnProcessGroup(
        key = "turn-process-${messages[tailIndex].id}",
        label = labels.joinToString(" · ").ifEmpty { "处理过程" },
        members = members,
        isFirst = processIndices.first() == index,
    )
}

/**
 * 以 [anchorId] 为锚点聚合整个回合的完整助手正文。
 *
 * 回合边界：锚点前最后一个 `USER` 消息之后、锚点后第一个 `USER` 消息之前。
 * 期间所有助手正文段（排除推理/上下文注入/附件卡片）按顺序以空行拼接，
 * 因此即使正文被工具调用切分成多段，复制结果也是完整的。
 *
 * [contentFor] 可覆盖某条消息的取值（例如流式中的实时内容）。
 */

/**
 * 以 [anchorId] 为锚点聚合整个回合的完整助手正文。
 *
 * 回合边界：锚点前最后一个 `USER` 消息之后、锚点后第一个 `USER` 消息之前。
 * 期间所有助手正文段（排除推理/上下文注入/附件卡片）按顺序以空行拼接，
 * 因此即使正文被工具调用切分成多段，复制结果也是完整的。
 *
 * [contentFor] 可覆盖某条消息的取值（例如流式中的实时内容）。
 */
internal fun dshTurnBodyText(
    messages: List<DshMessage>,
    anchorId: String,
    contentFor: (DshMessage) -> String = { it.content },
): String {
    val anchorIndex = messages.indexOfFirst { it.id == anchorId }
    if (anchorIndex < 0) return ""
    val lastUserIndex = messages.take(anchorIndex).indexOfLast { it.role == DshMessageRole.USER }
    val nextUserIndex = messages.withIndex()
        .firstOrNull { it.index > anchorIndex && it.value.role == DshMessageRole.USER }
        ?.index ?: messages.size
    val parts = messages.subList(lastUserIndex + 1, nextUserIndex)
        .filter {
            it.role == DshMessageRole.ASSISTANT &&
                !it.isReasoning &&
                !it.isContextInjection &&
                it.attachmentId == null
        }
        .map(contentFor)
        .filter { it.isNotEmpty() }
    return parts.joinToString("\n\n")
}

/**
 * Assistant text that belongs to the in-progress turn sits after the latest
 * user message. A completed previous reply is before that user and must not
 * be reused as the live streaming target.
 */

/**
 * Assistant text that belongs to the in-progress turn sits after the latest
 * user message. A completed previous reply is before that user and must not
 * be reused as the live streaming target.
 */
internal fun dshAssistantTailForCurrentTurn(messages: List<DshMessage>): DshMessage? {
    val lastUserIndex = messages.indexOfLast { it.role == DshMessageRole.USER }
    return messages.withIndex().lastOrNull { (index, message) ->
        index > lastUserIndex && dshIsLiveAssistantText(message)
    }?.value
}

/**
 * History resync after sending a new prompt can still contain the previous
 * assistant. [anchorAssistantId] is that previous reply; never resume into it.
 */

/**
 * History resync after sending a new prompt can still contain the previous
 * assistant. [anchorAssistantId] is that previous reply; never resume into it.
 */
internal fun dshHistoryTailToResume(
    messages: List<DshMessage>,
    anchorAssistantId: String,
): DshMessage? {
    val live = dshAssistantTailForCurrentTurn(messages) ?: return null
    if (anchorAssistantId.isNotEmpty() && live.id == anchorAssistantId) return null
    return live
}

/** Ignores id/streaming so a host key remap does not look like new content. */

/** Ignores id/streaming so a host key remap does not look like new content. */
internal fun DshMessage.visuallyEquals(other: DshMessage): Boolean =
    role == other.role &&
        content == other.content &&
        toolName == other.toolName &&
        hidden == other.hidden &&
        toolCardType == other.toolCardType &&
        toolRunning == other.toolRunning &&
        toolError == other.toolError &&
        toolStopped == other.toolStopped &&
        isContextInjection == other.isContextInjection &&
        contextForm == other.contextForm &&
        contextBody == other.contextBody &&
        isReasoning == other.isReasoning &&
        attachmentId == other.attachmentId &&
        attachmentIds == other.attachmentIds &&
        imagePreviews == other.imagePreviews &&
        toolCallId == other.toolCallId &&
        remoteTool == other.remoteTool

internal fun dshMessagesVisuallyEqual(left: List<DshMessage>, right: List<DshMessage>): Boolean {
    if (left.size != right.size) return false
    return left.indices.all { left[it].visuallyEquals(right[it]) }
}

internal data class DshContextCatalogEntry(
    val name: String,
    val description: String,
)

internal data class DshContextSection(
    val title: String,
    val body: String,
)

internal data class DshContextRecall(
    val label: String,
    val retainedMessages: Int,
    val omittedMessages: Int,
    val truncated: Boolean,
)

internal data class DshContextInstruction(
    val path: String,
    val action: String,
)

internal data class DshWebTimelineItem(
    val key: String,
    val kind: Kind,
    val text: String = "",
    val sourceLabel: String = "",
    val toolName: String? = null,
    val input: String? = null,
    val output: String? = null,
    val error: String? = null,
    val running: Boolean = false,
    val stopped: Boolean = false,
    val callId: String = "",
    val callSeq: Int = -1,
    val cardType: DshToolCardType = DshToolCardType.GENERIC,
    val cardTitle: String = "",
    val cardBody: String = "",
    val attachmentId: String? = null,
    val attachmentIds: List<String> = emptyList(),
    val imagePreviews: List<String> = emptyList(),
    val source: com.tencent.kuikly.core.nvi.serialization.json.JSONObject? = null,
    val remoteTool: DshRemoteToolCallModel? = null,
    val readableContent: String? = null,
    val sourceSeq: Int? = null,
) {
    enum class Kind {
        USER,
        ASSISTANT,
        REASONING,
        IMAGE,
        UNKNOWN_BLOCK,
        CONTEXT,
        TOOL,
        ERROR,
    }
}

internal fun DshMessage.isRuntimeContextSnapshot(): Boolean {
    return role == DshMessageRole.USER &&
        content.startsWith("Current runtime context. This snapshot supersedes earlier runtime-context snapshots.")
}
