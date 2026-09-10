package com.example.dsh.conversation

import com.example.dsh.base.*
import com.example.dsh.chat.*
import com.example.dsh.connection.*
import com.example.dsh.conversation.*
import com.example.dsh.home.*
import com.example.dsh.infrastructure.*
import com.example.dsh.rendering.*
import com.example.dsh.storage.*
import com.example.dsh.web.*
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
internal enum class DshConnectionMode {
    LOCAL,
    RELAY,
    SSH,
}

internal data class DshSessionScope(
    val mode: DshConnectionMode,
    val profileId: String? = null,
) {
    val storageKey: String
        get() = when (mode) {
            DshConnectionMode.LOCAL -> LOCAL_STORAGE_KEY
            DshConnectionMode.RELAY -> "relay:${profileId ?: "default"}"
            DshConnectionMode.SSH -> "ssh:${profileId ?: DEFAULT_REMOTE_PROFILE_ID}"
        }

    companion object {
        const val DEFAULT_REMOTE_PROFILE_ID = "default"
        const val LOCAL_STORAGE_KEY = "local"
    }
}

internal data class DshRelayProfile(
    val hostId: String,
    val hostName: String,
    val relayOrigin: String,
    val pairedAt: Long,
)

internal data class DshRemoteProfile(
    val profileId: String = DshSessionScope.DEFAULT_REMOTE_PROFILE_ID,
    val host: String,
    val sshPort: Int,
    val username: String,
    val remoteDshPort: Int,
    val keyId: String,
    val hostFingerprint: String = "",
)

internal enum class DshSessionCacheState {
    SYNCED,
    STALE,
    SYNC_FAILED,
}

internal enum class DshHostRuntimePhase {
    DISCONNECTED,
    CONNECTING,
    HOST_HANDSHAKE,
    SYNCING,
    READY,
    RECONNECTING,
    ERROR,
    STOPPED,
}

internal data class DshHostRuntimeState(
    val phase: DshHostRuntimePhase,
    val generation: Long,
    val muxOpen: Boolean = false,
    val hostOpen: Boolean = false,
    val message: String = "",
)

internal enum class DshEventStream {
    MUX,
    HOST,
}

/** A raw downlink frame. Reducers must route mux frames by sessionId. */
internal data class DshDownlinkFrame(
    val generation: Long,
    val stream: DshEventStream,
    val raw: String,
)

internal data class DshRpcError(
    val code: String,
    val message: String,
    val details: String = "{}",
)

internal fun dshIsTransportInterrupt(code: String, message: String = ""): Boolean {
    if (code == "generation-cancelled" || code == "cancelled") return true
    if (code.startsWith("transport-")) return true
    return message.contains("世代已失效") || message.contains("连接已停止")
}

internal fun dshTurnStatusLabel(reconnecting: Boolean): String =
    if (reconnecting) "Reconnecting..." else "Deep diving..."

/** Matches DSH conversation `duration.seconds` / `duration.minutes`. */
internal fun dshFormatTurnDuration(elapsedMs: Long): String {
    val total = maxOf(0L, elapsedMs / 1000L)
    val minutes = total / 60L
    val seconds = total % 60L
    return if (minutes > 0) "${minutes}分${seconds.toString().padStart(2, '0')}秒" else "${total}秒"
}

