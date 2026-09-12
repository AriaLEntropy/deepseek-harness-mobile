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
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * Remote-only Host repository. Local mode intentionally keeps the legacy
 * repository and its transport behavior unchanged.
 */
internal class DshRemoteRepository(
    network: NetworkModule,
    webSocket: DshWebSocketModule,
    connection: DshHostConnection,
    pagerId: String,
    onState: (DshHostRuntimeState) -> Unit = {},
    onQueueSnapshot: (String) -> Unit = {},
    onJobsSnapshot: (String) -> Unit = {},
    onSessionStatus: (String, Boolean) -> Unit = { _, _ -> },
    onProjection: (String, String, String, Int) -> Unit = { _, _, _, _ -> },
    onSessionEvent: (String, DshRawSessionEvent) -> Unit = { _, _ -> },
    onRemoteEvent: (String) -> Unit = {},
    onPendingInteraction: (String) -> Unit = {},
) : DshRepository {
    private val delegate = DshRemoteHostRepository(
        network,
        webSocket,
        connection,
        pagerId,
        onState,
        onQueueSnapshot = onQueueSnapshot,
        onJobsSnapshot = onJobsSnapshot,
        onSessionStatus = onSessionStatus,
        onProjection = onProjection,
        onSessionEvent = onSessionEvent,
        onRemoteEvent = onRemoteEvent,
        onPendingInteraction = onPendingInteraction,
    )
    internal val store get() = delegate.store

    override fun loadAgentPresets(onSuccess: (List<DshAgentPresetOption>) -> Unit, onError: (String) -> Unit) =
        delegate.loadAgentPresets(onSuccess, onError)

    override fun loadHostVersion(onSuccess: (String) -> Unit, onError: (String) -> Unit) =
        delegate.loadHostVersion(onSuccess, onError)

    override fun describeSettings(onSuccess: (DshSettingsSnapshot) -> Unit, onError: (String) -> Unit) =
        delegate.describeSettings(onSuccess, onError)

    override fun updateSetting(ns: String, patch: JSONObject, expectedRevision: Int, onSuccess: () -> Unit, onError: (String) -> Unit) =
        delegate.updateSetting(ns, patch, expectedRevision, onSuccess, onError)

    fun isProductReady(): Boolean = delegate.isProductReady()
    fun loadPluginInventory(onSuccess: (List<DshPluginEntry>) -> Unit, onError: (String) -> Unit) =
        delegate.loadPluginInventory(onSuccess, onError)
    fun pluginAction(entryId: String, action: String, onSuccess: () -> Unit, onError: (String) -> Unit) =
        delegate.pluginAction(entryId, action, onSuccess, onError)
    fun stop() = delegate.stop()
    fun respondApproval(
        rpcId: String,
        sessionId: String,
        approvalId: String,
        outcome: String,
        callback: (Boolean, String) -> Unit,
    ) = delegate.respondApproval(rpcId, sessionId, approvalId, outcome, callback)

    fun respondQuestion(
        rpcId: String,
        sessionId: String,
        answer: JSONObject,
        callback: (Boolean, String) -> Unit,
    ) = delegate.respondQuestion(rpcId, sessionId, answer, callback)

    fun respondQuestionCancel(
        rpcId: String,
        sessionId: String,
        callback: (Boolean, String) -> Unit,
    ) = delegate.respondQuestionCancel(rpcId, sessionId, callback)

    fun clearPending(rpcId: String) = delegate.clearPending(rpcId)

    /** 会话产生新消息时刷新其 updatedAt（消息时间），供抽屉 workspaceGroups 实时重排。 */
    fun touchSessionActivity(sessionId: String, updatedAt: Long) = delegate.touchSessionActivity(sessionId, updatedAt)

    fun loadWebTimeline(
        sessionId: String,
        onSuccess: (List<DshWebTimelineItem>) -> Unit,
        onError: (String) -> Unit = {},
        isCurrent: () -> Boolean = { true },
    ) = delegate.loadWebTimeline(sessionId, onSuccess, onError, isCurrent)

    fun loadCompleteHistory(sessionId: String, onSuccess: (JSONArray) -> Unit,
        onError: (String) -> Unit, isCurrent: () -> Boolean = { true }) =
        delegate.loadCompleteHistory(sessionId, onSuccess, onError, isCurrent)

    fun adoptLiveStream(
        sessionId: String,
        onDelta: (String, Boolean) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ): DshStreamHandle = delegate.adoptLiveStream(sessionId, onDelta, onComplete, onError)

    fun detachLiveStreams(sessionId: String) = delegate.detachLiveStreams(sessionId)

    fun loadSkills(sessionId: String, onSuccess: (List<DshSkill>) -> Unit, onError: (String) -> Unit = {}) =
        delegate.loadSkills(sessionId, onSuccess, onError)

    fun goalEdit(sessionId: String, goal: DshGoalSnapshot, objective: String, callback: (DshRpcError?) -> Unit) =
        delegate.goalEdit(sessionId, goal, objective, callback)

    fun goalPause(sessionId: String, goal: DshGoalSnapshot, callback: (DshRpcError?) -> Unit) =
        delegate.goalPause(sessionId, goal, callback)

    fun goalResume(sessionId: String, goal: DshGoalSnapshot, callback: (DshRpcError?) -> Unit) =
        delegate.goalResume(sessionId, goal, callback)

    fun goalClear(sessionId: String, goal: DshGoalSnapshot, callback: (DshRpcError?) -> Unit) =
        delegate.goalClear(sessionId, goal, callback)

    fun loadAttachment(
        sessionId: String,
        attachmentId: String,
        callback: (String?, String?) -> Unit,
    ) = delegate.loadAttachment(sessionId, attachmentId, callback)
    fun streamReplyWithImages(
        pagerId: String,
        sessionId: String,
        prompt: String,
        images: List<DshPendingImage>,
        onDelta: (String, Boolean) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ): DshStreamHandle = delegate.streamReplyWithImages(pagerId, sessionId, prompt, images, onDelta, onComplete, onError)

    fun queue(sessionId: String): List<DshQueueItem> = delegate.queue(sessionId)

    fun jobs(sessionId: String): List<DshJobItem> = delegate.jobs(sessionId)

    fun workspaceGroups(): List<DshWorkspaceGroup> = delegate.workspaceGroups()

    /** 归档会话按项目分组（只含已归档会话），供归档页项目筛选与分组展示。 */
    fun archivedWorkspaceGroups(): List<DshWorkspaceGroup> =
        delegate.workspaceGroups(includeArchived = true, archivedOnly = true)

    fun loadSessionCatalog(onSuccess: (DshSessionCatalog) -> Unit, onError: (DshRpcError) -> Unit) =
        delegate.loadSessionCatalog(onSuccess, onError)

    fun workspaceIdForSession(sessionId: String): String? = delegate.workspaceIdForSession(sessionId)

    fun blankSessionInWorkspace(workspaceId: String?): DshSession? = delegate.blankSessionInWorkspace(workspaceId)

    fun pendingInteractions(sessionId: String): Pair<DshPendingApproval?, DshPendingQuestion?> =
        delegate.pendingInteractions(sessionId)

    fun updateQueue(
        sessionId: String,
        itemId: String,
        action: com.tencent.kuikly.core.nvi.serialization.json.JSONObject,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) = delegate.updateQueue(sessionId, itemId, action, callback)

    fun renameSession(
        sessionId: String,
        title: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) = delegate.renameSession(sessionId, title, callback)

    fun archiveSession(
        sessionId: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) = delegate.archiveSession(sessionId, callback)

    fun forkMessage(
        sessionId: String,
        message: DshMessage,
        callback: (String?, DshRpcError?) -> Unit,
    ) = delegate.forkMessage(sessionId, message, callback)

    fun sessionExportUrl(sessionId: String): String = delegate.sessionExportUrl(sessionId)

    fun listDirectory(
        path: String?,
        callback: (DshDirectoryListing?, DshRpcError?) -> Unit,
    ) = delegate.listDirectory(path, callback)

    fun createDirectory(
        path: String,
        name: String,
        callback: (String?, DshRpcError?) -> Unit,
    ) = delegate.createDirectory(path, name, callback)

    fun createWorkspace(
        path: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) = delegate.createWorkspace(path, callback)

    fun renameWorkspace(
        workspaceId: String,
        title: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) = delegate.renameWorkspace(workspaceId, title, callback)

    fun deleteWorkspace(
        workspaceId: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) = delegate.deleteWorkspace(workspaceId, callback)

    fun moveWorkspaceBefore(
        workspaceId: String,
        beforeWorkspaceId: String?,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) = delegate.moveWorkspaceBefore(workspaceId, beforeWorkspaceId, callback)

    override fun loadCredentialSetup(onSuccess: (DshCredentialSetup) -> Unit, onError: (String) -> Unit) =
        delegate.loadCredentialSetup(onSuccess, onError)

    override fun saveDeepSeekApiKey(apiKey: String, onSuccess: () -> Unit, onError: (String) -> Unit) =
        delegate.saveDeepSeekApiKey(apiKey, onSuccess, onError)

    override fun loadModels(sessionId: String, onSuccess: (DshSessionModels) -> Unit, onError: (String) -> Unit) =
        delegate.loadModels(sessionId, onSuccess, onError)

    override fun selectModel(sessionId: String, option: DshModelOption, onSuccess: (DshModelOption) -> Unit, onError: (String) -> Unit) =
        delegate.selectModel(sessionId, option, onSuccess, onError)

    override fun loadSessions(onSuccess: (List<DshSession>) -> Unit, onError: (String) -> Unit) =
        delegate.loadSessions(onSuccess, onError)

    override fun createSession(workspaceId: String?, onSuccess: (String) -> Unit, onError: (String) -> Unit, permission: String?, agentPreset: String?) =
        delegate.createSession(workspaceId, onSuccess, onError, permission, agentPreset)

    override fun loadHistory(sessionId: String, onSuccess: (List<DshMessage>) -> Unit, onError: (String) -> Unit) =
        delegate.loadHistory(sessionId, onSuccess, onError)

    fun streamReply(
        pagerId: String,
        sessionId: String,
        prompt: String,
        onDelta: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ): DshStreamHandle = delegate.streamReply(pagerId, sessionId, prompt, onDelta, onComplete, onError)

    override fun streamReply(
        pagerId: String,
        sessionId: String,
        prompt: String,
        onDelta: (String, Boolean) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ): DshStreamHandle = delegate.streamReply(pagerId, sessionId, prompt, onDelta, onComplete, onError)

    /**
     * 调用 DSH 插件 HTTP 端点。
     * 插件端点不在标准 RPC 路径下，通过 HTTP POST 直接调用。
     */
    fun callPlugin(
        endpoint: String,
        payload: JSONObject,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) = delegate.callPlugin(endpoint, payload, callback)

    /** 读取 Host 会话元数据（createdAt/cwd），供归档页排序与项目分组。 */
    fun loadSessionMeta(onSuccess: (List<DshSessionMeta>) -> Unit, onError: (String) -> Unit) {
        callPlugin("meta", JSONObject()) { value, error ->
            if (error != null || value == null) onError(error?.message ?: "Host 未返回会话元数据")
            else runCatching { parseDshSessionMeta(value) }.onSuccess(onSuccess)
                .onFailure { onError(it.message ?: "会话元数据解析失败") }
        }
    }

    /** 删除单条会话（Host 文件级删除）。 */
    fun deleteSession(sessionId: String, callback: (Boolean, String) -> Unit) {
        callPlugin("delete", JSONObject().apply { put("sessionId", sessionId) }) { _, error ->
            if (error != null) callback(false, error.message) else callback(true, "")
        }
    }

    /** 批量删除会话，回调删除成功的 id 与失败原因（id to message）。 */
    fun deleteSessions(sessionIds: List<String>, callback: (List<String>, List<Pair<String, String>>) -> Unit) {
        val ids = JSONArray()
        sessionIds.forEach { ids.put(it) }
        callPlugin("deleteMany", JSONObject().apply { put("sessionIds", ids) }) { value, error ->
            if (error != null || value == null) {
                callback(emptyList(), sessionIds.map { it to (error?.message ?: "删除失败") })
                return@callPlugin
            }
            val deletedJson = value.optJSONArray("deleted") ?: JSONArray()
            val deleted = buildList {
                for (i in 0 until deletedJson.length()) deletedJson.optString(i)?.takeIf { it.isNotEmpty() }?.let(::add)
            }
            val failedJson = value.optJSONArray("failed") ?: JSONArray()
            val failed = buildList {
                for (i in 0 until failedJson.length()) {
                    val item = failedJson.optJSONObject(i) ?: continue
                    add(item.optString("sessionId") to item.optString("error", "删除失败"))
                }
            }
            callback(deleted, failed)
        }
    }
}

internal fun parseDshSessionMeta(value: JSONObject): List<DshSessionMeta> {
    val items = value.optJSONArray("sessions") ?: JSONArray()
    return buildList {
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val id = item.optString("sessionId")
            if (id.isEmpty()) continue
            add(DshSessionMeta(id, item.optLong("createdAt"), item.optString("cwd")))
        }
    }
}
