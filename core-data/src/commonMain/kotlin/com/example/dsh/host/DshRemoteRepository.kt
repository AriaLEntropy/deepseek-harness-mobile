package com.example.dsh.host

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.example.dsh.session.DshDirectoryListing
import com.example.dsh.session.DshGoalSnapshot
import com.example.dsh.session.DshJobItem
import com.example.dsh.message.DshMessage
import com.example.dsh.interaction.DshPendingApproval
import com.example.dsh.attachment.DshPendingImage
import com.example.dsh.interaction.DshPendingQuestion
import com.example.dsh.plugin.DshPluginEntry
import com.example.dsh.session.DshQueueItem
import com.example.dsh.host.DshRepository
import com.example.dsh.host.DshRpcError
import com.example.dsh.session.DshSession
import com.example.dsh.session.DshSessionMeta
import com.example.dsh.session.DshSkill
import com.example.dsh.host.DshStreamHandle
import com.example.dsh.attachment.DshUploadedFile
import com.example.dsh.message.DshWebTimelineItem
import com.example.dsh.session.DshWorkspaceGroup
import com.example.dsh.session.DshSessionCatalog

/**
 * Remote-only Host capability surface.
 *
 * Local mode deliberately keeps the legacy repository and does not implement this,
 * so pages depend on this interface (a capability check) rather than the concrete
 * remote implementation for remote-only operations.
 */
interface DshRemoteRepository : DshRepository {

    /** 已归档会话 id 快照，供归档/删除后重算活动会话。 */
    val archivedSessionIds: Set<String>

    /** Host 删除成功后同步内存 store，移除本地缓存会话。 */
    fun removeSession(sessionId: String)

    /** 用当前 store 的归档集与工作区基线组装会话目录（避免向调用方泄漏 store）。 */
    fun sessionCatalog(sessions: List<DshSession>): DshSessionCatalog

    fun isProductReady(): Boolean
    fun loadPluginInventory(onSuccess: (List<DshPluginEntry>) -> Unit, onError: (String) -> Unit)
    fun pluginAction(entryId: String, action: String, onSuccess: () -> Unit, onError: (String) -> Unit)
    fun stop()
    fun loadSessionCatalog(onSuccess: (DshSessionCatalog) -> Unit, onError: (DshRpcError) -> Unit)
    fun respondApproval(rpcId: String, sessionId: String, approvalId: String, outcome: String, callback: (Boolean, String) -> Unit)
    fun respondQuestion(rpcId: String, sessionId: String, answer: JSONObject, callback: (Boolean, String) -> Unit)
    fun respondQuestionCancel(rpcId: String, sessionId: String, callback: (Boolean, String) -> Unit)
    fun clearPending(rpcId: String)
    fun touchSessionActivity(sessionId: String, updatedAt: Long)
    fun loadCompleteHistory(sessionId: String, onSuccess: (JSONArray) -> Unit, onError: (String) -> Unit, isCurrent: () -> Boolean = { true })
    fun loadWebTimeline(
        sessionId: String,
        onSuccess: (List<DshWebTimelineItem>) -> Unit,
        onError: (String) -> Unit = {},
        isCurrent: () -> Boolean = { true },
    )
    fun loadSkills(sessionId: String, onSuccess: (List<DshSkill>) -> Unit, onError: (String) -> Unit = {})
    fun goalEdit(sessionId: String, goal: DshGoalSnapshot, objective: String, callback: (DshRpcError?) -> Unit)
    fun goalPause(sessionId: String, goal: DshGoalSnapshot, callback: (DshRpcError?) -> Unit)
    fun goalResume(sessionId: String, goal: DshGoalSnapshot, callback: (DshRpcError?) -> Unit)
    fun goalClear(sessionId: String, goal: DshGoalSnapshot, callback: (DshRpcError?) -> Unit)
    fun loadAttachment(sessionId: String, attachmentId: String, callback: (String?, String?) -> Unit)
    fun uploadAttachment(sessionId: String, name: String, mediaType: String, dataBase64: String, callback: (DshUploadedFile?, DshRpcError?) -> Unit)
    fun queue(sessionId: String): List<DshQueueItem>
    fun pendingInteractions(sessionId: String): Pair<DshPendingApproval?, DshPendingQuestion?>
    fun jobs(sessionId: String): List<DshJobItem>
    fun workspaceGroups(includeArchived: Boolean = false, archivedOnly: Boolean = false): List<DshWorkspaceGroup>
    fun archivedWorkspaceGroups(): List<DshWorkspaceGroup> = workspaceGroups(includeArchived = true, archivedOnly = true)
    fun workspaceIdForSession(sessionId: String): String?
    fun blankSessionInWorkspace(workspaceId: String?): DshSession?
    fun updateQueue(sessionId: String, itemId: String, action: JSONObject, callback: (JSONObject?, DshRpcError?) -> Unit)
    fun renameSession(sessionId: String, title: String, callback: (JSONObject?, DshRpcError?) -> Unit)
    fun archiveSession(sessionId: String, callback: (JSONObject?, DshRpcError?) -> Unit)
    fun unarchiveSession(sessionId: String, callback: (JSONObject?, DshRpcError?) -> Unit)
    fun forkMessage(sessionId: String, message: DshMessage, callback: (String?, DshRpcError?) -> Unit)
    fun sessionExportUrl(sessionId: String, includeDescendants: Boolean = true): String
    fun listDirectory(path: String?, callback: (DshDirectoryListing?, DshRpcError?) -> Unit)
    fun createDirectory(path: String, name: String, callback: (String?, DshRpcError?) -> Unit)
    fun createWorkspace(path: String, callback: (JSONObject?, DshRpcError?) -> Unit)
    fun renameWorkspace(workspaceId: String, title: String, callback: (JSONObject?, DshRpcError?) -> Unit)
    fun deleteWorkspace(workspaceId: String, callback: (JSONObject?, DshRpcError?) -> Unit)
    fun moveWorkspaceBefore(workspaceId: String, beforeWorkspaceId: String?, callback: (JSONObject?, DshRpcError?) -> Unit)
    fun streamReply(
        pagerId: String,
        sessionId: String,
        prompt: String,
        onDelta: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ): DshStreamHandle
    fun streamReplyWithImages(
        pagerId: String,
        sessionId: String,
        prompt: String,
        images: List<DshPendingImage>,
        onDelta: (String, Boolean) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ): DshStreamHandle
    fun adoptLiveStream(
        sessionId: String,
        onDelta: (String, Boolean) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ): DshStreamHandle
    fun detachLiveStreams(sessionId: String)
    fun callPlugin(endpoint: String, payload: JSONObject, callback: (JSONObject?, DshRpcError?) -> Unit)

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

fun parseDshSessionMeta(value: JSONObject): List<DshSessionMeta> {
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