/** Host 下发的图片接入限额（来自 `imageLimits` projection，Host 为最终裁决者）。 */
internal data class DshImageLimits(
    val maxImageBytes: Long,
    val maxImagesPerMessage: Int,
    val maxMessageImageBytes: Long,
    val maxImagePixels: Long,
    /** 单张图片最大边长（宽、高各自的上限，单位像素）。 */
    val maxImageDimension: Long,
    val mediaTypes: List<String>,
) {
    companion object {
        /** imageLimits projection 缺失时的保守默认，仅用于前置体验；Host 校验仍然权威。 */
        val DEFAULT = DshImageLimits(
            maxImageBytes = 20L * 1024 * 1024,
            maxImagesPerMessage = 20,
            maxMessageImageBytes = 200L * 1024 * 1024,
            maxImagePixels = 64_000_000L,
            maxImageDimension = 8192L,
            mediaTypes = listOf("image/png", "image/jpeg", "image/webp", "image/gif"),
        )

        fun fromJson(value: com.tencent.kuikly.core.nvi.serialization.json.JSONObject?): DshImageLimits? {
            if (value == null) return null
            val mediaTypes = buildList {
                val array = value.optJSONArray("mediaTypes") ?: return@buildList
                for (index in 0 until array.length()) {
                    array.optString(index)?.takeIf { it.isNotEmpty() }?.let(::add)
                }
            }
            if (mediaTypes.isEmpty()) return null
            return DshImageLimits(
                maxImageBytes = value.optLong("maxImageBytes", 0L).takeIf { it > 0L } ?: return null,
                maxImagesPerMessage = value.optInt("maxImagesPerMessage", 0).takeIf { it > 0 } ?: return null,
                maxMessageImageBytes = value.optLong("maxMessageImageBytes", 0L).takeIf { it > 0L } ?: return null,
                maxImagePixels = value.optLong("maxImagePixels", 0L).takeIf { it > 0L } ?: return null,
                maxImageDimension = value.optLong("maxImageDimension", 0L).takeIf { it > 0L } ?: return null,
                mediaTypes = mediaTypes,
            )
        }
    }
}

/** 输入区待发送图片的发送阶段。 */
internal enum class DshImageDraftState {
    /** 已选择并通过预检，等待随消息发送。 */
    SELECTED,
    /** 预检不通过（超限/类型不支持），显示原因，不进入发送。 */
    INVALID,
    /** 已随 session.prompt 发送，等待 Host 确认。 */
    UPLOADING,
    /** Host 已接收（消息事实包含 ImageAttachmentRef）。 */
    SENT,
    /** 发送失败，可删除或重试。 */
    FAILED,
}

/**
 * 发送前的图片草稿。仅存在于输入区生命周期内，绝不写入会话历史；
 * Host 落盘后历史只保留 [DshImageAttachmentRef] 形式的引用。
 */
internal data class DshPendingImage(
    val clientId: String,
    val mediaType: String,
    val name: String,
    /** 规范 Base64（发送给 Host 的 data 字段）。 */
    val dataBase64: String,
    /** 本地预览 dataUrl（UI 缩略图）。 */
    val previewDataUrl: String,
    val bytes: Long,
    val width: Int,
    val height: Int,
    val state: DshImageDraftState = DshImageDraftState.SELECTED,
    val error: String = "",
) {
    val isInvalid: Boolean get() = state == DshImageDraftState.INVALID
    val isUploading: Boolean get() = state == DshImageDraftState.UPLOADING
}

/** Host 历史中的图片引用（ImageAttachmentRef），不含 path/url/Base64。 */
internal data class DshImageAttachmentRef(
    val attachmentId: String,
    val mediaType: String,
    val bytes: Long,
    val width: Int,
    val height: Int,
    val name: String = "",
)

internal data class DshRawSessionEvent(
    val seq: Int,
    val type: String,
    val raw: String,
)

internal data class DshProjectionCell(
    val value: String,
    val seq: Int,
)

/** Host-authoritative control-plane state, partitioned by session id. */
internal class DshHostStore {
    val sessions = linkedMapOf<String, DshSession>()
    var workspaceBaseline: String = "{}"
        private set
    var archivedSessionIds: Set<String> = emptySet()
        private set
    val sessionEvents = linkedMapOf<String, MutableList<DshRawSessionEvent>>()
    val sessionLastSeq = linkedMapOf<String, Int>()
    val queueSnapshots = linkedMapOf<String, String>()
    val jobSnapshots = linkedMapOf<String, String>()
    val projections = linkedMapOf<String, MutableMap<String, DshProjectionCell>>()
    val pendingInteractions = linkedMapOf<String, String>()

    fun replaceWorkspaceBaseline(raw: String, archived: Set<String>) {
        workspaceBaseline = raw
        archivedSessionIds = archived
    }

