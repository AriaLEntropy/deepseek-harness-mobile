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
    ) = delegate.loadWebTimeline(sessionId, onSuccess, onError)

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

    companion object {
        fun parseWebTimelineForTest(events: JSONArray): List<DshWebTimelineItem> =
            DshWebTimelineParser.parseWebTimeline(events)
    }

    fun queue(sessionId: String): List<DshQueueItem> = delegate.queue(sessionId)

    fun jobs(sessionId: String): List<DshJobItem> = delegate.jobs(sessionId)

    fun workspaceGroups(): List<DshWorkspaceGroup> = delegate.workspaceGroups()

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

    fun forkSession(
        sessionId: String,
        atSeq: Int?,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) = delegate.forkSession(sessionId, atSeq, callback)

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
}
