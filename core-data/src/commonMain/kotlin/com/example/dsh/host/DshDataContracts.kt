package com.example.dsh.host

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.example.dsh.models.DshAgentPresetOption
import com.example.dsh.models.DshCredentialSetup
import com.example.dsh.message.DshMessage
import com.example.dsh.models.DshModelOption
import com.example.dsh.models.DshModelsSettings
import com.example.dsh.plugin.DshPluginConfigSave
import com.example.dsh.plugin.DshPluginConfigState
import com.example.dsh.session.DshSession
import com.example.dsh.session.DshSessionModels
import com.example.dsh.models.DshSettingsSnapshot

data class DshRawSessionEvent(
    val seq: Int,
    val type: String,
    val raw: String,
)

data class DshProjectionCell(
    val value: String,
    val seq: Int,
)

/** Host-authoritative control-plane state, partitioned by session id. */

/** Host-authoritative control-plane state, partitioned by session id. */
class DshHostStore {
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

    /** Null ids means the ungrouped workspace; explicit ids preserve Host workspace order. */
    fun unarchivedBlankSession(sessionIds: List<String>? = null): DshSession? {
        val candidates = sessionIds?.mapNotNull { sessions[it] }
            ?: sessions.values.filter { it.cwd.isEmpty() }
        return candidates.firstOrNull { it.blank && it.id !in archivedSessionIds }
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

interface DshStreamHandle {
    fun cancel()
}

interface DshRepository {
    fun loadCredentialSetup(
        onSuccess: (DshCredentialSetup) -> Unit,
        onError: (String) -> Unit,
    )

    fun saveDeepSeekApiKey(
        apiKey: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    )

    /** 设置页「模型」板块：提供方目录 × 配置 × 密钥状态联接。 */
    fun loadModelsSettings(
        onSuccess: (DshModelsSettings) -> Unit,
        onError: (String) -> Unit,
    )

    /** settings.mutate：按路径 op 写入一个 namespace 的用户层。 */
    fun mutateSetting(
        ns: String,
        ops: JSONArray,
        expectedRevision: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    )

    /** credentials.set：写入一个凭据引用（只写）。 */
    fun setCredential(
        ref: String,
        value: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    )

    /** credentials.unset：移除可写层中的一个凭据引用。 */
    fun unsetCredential(
        ref: String,
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

    /** 插件配置：settings.describe 中 shell / agent-loop / web-search-deepseek 三个 namespace。 */
    fun loadPluginConfig(
        onSuccess: (DshPluginConfigState) -> Unit,
        onError: (String) -> Unit,
    ) {
        onError("当前连接不支持插件配置")
    }

    /** 保存一个插件配置卡：字段走 settings.mutate，密钥走 credentials.set。 */
    fun savePluginConfig(
        save: DshPluginConfigSave,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        onError("当前连接不支持插件配置")
    }

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