    fun reorderWorkspaces(orderJson: String) {
        val order = runCatching { com.tencent.kuikly.core.nvi.serialization.json.JSONArray(orderJson) }
            .getOrNull() ?: return
        val orderedIds = buildList {
            for (index in 0 until order.length()) add(order.optString(index))
        }
        val current = runCatching {
            com.tencent.kuikly.core.nvi.serialization.json.JSONArray(workspaceBaseline)
        }.getOrNull() ?: return
        val byId = buildMap {
            for (index in 0 until current.length()) {
                val workspace = current.optJSONObject(index) ?: continue
                put(workspace.optString("workspaceId"), workspace)
            }
        }
        val reordered = orderedIds.mapNotNull { byId[it] }
        val remaining = (0 until current.length())
            .mapNotNull { index -> current.optJSONObject(index) }
            .filterNot { orderedIds.contains(it.optString("workspaceId")) }
        val result = com.tencent.kuikly.core.nvi.serialization.json.JSONArray()
        (reordered + remaining).forEach(result::put)
        workspaceBaseline = result.toString()
    }

    /** List baseline is authoritative for blank, while retaining local seq-newer projections. */
    fun replaceSessions(baseline: List<DshSession>) {
        val old = sessions.toMap()
        sessions.clear()
        baseline.forEach { next ->
            val previous = old[next.id]
            val titleProjection = projections[next.id]?.get("title")?.value?.trim()?.removeSurrounding("\"")
            sessions[next.id] = if (previous == null) next.copy(title = titleProjection ?: next.title) else next.copy(
                title = titleProjection ?: previous.title.takeUnless { it == "尚无标题" } ?: next.title,
                blank = next.blank,
                subscribedLastSeq = maxOf(previous.subscribedLastSeq, next.subscribedLastSeq),
            )
        }
    }

    /** Creation frames must never turn an existing list row back into blank. */
    fun applySessionAdded(session: DshSession): DshSession {
        val previous = sessions[session.id]
        val merged = if (previous == null) session else previous.copy(
            running = session.running || previous.running,
            cwd = session.cwd.ifEmpty { previous.cwd },
            parentSessionId = session.parentSessionId ?: previous.parentSessionId,
            origin = session.origin ?: previous.origin,
            agentPreset = session.agentPreset ?: previous.agentPreset,
            blank = previous.blank,
        )
        sessions[session.id] = merged
        return merged
    }

    fun applySubscribed(sessionId: String, lastSeq: Int) {
        sessionLastSeq[sessionId] = maxOf(sessionLastSeq[sessionId] ?: -1, lastSeq)
        sessions[sessionId]?.let { sessions[sessionId] = it.copy(subscribedLastSeq = maxOf(it.subscribedLastSeq, lastSeq)) }
    }

    fun applySessionEvent(sessionId: String, seq: Int, type: String, raw: String) {
        val events = sessionEvents.getOrPut(sessionId) { mutableListOf() }
        if (events.none { it.seq == seq }) {
            events += DshRawSessionEvent(seq, type, raw)
            events.sortBy { it.seq }
        }
        sessionLastSeq[sessionId] = maxOf(sessionLastSeq[sessionId] ?: -1, seq)
    }

    /** Queue/jobs are whole snapshots; later frames replace the whole value. */
    fun replaceQueue(sessionId: String, rawItems: String) { queueSnapshots[sessionId] = rawItems }
    fun replaceJobs(sessionId: String, rawJobs: String) { jobSnapshots[sessionId] = rawJobs }

    /** Projection updates use higher-seq-wins, including across reconnect baselines. */
    fun applyProjection(sessionId: String, key: String, value: String, seq: Int) {
        val cells = projections.getOrPut(sessionId) { mutableMapOf() }
        val previous = cells[key]
        if (previous == null || seq >= previous.seq) {
            cells[key] = DshProjectionCell(value, seq)
            if (key == "title") {
                val title = value.trim().removeSurrounding("\"")
                sessions[sessionId]?.let { sessions[sessionId] = it.copy(title = title) }
            }
        }
    }

    fun putPending(rpcId: String, raw: String) { pendingInteractions[rpcId] = raw }
    fun removePending(rpcId: String) { pendingInteractions.remove(rpcId) }
}

internal enum class DshRemoteFailure {
    KEY_MISSING,
    AUTH_FAILED,
    HOST_FINGERPRINT_REQUIRED,
    SSH_UNREACHABLE,
    SSH_PORT_IN_USE,
    DSH_UNAVAILABLE,
}

