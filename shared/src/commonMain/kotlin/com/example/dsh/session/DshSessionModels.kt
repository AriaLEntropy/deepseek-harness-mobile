package com.example.dsh.session

import com.example.dsh.host.DshConnectionMode
import com.example.dsh.models.DshModelOption

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

/** The small client-side model used by the first DSH surface. */
internal data class DshSession(
    val id: String,
    val title: String,
    val workspace: String,
    val updatedLabel: String,
    /** Host 会话项的 updatedAt（毫秒时间戳），用于按消息时间排序会话列表。 */
    val updatedAt: Long = 0L,
    /** Host 会话项的 createdAt（毫秒时间戳），由 session-manager meta 端点补充；缺失为 0。 */
    val createdAt: Long = 0L,
    val running: Boolean = false,
    val blank: Boolean = false,
    val cwd: String = "",
    val parentSessionId: String? = null,
    val origin: String? = null,
    val agentPreset: String? = null,
    val permission: String? = null,
    val subscribedLastSeq: Int = -1,
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

/** Host session-manager `meta` 端点的单条会话元数据。 */

/** Host session-manager `meta` 端点的单条会话元数据。 */
internal data class DshSessionMeta(
    val sessionId: String,
    val createdAt: Long,
    val cwd: String,
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
