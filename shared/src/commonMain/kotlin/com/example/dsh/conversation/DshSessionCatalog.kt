package com.example.dsh.conversation

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** A paired Host snapshot. Archived sessions remain available without entering the main list. */
internal data class DshSessionCatalog(
    val sessions: List<DshSession>,
    val archivedIds: Set<String>,
    val workspaceJson: String,
) {
    val active: List<DshSession> get() = sessions.filterNot { it.id in archivedIds }
    val archived: List<DshSession> get() = sessions.filter { it.id in archivedIds }
        .sortedByDescending { it.updatedAt }

    fun nextActive(preferredId: String?, preferBlank: Boolean = false): DshSession? {
        val candidates = active
        if (preferBlank) return candidates.firstOrNull { it.blank }
        return candidates.firstOrNull { it.id == preferredId }
            ?: candidates.filterNot { it.blank }.maxByOrNull { it.updatedAt }
            ?: candidates.firstOrNull()
    }

    /** An explicitly opened archived history can survive refresh; defaults still exclude archives. */
    fun forReload(preferredId: String?, preferBlank: Boolean = false): DshSession? =
        if (preferBlank) nextActive(null, preferBlank = true)
        else sessions.firstOrNull { it.id == preferredId } ?: nextActive(null)
}

/** Joins two existing RPCs; a partial, superseded or invalid response never commits a catalog. */
internal class DshSessionCatalogLoader(
    private val request: (String, (JSONObject?, DshRpcError?) -> Unit) -> Unit,
    private val parseSessions: (JSONObject) -> List<DshSession>,
    private val commit: (DshSessionCatalog) -> Unit,
    private val connectionGeneration: () -> Long = { 0L },
) {
    private var revision = 0L

    fun invalidate() { revision++ }

    fun load(onSuccess: (DshSessionCatalog) -> Unit, onError: (DshRpcError) -> Unit) {
        val expected = ++revision
        val expectedConnection = connectionGeneration()
        var finished = false
        var workspace: JSONObject? = null
        var sessions: JSONObject? = null
        fun receive(method: String, value: JSONObject?, error: DshRpcError?) {
            if (finished) return
            val failure = when {
                expected != revision -> DshRpcError(SUPERSEDED, "会话列表请求已更新")
                expectedConnection != connectionGeneration() -> DshRpcError("generation-cancelled", "连接已变化，请刷新会话列表")
                error != null -> error
                value?.optJSONArray("items") == null -> DshRpcError("bad-response", "$method 缺少 items 列表")
                method == DshHostProtocol.WORKSPACE_LIST && value.optJSONArray("archivedSessionIds") == null ->
                    DshRpcError("bad-response", "workspace.list 缺少归档集合，无法确认归档状态")
                else -> null
            }
            if (failure != null) {
                finished = true
                onError(failure)
                return
            }
            if (method == DshHostProtocol.WORKSPACE_LIST) workspace = value else sessions = value
            val w = workspace ?: return
            val s = sessions ?: return
            val parsed = runCatching {
                val ids = w.optJSONArray("archivedSessionIds")!!
                DshSessionCatalog(parseSessions(s), buildSet {
                    for (index in 0 until ids.length()) ids.optString(index)?.takeIf { it.isNotEmpty() }?.let(::add)
                }, w.optJSONArray("items")!!.toString())
            }
            finished = true
            parsed.onSuccess { catalog -> commit(catalog); onSuccess(catalog) }
                .onFailure { onError(DshRpcError("bad-response", "会话列表解析失败")) }
        }
        request(DshHostProtocol.WORKSPACE_LIST) { value, error -> receive(DshHostProtocol.WORKSPACE_LIST, value, error) }
        if (!finished) request(DshHostProtocol.SESSION_LIST) { value, error -> receive(DshHostProtocol.SESSION_LIST, value, error) }
    }

    companion object { const val SUPERSEDED = "catalog-superseded" }
}