internal data class DshLegacyRemoteProfile(
    val mode: DshConnectionMode,
    val host: String,
    val sshPort: Int,
    val username: String,
    val remoteDshPort: Int,
    val keyId: String,
    val hostFingerprint: String = "",
)

/** The small client-side model used by the first DSH surface. */
internal data class DshSession(
    val id: String,
    val title: String,
    val workspace: String,
    val updatedLabel: String,
    /** Host 会话项的 updatedAt（毫秒时间戳），用于按消息时间排序会话列表。 */
    val updatedAt: Long = 0L,
    val running: Boolean = false,
    val blank: Boolean = false,
    val cwd: String = "",
    val parentSessionId: String? = null,
    val origin: String? = null,
    val agentPreset: String? = null,
    val permission: String? = null,
    val subscribedLastSeq: Int = -1,
)

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

internal data class DshTurnProcessSummary(
    val key: String,
    val label: String,
)

/** Returns a summary for the closed-turn process members before the final answer. */
internal fun dshTurnProcessSummary(messages: List<DshMessage>, message: DshMessage): DshTurnProcessSummary? {
    val lastUser = messages.indexOfLast { it.role == DshMessageRole.USER }
    if (lastUser < 0) return null
    val tail = dshTurnTailAssistant(messages) ?: return null
    val tailIndex = messages.indexOfFirst { it.id == tail.id }
    if (tailIndex <= lastUser) return null
    val members = messages.subList(lastUser + 1, tailIndex).filter {
        it.role == DshMessageRole.TOOL || it.isReasoning || it.isContextInjection ||
            (it.role == DshMessageRole.ASSISTANT && !it.isReasoning)
    }
    if (members.isEmpty() || members.none { it.id == message.id }) return null
    if (members.first().id != message.id) return DshTurnProcessSummary("", "")
    val toolCount = members.count { it.role == DshMessageRole.TOOL && !it.isContextInjection }
    val messageCount = members.count { it.role == DshMessageRole.ASSISTANT && !it.isReasoning }
    val labels = buildList {
        if (toolCount > 0) add("$toolCount 个工具调用")
        if (messageCount > 0) add("$messageCount 条消息")
        if (members.any { it.isReasoning }) add("思考")
        if (members.any { it.isContextInjection }) add("上下文")
    }
    return DshTurnProcessSummary("turn-process-${tail.id}", labels.joinToString(" · ").ifEmpty { "思考了一会儿" })
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
internal fun dshHistoryTailToResume(
    messages: List<DshMessage>,
    anchorAssistantId: String,
): DshMessage? {
    val live = dshAssistantTailForCurrentTurn(messages) ?: return null
    if (anchorAssistantId.isNotEmpty() && live.id == anchorAssistantId) return null
    return live
}

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

internal enum class DshToolCardType {
    GENERIC,
    TERMINAL,
    READ,
    DIFF,
    SEARCH,
    WEB,
    JSON,
}

internal data class DshJsonNode(
    val key: String,
    val label: String,
    val preview: String,
    val children: List<DshJsonNode> = emptyList(),
    val depth: Int = 0,
)

internal data class DshQueueItem(
    val id: String,
    val placement: String,
    val preview: String,
    val text: String?,
)

internal data class DshJobItem(
    val id: String,
    val kind: String,
    val label: String,
    val status: String,
    val detail: String,
    val startedAt: Long,
    val finishedAt: Long?,
)

internal data class DshWorkspaceGroup(
    val workspaceId: String,
    val title: String,
    val path: String,
    val sessions: List<DshSession>,
)

internal data class DshDirectoryEntry(
    val name: String,
    val path: String,
    val hidden: Boolean,
)

internal data class DshDirectoryListing(
    val path: String,
    val home: String,
    val crumbs: List<DshDirectoryEntry>,
    val entries: List<DshDirectoryEntry>,
    val truncated: Boolean,
)

internal data class DshPendingApproval(
    val rpcId: String,
    val sessionId: String,
    val approvalId: String,
    val toolName: String,
    val callId: String?,
    val reason: String?,
    val command: String? = null,
)

internal data class DshPendingQuestionOption(
    val label: String,
    val description: String,
)

internal data class DshPendingQuestionItem(
    val id: String,
    val question: String,
    val header: String,
    val detail: String,
    val options: List<DshPendingQuestionOption>,
    val multiSelect: Boolean,
)

internal data class DshPendingQuestion(
    val rpcId: String,
    val sessionId: String,
    val questions: List<DshPendingQuestionItem>,
)

internal data class DshQuestionDraft(
    val selected: List<String> = emptyList(),
    val custom: String = "",
    val skipped: Boolean = false,
)

internal fun DshMessage.isRuntimeContextSnapshot(): Boolean {
    return role == DshMessageRole.USER &&
        content.startsWith("Current runtime context. This snapshot supersedes earlier runtime-context snapshots.")
}

internal data class DshCredentialSetup(
    val providerAvailable: Boolean,
    val configured: Boolean,
    val writable: Boolean,
    val credentialRef: String = "DEEPSEEK_API_KEY",
)

internal data class DshModelOption(
    val provider: String,
    val providerName: String,
    val model: String,
    val name: String,
    val description: String = "",
    val reasoningEffort: String? = null,
    val reasoningEfforts: List<DshReasoningEffort> = emptyList(),
    val selected: Boolean = false,
)

// 单个模型的推理等级选项（id 用于提交，name 用于展示）
internal data class DshReasoningEffort(
    val id: String,
    val name: String,
    val description: String = "",
)

internal data class DshSkill(
    val name: String,
    val description: String,
    val whenToUse: String = "",
    val modelInvocable: Boolean = true,
)

internal data class DshGoalSnapshot(
    val id: String,
    val revision: Int,
    val objective: String,
    val phase: String,
    val blockedReason: String = "",
)

internal data class DshSessionModels(
    val current: DshModelOption,
    val options: List<DshModelOption>,
    val routable: Boolean,
)

internal interface DshStreamHandle {
    fun cancel()
}

internal interface DshRepository {
    fun loadCredentialSetup(
        onSuccess: (DshCredentialSetup) -> Unit,
        onError: (String) -> Unit,
    )

    fun saveDeepSeekApiKey(
        apiKey: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    )

    fun loadAgentPresets(
        onSuccess: (List<DshAgentPresetOption>) -> Unit,
        onError: (String) -> Unit,
    )

    fun loadHostVersion(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    )

    fun describeSettings(
        onSuccess: (DshSettingsSnapshot) -> Unit,
        onError: (String) -> Unit,
    )

    fun updateSetting(
        ns: String,
        patch: JSONObject,
        expectedRevision: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    )

    fun loadModels(
        sessionId: String,
        onSuccess: (DshSessionModels) -> Unit,
        onError: (String) -> Unit,
    )

    fun selectModel(
        sessionId: String,
        option: DshModelOption,
        onSuccess: (DshModelOption) -> Unit,
        onError: (String) -> Unit,
    )

    fun loadSessions(
        onSuccess: (List<DshSession>) -> Unit,
        onError: (String) -> Unit,
    )

    fun createSession(
        workspaceId: String?,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
        permission: String? = null,
        agentPreset: String? = null,
    )

    fun loadHistory(
        sessionId: String,
        onSuccess: (List<DshMessage>) -> Unit,
        onError: (String) -> Unit,
    )
    fun streamReply(
        pagerId: String,
        sessionId: String,
        prompt: String,
        onDelta: (String, Boolean) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ): DshStreamHandle
}

internal data class DshAgentPresetOption(
    val id: String,
    val name: String,
    val description: String = "",
    val isDefault: Boolean = false,
)

internal data class DshSettingsChoice(
    val value: String,
    val label: String = "",
    val description: String = "",
)

internal data class DshSettingsSnapshot(
    val writable: Boolean = false,
    val permissionPreset: String = "",
    val permissionChoices: List<DshSettingsChoice> = emptyList(),
    val permissionRevision: Int = 0,
    val localeValue: String = "",
    val localeRevision: Int = 0,
    val themeValue: String = "",
    val themeRevision: Int = 0,
    val defaultModelProvider: String = "",
    val defaultModelLabel: String = "",
    val defaultModelRevision: Int = 0,
)
