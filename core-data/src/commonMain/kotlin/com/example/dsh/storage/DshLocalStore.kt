package com.example.dsh.storage

import com.example.dsh.host.DshConnectionMode
import com.example.dsh.session.DshLegacyRemoteProfile
import com.example.dsh.message.DshMessage
import com.example.dsh.session.DshRelayProfile
import com.example.dsh.session.DshRemoteProfile
import com.example.dsh.session.DshSession
/** Small durable cache used to make the native client feel continuous across launches. */
interface DshLocalStore {
    fun loadApiKey(): String
    fun saveApiKey(apiKey: String)
    fun loadLastConnectionMode(): DshConnectionMode
    fun saveLastConnectionMode(mode: DshConnectionMode)
    fun loadRemoteProfile(): DshRemoteProfile?
    fun saveRemoteProfile(profile: DshRemoteProfile)
    fun loadRelayProfile(): DshRelayProfile?
    fun saveRelayProfile(profile: DshRelayProfile)
    fun clearRelayProfile()
    fun migrateLegacyRemoteProfile(profile: DshLegacyRemoteProfile): Boolean
    fun clearLegacyRemotePreferenceKeys() = Unit

    fun loadSessions(scopeId: String): List<DshSession>
    fun replaceSessions(scopeId: String, sessions: List<DshSession>)
    fun loadMessages(scopeId: String, sessionId: String): List<DshMessage>
    fun replaceMessages(scopeId: String, sessionId: String, messages: List<DshMessage>)

    fun clearScope(scopeId: String)

    /**
     * 永久删除一个会话及其所有消息。
     * 与 archive 不同，此操作不可恢复。
     * 如果 sessionId 不存在，静默成功（幂等）。
     */
    fun deleteSession(scopeId: String, sessionId: String)
}

expect fun createDshLocalStore(
    path: String,
    legacyProfile: DshLegacyRemoteProfile? = null,
): DshLocalStore
