package com.example.dsh.storage

import com.example.dsh.host.DshConnectionMode
import com.example.dsh.session.DshLegacyRemoteProfile
import com.example.dsh.message.DshMessage
import com.example.dsh.session.DshRelayProfile
import com.example.dsh.session.DshRemoteProfile
import com.example.dsh.session.DshSession
actual fun createDshLocalStore(path: String, legacyProfile: DshLegacyRemoteProfile?): DshLocalStore = EmptyDshLocalStore

private object EmptyDshLocalStore : DshLocalStore {
    override fun loadApiKey(): String = ""
    override fun saveApiKey(apiKey: String) = Unit
    override fun loadLastConnectionMode(): DshConnectionMode = DshConnectionMode.RELAY
    override fun saveLastConnectionMode(mode: DshConnectionMode) = Unit
    override fun loadRemoteProfile(): DshRemoteProfile? = null
    override fun saveRemoteProfile(profile: DshRemoteProfile) = Unit
    override fun loadRelayProfile(): DshRelayProfile? = null
    override fun saveRelayProfile(profile: DshRelayProfile) = Unit
    override fun clearRelayProfile() = Unit
    override fun migrateLegacyRemoteProfile(profile: DshLegacyRemoteProfile): Boolean = false
    override fun loadSessions(connectionId: String): List<DshSession> = emptyList()
    override fun replaceSessions(connectionId: String, sessions: List<DshSession>) = Unit
    override fun loadMessages(connectionId: String, sessionId: String): List<DshMessage> = emptyList()
    override fun replaceMessages(connectionId: String, sessionId: String, messages: List<DshMessage>) = Unit
    override fun clearScope(scopeId: String) = Unit
    override fun deleteSession(scopeId: String, sessionId: String) = Unit
}
